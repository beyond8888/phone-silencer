package com.silencer.app.data

import android.content.Context
import android.content.SharedPreferences
import com.silencer.app.logic.DayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 节假日数据仓库：
 *  - 从 timor.tech 免费接口拉取全年数据（type 字段：0 工作日 / 1 周末 / 2 节日 / 3 调休补班）
 *  - 本地缓存到 SharedPreferences，离线可用
 *  - 注意：holiday 对象里普通周末也标了 holiday=true，因此判定必须用 type 对象
 *
 * 更新策略（国务院每年 11 月左右公布次年安排）：
 *  - 数据超过 7 天视为过期，触发自动刷新（当年 + 明年）
 *  - 每年 11 月起必须预拉下一年数据，跨年不中断
 *  - 刷新失败后 6 小时冷却，避免反复请求
 */
class HolidayRepository(context: Context) {

    companion object {
        private const val STALE_DAYS = 7L                  // 数据超过 7 天视为过期
        private const val RETRY_COOLDOWN_HOURS = 6L        // 失败后冷却时间
        private const val MS_PER_DAY = 24L * 3600_000L
        private const val MS_PER_HOUR = 3600_000L
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("holiday_cache", Context.MODE_PRIVATE)

    /** 内存缓存：避免每次判断都解析整年 JSON */
    private val memoryTypes = mutableMapOf<Int, Map<String, Int>>()

    /** 判断某天类型；无缓存数据时按星期回退 */
    fun dayType(date: LocalDate): DayType {
        val year = date.year
        val types = memoryTypes.getOrPut(year) {
            prefs.getString("types_$year", null)?.let(::parseTypes)?.let { json ->
                json.keys().asSequence().associateWith { json.optInt(it, 0) }
            } ?: emptyMap()
        }
        val t = types[date.toString()] ?: -1
        return when (t) {
            2 -> DayType.HOLIDAY
            3 -> DayType.EXTRA_WORKDAY
            1 -> DayType.WEEKEND
            0 -> DayType.WORKDAY
            else -> fallbackByWeekday(date)
        }
    }

    fun getCachedYear(): Int = prefs.getInt("cached_year", 0)

    fun hasCachedYear(year: Int): Boolean = prefs.contains("types_$year")

    /** 上次成功刷新时间戳；0 表示从未成功过 */
    fun lastRefreshTime(): Long = prefs.getLong("last_refresh", 0L)

    /**
     * 数据是否新鲜：当年有缓存 +（11 月起）明年有缓存 + 7 天内成功更新过。
     * 节假日安排一年内几乎不变，7 天窗口兼顾正确性与省流量。
     */
    fun isFresh(now: LocalDate = LocalDate.now()): Boolean {
        val hasThisYear = hasCachedYear(now.year)
        // 11 月起必须预拉下一年（国务院一般 11 月中旬公布次年安排）
        val nextYearNeeded = now.monthValue >= 11
        val hasNextYear = !nextYearNeeded || hasCachedYear(now.year + 1)
        val updatedRecently = lastRefreshTime() > 0 &&
            System.currentTimeMillis() - lastRefreshTime() < STALE_DAYS * MS_PER_DAY
        return hasThisYear && hasNextYear && updatedRecently
    }

    /**
     * 静默自动刷新：数据过期/缺失才拉，带失败冷却。
     * 供 App 启动和后台兜底任务调用，返回是否真的发生了刷新。
     */
    suspend fun refreshIfStale(): Boolean {
        if (isFresh()) return false
        val lastAttempt = prefs.getLong("last_attempt", 0L)
        if (System.currentTimeMillis() - lastAttempt < RETRY_COOLDOWN_HOURS * MS_PER_HOUR) return false
        prefs.edit().putLong("last_attempt", System.currentTimeMillis()).apply()
        return refreshCurrentAndNext().ok
    }

    /** 拉取当前年与下一年数据，返回成功拉取的年份列表 */
    suspend fun refreshCurrentAndNext(): RefreshResult = withContext(Dispatchers.IO) {
        val thisYear = LocalDate.now().year
        val okYears = mutableListOf<Int>()
        var message = ""
        for (year in listOf(thisYear, thisYear + 1)) {
            if (fetchYear(year)) okYears.add(year)
        }
        message = if (okYears.isEmpty()) {
            "网络获取失败，请检查网络后重试"
        } else {
            "已更新 ${okYears.joinToString("、") { "$it 年" }}"
        }
        RefreshResult(okYears.isNotEmpty(), message, okYears)
    }

    /** 拉取指定年份并缓存，返回是否成功 */
    private fun fetchYear(year: Int): Boolean = runCatching {
        val url = URL("https://timor.tech/api/holiday/year/$year/?type=Y&week=Y")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) return false
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("code") != 0) return false
            val types = root.optJSONObject("type") ?: return false
            val map = JSONObject()
            for (key in types.keys()) {
                val item = types.optJSONObject(key) ?: continue
                map.put(key, item.optInt("type", 0))
            }
            prefs.edit()
                .putString("types_$year", map.toString())
                .putInt("cached_year", year)
                .putLong("last_refresh", System.currentTimeMillis())
                .apply()
            true
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    private fun parseTypes(json: String): JSONObject = runCatching { JSONObject(json) }.getOrNull() ?: JSONObject()

    private fun fallbackByWeekday(date: LocalDate): DayType {
        val v = date.dayOfWeek.value
        return if (v == 6 || v == 7) DayType.WEEKEND else DayType.WORKDAY
    }

    data class RefreshResult(val ok: Boolean, val message: String, val years: List<Int>)
}
