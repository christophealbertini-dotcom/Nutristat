package com.pedometer.pulselab

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Finds previously transferred workout file IDs in Mi Fitness diagnostic logs.
 * All parsing happens locally; only 7-byte file IDs are saved as preferences.
 *
 * This does NOT guarantee re-fetch works on firmware that clears ACKed files.
 */
object MiFitnessWorkoutHints {
    private const val PREF = "pulselab_mifitness_workout_hints_v1"
    private const val KEY = "file_ids"
    private val pattern = Regex(
        """timeStamp\s*=\s*(\d+),\s*timeZone\s*=\s*(-?\d+),\s*dataType\s*=\s*(\d+),\s*sportType\s*=\s*(\d+),\s*dailyType\s*=\s*(\d+),\s*fileType\s*=\s*(\d+),\s*version\s*=\s*(\d+)"""
    )

    data class Report(val sessions: Int, val ids: Int, val scanned: Int, val message: String) {
        fun french(): String = if (message.isNotEmpty()) message else
            "Indices détectés : " + sessions + " séance(s), " + ids +
            " fichiers sportifs identifiés. " +
            "Appuie sur Synchroniser pour tester leur relecture directe."
    }

    fun load(context: Context): List<ByteArray> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        return raw.split(",").mapNotNull { s ->
            if (!s.matches(Regex("[a-f0-9]{14}"))) null
            else try {
                ByteArray(7) { index ->
                    s.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            } catch (_: Exception) { null }
        }
    }

    fun describe(context: Context): String {
        val ids = load(context)
        return if (ids.isEmpty())
            "Aucun identifiant d'ancienne séance importé. Tu peux sélectionner un ZIP " +
            "de diagnostic Mi Fitness pour retrouver les identifiants déjà transmis."
        else "Indices présents : " + (ids.map { it.copyOfRange(0, 4).toList() }.distinct().size) +
            " séance(s) et " + ids.size + " fichier(s)."
    }

    suspend fun importLogs(context: Context, uri: Uri): Report = withContext(Dispatchers.IO) {
        try {
            val newIds = LinkedHashSet<String>()
            val sessions = LinkedHashSet<String>()
            var scanned = 0

            fun parse(line: String) {
                scanned++
                if (!line.contains("dataType = 1") || !line.contains("fileType = ")) return
                val m = pattern.find(line) ?: return
                val v = (1..7).map { m.groupValues[it].toLongOrNull() ?: return }
                val ts = v[0]
                val tz = v[1].toInt()
                val type = v[2].toInt()
                val sport = v[3].toInt()
                val detail = v[5].toInt()
                val version = v[6].toInt()
                if (type != 1 || sport !in 0..31 || detail !in 1..2 ||
                    tz !in -128..127 || version !in 1..255 ||
                    ts !in 1000000000L..4102444800L) return
                val id = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(ts.toInt())
                    .put(tz.toByte())
                    .put(version.toByte())
                    .put(((1 shl 7) or (sport shl 2) or detail).toByte())
                    .array()
                newIds.add(id.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) })
                sessions.add(ts.toString() + ":" + sport)
            }

            fun scanStream(stream: InputStream) {
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 16384)
                while (scanned < 1500000) {
                    val line = reader.readLine() ?: break
                    parse(line)
                }
            }

            val resolver = context.contentResolver
            var name = ""
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: ""
            }
            resolver.openInputStream(uri)?.use { input ->
                if (name.lowercase().endsWith(".zip")) {
                    val zip = ZipInputStream(input.buffered())
                    var entry = zip.nextEntry
                    while (entry != null && scanned < 1500000) {
                        if (!entry.isDirectory && (entry.name.lowercase().endsWith(".log") ||
                                    entry.name.lowercase().endsWith(".txt"))) {
                            scanStream(zip)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                } else scanStream(input)
            } ?: return@withContext Report(0, 0, 0, "Impossible d'ouvrir le fichier sélectionné.")

            val previous = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, "") ?: ""
            val merged = LinkedHashSet<String>()
            previous.split(",").filter { it.matches(Regex("[a-f0-9]{14}")) }
                .forEach { merged.add(it) }
            // Keep the history-index bounded, never store raw diagnostic logs.
            newIds.take(600).forEach { merged.add(it) }
            val ordered = merged.take(600).toList()
            val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, ordered.joinToString(",")).commit()
            if (!saved) Report(0, 0, scanned, "Échec de sauvegarde de l'index local.")
            else Report(sessions.size, newIds.size, scanned, "")
        } catch (e: Exception) {
            Report(0, 0, 0, "Échec de lecture du ZIP : " +
                (e.message ?: e.javaClass.simpleName).take(120))
        }
    }
}
