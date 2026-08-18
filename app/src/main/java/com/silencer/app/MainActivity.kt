package com.silencer.app

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.silencer.app.ui.SettingsScreen
import com.silencer.app.ui.theme.SilencerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 华为等 ROM 上 enableEdgeToEdge 配合 Material3 主题易启动崩溃，暂去掉
        super.onCreate(savedInstanceState)
        installCrashHandler()
        setContent {
            SilencerTheme {
                SettingsScreen()
            }
        }
    }

    /** 捕获未处理异常，写入 app 私有文件，方便在手机上导出排查 */
    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(getExternalFilesDir(null), "crash")
                dir.mkdirs()
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                File(dir, "crash_$ts.txt").writeText(
                    "thread=${thread.name}\n" +
                        "time=${Date()}\n" +
                        throwable.stackTraceToString()
                )
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
