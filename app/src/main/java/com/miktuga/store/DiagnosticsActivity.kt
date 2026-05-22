package com.miktuga.store

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : AppCompatActivity() {

    private data class Section(
        val title: String,
        val rows: List<Pair<String, String>>,
        val badge: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        findViewById<TextView>(R.id.textTimestamp).text = timestamp()

        val sections = buildSections()
        renderSections(sections)

        findViewById<View>(R.id.buttonExportDiag).setOnClickListener {
            exportReport(buildTextReport(sections))
            Toast.makeText(this, "Отчёт сохранён", Toast.LENGTH_SHORT).show()
        }

        if (intent.getBooleanExtra("export_only", false)) {
            exportReport(buildTextReport(sections))
            Toast.makeText(this, "Отчёт экспортирован", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderSections(sections: List<Section>) {
        val container = findViewById<LinearLayout>(R.id.diagContainer)
        val inflater = LayoutInflater.from(this)
        for (s in sections) {
            val card = inflater.inflate(R.layout.item_diag_section, container, false)
            card.findViewById<TextView>(R.id.sectionTitle).text = s.title
            val badge = card.findViewById<TextView>(R.id.sectionBadge)
            if (s.badge != null) {
                badge.text = s.badge
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }
            val rowsContainer = card.findViewById<LinearLayout>(R.id.sectionRows)
            for ((k, v) in s.rows) {
                val row = inflater.inflate(R.layout.item_diag_row, rowsContainer, false)
                row.findViewById<TextView>(R.id.rowKey).text = k
                row.findViewById<TextView>(R.id.rowValue).text = v
                rowsContainer.addView(row)
            }
            container.addView(card)
        }
    }

    private fun buildSections(): List<Section> {
        val result = mutableListOf<Section>()

        // System
        result += Section(
            "Система",
            listOf(
                "Android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                "Brand / Manufacturer" to "${Build.BRAND} / ${Build.MANUFACTURER}",
                "Model / Device" to "${Build.MODEL} / ${Build.DEVICE}",
                "Board / Hardware" to "${Build.BOARD} / ${Build.HARDWARE}",
                "Product" to Build.PRODUCT,
                "Supported ABIs" to Build.SUPPORTED_ABIS.joinToString(),
                "Build tags" to (Build.TAGS ?: "n/a"),
                "Serial" to runCatching { Build.SERIAL }.getOrDefault("n/a"),
                "Fingerprint" to Build.FINGERPRINT
            )
        )

        // Display
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        val rotation = windowManager.defaultDisplay.rotation
        result += Section(
            "Дисплей",
            listOf(
                "Разрешение" to "${dm.widthPixels} × ${dm.heightPixels}",
                "Плотность" to "${dm.densityDpi} dpi (×${dm.density})",
                "Ориентация" to if (rotation == 0 || rotation == 2) "portrait" else "landscape"
            )
        )

        // Memory
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        result += Section(
            "Память",
            listOf(
                "Всего RAM" to "$totalMb МБ",
                "Доступно" to "$availMb МБ",
                "Low memory" to if (memInfo.lowMemory) "ДА" else "нет",
                "Threshold" to "${memInfo.threshold / (1024 * 1024)} МБ"
            ),
            badge = "$availMb / $totalMb МБ"
        )

        // Storage
        val storageRows = mutableListOf<Pair<String, String>>()
        storageRows += "External state" to Environment.getExternalStorageState()
        storageRows += "External path" to Environment.getExternalStorageDirectory().absolutePath
        var storageBadge: String? = null
        runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            storageRows += "Всего" to "${totalBytes / (1024 * 1024)} МБ"
            storageRows += "Свободно" to "${freeBytes / (1024 * 1024)} МБ"
            storageBadge = "${freeBytes / (1024 * 1024)} МБ свободно"
        }
        result += Section("Хранилище", storageRows, storageBadge)

        // USB / Mount points
        val mountRows = mutableListOf<Pair<String, String>>()
        val mountPaths = listOf(
            "/storage/usbotg",
            "/storage/usbotg/usbotg-otg1",
            "/storage/usb0",
            "/storage/usb1",
            "/mnt/usb_storage",
            "/mnt/media_rw",
            "/storage/sdcard1",
            "/storage/extSdCard"
        )
        var usbFound = false
        for (path in mountPaths) {
            val f = File(path)
            if (f.exists()) {
                val items = f.listFiles()?.size ?: 0
                mountRows += path to "EXISTS ($items items)"
                usbFound = true
            }
        }
        val storageDir = File("/storage")
        if (storageDir.exists()) {
            storageDir.listFiles()?.forEach { child ->
                if (child.isDirectory && mountRows.none { it.first == child.absolutePath }) {
                    mountRows += "/storage/${child.name}" to "${child.listFiles()?.size ?: 0} items"
                }
            }
        }
        if (mountRows.isEmpty()) mountRows += "Mount points" to "не найдены"
        result += Section(
            "USB / Точки монтирования",
            mountRows,
            if (usbFound) "USB найдена" else "USB нет"
        )

        // Root
        val rootPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk"
        )
        val foundRootPaths = rootPaths.filter { File(it).exists() }
        val rootRows = mutableListOf<Pair<String, String>>()
        for (p in foundRootPaths) rootRows += "Найден" to p
        rootRows += "Build tags" to (Build.TAGS ?: "n/a")
        if (rootRows.size == 1) rootRows += "Поиск su" to "не найден"
        result += Section(
            "Root",
            rootRows,
            if (foundRootPaths.isNotEmpty()) "ДА" else "нет"
        )

        // Connectivity
        val connRows = mutableListOf<Pair<String, String>>()
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        connRows += "Wi-Fi" to if (wifiManager?.isWifiEnabled == true) "включён" else "выключен"
        runCatching {
            val bt = BluetoothAdapter.getDefaultAdapter()
            connRows += "Bluetooth" to
                if (bt != null) (if (bt.isEnabled) "включён" else "выключен")
                else "недоступен"
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        connRows += "GPS provider" to
            if (lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) "включён" else "выключен"
        connRows += "Network provider" to
            if (lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) "включён" else "выключен"
        result += Section("Связь", connRows)

        // Sensors
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        val sensorRows = sensors.take(8).map { it.name to (it.vendor ?: "n/a") }
        result += Section(
            "Сенсоры",
            sensorRows.ifEmpty { listOf("Сенсоры" to "не найдены") },
            "${sensors.size} шт"
        )

        // Permissions
        val permRows = mutableListOf<Pair<String, String>>()
        runCatching {
            val pi = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            pi.requestedPermissions?.forEachIndexed { i, perm ->
                val granted = pi.requestedPermissionsFlags != null &&
                    (pi.requestedPermissionsFlags[i] and PackageManager.PERMISSION_GRANTED) != 0
                val name = perm.substringAfterLast('.')
                permRows += name to if (granted) "✓ GRANTED" else "✗ DENIED"
            }
        }
        if (permRows.isEmpty()) permRows += "Permissions" to "—"
        result += Section("Разрешения приложения", permRows)

        // Packages summary
        val packages = packageManager.getInstalledPackages(0)
        val systemPkgs = packages.count {
            it.applicationInfo != null &&
                (it.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        }
        val userPkgs = packages.size - systemPkgs
        result += Section(
            "Установленные пакеты",
            listOf(
                "Всего" to "${packages.size}",
                "Системных" to "$systemPkgs",
                "Пользовательских" to "$userPkgs"
            ),
            "${packages.size}"
        )

        return result
    }

    private fun buildTextReport(sections: List<Section>): String = buildString {
        appendLine("═══════════════════════════════════")
        appendLine("  TUGA STORE — DIAGNOSTICS v0.2")
        appendLine("  ${timestamp()}")
        appendLine("═══════════════════════════════════")
        appendLine()
        for (s in sections) {
            appendLine("── ${s.title.uppercase()} ──${s.badge?.let { " [$it]" } ?: ""}")
            for ((k, v) in s.rows) appendLine("  $k: $v")
            appendLine()
        }
        appendLine("═══════════════════════════════════")
    }

    private fun exportReport(content: String) {
        runCatching {
            val dir = File(getExternalFilesDir(null), "reports")
            if (!dir.exists()) dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "diagnostics_$ts.txt")
            file.writeText(content)

            val usbDir = File("/storage/usbotg/usbotg-otg1/tugastore_reports")
            if (File("/storage/usbotg/usbotg-otg1").exists()) {
                runCatching {
                    if (!usbDir.exists()) usbDir.mkdirs()
                    File(usbDir, "diagnostics_$ts.txt").writeText(content)
                }
            }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
