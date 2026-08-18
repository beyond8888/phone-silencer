package com.silencer.app

import android.app.Application
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 在 Application 最早阶段注册全局崩溃捕获，写到 /sdcard 根目录（不经 Android/data，
 * 华为文件管理一定能看到），方便在无 ADB 时导出排查。最多保留最近 5 个。
 */
class SilencerApplication : Application() {

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = Environment.getExternalStorageDirectory()
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(dir, "silencer_crash_$ts.txt")
                file.writeText(
                    "thread=${thread.name}\n" +
                        "time=${Date()}\n" +
                        "model=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        "android=${android.os.Build.VERSION.SDK_INT}\n" +
                        throwable.stackTraceToString()
                )
                // 仅保留最近 5 个崩溃文件
                dir.listFiles { f -> f.name.startsWith("silencer_crash_") && f.name.endsWith(".txt") }
                    ?.sortedBy { it.name }
                    ?.dropLast(5)
                    ?.forEach { it.delete() }
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
