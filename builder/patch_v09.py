#!/usr/bin/env python3
"""Pulse Lab v0.9 read-only extraction for handing over to Mi Fitness.
Never ACK any fetched Xiaomi health/activity file. Only query TODAY and PAST.
Auto release the Bluetooth link after completed extraction, to allow official Mi Fitness sync.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1])
src=root/"app/src/main/java/com/pedometer"

def change(path,old,new):
    p=Path(path)
    s=p.read_text(encoding="utf-8")
    if s.count(old)!=1:
        raise RuntimeError(f"v09 patch anchor count={s.count(old)} in {path}: {old[:100]!r}")
    p.write_text(s.replace(old,new),encoding="utf-8")

activity=src/"health/ActivitySync.kt"
change(activity,
    "    private val historicalCandidates: () -> List<ByteArray> = { emptyList() },\n",
    """    private val historicalCandidates: () -> List<ByteArray> = { emptyList() },
    private val onReadOnlyComplete: (() -> Unit)? = null,
""")

# A deliberately *total* guard: EVERY call to ackFile becomes a no-op.
# This covers daily health files, GPS files, workouts, and future parser fallbacks.
# Never send Xiaomi type=8, subtype=5. Do not assume ACK only applies to workouts.
s=activity.read_text(encoding="utf-8")
start=s.index("    private fun ackFile(fileId: ByteArray) {")
end=s.index("    /**",start)
replacement="""    /** Read-only mode: do NOT acknowledge Xiaomi files, even if already decoded.
     * Gadgetbridge's keepActivityDataOnDevice uses the same principle.
     * Mi Fitness must remain able to download the watch's pending records.
     */
    private fun ackFile(fileId: ByteArray) {
        Log.i(TAG, "READ_ONLY_NO_ACK file=" + fileId.toHex())
    }

"""
s=s[:start]+replacement+s[end:]
activity.write_text(s,encoding="utf-8")

# Only TODAY -> PAST -> finish. No alternate history probe or known-ID requests.
change(activity,
    """            2 -> requestArchiveProbe()
""",
    """            2 -> {
                onStatus("FIN LECTURE SEULE : " + receivedCount +
                    " fichier(s) copiés, " + workoutCount +
                    " fichier(s) sportifs. Aucun ACK envoyé. " +
                    "Déconnexion pour laisser la montre à Mi Fitness.")
                onReadOnlyComplete?.invoke()
            }
""")

# Keep diagnostic summary free of untrue claims about confirmed JSON if parsing failed.
raw=src/"pulselab/PulseLabRawStore.kt"
change(raw,
    '''val name = "redmi-" + id.joinToString("") { "%02x".format(it) } + ".bin"''',
    '''val name = "redmi-v09-" + id.joinToString("") { "%02x".format(it) } + ".bin"''')

repo=src/"repo/WatchRepository.kt"
change(repo,
    '''            historicalCandidates = { MiFitnessWorkoutHints.load(context) },
''',
    '''            historicalCandidates = { emptyList() },
            onReadOnlyComplete = {
                scope.launch {
                    // GPS points are saved on a separate IO coroutine.
                    // Schedule JSON export after those writes can complete.
                    delay(1500)
                    PulseLabExportWorker.changed(context)
                    delay(2000)
                    noteSyncStatus("Mode lecture seule terminé. " +
                        "Export JSON demandé ; aucun ACK envoyé. " +
                        "Bluetooth libéré : tu peux ouvrir Mi Fitness.")
                    disconnect()
                }
            },
''')
change(repo,
    '''MutableStateFlow("Diagnostic de synchronisation – v0.8")''',
    '''MutableStateFlow("Diagnostic de synchronisation – v0.9")''')
# Prevent automatic reconnect when Wi-Fi changes after an explicit handover.
change(repo,
    '''        if (!connected) userDisconnected = false
        homeWifiConnected = connected
''',
    '''        // Keep the explicit Mi Fitness handover disconnect latched.
        homeWifiConnected = connected
''')

main=src/"MainActivity.kt"
change(main,
    '''PULSE LAB WATCH • VERSION 0.8''',
    '''PULSE LAB WATCH • VERSION 0.9''')
change(main,
    '''Version active : 0.8 — importer d'abord le ZIP des journaux Mi Fitness.''',
    '''Version active : 0.9 — lecture seule, AUCUN ACK envoyé. Mi Fitness reste prioritaire.''')
change(main,
    '''Text("Étape 2 : synchroniser les séances", style = MaterialTheme.typography.titleLarge)''',
    '''Text("Extraire les séances sans ACK", style = MaterialTheme.typography.titleLarge)''')
change(main,
    '''Text("Synchronisation normale, recherche historique, puis essai de relecture directe des fichiers sportifs connus. Le journal détaille chaque étape.")''',
    '''Text("Mode lecture seule : copie les fichiers du jour et les fichiers passés encore proposés. " +
                                    "AUCUN acquittement n'est envoyé : Mi Fitness pourra ensuite effectuer sa propre synchronisation.")''')
change(main,
    '''Text("Fichiers non décodés : sous-dossier Brut. Relecture ciblée sans ACK et sans réinitialiser la montre.")''',
    '''Text("Fichiers bruts : sous-dossier Brut. La connexion Bluetooth sera libérée après extraction.")''')
change(main,
    '''Text("Synchroniser maintenant")''',
    '''Text("Extraire maintenant (sans ACK)")''')
change(main,
    '''"Synchronisation demandée à la montre."''',
    '''"Extraction lecture seule demandée à la montre ; aucun ACK ne sera envoyé."''')
change(main,
    '''"Pulse Lab — diagnostic v0.8"''',
    '''"Pulse Lab — diagnostic v0.9"''')

# Remove the old Mi Fitness log import card; archived re-fetch has been retired.
p=main
s=p.read_text(encoding="utf-8")
first='''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Étape 1 : importer les anciennes séances",'''
second='''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Extraire les séances sans ACK",'''
assert s.count(first)==1 and s.count(second)==1, "cannot locate former history import panel"
start=s.index(first);end=s.index(second,start)
s=s[:start]+s[end:]
p.write_text(s,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
change(gradle,
    'applicationId = "com.pulselab.watchconnector.historical08"',
    'applicationId = "com.pulselab.watchconnector.readonly09"')
change(gradle,
    'versionName = "0.8.0-fr"',
    'versionName = "0.9.0-fr"')
manifest=root/"app/src/main/AndroidManifest.xml"
change(manifest,
    'android:label="PulseLab Watch 0.8"',
    'android:label="PulseLab Watch 0.9"')

# Fail the build if an ACK command is accidentally restored.
code=activity.read_text(encoding="utf-8")
assert "setSubtype(CMD_FETCH_ACK)" not in code, "ACK command remains active!"
assert "2 -> requestArchiveProbe()" not in code, "historic probe remains enabled!"
assert "3 -> requestKnownWorkoutHints()" in code, "old code changed unexpectedly"
assert "2 -> {" in code and "onReadOnlyComplete?.invoke()" in code
print("v0.9 READ ONLY: all ACK sends removed; no historical probes; Bluetooth auto release.")
