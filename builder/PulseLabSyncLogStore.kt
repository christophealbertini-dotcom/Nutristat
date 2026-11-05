package com.pedometer.pulselab

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.IOException

/**
 * Shareable non-sensitive watch sync log (no MAC, no authentication key).
 * The user can attach the file for troubleshooting without ADB.
 */
object PulseLabSyncLogStore {
    fun save(context: Context, log: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val resolver = context.contentResolver
            val dir = Environment.DIRECTORY_DOWNLOADS + "/PulseLab/WatchInbox/"
            val name = "diagnostic-synchronisation.txt"
            val media = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            var existing: android.net.Uri? = null
            resolver.query(media, arrayOf(MediaStore.MediaColumns._ID),
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(name, dir), null)?.use { cur ->
                if (cur.moveToFirst()) {
                    existing = ContentUris.withAppendedId(media, cur.getLong(0))
                }
            }
            val uri = existing ?: resolver.insert(media, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }) ?: throw IOException("Cannot create diagnostic text file")
            if (existing != null) resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }, null, null)
            resolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(log.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: throw IOException("Cannot write diagnostic file")
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            true
        } catch (e: Exception) {
            Log.e("PulseLabDiag", "Failed to export sync log", e)
            false
        }
    }
}
