#!/usr/bin/env python3
"""Pulse Lab v1.1: start from LAST PROVEN v0.8 transfer engine.
Copy SPORTS ONLY (no daily health file requests), no Xiaomi activity ACKs.
No historical targeted replay and no alternate list; keep v0.8 file RX/parser.
Manual disconnect to hand the watch back to official Mi Fitness.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1]);src=root/"app/src/main/java/com/pedometer"

def change(path,old,new):
    p=Path(path);s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1:
        raise RuntimeError(f"v11 patch {p}: anchor count {n}, {old[:100]!r}")
    p.write_text(s.replace(old,new),encoding="utf-8")

def replace_section(path,start,end,replacement):
    p=Path(path);s=p.read_text(encoding="utf-8")
    assert s.count(start)==1 and s.count(end)==1, f"v11 section anchors not unique: {p} {start}"
    a=s.index(start);b=s.index(end,a)
    assert a<b
    p.write_text(s[:a]+replacement+s[b:],encoding="utf-8")

activity=src/"health/ActivitySync.kt"

# v0.8's successful TODAY/PASSED file transport is unchanged.
# Disable historical / alternate probe after past files: they repeatedly stalled and disconnected.
replace_section(activity,
    "    private fun finishPhase() {\n",
    "    fun requestToday() {\n",
    '''    private fun finishPhase() {
        currentFileId = null
        val completed = syncPhase
        syncPhase = 0
        when (completed) {
            1 -> requestPast()  // preserve the working v0.8 normal list protocol
            else -> onStatus("FIN v1.1 : " + receivedCount +
                " fichier(s) sportifs bruts enregistrés, aucun fichier quotidien lu, " +
                "AUCUN ACK envoyé. Attends les JSON, puis appuie sur Déconnecter la montre " +
                "avant d'ouvrir Mi Fitness.")
        }
    }

''')

# Keep original order from v0.8. The Redmi Watch 5 Lite correctly delivered
# 84f0ed6a0402da / 84f0ed6a0405d8 / 84f0ed6a0405d9 with this implementation.
# The key difference: no requests for health file type=0, so daily file 040300
# cannot block our sport transfers as it did in the v0.9 logs.
replace_section(activity,
    "    private fun parseFileIds(data: ByteArray) {\n",
    "    /** Returns the parsed workout",
    '''    private fun parseFileIds(data: ByteArray) {
        val fileCount = data.size / 7
        val sports = mutableListOf<ByteArray>()
        for (i in 0 until fileCount) {
            val id = data.copyOfRange(i * 7, (i + 1) * 7)
            if (decodeFileId(id).type == 1) {
                sports.add(id)
            } else {
                Log.i(TAG, "v1.1: daily file SKIPPED (not requested) " + id.toHex())
            }
        }
        // Caller has already incremented offeredCount for the whole list.
        offeredCount = (offeredCount - (fileCount - sports.size)).coerceAtLeast(0)
        onStatus("MODE SPORT SEUL (moteur v0.8) : " +
            sports.size + " fichier(s) sportifs à copier, " +
            (fileCount - sports.size) + " fichier(s) quotidiens IGNORÉS, zéro ACK.")
        pendingFileIds.clear()
        pendingFileIds.addAll(sports) // DO NOT reorder: preserve working v0.8 behavior
        if (pendingFileIds.isNotEmpty()) {
            requestFile(pendingFileIds.removeAt(0))
        } else {
            finishPhase()
        }
    }

''')

# Central gate covers every activity file branch, including summary,
# details, GPS, manual, non-workout and parse failures.
replace_section(activity,
    "    private fun ackFile(fileId: ByteArray) {\n",
    "    /**\n     * ACK a workout-summary",
    '''    /** v1.1 read-only. Never clear watch's pending sync for Mi Fitness. */
    private fun ackFile(fileId: ByteArray) {
        Log.i(TAG, "v1.1 NO_ACK (copy only) " + fileId.toHex())
    }

''')

# Preserve the v0.8 binary file naming to allow the user to find every raw file
# in Downloads/PulseLab/WatchInbox/Brut even if parser misses a workout.
repo=src/"repo/WatchRepository.kt"
change(repo,
    '''            historicalCandidates = { MiFitnessWorkoutHints.load(context) },''',
    '''            historicalCandidates = { emptyList() },''')
change(repo,
    '''MutableStateFlow("Diagnostic de synchronisation – v0.8")''',
    '''MutableStateFlow("Diagnostic de synchronisation – v1.1")''')
# Eliminate the duplicate "automatic workout ended" read and reserve this protocol
# for the explicit button; avoids racing a new read against Mi Fitness.
r=repo.read_text(encoding="utf-8")
trigger='''                    // Fetch activity data to get workout results + GPS track
'''
assert r.count(trigger)==1,"Cannot find automatic fetch after workout"
a=r.index(trigger)
b=r.index("                }\n                1 -> if (enabled) startStepPoll()",a)
old=r[a:b]
assert "activitySync?.requestPast()" in old and "buildActivityFetchToday" in old
r=r[:a]+'''                    // v1.1: manual extraction only; no background workout-file commands.
                    // Keep new workout pending for the user's chosen Pulse Lab / Mi Fitness order.
'''+r[b:]
repo.write_text(r,encoding="utf-8")

main=src/"MainActivity.kt"
change(main,
    '''PULSE LAB WATCH • VERSION 0.8''',
    '''PULSE LAB WATCH • VERSION 1.1''')
change(main,
    '''Version active : 0.8 — importer d'abord le ZIP des journaux Mi Fitness.''',
    '''Version active : 1.1 — moteur éprouvé v0.8, SPORTS SEULS, zéro ACK.''')
change(main,
    '''Text("Étape 2 : synchroniser les séances", style = MaterialTheme.typography.titleLarge)''',
    '''Text("Copier les séances sportives (sans ACK)", style = MaterialTheme.typography.titleLarge)''')
change(main,
    '''"Pulse Lab — diagnostic v0.8"''',
    '''"Pulse Lab — diagnostic v1.1"''')
change(main,
    '''Text("Synchroniser maintenant")''',
    '''Text("Copier les sports maintenant")''')
# Remove obsolete importer UI (replay of archived IDs is disabled).
p=main
s=p.read_text(encoding="utf-8")
a='''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Étape 1 : importer les anciennes séances",'''
b='''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Copier les séances sportives (sans ACK)",'''
assert s.count(a)==s.count(b)==1
s=s[:s.index(a)]+s[s.index(b,s.index(a)):]
p.write_text(s,encoding="utf-8")
# Replace misleading old history search guidance with direct Mi Fitness handover.
s=p.read_text(encoding="utf-8")
for line in s.splitlines():
    if ("Synchronisation normale, recherche historique" in line or
        "Relecture ciblée sans ACK" in line or
        "Sélectionne le ZIP original Mi Fitness" in line):
        print("UI old text remains:",line.strip())
s=s.replace(
    '''Text("Synchronisation normale, recherche historique, puis essai de relecture directe des fichiers sportifs connus. Le journal détaille chaque étape.")''',
    '''Text("Moteur Bluetooth v0.8 : copie uniquement les fichiers sportifs avec leur GPS. " +
                                    "Fichiers quotidiens ignorés. Aucun ACK envoyé. " +
                                    "Après extraction, appuie sur Déconnecter la montre puis ouvre Mi Fitness.")''')
s=s.replace(
    '''Text("Fichiers non décodés : sous-dossier Brut. Relecture ciblée sans ACK et sans réinitialiser la montre.")''',
    '''Text("Fichiers bruts : sous-dossier Brut. Ne pas ouvrir Mi Fitness avant d'avoir déconnecté Pulse Lab.")''')
p.write_text(s,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
change(gradle,
    '''applicationId = "com.pulselab.watchconnector.historical08"''',
    '''applicationId = "com.pulselab.watchconnector.v11sportreadonly"''')
change(gradle,
    '''versionName = "0.8.0-fr"''',
    '''versionName = "1.1.0-fr"''')
manifest=root/"app/src/main/AndroidManifest.xml"
change(manifest,
    '''android:label="PulseLab Watch 0.8"''',
    '''android:label="PulseLab Watch 1.1"''')

# Static safety assertions: ACK is not transmitted by ANY Kotlin source.
app=root/"app/src/main/java"
all_kotlin="\n".join(p.read_text(encoding="utf-8") for p in app.rglob("*.kt"))
code=activity.read_text(encoding="utf-8")
assert "setSubtype(CMD_FETCH_ACK)" not in all_kotlin, "ACK sending unexpectedly enabled"
assert "setActivitySyncAckFileIds(" not in all_kotlin, "ACK protobuf unexpectedly emitted"
assert "requestArchiveProbe()" not in code.split("private fun finishPhase()")[1].split("fun requestToday()")[0]
assert "3 -> requestKnownWorkoutHints()" not in code.split("private fun finishPhase()")[1].split("fun requestToday()")[0]
assert "pendingFileIds.addAll(sports) // DO NOT reorder" in code
assert "if (decodeFileId(id).type == 1)" in code
print("v1.1: v0.8 activity reception preserved; ONLY sports requested; NO ACK anywhere; no archive replay")
