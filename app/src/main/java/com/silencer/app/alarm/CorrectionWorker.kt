package com.silencer.app.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.silencer.app.control.SilenceController
import com.silencer.app.data.HolidayRepository
import com.silencer.app.data.SettingsStore
import com.silencer.app.logic.ScheduleChecker
import java.time.LocalDateTime

/**
 * 兜底任务：每 2 小时校正一次实际状态（针对系统拦截精确闹钟的最坏情况），
 * 重新调度下一次边界闹钟，并顺带维护节假日数据新鲜度。
 * 主机制仍是边界精确闹钟；本任务不常驻内存、正常路径无网络请求，
 * 单次开销约 10-50ms CPU，2 小时间隔对电量影响可忽略。
 */
class CorrectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val store = SettingsStore(applicationContext)
            val repo = HolidayRepository(applicationContext)
            // 顺带维护节假日数据新鲜度：过期/跨年缺数据才拉，失败有冷却，不会频繁请求
            repo.refreshIfStale()
            val settings = store.current()
            if (settings.enabled) {
                val shouldSilent = ScheduleChecker.isSilentAt(
                    now = LocalDateTime.now(),
                    windows = settings.windows,
                    weekdays = settings.weekdays,
                    respectHolidays = settings.respectHolidays,
                    dayType = repo::dayType
                )
                val controller = SilenceController(applicationContext)
                // 状态偏差超过阈值才动手，避免频繁打扰系统
                if (controller.isActuallySilent(settings.silenceMode) != shouldSilent) {
                    controller.apply(shouldSilent, settings.silenceMode)
                }
            }
            AlarmScheduler.schedule(applicationContext, settings)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
