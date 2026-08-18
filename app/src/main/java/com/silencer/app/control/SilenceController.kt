package com.silencer.app.control

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/**
 * 静音/恢复控制器。
 * 支持两种模式：
 *  - dnd：勿扰模式（只允许闹钟响），需要“通知使用权”，华为上最稳定
 *  - ringer：把铃声模式切到静音（API 25+ 第三方应用可切 RINGER_MODE_SILENT）
 * 进入静音前保存原状态，恢复时还原，避免破坏用户手动设置。
 */
class SilenceController(private val context: Context) {

    private val nm = context.getSystemService(NotificationManager::class.java)
    private val am = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("silence_state", Context.MODE_PRIVATE)

    fun hasDndAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    fun apply(shouldSilent: Boolean, mode: String) {
        if (shouldSilent) enterSilent(mode) else exitSilent(mode)
    }

    /** 当前是否真的处于“静音/勿扰”状态 */
    fun isActuallySilent(mode: String): Boolean =
        if (mode == "dnd") {
            nm.isNotificationPolicyAccessGranted &&
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
        } else {
            am.ringerMode == AudioManager.RINGER_MODE_SILENT
        }

    private fun enterSilent(mode: String) {
        if (mode == "dnd" && nm.isNotificationPolicyAccessGranted) {
            if (!prefs.contains("saved_filter")) {
                prefs.edit().putInt("saved_filter", nm.currentInterruptionFilter).apply()
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        } else {
            if (!prefs.contains("saved_ringer")) {
                prefs.edit().putInt("saved_ringer", am.ringerMode).apply()
            }
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
        }
    }

    private fun exitSilent(mode: String) {
        if (mode == "dnd") {
            if (nm.isNotificationPolicyAccessGranted) {
                val saved = prefs.getInt("saved_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
                nm.setInterruptionFilter(saved)
            }
            prefs.edit().remove("saved_filter").apply()
        } else {
            val saved = prefs.getInt("saved_ringer", AudioManager.RINGER_MODE_NORMAL)
            am.ringerMode = saved
            prefs.edit().remove("saved_ringer").apply()
        }
    }
}
