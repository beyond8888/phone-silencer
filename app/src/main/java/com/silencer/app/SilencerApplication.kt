package com.silencer.app

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 在 Application 最早阶段注册全局崩溃捕获，写到 App 私有目录（无需任何存储权限）。
 * 路径由系统 getExternalFilesDir 动态返回（不硬编码），并在 logcat 打印绝对路径，
 * 方便用 adb logcat 直接看到文件写在哪。最多保留最近 5 个。
 * 写文件前用 file.parentFile?.mkdirs() 兜底创建目录，目录不存在也一定能写成功。
 */
class SilencerApplication : Application() {

    companion object {
        private const val TAG = "SilencerCrash"
    }

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                // 直接获取系统返回的安装路径，不硬编码、不猜测
                val baseDir = getExternalFilesDir(null)
                Log.d(TAG, "crash baseDir=$baseDir")
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(baseDir, "crash/silencer_crash_$ts.txt")
                // 直接写到文件本身所在路径：父目录不存在就创建，绝不因目录缺失而写失败
                file.parentFile?.mkdirs()
                file.writeText(
                    "file=${file.absolutePath}\n" +
                        "thread=${thread.name}\n" +
                        "time=${Date()}\n" +
                        "model=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        "android=${android.os.Build.VERSION.SDK_INT}\n" +
                        throwable.stackTraceToString()
                )
                Log.d(TAG, "crash written: ${file.absolutePath}")
                // 仅保留最近 5 个崩溃文件
                val crashDir = file.parentFile
                crashDir?.listFiles { f -> f.name.startsWith("silencer_crash_") && f.name.endsWith(".txt") }
                    ?.sortedBy { it.name }
                    ?.dropLast(5)
                    ?.forEach { it.delete() }
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
