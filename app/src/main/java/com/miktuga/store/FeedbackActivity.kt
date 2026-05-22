package com.miktuga.store

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.miktuga.design.feedback.FeedbackLauncher
import com.miktuga.design.feedback.FeedbackPayload
import com.miktuga.design.feedback.FeedbackSubmitter

class FeedbackActivity : AppCompatActivity() {

    private data class Type(val key: String, val label: String) {
        override fun toString(): String = label
    }

    private val types = listOf(
        Type("bug", "Баг"),
        Type("idea", "Идея"),
        Type("question", "Вопрос"),
        Type("other", "Другое")
    )

    private lateinit var spinnerType: Spinner
    private lateinit var editMessage: EditText
    private lateinit var editEmail: EditText
    private lateinit var checkDiagnostic: CheckBox
    private lateinit var buttonSubmit: AppCompatButton
    private lateinit var textSourceApp: TextView

    private lateinit var sourceApp: String
    private lateinit var sourceVersion: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        // FeedbackActivity is exported via intent-filter (com.miktuga.action.FEEDBACK).
        // Cap caller-supplied extras to keep UI + queued JSON bounded for spoofed sources.
        sourceApp = (intent.getStringExtra(FeedbackLauncher.EXTRA_SOURCE_APP) ?: packageName)
            .take(MAX_SOURCE_LEN)
        sourceVersion = (intent.getStringExtra(FeedbackLauncher.EXTRA_SOURCE_VERSION) ?: ownVersion())
            .take(MAX_SOURCE_LEN)

        spinnerType = findViewById(R.id.spinnerType)
        editMessage = findViewById(R.id.editMessage)
        editEmail = findViewById(R.id.editEmail)
        checkDiagnostic = findViewById(R.id.checkDiagnostic)
        buttonSubmit = findViewById(R.id.buttonSubmit)
        textSourceApp = findViewById(R.id.textSourceApp)

        textSourceApp.text = "$sourceApp · $sourceVersion"

        spinnerType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            types
        )

        findViewById<View>(R.id.buttonBack).setOnClickListener { finish() }
        buttonSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        val message = editMessage.text.toString().trim().take(MAX_MESSAGE_LEN)
        if (message.isEmpty()) {
            Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show()
            return
        }
        val type = (spinnerType.selectedItem as? Type) ?: types.first()
        val email = editEmail.text.toString().trim().take(MAX_EMAIL_LEN).takeIf { it.isNotEmpty() }
        val diagnostic = if (checkDiagnostic.isChecked) buildDiagnosticSnapshot() else null

        val payload = FeedbackPayload(
            app = sourceApp,
            version = sourceVersion,
            type = type.key,
            message = message,
            email = email,
            diagnostic = diagnostic
        )

        buttonSubmit.isEnabled = false
        buttonSubmit.text = "ОТПРАВЛЯЕМ…"
        // Use applicationContext for the Toast and guard view writes — the user may
        // have backed out during the ~5s POST and `this` could already be destroyed.
        val appCtx = applicationContext
        FeedbackSubmitter.submit(this, payload) onResult@{ result ->
            val msg = when (result) {
                FeedbackSubmitter.Result.SENT -> "Сообщение отправлено, спасибо"
                FeedbackSubmitter.Result.QUEUED -> "Сообщение сохранено, отправим когда появится сеть"
                FeedbackSubmitter.Result.FAILED -> "Не удалось отправить или сохранить"
            }
            Toast.makeText(appCtx, msg, Toast.LENGTH_LONG).show()
            if (isFinishing || isDestroyed) return@onResult
            if (result == FeedbackSubmitter.Result.FAILED) {
                buttonSubmit.isEnabled = true
                buttonSubmit.text = "ОТПРАВИТЬ"
            } else {
                finish()
            }
        }
    }

    private fun buildDiagnosticSnapshot(): String = buildString {
        appendLine("device: ${Build.BRAND} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("abis: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("fingerprint: ${Build.FINGERPRINT}")
        runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val freeMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
            val totalMb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024)
            appendLine("storage: $freeMb / $totalMb MB free")
        }
    }.trimEnd()

    private fun ownVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
    }.getOrDefault("0.0.0")

    companion object {
        private const val MAX_SOURCE_LEN = 64
        private const val MAX_EMAIL_LEN = 256
        private const val MAX_MESSAGE_LEN = 4096
    }
}
