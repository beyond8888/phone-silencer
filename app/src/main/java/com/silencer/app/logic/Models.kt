package com.silencer.app.logic

/**
 * 某一天的属性：工作日 / 周末 / 法定假日 / 调休补班。
 * 来自节假日 API（type 字段）或星期回退。
 */
enum class DayType {
    WORKDAY,       // 普通工作日（周一到周五）
    WEEKEND,       // 普通周末
    HOLIDAY,       // 法定节假日（不该静音）
    EXTRA_WORKDAY  // 调休补班（周末上班，按工作日处理）
}

/**
 * 一个工作时间段，用“当天 0 点起的分钟数”表示，支持跨天。
 * 例：09:00-18:00 -> (540, 1080)；22:00-06:00 -> (1320, 360)。
 */
data class WorkWindow(val startMinute: Int, val endMinute: Int) {

    init {
        require(startMinute in 0..1439) { "startMinute out of range: $startMinute" }
        require(endMinute in 0..1439) { "endMinute out of range: $endMinute" }
        require(startMinute != endMinute) { "start and end must differ" }
    }

    /** 某个分钟时刻是否落在本窗口内（含跨天） */
    fun contains(minute: Int): Boolean =
        if (startMinute < endMinute) minute >= startMinute && minute < endMinute
        else minute >= startMinute || minute < endMinute

    /** 与另一窗口是否有重叠时刻（含跨天窗口，按 24 小时环判定） */
    fun overlaps(other: WorkWindow): Boolean =
        contains(other.startMinute) || other.contains(startMinute)

    fun startLabel(): String = String.format("%02d:%02d", startMinute / 60, startMinute % 60)

    fun endLabel(): String = String.format("%02d:%02d", endMinute / 60, endMinute % 60)

    override fun toString(): String = "${startLabel()}-${endLabel()}"

    companion object {
        /** 解析 "09:00-12:00" */
        fun parse(s: String): WorkWindow? = runCatching {
            val parts = s.split("-")
            require(parts.size == 2)
            WorkWindow(minuteOf(parts[0]), minuteOf(parts[1]))
        }.getOrNull()

        fun minuteOf(hhmm: String): Int {
            val (h, m) = hhmm.split(":").map { it.trim().toInt() }
            require(h in 0..23 && m in 0..59)
            return h * 60 + m
        }
    }
}

/** 全部用户设置（与 DataStore 持久化的字段一一对应） */
data class Settings(
    val enabled: Boolean = false,
    val windows: List<WorkWindow> = emptyList(),
    val weekdays: Set<Int> = emptySet(),   // 1=周一 .. 7=周日
    val respectHolidays: Boolean = true,   // 法定节假日不静音；调休补班按工作日
    val silenceMode: String = "dnd"        // "dnd" 勿扰模式 / "ringer" 仅静音铃声
) {
    companion object {
        fun defaultWindows(): List<WorkWindow> = listOf(
            WorkWindow(9 * 60, 12 * 60),
            WorkWindow(14 * 60, 18 * 60)
        )

        fun defaultWeekdays(): Set<Int> = setOf(1, 2, 3, 4, 5)
    }
}
