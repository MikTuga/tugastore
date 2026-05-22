package com.miktuga.store

import java.io.File

data class CatalogItem(
    val id: String,
    val title: String,
    val description: String,
    val packageName: String,
    val version: String,
    val apkPath: String,
) {
    /**
     * Find APK file on disk. Tries the primary apkPath (USB on real Tugella),
     * then fallback paths for emulator/dev testing.
     */
    fun resolveApk(): File? {
        if (apkPath.isEmpty()) return null
        val fileName = apkPath.substringAfterLast('/')
        val candidates = listOf(
            apkPath,
            "/data/local/tmp/apps/$fileName",
            "/sdcard/apps/$fileName",
            "/sdcard/Download/$fileName",
        )
        return candidates.map { File(it) }.firstOrNull { it.exists() }
    }
}
