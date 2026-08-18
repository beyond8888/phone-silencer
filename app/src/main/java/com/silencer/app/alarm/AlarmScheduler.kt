package com.silencer.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.silencer.app.data.HolidayRepository
import com.silencer.app.logic.ScheduleChecker
import com.silencer.app.logic.Settings
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 精确闹钟调度：在“下一次状态切换边界”触发 [ScheduleAlarmReceiver]（主机制，准点）。
 * 同时注册低频 WorkManager 周期性兜底任务，防止华为等系统推迟/拦截闹钟后状态长期错乱。
 */
object AlarmScheduler {

    private const val REQUEST_CODE = 1001
    private const val CORRECTION_WORK_NAME = "silencer-correction"

    /**
     * 兜底任务间隔：主机制是边界精确闹钟，兜底只防最坏情况。
     * 该任务不常驻内存、正常路径无网络请求，单次开销约 10-50ms CPU，
     * 故 2 小时一次电量影响可忽略，同时保证状态错乱最长 2 小时自愈。
     */
    private const val CORRECTION_INTERVAL_HOURS = 2L

    fun schedule(context: Context, settings: Settings) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        am.cancel(pi)

        if (!settings.enabled) {
            // 总开关关闭：取消兜底任务，后台彻底停用（闹钟已在上方 cancel）
            WorkManager.getInstance(context).cancelUniqueWork(CORRECTION_WORK_NAME)
            return
        }

        ensureCorrectionWorker(context)

        val repo = HolidayRepository(context)
        val next = ScheduleChecker.nextTransitionAfter(
            now = LocalDateTime.now(),
            windows = settings.windows,
            weekdays = settings.weekdays,
            respectHolidays = settings.respectHolidays,
            dayType = repo::dayType
        )
        if (next == null) return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            // 用户未授予精确闹钟权限时退化为普通闹钟
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ScheduleAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ensureCorrectionWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<CorrectionWorker>(
            CORRECTION_INTERVAL_HOURS, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CORRECTION_WORK_NAME,
            // REPLACE：保证间隔调整后旧任务也会被替换为新间隔（KEEP 不会）
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }
}
