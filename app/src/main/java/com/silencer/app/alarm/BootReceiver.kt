package com.silencer.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.silencer.app.control.SilenceController
import com.silencer.app.data.HolidayRepository
import com.silencer.app.data.SettingsStore
import com.silencer.app.logic.ScheduleChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** 开机 / 应用更新后恢复调度并校正当前状态 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val store = SettingsStore(context)
                val settings = store.current()
                if (settings.enabled) {
                    val repo = HolidayRepository(context)
                    val shouldSilent = ScheduleChecker.isSilentAt(
                        now = LocalDateTime.now(),
                        windows = settings.windows,
                        weekdays = settings.weekdays,
                        respectHolidays = settings.respectHolidays,
                        dayType = repo::dayType
                    )
                    SilenceController(context).apply(shouldSilent, settings.silenceMode)
                }
                AlarmScheduler.schedule(context, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
