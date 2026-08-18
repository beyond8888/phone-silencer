package com.silencer.app.ui

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silencer.app.alarm.AlarmScheduler
import com.silencer.app.control.SilenceController
import com.silencer.app.data.HolidayRepository
import com.silencer.app.data.SettingsStore
import com.silencer.app.logic.ScheduleChecker
import com.silencer.app.logic.Settings
import com.silencer.app.logic.WorkWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class UiState(
    val settings: Settings = Settings(),
    val now: LocalDateTime = LocalDateTime.now(),
    val silentNow: Boolean = false,
    val nextTransition: LocalDateTime? = null,
    val dndGranted: Boolean = false,
    val batteryOk: Boolean = false,
    val exactAlarmOk: Boolean = true,
    val cachedYears: List<Int> = emptyList(),
    val lastRefresh: Long = 0L,
    val holidayBusy: Boolean = false,
    val holidayMessage: String? = null
)

class SilencerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val repo = HolidayRepository(app)
    private val controller = SilenceController(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // 设置变化 -> 更新界面 + 重新调度闹钟
        viewModelScope.launch {
            store.settings.collect { settings ->
                _state.update {
                    it.copy(
                        settings = settings,
                        dndGranted = controller.hasDndAccess(),
                        batteryOk = isIgnoringBatteryOptimizations(),
                        exactAlarmOk = canScheduleExactAlarms(),
                        silentNow = computeSilent(settings),
                        nextTransition = computeNext(settings)
                    )
                }
                AlarmScheduler.schedule(getApplication(), settings)
            }
        }
        // 每秒刷新当前时刻与状态
        viewModelScope.launch {
            while (true) {
                val s = _state.value.settings
                _state.update {
                    it.copy(
                        now = LocalDateTime.now(),
                        silentNow = computeSilent(s),
                        nextTransition = computeNext(s),
                        dndGranted = controller.hasDndAccess(),
                        batteryOk = isIgnoringBatteryOptimizations(),
                        exactAlarmOk = canScheduleExactAlarms()
                    )
                }
                delay(1_000)
            }
        }
        refreshCachedYears()
        autoRefreshHolidays()
    }

    /** 启动时静默检查数据新鲜度，过期/缺失才拉取，不打扰用户 */
    private fun autoRefreshHolidays() {
        viewModelScope.launch {
            if (repo.refreshIfStale()) refreshCachedYears()
        }
    }

    private fun computeSilent(settings: Settings): Boolean =
        settings.enabled && ScheduleChecker.isSilentAt(
            now = LocalDateTime.now(),
            windows = settings.windows,
            weekdays = settings.weekdays,
            respectHolidays = settings.respectHolidays,
            dayType = repo::dayType
        )

    private fun computeNext(settings: Settings): LocalDateTime? =
        ScheduleChecker.nextTransitionAfter(
            now = LocalDateTime.now(),
            windows = settings.windows,
            weekdays = settings.weekdays,
            respectHolidays = settings.respectHolidays,
            dayType = repo::dayType
        )

    // ---- 用户操作 ----

    fun toggleEnabled() {
        val s = _state.value
        val next = !s.settings.enabled
        if (!next) {
            // 关闭总开关：立即恢复正常响铃，不残留静音状态
            controller.apply(false, s.settings.silenceMode)
        }
        mutate { it.copy(enabled = next) }
    }

    fun addWindow(window: WorkWindow) = mutate { it.copy(windows = it.windows + window) }

    fun updateWindow(index: Int, window: WorkWindow) = mutate { s ->
        s.copy(windows = s.windows.toMutableList().also { if (index in it.indices) it[index] = window })
    }

    fun removeWindow(index: Int) = mutate { s ->
        s.copy(windows = s.windows.toMutableList().also { if (index in it.indices) it.removeAt(index) })
    }

    fun toggleWeekday(weekday: Int) = mutate { s ->
        val next = if (weekday in s.weekdays) s.weekdays - weekday else s.weekdays + weekday
        s.copy(weekdays = next)
    }

    fun toggleHolidays() = mutate { it.copy(respectHolidays = !it.respectHolidays) }

    fun setSilenceMode(mode: String) = mutate { it.copy(silenceMode = mode) }

    fun refreshHolidays() {
        viewModelScope.launch {
            _state.update { it.copy(holidayBusy = true) }
            val result = repo.refreshCurrentAndNext()
            _state.update {
                it.copy(
                    holidayBusy = false,
                    holidayMessage = result.message,
                    lastRefresh = System.currentTimeMillis(),
                    cachedYears = listOf(LocalDateTime.now().year, LocalDateTime.now().year + 1)
                        .filter { y -> repo.hasCachedYear(y) }
                )
            }
        }
    }

    /** 立即按当前规则执行一次静音/恢复（用于手动测试） */
    fun applyNow() {
        val s = _state.value
        controller.apply(s.silentNow, s.settings.silenceMode)
    }

    fun openDndSettings() = openSettingsIntent(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun openBatterySettings() = openSettingsIntent(
        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${getApplication<Application>().packageName}"))
    )

    fun openExactAlarmSettings() = openSettingsIntent(
        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${getApplication<Application>().packageName}"))
    )

    // ---- 私有 ----

    private fun mutate(transform: (Settings) -> Settings) {
        viewModelScope.launch { store.update(transform) }
    }

    private fun openSettingsIntent(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    private fun refreshCachedYears() {
        val now = LocalDateTime.now()
        _state.update {
            it.copy(
                cachedYears = listOf(now.year, now.year + 1).filter { y -> repo.hasCachedYear(y) }
            )
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getApplication<Application>().getSystemService(AlarmManager::class.java)
        return am.canScheduleExactAlarms()
    }
}
