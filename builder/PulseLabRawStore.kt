package com.pedometer.pulselab

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.IOException

/** Keep exact watch transfer bytes in local Downloads before ACK. */
object PulseLabRawStore {
    fun save(context: Context, id: ByteArray, data: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val resolver = context.contentResolver
            val folder = Environment.DIRECTORY_DOWNLOADS + "/PulseLab/WatchInbox/Brut/"
            val name = "redmi-" + id.joinToString("") { "%02x".format(it) } + ".bin"
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            var previous: android.net.Uri? = null
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(name,folder), null)?.use { c ->
                if (c.moveToFirst()) {
                    previous = ContentUris.withAppendedId(collection,c.getLong(0))
                }
            }
            val uri = previous ?: resolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,name)
                put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH,folder)
                put(MediaStore.MediaColumns.IS_PENDING,1)
            }) ?: throw IOException("Cannot create raw watch file")
            if (previous != null) resolver.update(uri,ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING,1)
            },null,null)
            resolver.openOutputStream(uri,"wt")?.use { out ->
                out.write(data); out.flush()
            } ?: throw IOException("Cannot write raw watch file")
            resolver.update(uri,ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING,0)
            },null,null)
            true
        } catch (e:Exception) {
            Log.e("PulseLabRawStore","Cannot save raw activity",e)
            false
        }
    }
}
