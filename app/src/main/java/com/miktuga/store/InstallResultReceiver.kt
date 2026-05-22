package com.miktuga.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_INSTALL_RESULT) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val label = intent.getStringExtra(ApkInstaller.EXTRA_APP_LABEL).orEmpty()
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    Log.w(TAG, "PENDING_USER_ACTION but no EXTRA_INTENT")
                    Toast.makeText(context, "Не удалось показать подтверждение установки", Toast.LENGTH_LONG).show()
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirm)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to launch installer confirm activity", e)
                    Toast.makeText(context, "Не удалось показать подтверждение установки", Toast.LENGTH_LONG).show()
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val msg = if (label.isNotEmpty()) "$label · установлено" else "Установлено"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            else -> {
                val reason = describeFailure(status)
                val full = buildString {
                    if (label.isNotEmpty()) {
                        append(label)
                        append(" · ")
                    }
                    append(reason)
                    if (statusMessage.isNotBlank()) {
                        append(" (")
                        append(statusMessage)
                        append(')')
                    }
                }
                Toast.makeText(context, full, Toast.LENGTH_LONG).show()
                Log.w(TAG, "Install failed status=$status message=$statusMessage label=$label")
            }
        }
    }

    private fun describeFailure(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE -> "Установка не удалась"
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Установка отменена"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Установка заблокирована"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "Конфликт версий"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK несовместим с устройством"
        PackageInstaller.STATUS_FAILURE_INVALID -> "Повреждённый APK"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Недостаточно места"
        else -> "Неизвестная ошибка установки ($status)"
    }

    companion object {
        private const val TAG = "InstallResultReceiver"
    }
}
