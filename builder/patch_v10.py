#!/usr/bin/env python3
"""v1.0: sport-only, manual, read-only workout transfer for Redmi Watch 5 Lite.
Avoid v0.9's recurring stall on daily files preceding all sport records.
Do NOT send Xiaomi ACK; preserve pending workouts for Mi Fitness.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1])
src=root/"app/src/main/java/com/pedometer"

def change(path, old, new):
    p=Path(path)
    data=p.read_text(encoding="utf-8")
    count=data.count(old)
    if count != 1:
        raise RuntimeError(f"v10 anchor count={count} in {p}: {old[:130]!r}")
    p.write_text(data.replace(old,new),encoding="utf-8")

activity=src/"health/ActivitySync.kt"
s=activity.read_text(encoding="utf-8")
start=s.index("    private fun parseFileIds(data: ByteArray) {")
end=s.index("    /** Returns the parsed workout",start)
s=s[:start]+'''    /**
     * SPORT ONLY. The watch offers daily files first. On this firmware the
     * second daily file can stall for 30s and disconnect Bluetooth, preventing
     * subsequent workout summary, detail and GPS files from ever downloading.
     * No ACK is needed; we request individual SPORT files in order.
     */
    private fun parseFileIds(data: ByteArray) {
        val count = data.size / 7
        val sports = mutableListOf<ByteArray>()
        for (i in 0 until count) {
            val id = data.copyOfRange(i * 7, (i + 1) * 7)
            val info = decodeFileId(id)
            if (info.type == 1) sports.add(id)
            else Log.d(TAG, "SPORT_ONLY skipping daily-health file: " + id.toHex())
        }

        // Correct the offered count for the health files we did not fetch.
        offeredCount = (offeredCount - count + sports.size).coerceAtLeast(0)
        val preferred = sports.sortedWith(compareBy<ByteArray> { id ->
            when ((id[6].toInt() and 0xff) and 0x03) {
                1 -> 0   // summary first (best chance of obtaining an activity JSON)
                2 -> 1   // GPS second
                else -> 2 // optional workout detail
            }
        })
        pendingFileIds.clear()
        pendingFileIds.addAll(preferred)
        onStatus("LECTURE SPORT SEULE : " + sports.size +
            " fichier(s) sportifs, " + (count - sports.size) +
            " fichier(s) quotidiens IGNORÉS, sans aucun ACK.")
        if (pendingFileIds.isNotEmpty()) {
            requestFile(pendingFileIds.removeAt(0))
        } else {
            finishPhase()
        }
    }

'''+s[end:]
activity.write_text(s,encoding="utf-8")

# If a sport file is unresponsive, move to remaining sport files instead
# of abandoning all workouts. No ACK and no deletion on timeout.
change(activity,
    """        if (elapsed < (if (syncPhase == 4) 9000L else 30000L)) return
""",
    """        if (elapsed < (if (syncPhase == 4) 9000L else 30000L)) return
        if ((syncPhase == 1 || syncPhase == 2) && currentFileId != null) {
            onStatus("SPORT SANS RÉPONSE : " + currentFileId!!.toHex() +
                " (30 s). Fichier conservé sur la montre, essai du suivant.")
            pendingChunks.clear()
            currentFileId = null
            lastProgressMs = SystemClock.elapsedRealtime()
            if (pendingFileIds.isNotEmpty()) {
                requestFile(pendingFileIds.removeAt(0))
            } else {
                finishPhase()
            }
            return
        }
""")

raw=src/"pulselab/PulseLabRawStore.kt"
change(raw,
    '''val name = "redmi-v09-" + id.joinToString("") { "%02x".format(it) } + ".bin"''',
    '''val name = "redmi-v10-" + id.joinToString("") { "%02x".format(it) } + ".bin"''')

repo=src/"repo/WatchRepository.kt"
change(repo,
    '''MutableStateFlow("Diagnostic de synchronisation – v0.9")''',
    '''MutableStateFlow("Diagnostic de synchronisation – v1.0")''')
change(repo,
    '''                    noteSyncStatus("Mode lecture seule terminé. " +
                        "Export JSON demandé ; aucun ACK envoyé. " +
                        "Bluetooth libéré : tu peux ouvrir Mi Fitness.")
                    disconnect()
''',
    '''                    disconnect()
                    noteSyncStatus("FIN : extraction sport seule terminée. " +
                        "Aucun ACK envoyé. Bluetooth déconnecté. " +
                        "Tu peux ouvrir Mi Fitness pour synchroniser.")
''')

# Do not launch unsolicited watch reads when an in-progress workout finishes.
# One user button controls the extraction, and only when Mi Fitness is shut down.
s=repo.read_text(encoding="utf-8")
marker="                    // Fetch activity data to get workout results + GPS track\n"
a=s.index(marker)
b=s.index("                }\n                1 -> if (enabled) startStepPoll()",a)
old=s[a:b]
assert "activitySync?.requestToday()" in old, repr(old)
s=s[:a]+'''                    // v1.0 manual read-only mode: do not automatically fetch here.
                    // Mi Fitness needs full control until the user explicitly requests extraction.
'''+s[b:]
repo.write_text(s,encoding="utf-8")

# Never accept orphaned reconnects during Mi Fitness handover.
change(repo,
    '''    private fun onAuthenticated() {
        val handler = link.protocolHandler ?: return
''',
    '''    private fun onAuthenticated() {
        if (userDisconnected) {
            link.disconnect()
            noteSyncStatus("Connexion automatique refusée : priorité à Mi Fitness.")
            return
        }
        val handler = link.protocolHandler ?: return
''')

main=src/"MainActivity.kt"
change(main,'''PULSE LAB WATCH • VERSION 0.9''','''PULSE LAB WATCH • VERSION 1.0''')
change(main,
    '''Version active : 0.9 — lecture seule, AUCUN ACK envoyé. Mi Fitness reste prioritaire.''',
    '''Version active : 1.0 — SPORT SEUL. Zéro ACK, puis déconnexion pour Mi Fitness.''')
change(main,
    '''Text("Extraire les séances sans ACK", style = MaterialTheme.typography.titleLarge)''',
    '''Text("Extraire les sports en lecture seule", style = MaterialTheme.typography.titleLarge)''')
change(main,
    '''Text("Mode lecture seule : copie les fichiers du jour et les fichiers passés encore proposés. " +
                                    "AUCUN acquittement n'est envoyé : Mi Fitness pourra ensuite effectuer sa propre synchronisation.")''',
    '''Text("Nouveau mode SPORT SEUL : ignore les fichiers quotidiens qui bloquaient la v0.9. " +
                                    "Demande d'abord les résumés sportifs, puis le GPS et les détails. " +
                                    "AUCUN ACK n'est envoyé : Mi Fitness conserve son droit de synchroniser.")''')
change(main,
    '''Text("Extraire maintenant (sans ACK)")''',
    '''Text("Extraire les sports (sans ACK)")''')
change(main,
    '''"Pulse Lab — diagnostic v0.9"''',
    '''"Pulse Lab — diagnostic v1.0"''')

gradle=root/"app/build.gradle.kts"
change(gradle,
    'applicationId = "com.pulselab.watchconnector.readonly09"',
    'applicationId = "com.pulselab.watchconnector.sportonly10"')
change(gradle,
    'versionName = "0.9.0-fr"',
    'versionName = "1.0.0-fr"')
manifest=root/"app/src/main/AndroidManifest.xml"
change(manifest,
    'android:label="PulseLab Watch 0.9"',
    'android:label="PulseLab Watch 1.0"')

code=activity.read_text(encoding="utf-8")
assert "setSubtype(CMD_FETCH_ACK)" not in code, "ACK command must never be sent"
assert "2 -> requestArchiveProbe()" not in code, "archive probe must remain disabled"
assert "SPORT_ONLY skipping daily-health file" in code
assert "LECTURE SPORT SEULE" in code
print("v1.0 sport-only / summary-first / no ACK / manual / handover built safely")
