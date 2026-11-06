package com.pedometer.pulselab

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Store each watch-sync log as a NEW public Downloads entry.
 * Never attempts to modify an existing entry owned by an older app package.
 */
object PulseLabSyncLogStore {
    fun save(context: Context, log: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val resolver = context.contentResolver
            val dir = Environment.DIRECTORY_DOWNLOADS + "/PulseLab/WatchInbox/"
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.FRANCE).format(Date())
            val name = "diagnostic-pulselab-v08-" + stamp + ".txt"
            val media = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(media, values)
                ?: throw IOException("Impossible de créer le rapport")
            try {
                resolver.openOutputStream(uri, "wt")?.use { output ->
                    output.write(log.toByteArray(Charsets.UTF_8))
                    output.flush()
                } ?: throw IOException("Impossible d'écrire le rapport")
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
                name
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } catch (e: Exception) {
            Log.e("PulseLabDiag", "Failed to export sync log v0.8", e)
            null
        }
    }
}
