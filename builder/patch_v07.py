#!/usr/bin/env python3
"""v0.7: private Mi Fitness log-index import + safe direct re-fetch by known IDs."""
from pathlib import Path
import shutil
import sys
root=Path(sys.argv[1]);src=root/"app/src/main/java/com/pedometer"

def change(path,old,new):
    p=Path(path);s=p.read_text(encoding="utf-8")
    count=s.count(old)
    if count!=1:
        raise RuntimeError("v0.7 anchor "+str(p)+" count="+str(count)+" old="+repr(old[:90]))
    p.write_text(s.replace(old,new),encoding="utf-8")

dest=src/"pulselab/MiFitnessWorkoutHints.kt"
dest.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2("builder/MiFitnessWorkoutHints.kt",dest)

activity=src/"health/ActivitySync.kt"
change(activity,
    "    private val onRawFile: ((ByteArray, ByteArray) -> Boolean)? = null,\n",
    """    private val onRawFile: ((ByteArray, ByteArray) -> Boolean)? = null,
    private val historicalCandidates: () -> List<ByteArray> = { emptyList() },
""")
change(activity,
    "    private val seenFileIds = mutableSetOf<String>()\n",
    """    private val seenFileIds = mutableSetOf<String>()
    private val archiveHintQueue = java.util.ArrayDeque<ByteArray>()
    private var directHintCount = 0
    private var directHintSuccess = 0

    private fun requestNextKnownFile() {
        val next = archiveHintQueue.poll()
        if (next == null) {
            finishPhase()
            return
        }
        onStatus("Relecture ciblée " + (directHintCount - archiveHintQueue.size) +
            "/" + directHintCount + " : " + next.toHex() +
            " (sans suppression ni acquittement)…")
        requestFile(next)
    }

    private fun requestKnownWorkoutHints() {
        val candidates = try {
            historicalCandidates().filter { it.size == 7 }.distinctBy { it.toHex() }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot read imported workout hints", e)
            emptyList()
        }
        val missing = candidates.filterNot { seenFileIds.contains(it.toHex()) }
        if (missing.isEmpty()) {
            onStatus("FIN : " + workoutCount + " fichier(s) sportifs reçu(s). " +
                "Aucun ancien ID à tester. Importe le ZIP de journaux Mi Fitness " +
                "pour une recherche ciblée. 20 séances non encore confirmées.")
            return
        }
        syncPhase = 4
        directHintCount = missing.size
        directHintSuccess = 0
        offeredCount += missing.size
        archiveHintQueue.clear()
        archiveHintQueue.addAll(missing)
        onStatus("Essai de relecture directe de " + missing.size +
            " fichier(s) sportifs identifiés dans Mi Fitness.")
        requestNextKnownFile()
    }
""")
change(activity,
    """            else -> onStatus(
                "FIN : " + receivedCount + " fichier(s) sauvegardé(s), " +
""",
    """            3 -> requestKnownWorkoutHints()
            else -> onStatus(
                "FIN : " + receivedCount + " fichier(s) sauvegardé(s), " +
""")
change(activity,
    "        if (elapsed < 30000L) return\n",
    """        if (elapsed < (if (syncPhase == 4) 9000L else 30000L)) return
        if (syncPhase == 4) {
            onStatus("La montre ne répond pas à l'ID historique " +
                (currentFileId?.toHex() ?: "inconnu") + " (9 s). Essai suivant.")
            pendingChunks.clear()
            currentFileId = null
            lastProgressMs = SystemClock.elapsedRealtime()
            requestNextKnownFile()
            return
        }
""")
# Always know the expected ID; ignore chunks after timeouts (instead of counting phantom files).
change(activity,
    """            // Extract fileId from first 7 bytes of the data itself
            if (fullData.size >= 7) {
""",
    """            // Validate the watch payload before saving or ACKing.
            if (fullData.size < 13) {
                onStatus("Fichier incomplet, aucun acquittement envoyé.")
                syncPhase = 0
                return
            }
            val calculatedCrc = java.util.zip.CRC32().apply {
                update(fullData, 0, fullData.size - 4)
            }.value
            val expectedCrc = java.nio.ByteBuffer.wrap(fullData,
                fullData.size - 4, 4).order(ByteOrder.LITTLE_ENDIAN)
                .int.toLong() and 0xffffffffL
            if (calculatedCrc != expectedCrc) {
                onStatus("CRC32 invalide : fichier refusé, aucun acquittement.")
                syncPhase = 0
                return
            }
            if (currentFileId != null &&
                !fullData.copyOfRange(0, 7).contentEquals(currentFileId)) {
                onStatus("Fichier reçu différent du fichier demandé, aucun acquittement.")
                syncPhase = 0
                return
            }
            // Extract fileId from first 7 bytes of the data itself
            if (fullData.size >= 7) {
""")
change(activity,
    """                when {
                    // Don't ACK manual samples (subtype=6) so they come again on next fetch
""",
    """                when {
                    syncPhase == 4 -> {
                        // Deliberately NEVER ACK a directly-probed historical file.
                        // It might no longer be in the watch's transfer queue.
                        if (workout != null && onWorkoutPersist != null) {
                            try {
                                runBlocking { onWorkoutPersist.invoke(workout) }
                            } catch (e: Exception) {
                                onStatus("Fichier brut conservé, erreur base : " +
                                    (e.message ?: "inconnue"))
                                syncPhase = 0
                                return
                            }
                        }
                        directHintSuccess++
                        onStatus("ID historique récupéré et conservé sans ACK : " + fileId.toHex())
                    }
                    // Don't ACK manual samples (subtype=6) so they come again on next fetch
""")
change(activity,
    """            if (pendingFileIds.isNotEmpty()) {
                val nextId = pendingFileIds.removeAt(0)
                requestFile(nextId)
            } else {
                finishPhase()
            }
""",
    """            if (syncPhase == 4) {
                requestNextKnownFile()
            } else if (pendingFileIds.isNotEmpty()) {
                val nextId = pendingFileIds.removeAt(0)
                requestFile(nextId)
            } else {
                finishPhase()
            }
""")
# Make phase-4 summary readable when queue ends.
change(activity,
    """            3 -> requestKnownWorkoutHints()
            else -> onStatus(
""",
    """            3 -> requestKnownWorkoutHints()
            4 -> onStatus("FIN relecture historique : " + directHintSuccess + "/" +
                directHintCount + " fichier(s) ciblé(s) reçu(s), " + workoutCount +
                " fichier(s) sportifs au total. Aucun accusé de réception " +
                "envoyé pour ces essais. Exporter le journal.")
            else -> onStatus(
""")

repo=src/"repo/WatchRepository.kt"
change(repo,
    "import com.pedometer.pulselab.PulseLabRawStore\n",
    "import com.pedometer.pulselab.PulseLabRawStore\nimport com.pedometer.pulselab.MiFitnessWorkoutHints\n")
change(repo,
    "            onRawFile = { id, data -> PulseLabRawStore.save(context, id, data) },\n",
    """            onRawFile = { id, data -> PulseLabRawStore.save(context, id, data) },
            historicalCandidates = { MiFitnessWorkoutHints.load(context) },
""")
change(repo,
    'MutableStateFlow("Diagnostic de synchronisation – v0.4")',
    'MutableStateFlow("Diagnostic de synchronisation – v0.7")')

main=src/"MainActivity.kt"
change(main,
    "import com.pedometer.pulselab.PulseLabExportWorker\n",
    "import com.pedometer.pulselab.PulseLabExportWorker\nimport com.pedometer.pulselab.MiFitnessWorkoutHints\n")
change(main,
    "import java.util.Locale\n",
    "import java.util.Locale\nimport kotlinx.coroutines.launch\n")
change(main,
    "                val syncLog by PedometerApp.repository.syncLog.collectAsState()\n",
    """                val syncLog by PedometerApp.repository.syncLog.collectAsState()
                var workoutHintText by remember {
                    mutableStateOf(MiFitnessWorkoutHints.describe(this@MainActivity))
                }
                var importingHints by remember { mutableStateOf(false) }
                val hintScope = rememberCoroutineScope()
                val hintFilePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        importingHints = true
                        workoutHintText = "Lecture des journaux Mi Fitness en cours…"
                        hintScope.launch {
                            val report = MiFitnessWorkoutHints.importLogs(
                                this@MainActivity, uri
                            )
                            workoutHintText = report.french()
                            importingHints = false
                        }
                    }
                }
""")
change(main,
    'Text("Connecteur Redmi Watch 5 Lite", style = MaterialTheme.typography.titleMedium)',
    'Text("PULSE LAB WATCH • VERSION 0.7", style = MaterialTheme.typography.titleLarge)\n                        Text("Redmi Watch 5 Lite — extraction des séances", style = MaterialTheme.typography.titleMedium)')
change(main,
    'Text("Le bouton recherche les fichiers courants puis teste une liste alternative. Les 20 séances déjà synchronisées ne sont pas garanties accessibles.")',
    'Text("Synchronisation normale, recherche historique, puis essai de relecture directe des fichiers sportifs connus. Le journal détaille chaque étape.")')
change(main,
    'Text("Fichiers non décodés : sous-dossier Brut. Recherche historique expérimentale ; aucune remise à zéro de la montre.")',
    """Text("Fichiers non décodés : sous-dossier Brut. Relecture ciblée sans ACK et sans réinitialiser la montre.")
                                Text("Version active : 0.7 — si ce numéro n'apparaît pas, une ancienne application est ouverte.")""")
change(main,
    '                                Text("Chronologie :", style = MaterialTheme.typography.labelLarge)\n',
    '''                                Text("Chronologie :", style = MaterialTheme.typography.labelLarge)
''')
# Add the log selector card immediately above the workouts card.
change(main,
'''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Séances récupérées : " + state.recentWorkouts.size,
''',
'''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Anciennes séances : importer les identifiants",
                                    style = MaterialTheme.typography.titleLarge)
                                Text("Sélectionne une seule fois le ZIP de journaux Mi Fitness. " +
                                    "L'application lit uniquement les identifiants des fichiers sportifs " +
                                    "et les conserve localement, sans envoyer de données sur Internet.")
                                Button(
                                    onClick = { hintFilePicker.launch(arrayOf("*/*")) },
                                    enabled = !importingHints,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (importingHints) "Analyse en cours…" else
                                        "Importer les journaux Mi Fitness (.zip)")
                                }
                                Text(workoutHintText)
                                Text("Puis appuie sur Synchroniser maintenant. " +
                                    "La présence de séances sur l'écran de la montre ne garantit pas " +
                                    "leur relecture après synchronisation par Mi Fitness.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Séances récupérées : " + state.recentWorkouts.size,
''')

gradle=root/"app/build.gradle.kts"
change(gradle,
    'applicationId = "com.pulselab.watchconnector.probe06"',
    'applicationId = "com.pulselab.watchconnector.historical07"')
change(gradle,
    'versionName = "0.6.0-fr"',
    'versionName = "0.7.0-fr"')
manifest=root/"app/src/main/AndroidManifest.xml"
change(manifest, 'android:label="PulseLab Watch Connector"',
    'android:label="PulseLab Watch 0.7"')
print("v0.7 historical re-fetch by IDs + CRC32 + Mi Fitness log importer applied")
