#!/usr/bin/env python3
from pathlib import Path
import sys, shutil

root=Path(sys.argv[1]); src=root/"app/src/main/java/com/pedometer"

def change(path,old,new):
    p=Path(path); s=p.read_text(encoding="utf-8")
    if s.count(old)!=1: raise RuntimeError("Anchor error in "+str(p)+": "+repr(old[:80])+" count="+str(s.count(old)))
    p.write_text(s.replace(old,new),encoding="utf-8")

act=src/"health/ActivitySync.kt"
change(act,
"    private val context: Context? = null, // for debug dumps into filesDir\n",
"""    private val context: Context? = null, // for debug dumps into filesDir
    private val onStatus: (String) -> Unit = {},
    private val onRawFile: ((ByteArray, ByteArray) -> Boolean)? = null,
""")
change(act,
"    private var currentFileId: ByteArray? = null\n",
"""    private var currentFileId: ByteArray? = null
    @Volatile private var syncPhase = 0 // 0 idle, 1 today, 2 past
    private var receivedCount = 0
    private var offeredCount = 0
    private var workoutCount = 0

    private fun finishPhase() {
        currentFileId = null
        if (syncPhase == 1) {
            syncPhase = 0
            requestPast()
        } else {
            syncPhase = 0
            onStatus("Terminé : " + receivedCount + " fichier(s) sauvegardé(s), " +
                workoutCount + " séance(s) décodée(s). Voir Téléchargements/PulseLab/WatchInbox.")
        }
    }
""")
change(act,
"    fun requestToday() {\n",
"""    fun requestToday() {
        if (syncPhase != 0) {
            onStatus("Une synchronisation est déjà en cours (" + receivedCount + " fichier(s) reçus).")
            return
        }
        syncPhase = 1
        receivedCount = 0
        offeredCount = 0
        workoutCount = 0
        pendingChunks.clear()
        pendingFileIds.clear()
        currentFileId = null
        onStatus("Demande de la liste des données du jour…")
""")
change(act,
"    fun requestPast() {\n",
"""    fun requestPast() {
        if (syncPhase != 0) return
        syncPhase = 2
        onStatus("Demande de la liste des données historiques…")
""")
change(act,
"    private fun requestFile(fileId: ByteArray) {\n        currentFileId = fileId\n",
"""    private fun requestFile(fileId: ByteArray) {
        currentFileId = fileId
        onStatus("Réception du fichier " + (receivedCount + 1) + " sur " + offeredCount + "…")
""")
a=act.read_text(encoding="utf-8")
start=a.index("            CMD_FETCH_TODAY, CMD_FETCH_PAST -> {")
end=a.index("            CMD_FETCH_FILE -> {",start)
a=a[:start]+"""            CMD_FETCH_TODAY, CMD_FETCH_PAST -> {
                if (cmd.subtype != syncPhase) return
                val bytes = if (cmd.hasHealth() && cmd.health.hasActivityRequestFileIds())
                    cmd.health.activityRequestFileIds.toByteArray() else ByteArray(0)
                if (bytes.isEmpty()) {
                    onStatus("Aucun fichier pour cette période.")
                    finishPhase()
                } else if (bytes.size % 7 != 0) {
                    onStatus("Liste des fichiers invalide : " + bytes.size + " octets.")
                    syncPhase = 0
                } else {
                    offeredCount += bytes.size / 7
                    onStatus("La montre propose " + (bytes.size / 7) + " fichier(s).")
                    parseFileIds(bytes)
                }
            }
"""+a[end:]
act.write_text(a,encoding="utf-8")
change(act,
'        if (chunkNum == totalChunks) {\n',
"""        if (chunkNum == totalChunks && chunks.size == totalChunks && chunks.all { it.isNotEmpty() }) {
""")
change(act,
"""                val workout = processFile(fileId, fullData)
                when {
""",
"""                val stored = try { onRawFile?.invoke(fileId, fullData) ?: true }
                    catch (e: Exception) { Log.e(TAG, "Raw save failed", e); false }
                if (!stored) {
                    onStatus("Impossible de sauvegarder le fichier brut. Aucun acquittement envoyé.")
                    syncPhase = 0
                    return
                }
                val workout = processFile(fileId, fullData)
                when {
""")
change(act,
"""                    workout != null && onWorkoutPersist != null ->
                        persistAndAck(workout, fileId)
""",
"""                    workout != null && onWorkoutPersist != null ->
                        persistAndAck(workout, fileId)
""")
change(act,
"""            // Request next file if any
            if (pendingFileIds.isNotEmpty()) {
                val nextId = pendingFileIds.removeAt(0)
                requestFile(nextId)
            }
""",
"""            receivedCount++
            if (fullData.size > 0 && fullData.size >= 7 && decodeFileId(fullData.copyOfRange(0, 7)).type == 1) workoutCount++
            currentFileId = null
            onStatus("Sauvegardés : " + receivedCount + " / " + offeredCount +
                " • fichiers sportifs : " + workoutCount)
            if (pendingFileIds.isNotEmpty()) {
                val nextId = pendingFileIds.removeAt(0)
                requestFile(nextId)
            } else {
                finishPhase()
            }
""")
change(act,
"""            ackFile(fileId)
        } catch (e: Exception) {
            Log.e(TAG, "Workout parse failed, dropping file """,
"""            ackFile(fileId)
        } catch (e: Exception) {
            Log.e(TAG, "Workout parse failed; not ACKing file """)
change(act,
"""            ackFile(fileId)
        }
    }

    fun handleCommand""",
"""            onStatus("Échec de l’enregistrement de la séance. Fichier brut conservé, non acquitté.")
        }
    }

    fun handleCommand""")

raw=src/"pulselab/PulseLabRawStore.kt"; raw.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2("builder/PulseLabRawStore.kt",raw)
repo=src/"repo/WatchRepository.kt"
change(repo,
"    val data: StateFlow<WatchData> = _data\n",
"""    val data: StateFlow<WatchData> = _data
    private val _syncStatus = MutableStateFlow("Aucune synchronisation lancée.")
    val syncStatus: StateFlow<String> = _syncStatus
""")
change(repo,"import com.pedometer.pulselab.PulseLabExportWorker\n",
"import com.pedometer.pulselab.PulseLabExportWorker\nimport com.pedometer.pulselab.PulseLabRawStore\n")
change(repo,
"""            context = context,
        )
        activitySync = sync
""",
"""            context = context,
            onStatus = { message -> _syncStatus.value = message },
            onRawFile = { id, data -> PulseLabRawStore.save(context, id, data) },
        )
        activitySync = sync
""")
change(repo,
"""            healthService?.initialize()
            delay(300)
""",
"""            healthService?.initialize()
            delay(300)
            activitySync?.requestToday()
""")
change(repo,
"""                        link.send(CommandHelper.buildActivityFetchToday())
                        delay(3000)
                        activitySync?.requestPast()
""",
"""                        activitySync?.requestToday()
""")
change(repo,
"""            link.send(CommandHelper.buildBatteryRequest())
            link.send(CommandHelper.buildActivityFetchToday())
            delay(1000)
            activitySync?.requestPast()
            delay(500)
""",
"""            link.send(CommandHelper.buildBatteryRequest())
            activitySync?.requestToday()
            delay(500)
""")
change(repo,
"""    fun refreshFromWatch() {
        if (_data.value.connectionStatus != ConnectionStatus.Connected) return
""",
"""    fun refreshFromWatch() {
        if (_data.value.connectionStatus != ConnectionStatus.Connected) {
            _syncStatus.value = "Montre déconnectée : commande non envoyée."
            return
        }
""")
health=src/"health/HealthService.kt"
change(health,
"""        protocolHandler.sendCommand(CommandHelper.buildActivityFetchToday())
        // Past will be requested by ActivitySync after today's files are processed
""",
"""        // ActivitySync requests TODAY then PAST sequentially.
""")
main=src/"MainActivity.kt"
change(main,
"                val state by vm.state.collectAsState()\n",
"""                val state by vm.state.collectAsState()
                val syncInfo by PedometerApp.repository.syncStatus.collectAsState()
""")
change(main,
'                                Text("Dossier : Téléchargements / PulseLab / WatchInbox")\n',
'''                                Text("État réel du téléchargement :", style = MaterialTheme.typography.labelLarge)
                                Text(syncInfo)
                                Text("Dossier : Téléchargements / PulseLab / WatchInbox")
                                Text("Fichiers non décodés : sous-dossier Brut.")
''')
gradle=root/"app/build.gradle.kts"
change(gradle,
'applicationId = "com.pulselab.watchconnector.fr"',
'applicationId = "com.pulselab.watchconnector.diagsync"')
change(gradle,
'versionName = "0.2.0-fr"',
'versionName = "0.3.0-fr"')
print("Synchronisation séquentielle, statut visible et fichiers bruts activés")
