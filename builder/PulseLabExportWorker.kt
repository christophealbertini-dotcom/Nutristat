package com.pedometer.pulselab

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pedometer.data.GpsPointRecord
import com.pedometer.data.StepDatabase
import com.pedometer.data.WorkoutRecord
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * PulseLab independent output adapter.
 * Output: Downloads/PulseLab/WatchInbox/redmi-watch-5-lite-{start}.json
 * No account, internet, watch key or MAC in the exported documents.
 */
class PulseLabExportWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val NOW = "pulselab_watch_export_now"
        private const val PERIODIC = "pulselab_watch_export_periodic"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PulseLabExportWorker>(15, TimeUnit.MINUTES).build()
            )
            changed(context)
        }

        fun changed(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PulseLabExportWorker>().build()
            )
        }
    }

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT < 29) return Result.failure()
        return try {
            val dao = StepDatabase.get(applicationContext).stepDao()
            for (workout in dao.getRecentWorkouts(1000)) {
                if (isStopped) return Result.retry()
                export(workout, dao.getGpsPoints(workout.startTime))
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("PulseLabWatch", "File export failed; retry scheduled", e)
            Result.retry()
        }
    }

    private fun export(w: WorkoutRecord, gps: List<GpsPointRecord>) {
        val resolver = applicationContext.contentResolver
        val folder = Environment.DIRECTORY_DOWNLOADS + "/PulseLab/WatchInbox/"
        val name = "redmi-watch-5-lite-" + w.startTime + ".json"

        val activity = JSONObject()
            .put("startTimeMs", w.startTime)
            .put("endTimeMs", w.endTime)
            .put("sportType", w.sportType)
            .put("sportName", w.sportName)
            .put("durationSeconds", w.durationSec)
            .put("distanceMeters", w.distanceM)
            .put("caloriesKcal", w.calories)
            .put("heartRate", JSONObject()
                .put("averageBpm", w.hrAvg)
                .put("minimumBpm", w.hrMin)
                .put("maximumBpm", w.hrMax))

        val gpsArray = JSONArray()
        for (p in gps) {
            if (!p.lat.isFinite() || !p.lon.isFinite() ||
                p.lat !in -90.0..90.0 || p.lon !in -180.0..180.0) continue
            gpsArray.put(JSONObject()
                .put("timeMs", p.timestamp)
                .put("latitude", p.lat)
                .put("longitude", p.lon)
                .put("speedMetersPerSecond", if (p.speed.isFinite()) p.speed.toDouble() else 0.0))
        }
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("id", "redmi-watch-5-lite:" + w.startTime)
            .put("source", JSONObject()
                .put("deviceModel", "Redmi Watch 5 Lite")
                .put("transport", "bluetooth-spp")
                .put("adapter", "watch-connector"))
            .put("activity", activity)
            .put("gpsPoints", gpsArray)
        val bytes = json.toString(2).toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { b -> "%02x".format(b) }
        val prefs = applicationContext.getSharedPreferences("pulselab_watch_export_v1", Context.MODE_PRIVATE)
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val where = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
            MediaStore.MediaColumns.RELATIVE_PATH + "=?"
        var existing: Uri? = null
        resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
            where, arrayOf(name, folder), null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                existing = ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
        if (existing != null && prefs.getString(name, null) == hash) return

        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: throw IOException("Cannot create exported workout " + name)
        if (existing != null) {
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }, null, null)
        }
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw IOException("Cannot write workout " + name)
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
        check(prefs.edit().putString(name, hash).commit()) {
            "Cannot record exported workout checksum"
        }
    }
}
