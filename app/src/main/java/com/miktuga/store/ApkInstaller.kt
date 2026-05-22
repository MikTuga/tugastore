package com.miktuga.store

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File

object ApkInstaller {

    private const val TAG = "ApkInstaller"

    const val ACTION_INSTALL_RESULT = "com.miktuga.store.INSTALL_RESULT"
    const val EXTRA_APP_LABEL = "com.miktuga.store.extra.APP_LABEL"

    fun install(context: Context, apkPath: String, appLabel: String = "") {
        val file = File(apkPath)
        if (!file.exists()) {
            Toast.makeText(context, "APK не найден: $apkPath", Toast.LENGTH_LONG).show()
            return
        }
        if (!file.canRead()) {
            Toast.makeText(context, "Нет доступа к APK: $apkPath", Toast.LENGTH_LONG).show()
            return
        }

        val labelForUser = appLabel.ifBlank { file.nameWithoutExtension }
        Toast.makeText(context, "Устанавливается…", Toast.LENGTH_SHORT).show()

        val appContext = context.applicationContext
        Thread({ streamAndCommit(appContext, file, labelForUser) }, "ApkInstaller-stream").start()
    }

    private fun streamAndCommit(context: Context, file: File, labelForUser: String) {
        val main = Handler(Looper.getMainLooper())
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(file.length())
        }

        var sessionId = -1
        try {
            sessionId = pi.createSession(params)
            pi.openSession(sessionId).use { session ->
                session.openWrite("apk", 0, file.length()).use { out ->
                    file.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                    }
                    session.fsync(out)
                }

                val resultIntent = Intent(ACTION_INSTALL_RESULT).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_APP_LABEL, labelForUser)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    resultIntent,
                    flags
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PackageInstaller.Session failed for ${file.absolutePath}", e)
            if (sessionId != -1) {
                runCatching { pi.abandonSession(sessionId) }
            }
            val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            main.post {
                Toast.makeText(
                    context,
                    "Не удалось запустить установку: $reason",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun openApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Не удалось открыть: $packageName", Toast.LENGTH_SHORT).show()
        }
    }
}
