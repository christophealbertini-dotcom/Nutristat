#!/usr/bin/env python3
"""v0.4 diagnostic & retry: safer watch download, explicit timeouts, shareable log."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1])
src = root / "app/src/main/java/com/pedometer"

def change(path, old, new):
    p = Path(path)
    body = p.read_text(encoding="utf-8")
    count = body.count(old)
    if count != 1:
        raise RuntimeError("Cannot patch %s: anchor count %d: %r" % (path, count, old[:90]))
    p.write_text(body.replace(old, new), encoding="utf-8")

act = src / "health/ActivitySync.kt"
change(act,
    "import android.util.Log\n",
    "import android.util.Log\nimport android.os.SystemClock\n")
change(act,
    "    private var workoutCount = 0\n",
    """    private var workoutCount = 0
    @Volatile private var lastProgressMs = SystemClock.elapsedRealtime()
    private var lastChunk = 0
    private var totalChunksSeen = 0

    /** Called by the process-scoped watch service, even when the UI is closed. */
    fun checkTimeout() {
        if (syncPhase == 0) return
        val elapsed = SystemClock.elapsedRealtime() - lastProgressMs
        if (elapsed < 30000L) return
        val file = currentFileId?.toHex()
        val detail = if (file == null) "réponse à la liste de fichiers" else
            "fichier " + file + " (bloc " + lastChunk + "/" + totalChunksSeen + ")"
        syncPhase = 0
        pendingChunks.clear()
        pendingFileIds.clear()
        currentFileId = null
        onStatus("BLOCAGE : aucune réponse depuis " + (elapsed / 1000) +
            " s pour " + detail + ". Déconnecte/reconnecte la montre, puis relance.")
    }
""")
change(act,
    '        syncPhase = 1\n        receivedCount = 0\n',
    '        syncPhase = 1\n        lastProgressMs = SystemClock.elapsedRealtime()\n        receivedCount = 0\n')
change(act,
    '        syncPhase = 2\n        onStatus("Demande de la liste des données historiques…")\n',
    '        syncPhase = 2\n        lastProgressMs = SystemClock.elapsedRealtime()\n        onStatus("Demande de la liste des données historiques…")\n')
change(act,
    '        currentFileId = fileId\n        onStatus("Réception du fichier " + (receivedCount + 1) + " sur " + offeredCount + "…")\n',
    '''        currentFileId = fileId
        lastProgressMs = SystemClock.elapsedRealtime()
        lastChunk = 0
        totalChunksSeen = 0
        onStatus("Fichier " + (receivedCount + 1) + "/" + offeredCount +
            " • ID " + fileId.toHex() + " : attente des blocs Bluetooth…")
''')
change(act,
    '                if (cmd.subtype != syncPhase) return\n                val bytes =',
    '''                if (cmd.subtype != syncPhase) {
                    Log.w(TAG, "Unexpected activity file-list subtype " + cmd.subtype)
                    return
                }
                lastProgressMs = SystemClock.elapsedRealtime()
                val bytes =''')
change(act,
    '        val chunkNum = bb.short.toInt() and 0xFFFF\n        val chunkData =',
    '''        val chunkNum = bb.short.toInt() and 0xFFFF
        if (totalChunks !in 1..4096 || chunkNum !in 1..totalChunks) {
            onStatus("Bloc de données incorrect : " + chunkNum + "/" + totalChunks)
            return
        }
        lastProgressMs = SystemClock.elapsedRealtime()
        lastChunk = chunkNum
        totalChunksSeen = totalChunks
        if (chunkNum == 1 || chunkNum % 10 == 0 || chunkNum == totalChunks) {
            onStatus("Fichier " + (receivedCount + 1) + "/" + offeredCount +
                " • bloc " + chunkNum + "/" + totalChunks)
        }
        val chunkData =''')
change(act,
    '''                    info.type == 0 && info.subtype == 6 ->
                        Log.i(TAG, "Skipping ACK for manual sample — will re-fetch next time")
''',
    '''                    info.type == 0 && info.subtype == 6 -> {
                        // The raw file has been saved on disk. ACK is required for the
                        // watch to progress to the next file in the session.
                        ackFile(fileId)
                        onStatus("Mesure manuelle enregistrée et acquittée.")
                    }
''')
# The previous patch deliberately altered an error message; replace the
# entire tiny method to avoid depending on the upstream log-message text.
p = act
body = p.read_text(encoding="utf-8")
start = body.index("    private fun persistAndAck(")
end = body.index("    fun handleCommand(", start)
body = body[:start] + '''    private fun persistAndAck(workout: WorkoutSummary, fileId: ByteArray): Boolean {
        return try {
            runBlocking { onWorkoutPersist?.invoke(workout) }
            ackFile(fileId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Workout database save failed: " + fileId.toHex(), e)
            onStatus("Échec de la sauvegarde en base. Fichier brut conservé ; pas d’acquittement.")
            false
        }
    }

''' + body[end:]
p.write_text(body, encoding="utf-8")
change(act,
    '''                    workout != null && onWorkoutPersist != null ->
                        persistAndAck(workout, fileId)
''',
    '''                    workout != null && onWorkoutPersist != null -> {
                        if (!persistAndAck(workout, fileId)) {
                            syncPhase = 0
                            return
                        }
                    }
''')
change(act,
    '''            receivedCount++
            if (fullData.size > 0''',
    '''            receivedCount++
            lastProgressMs = SystemClock.elapsedRealtime()
            if (fullData.size > 0''')
# UI-facing status on successful file preserves the type distinctions.
change(act,
    '''            onStatus("Sauvegardés : " + receivedCount + " / " + offeredCount +
                " • fichiers sportifs : " + workoutCount)
''',
    '''            onStatus("Sauvegardés : " + receivedCount + "/" + offeredCount +
                " • fichiers sportifs reçus : " + workoutCount +
                " • dernier ID " + (fullData.copyOfRange(0, 7).toHex()))
''')

repo = src/"repo/WatchRepository.kt"
change(repo,
    '    val syncStatus: StateFlow<String> = _syncStatus\n',
    '''    val syncStatus: StateFlow<String> = _syncStatus
    private val _syncLog = MutableStateFlow("Diagnostic de synchronisation – v0.4")
    val syncLog: StateFlow<String> = _syncLog
    private var syncWatchdogJob: Job? = null

    private fun noteSyncStatus(message: String) {
        _syncStatus.value = message
        val timestamp = java.text.SimpleDateFormat(
            "HH:mm:ss", java.util.Locale.FRANCE
        ).format(java.util.Date())
        _syncLog.update { existing ->
            (existing + "\\n" + timestamp + " • " + message).takeLast(6500)
        }
    }

    fun exportSyncDiagnostics() {
        val snapshot = _syncLog.value
        scope.launch(Dispatchers.IO) {
            val ok = com.pedometer.pulselab.PulseLabSyncLogStore.save(context, snapshot)
            noteSyncStatus(if (ok)
                "Journal enregistré : Téléchargements/PulseLab/WatchInbox/diagnostic-synchronisation.txt"
            else "Impossible d’exporter le journal ; vérifie l’espace de stockage.")
        }
    }
''')
change(repo,
    '            onStatus = { message -> _syncStatus.value = message },\n',
    '            onStatus = { message -> noteSyncStatus(message) },\n')
change(repo,
    '''    private fun onLinkDropped() {
''',
    '''    private fun onLinkDropped() {
        syncWatchdogJob?.cancel()
        syncWatchdogJob = null
        noteSyncStatus("Connexion interrompue : les transferts en cours devront être repris.")
''')
change(repo,
    '''        buildServices(handler)
        startPostAuthInit()
''',
    '''        buildServices(handler)
        syncWatchdogJob?.cancel()
        syncWatchdogJob = scope.launch {
            while (isActive) {
                delay(3000)
                activitySync?.checkTimeout()
            }
        }
        startPostAuthInit()
''')
change(repo,
    '            _syncStatus.value = "Montre déconnectée : commande non envoyée."\n',
    '            noteSyncStatus("Montre déconnectée : commande non envoyée.")\n')

main = src/"MainActivity.kt"
change(main,
    '                val syncInfo by PedometerApp.repository.syncStatus.collectAsState()\n',
    '''                val syncInfo by PedometerApp.repository.syncStatus.collectAsState()
                val syncLog by PedometerApp.repository.syncLog.collectAsState()
''')
change(main,
    '                                Text("Fichiers non décodés : sous-dossier Brut.")\n',
    '''                                Text("Fichiers non décodés : sous-dossier Brut.")
                                OutlinedButton(
                                    onClick = { PedometerApp.repository.exportSyncDiagnostics() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Enregistrer le journal de diagnostic") }
                                Text("Chronologie :", style = MaterialTheme.typography.labelLarge)
                                Text(syncLog, style = MaterialTheme.typography.bodySmall)
''')
raw = src/"pulselab/PulseLabSyncLogStore.kt"
shutil.copy2("builder/PulseLabSyncLogStore.kt", raw)
gradle = root/"app/build.gradle.kts"
change(gradle,
    'applicationId = "com.pulselab.watchconnector.diagsync"',
    'applicationId = "com.pulselab.watchconnector.diagv04"')
change(gradle,
    'versionName = "0.3.0-fr"',
    'versionName = "0.4.0-fr"')
print("v0.4 watch sync diagnostics, stalled-transfer timeout and manual sample ACK applied")
