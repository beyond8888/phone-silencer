package com.silencer.app.logic

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 纯判定引擎，不依赖 Android 任何 API，便于单测与 Web Demo 对齐。
 * 规则：
 *  1. 未开启 / 未配置时段 / 未勾选星期 -> 永不静音；
 *  2. 尊重节假日时：法定假日、普通周末不静音；调休补班按工作日；
 *  3. 星期匹配且当前时刻落在任一窗口内 -> 静音。
 */
object ScheduleChecker {

    /** 指定时刻是否应当处于静音状态 */
    fun isSilentAt(
        now: LocalDateTime,
        windows: List<WorkWindow>,
        weekdays: Set<Int>,
        respectHolidays: Boolean,
        dayType: (LocalDate) -> DayType
    ): Boolean {
        if (windows.isEmpty() || weekdays.isEmpty()) return false
        val date = now.toLocalDate()
        if (respectHolidays) {
            when (dayType(date)) {
                DayType.HOLIDAY -> return false
                DayType.WEEKEND -> return false
                DayType.EXTRA_WORKDAY -> Unit // 补班按工作日，继续
                DayType.WORKDAY -> Unit
            }
        }
        if (date.dayOfWeek.value !in weekdays) return false
        val minute = now.hour * 60 + now.minute
        return windows.any { it.contains(minute) }
    }

    /**
     * 计算从 now 起下一次“静音状态发生变化”的时刻（精确到分钟）。
     * 扫描未来 10 天内所有窗口的起止边界，取状态翻转最早的边界。
     */
    fun nextTransitionAfter(
        now: LocalDateTime,
        windows: List<WorkWindow>,
        weekdays: Set<Int>,
        respectHolidays: Boolean,
        dayType: (LocalDate) -> DayType
    ): LocalDateTime? {
        if (windows.isEmpty() || weekdays.isEmpty()) return null
        val candidates = mutableListOf<LocalDateTime>()
        for (offset in 0L..10L) {
            val day = now.toLocalDate().plusDays(offset)
            for (w in windows) {
                val start = day.atTime(w.startMinute / 60, w.startMinute % 60)
                val end = if (w.startMinute < w.endMinute) {
                    day.atTime(w.endMinute / 60, w.endMinute % 60)
                } else {
                    day.plusDays(1).atTime(w.endMinute / 60, w.endMinute % 60)
                }
                for (t in listOf(start, end)) {
                    if (t <= now) continue
                    val before = isSilentAt(t.minusSeconds(1), windows, weekdays, respectHolidays, dayType)
                    val after = isSilentAt(t, windows, weekdays, respectHolidays, dayType)
                    if (before != after) candidates.add(t)
                }
            }
        }
        return candidates.minOrNull()
    }
}
