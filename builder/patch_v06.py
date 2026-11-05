#!/usr/bin/env python3
"""v0.6: try alternate read-only listing mode for archived workouts on Redmi Watch 5 Lite.
The mode is experimental: unknown1=1 is NOT documented as a historical replay switch.
It is used solely to probe for extra file IDs; any new records follow normal validated save.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1]); src=root/"app/src/main/java/com/pedometer"

def change(path,old,new):
    p=Path(path); s=p.read_text(encoding="utf-8")
    if s.count(old)!=1:
        raise RuntimeError("Patch anchor mismatch: "+str(p)+" count="+str(s.count(old))+" anchor="+repr(old[:85]))
    p.write_text(s.replace(old,new),encoding="utf-8")

act=src/"health/ActivitySync.kt"
change(act,
    "    private var workoutCount = 0\n",
    """    private var workoutCount = 0
    private val seenFileIds = mutableSetOf<String>()
""")
s=act.read_text(encoding="utf-8")
start=s.index("    private fun finishPhase() {")
end=s.index("    fun requestToday() {",start)
s=s[:start]+'''    private fun finishPhase() {
        currentFileId = null
        val finished = syncPhase
        syncPhase = 0
        when (finished) {
            1 -> requestPast()
            2 -> requestArchiveProbe()
            else -> onStatus(
                "FIN : " + receivedCount + " fichier(s) sauvegardé(s), " +
                workoutCount + " fichier(s) de sport détecté(s). " +
                if (workoutCount == 0)
                    "La montre ne propose pas ses séances déjà synchronisées " +
                    "dans les listes testées. Journal à exporter pour analyse."
                else "Vérifie les séances JSON sous Téléchargements/PulseLab/WatchInbox."
            )
        }
    }

    /** Alternate listing parameter from the Xiaomi protobuf, not a proven archive API.
     * Read-only list query; no deletion or state reset. */
    private fun requestArchiveProbe() {
        if (syncPhase != 0) return
        syncPhase = 3
        lastProgressMs = SystemClock.elapsedRealtime()
        onStatus("Recherche des séances historiques : essai de liste alternative (mode 1)…")
        val cmd = XiaomiProto.Command.newBuilder()
            .setType(COMMAND_TYPE)
            .setSubtype(CMD_FETCH_TODAY)
            .setHealth(XiaomiProto.Health.newBuilder()
                .setActivitySyncRequestToday(
                    XiaomiProto.ActivitySyncRequestToday.newBuilder().setUnknown1(1)))
            .build()
        protocolHandler.sendCommand(cmd)
    }

'''+s[end:]
act.write_text(s,encoding="utf-8")
change(act,
    """        workoutCount = 0
        pendingChunks.clear()
""",
    """        workoutCount = 0
        seenFileIds.clear()
        pendingChunks.clear()
""")
change(act,
    """        currentFileId = fileId
        lastProgressMs = SystemClock.elapsedRealtime()
""",
    """        currentFileId = fileId
        seenFileIds.add(fileId.toHex())
        lastProgressMs = SystemClock.elapsedRealtime()
""")
change(act,
    """                if (cmd.subtype != syncPhase) {
                    Log.w(TAG, "Unexpected activity file-list subtype " + cmd.subtype)
                    return
                }
""",
    """                val requiredSubtype = if (syncPhase == 3) CMD_FETCH_TODAY else syncPhase
                if (cmd.subtype != requiredSubtype || syncPhase == 0) {
                    Log.w(TAG, "Unexpected activity file-list subtype " + cmd.subtype +
                        " during phase " + syncPhase)
                    return
                }
""")
change(act,
    """                if (bytes.isEmpty()) {
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
""",
    """                if (bytes.isEmpty()) {
                    onStatus(if (syncPhase == 3)
                        "Liste alternative vide : aucune séance supplémentaire proposée."
                    else "Aucun fichier pour cette période.")
                    finishPhase()
                } else if (bytes.size % 7 != 0) {
                    onStatus("Liste des fichiers invalide : " + bytes.size + " octets.")
                    syncPhase = 0
                } else {
                    val allIds = bytes.toList().chunked(7).map { it.toByteArray() }
                    val newIds = allIds.filter { !seenFileIds.contains(it.toHex()) }
                    if (syncPhase == 3) {
                        onStatus("Liste alternative : " + allIds.size + " identifiant(s), " +
                            newIds.size + " jamais demandés dans cette session.")
                    } else {
                        onStatus("La montre propose " + allIds.size + " fichier(s).")
                    }
                    if (newIds.isEmpty()) {
                        onStatus("Tous les identifiants ont déjà été reçus dans cette session.")
                        finishPhase()
                    } else {
                        val filtered = newIds.flatMap { it.asList() }.toByteArray()
                        offeredCount += newIds.size
                        parseFileIds(filtered)
                    }
                }
""")
change(act,
    "    fun handleRawData(data: ByteArray) {\n        if (data.size < 4) return\n",
    """    fun handleRawData(data: ByteArray) {
        if (syncPhase == 0 || currentFileId == null) {
            Log.d(TAG, "Ignoring late unsolicited activity chunk")
            return
        }
        if (data.size < 4) return
""")
change(act,
    "        if (chunkNum == totalChunks && chunks.size == totalChunks && chunks.all { it.isNotEmpty() }) {\n",
    "        if (chunks.size == totalChunks && chunks.all { it.isNotEmpty() }) {\n")
repo=src/"repo/WatchRepository.kt"
change(repo,
    """            healthService?.initialize()
            delay(300)
            activitySync?.requestToday()
""",
    """            healthService?.initialize()
            delay(300)
            noteSyncStatus("Montre connectée. Appuie sur Synchroniser pour rechercher les séances.")
""")
# Keep watch's workout-finished trigger unchanged; manual refresh remains authoritative.
main=src/"MainActivity.kt"
change(main,
    'Text("Les nouvelles activités sont demandées à la connexion.")',
    'Text("Le bouton recherche les fichiers courants puis teste une liste alternative. Les 20 séances déjà synchronisées ne sont pas garanties accessibles.")')
change(main,
    'Text("Fichiers non décodés : sous-dossier Brut.")',
    'Text("Fichiers non décodés : sous-dossier Brut. Recherche historique expérimentale ; aucune remise à zéro de la montre.")')
change(main,
    'MutableStateFlow("Diagnostic de synchronisation – v0.4")',
    'MutableStateFlow("Diagnostic de synchronisation – v0.6")') if False else None
gradle=root/"app/build.gradle.kts"
change(gradle,
    'applicationId = "com.pulselab.watchconnector.diagv04"',
    'applicationId = "com.pulselab.watchconnector.probe06"')
change(gradle,
    'versionName = "0.4.0-fr"',
    'versionName = "0.6.0-fr"')
print("Alternate archive list query, dedup and manual sync applied")
