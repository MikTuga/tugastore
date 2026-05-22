package com.miktuga.store

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.miktuga.design.feedback.FeedbackSubmitter
import org.json.JSONArray
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        findViewById<TextView>(R.id.textVersion).text =
            if (version.isNotEmpty()) "Tugella Edition v$version" else "Tugella Edition"

        findViewById<View>(R.id.buttonDiagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<View>(R.id.buttonCatalog).setOnClickListener {
            startActivity(Intent(this, CatalogActivity::class.java))
        }
        findViewById<View>(R.id.buttonExport).setOnClickListener {
            val intent = Intent(this, DiagnosticsActivity::class.java)
            intent.putExtra("export_only", true)
            startActivity(intent)
        }
        findViewById<View>(R.id.buttonFeedback).setOnClickListener {
            startActivity(Intent(this, FeedbackActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusBar()
        updateCatalogStatus()
        FeedbackSubmitter.retryPending(this)
    }

    private fun updateStatusBar() {
        // USB
        val usbPath = File("/storage/usbotg/usbotg-otg1")
        val usbExists = usbPath.exists()
        findViewById<TextView>(R.id.textUsb).text =
            if (usbExists) "USB подключена" else "USB не найдена"
        findViewById<View>(R.id.dotUsb).setBackgroundResource(
            if (usbExists) R.drawable.status_dot_green else R.drawable.status_dot_gray
        )

        // Root
        val rootPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su"
        )
        val rooted = rootPaths.any { File(it).exists() }
        findViewById<TextView>(R.id.textRoot).text =
            if (rooted) "Root доступен" else "Root отсутствует"
        findViewById<View>(R.id.dotRoot).setBackgroundResource(
            if (rooted) R.drawable.status_dot_green else R.drawable.status_dot_orange
        )

        // Storage
        runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val freeMB = freeBytes / (1024 * 1024)
            findViewById<TextView>(R.id.textStorage).text =
                if (freeMB >= 1024) "${freeMB / 1024} ГБ свободно"
                else "$freeMB МБ свободно"
        }.getOrElse {
            findViewById<TextView>(R.id.textStorage).text = "Storage n/a"
        }

        // Android version
        findViewById<TextView>(R.id.textAndroid).text =
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    private fun updateCatalogStatus() {
        runCatching {
            val json = assets.open("catalog.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            var installed = 0
            for (i in 0 until array.length()) {
                val pkg = array.getJSONObject(i).optString("packageName")
                if (pkg.isNotEmpty() && isPackageInstalled(pkg)) installed++
            }
            findViewById<TextView>(R.id.textCatalogStatus).text =
                "$installed/${array.length()} установлено"
        }.getOrElse {
            findViewById<TextView>(R.id.textCatalogStatus).text = "приложения"
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean = runCatching {
        packageManager.getPackageInfo(pkg, 0)
        true
    }.getOrElse { false }
}
