package com.pedometer.pulselab

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException
import java.io.PushbackReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Completely local importer of Xiaomi's official privacy export.
 *
 * Knows the documented *_MiFitness_hlth_center_sport_record.csv format:
 * Uid,Sid,Key,Time,Category,Value,UpdateTime
 * Never uploads data; deliberately drops Uid/Sid and credentials.
 *
 * ZIP: plain ZIP accepted. Password-protected ZIP must be extracted by the user
 * first, then select the sport_record.csv. No device-specific keys are embedded.
 */
object MiFitnessArchiveImporter {
    data class Report(
        val archives: Int,
        val csvFiles: Int,
        val records: Int,
        val written: Int,
        val unchanged: Int,
        val ignored: Int,
        val errors: Int,
        val notes: List<String>
    ) {
        fun frenchSummary(): String {
            val base = records.toString() + " séance(s) lue(s), " +
                written + " enregistrée(s), " + unchanged + " déjà présente(s). " +
                csvFiles + " fichier(s) CSV d’activités identifié(s)."
            return if (notes.isEmpty()) base else base + "\n" + notes.joinToString("\n")
        }
    }

    private class Stats {
        var archives = 0
        var csvFiles = 0
        var records = 0
        var written = 0
        var unchanged = 0
        var ignored = 0
        var errors = 0
        val notes = mutableListOf<String>()
        fun asReport() = Report(archives, csvFiles, records, written, unchanged,
            ignored, errors, notes.toList())
    }

    /** CSV parser that supports commas/newlines and doubled quotes in Value JSON. */
    private class CsvReader(reader: InputStreamReader) {
        private val input = PushbackReader(BufferedReader(reader, 32768), 2)
        private var eof = false

        fun next(): List<String>? {
            if (eof) return null
            val fields = mutableListOf<String>()
            val text = StringBuilder()
            var quoted = false
            var seen = false
            var chars = 0
            while (true) {
                val c = input.read()
                if (c == -1) {
                    eof = true
                    if (!seen && text.isEmpty() && fields.isEmpty()) return null
                    fields.add(text.toString())
                    return fields
                }
                if (c == 0xFEFF && !seen && fields.isEmpty() && text.isEmpty()) continue
                seen = true
                chars++
                if (chars > 2_000_000) throw IOException("Ligne CSV trop longue")
                when (c.toChar()) {
                    '"' -> {
                        if (quoted) {
                            val next = input.read()
                            if (next == '"'.code) text.append('"')
                            else {
                                quoted = false
                                if (next != -1) input.unread(next)
                            }
                        } else if (text.isEmpty()) quoted = true
                        else text.append('"')
                    }
                    ',' -> if (!quoted) {
                        fields.add(text.toString()); text.setLength(0)
                    } else text.append(',')
                    '\n' -> if (!quoted) {
                        fields.add(text.toString()); return fields
                    } else text.append('\n')
                    '\r' -> if (quoted) text.append('\r')
                    else -> text.append(c.toChar())
                }
            }
        }
    }

    suspend fun importFiles(context: Context, uris: List<Uri>,
                            progress: (String) -> Unit = {}): Report =
        withContext(Dispatchers.IO) {
            val stats = Stats()
            if (Build.VERSION.SDK_INT < 29) {
                stats.notes += "Android 10 ou supérieur requis pour le dossier de sortie."
                return@withContext stats.asReport()
            }
            val resolver = context.contentResolver
            for ((i, uri) in uris.withIndex()) {
                progress("Lecture du fichier " + (i + 1) + "/" + uris.size + "…")
                try {
                    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                        null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "export-xiaomi"
                    resolver.openInputStream(uri)?.use { stream ->
                        val input = BufferedInputStream(stream, 65536)
                        input.mark(8)
                        val b1 = input.read(); val b2 = input.read()
                        input.reset()
                        val isZip = b1 == 0x50 && b2 == 0x4b
                        if (isZip) {
                            stats.archives++
                            importZip(context, input, stats, progress)
                        } else if (displayName.contains("sport_record", ignoreCase = true) &&
                            displayName.endsWith(".csv", ignoreCase = true)) {
                            importCsv(context, input, stats, progress)
                        } else {
                            stats.notes += "Fichier ignoré : " + displayName.take(70) +
                                ". Sélectionne l’archive Xiaomi ou un fichier hlth_center_sport_record.csv."
                        }
                    } ?: stats.notes.add("Impossible d’ouvrir le fichier sélectionné.")
                } catch (e: ZipException) {
                    stats.errors++
                    stats.notes += "ZIP impossible à lire (éventuellement protégé par mot de passe). " +
                        "Décompresse-le d’abord sur le téléphone, puis importe le CSV sport_record."
                } catch (e: Exception) {
                    stats.errors++
                    Log.e("MiFitnessArchive", "File import failed", e)
                    stats.notes += "Erreur d’importation : " + (e.message ?: e.javaClass.simpleName).take(140)
                }
            }
            if (stats.csvFiles == 0) stats.notes +=
                "Aucun CSV d’entraînements identifié. Fichier attendu : " +
                "*_MiFitness_hlth_center_sport_record.csv."
            stats.asReport()
        }

    private fun importZip(context: Context, stream: InputStream, stats: Stats,
                          progress: (String) -> Unit) {
        ZipInputStream(stream).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                if (count > 2000) throw IOException("Archive contenant trop de fichiers")
                val path = entry.name.lowercase(Locale.ROOT)
                if (!entry.isDirectory && path.endsWith(".csv") &&
                    path.contains("hlth_center_sport_record")) {
                    progress("Importation des séances depuis " + entry.name.substringAfterLast('/'))
                    importCsv(context, zip, stats, progress)
                }
                zip.closeEntry()
            }
        }
    }

    private fun importCsv(context: Context, input: InputStream, stats: Stats,
                          progress: (String) -> Unit) {
        // Never close this reader: when inside ZIP, ZipInputStream owns its lifecycle.
        val csv = CsvReader(InputStreamReader(input, StandardCharsets.UTF_8))
        val header = csv.next() ?: return
        val col = header.mapIndexed { index, s ->
            s.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) to index
        }.toMap()
        val valueIndex = col["value"]
        if (valueIndex == null) {
            stats.errors++
            stats.notes += "CSV non reconnu : colonne Value absente."
            return
        }
        stats.csvFiles++
        var n = 0
        while (true) {
            val row = csv.next() ?: break
            if (row.all { it.isBlank() }) continue
            n++
            if (n > 250000) throw IOException("Nombre de séances excessif (>250 000)")
            try {
                val value = row.getOrNull(valueIndex)?.trim().orEmpty()
                if (!value.startsWith("{")) {
                    stats.ignored++
                    continue
                }
                val payload = JSONObject(value)
                val timeCol = col["time"]?.let { row.getOrNull(it) }
                val startMs = asMillis(payload.opt("start_time"))
                    .takeIf { it > 0 } ?: asMillis(payload.opt("time"))
                    .takeIf { it > 0 } ?: asMillis(timeCol)
                if (startMs <= 0) {
                    stats.ignored++
                    continue
                }
                val key = col["key"]?.let { row.getOrNull(it) }?.trim().orEmpty()
                val cat = col["category"]?.let { row.getOrNull(it) }?.trim().orEmpty()
                val ended = asMillis(payload.opt("end_time")).takeIf { it >= startMs }
                    ?: startMs + (asInt(payload.opt("duration")) * 1000L)
                val duration = asInt(payload.opt("duration")).takeIf { it > 0 }
                    ?: ((ended - startMs) / 1000).toInt().coerceAtLeast(0)
                val result = storeWorkout(context, key.ifBlank { cat },
                    startMs, ended, duration, payload)
                stats.records++
                if (result) stats.written++ else stats.unchanged++
                if (stats.records % 100 == 0)
                    progress("Séances traitées : " + stats.records)
            } catch (e: Exception) {
                stats.errors++
                if (stats.errors < 4) stats.notes +=
                    "Enregistrement illisible (ligne " + n + ") : " +
                    (e.message ?: "format inconnu").take(80)
            }
        }
    }

    private fun asInt(value: Any?): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.toDoubleOrNull()?.toInt() ?: 0
        else -> 0
    }
    private fun asMillis(value: Any?): Long {
        val n = when (value) {
            is Number -> value.toLong()
            is String -> value.toDoubleOrNull()?.toLong() ?: 0L
            else -> 0L
        }
        if (n <= 0L) return 0L
        val ms = if (n < 100_000_000_000L) n * 1000L else n
        return if (ms in 1262304000000L..(System.currentTimeMillis() + 15552000000L))
            ms else 0L
    }

    private fun sportName(key: String): String = when (key.lowercase(Locale.ROOT)) {
        "outdoor_running", "running" -> "Course à pied"
        "indoor_running", "treadmill" -> "Tapis de course"
        "outdoor_walking", "walking" -> "Marche extérieure"
        "indoor_walking" -> "Marche intérieure"
        "outdoor_riding", "outdoor_cycling", "cycling" -> "Cyclisme"
        "indoor_riding", "indoor_cycling" -> "Vélo d’intérieur"
        "pool_swimming", "open_water_swimming", "swimming" -> "Natation"
        "free_training", "freestyle", "dumbbell_training" -> "Entraînement libre"
        "hiking", "mountaineering", "trekking" -> "Randonnée"
        "elliptical", "elliptical_machine" -> "Vélo elliptique"
        "jump_rope", "rope_skipping" -> "Corde à sauter"
        "rowing_machine" -> "Rameur"
        "yoga" -> "Yoga"
        else -> key.ifBlank { "Autre activité sportive" }
    }

    private fun storeWorkout(context: Context, sportKey: String, start: Long,
                             end: Long, duration: Int, source: JSONObject): Boolean {
        val activity = JSONObject()
            .put("startTimeMs", start)
            .put("endTimeMs", end)
            .put("sportType", asInt(source.opt("sport_type")))
            .put("sportName", sportName(sportKey))
            .put("sportKey", sportKey)
            .put("durationSeconds", duration)
            .put("distanceMeters", asInt(source.opt("distance")))
            .put("caloriesKcal", asInt(source.opt("calories")))
            .put("steps", asInt(source.opt("steps")))
            .put("heartRate", JSONObject()
                .put("averageBpm", asInt(source.opt("avg_hrm")))
                .put("maximumBpm", asInt(source.opt("max_hrm")))
                .put("minimumBpm", asInt(source.opt("min_hrm"))))
        val doc = JSONObject()
            .put("schemaVersion", 1)
            .put("id", "redmi-watch-5-lite:" + start)
            .put("source", JSONObject()
                .put("adapter", "mi-fitness-archive")
                .put("deviceModel", "Xiaomi / Redmi (modèle non garanti par l’export)")
                .put("transport", "fichier-export-xiaomi"))
            .put("activity", activity)
            .put("gpsPoints", JSONArray())
        // Do not include private Uid, Sid, account data or authentication values.
        val name = "mi-fitness-" + start + "-" +
            sportKey.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "_").take(45) + ".json"
        val dir = Environment.DIRECTORY_DOWNLOADS + "/PulseLab/WatchInbox/MiFitnessImport/"
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var previous: Uri? = null
        resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
            MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                MediaStore.MediaColumns.RELATIVE_PATH + "=?",
            arrayOf(name, dir), null)?.use { c ->
            if (c.moveToFirst())
                previous = ContentUris.withAppendedId(collection, c.getLong(0))
        }
        val bytes = doc.toString(2).toByteArray(StandardCharsets.UTF_8)
        if (previous != null) {
            // Preserve a future richer version, e.g. one supplemented with GPS.
            val old = resolver.openInputStream(previous!!)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
            if (old != null) {
                val existing = runCatching { JSONObject(old) }.getOrNull()
                val existingGps = existing?.optJSONArray("gpsPoints")
                if (existingGps != null && existingGps.length() > 0)
                    doc.put("gpsPoints", existingGps)
                if (existing?.toString(2) == doc.toString(2)) return false
            }
        }
        val uri = previous ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: throw IOException("Échec de création du fichier d'activité")
        if (previous != null) resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }, null, null)
        resolver.openOutputStream(uri, "wt")?.use {
            it.write(doc.toString(2).toByteArray(StandardCharsets.UTF_8)); it.flush()
        } ?: throw IOException("Échec d’écriture de l’activité")
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
        return true
    }
}
