#!/usr/bin/env python3
"""v0.8 fixes Mi Fitness ZIP importer and makes diagnostic delivery reliable.
No personal IDs or secrets are compiled into the application."""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1])
src=root/"app/src/main/java/com/pedometer"

def change(path, old, new):
    p=Path(path)
    text=p.read_text(encoding="utf-8")
    if text.count(old)!=1:
        raise RuntimeError("v08 anchor not unique in "+str(p)+": "+repr(old[:100])+" x"+str(text.count(old)))
    p.write_text(text.replace(old,new),encoding="utf-8")

hints=src/"pulselab/MiFitnessWorkoutHints.kt"
change(hints,
    '''                if (type != 1 || sport !in 0..31 || detail !in 1..2 ||
''',
    '''                // Mi Fitness uses fileType 0=workout details, 1=summary, 2=GPS.
                // v0.7 dropped details (type 0) and thus most workout hints.
                if (type != 1 || sport !in 0..31 || detail !in 0..2 ||
''')
change(hints,
    '''                        if (!entry.isDirectory && (entry.name.lowercase().endsWith(".log") ||
                                    entry.name.lowercase().endsWith(".txt"))) {
''',
    '''                        // Mi Fitness stores most older workout records in
                        // XiaomiFit.main.log.bak.1 / XiaomiFit.device.log.bak.1.
                        // ZIP files must include these rotated logs, not just .log.
                        val lower = entry.name.lowercase(java.util.Locale.ROOT)
                        if (!entry.isDirectory && (lower.contains(".log") ||
                                    lower.endsWith(".txt"))) {
''')
change(hints,
    '''        fun french(): String = if (message.isNotEmpty()) message else
            "Indices détectés : " + sessions + " séance(s), " + ids +
            " fichiers sportifs identifiés. " +
            "Appuie sur Synchroniser pour tester leur relecture directe."
''',
    '''        fun french(): String = if (message.isNotEmpty()) message else
            if (ids == 0)
                "Aucun ID sportif détecté après analyse de " + scanned +
                " lignes. Vérifie que tu as choisi le ZIP de diagnostics Mi Fitness " +
                "(pas le ZIP Brut)."
            else "Import réussi : " + sessions + " séance(s) et " + ids +
                " identifiant(s) sportifs. " +
                "Appuie maintenant sur Synchroniser pour tester leur relecture."
''')

store=src/"pulselab/PulseLabSyncLogStore.kt"
shutil.copy2("builder/PulseLabSyncLogStoreV08.kt",store)

repo=src/"repo/WatchRepository.kt"
change(repo,
    '''            val ok = com.pedometer.pulselab.PulseLabSyncLogStore.save(context, snapshot)
            noteSyncStatus(if (ok)
                "Journal enregistré : Téléchargements/PulseLab/WatchInbox/diagnostic-synchronisation.txt"
            else "Impossible d’exporter le journal ; vérifie l’espace de stockage.")
''',
    '''            val file = com.pedometer.pulselab.PulseLabSyncLogStore.save(context, snapshot)
            noteSyncStatus(if (file != null)
                "Journal enregistré : Téléchargements/PulseLab/WatchInbox/" + file
            else "Enregistrement impossible. Utilise Copier le journal ou Partager le journal.")
''')
change(repo,
    '''(existing + "\n" + timestamp + " • " + message).takeLast(6500)''',
    '''(existing + "\n" + timestamp + " • " + message).takeLast(30000)''')
change(repo,
    '''MutableStateFlow("Diagnostic de synchronisation – v0.7")''',
    '''MutableStateFlow("Diagnostic de synchronisation – v0.8")''')

main=src/"MainActivity.kt"
change(main,
    '''PULSE LAB WATCH • VERSION 0.7''',
    '''PULSE LAB WATCH • VERSION 0.8''')
change(main,
    '''Version active : 0.7 — si ce numéro n'apparaît pas, une ancienne application est ouverte.''',
    '''Version active : 0.8 — importer d'abord le ZIP des journaux Mi Fitness.''')
change(main,
    '''                                Text(syncLog, style = MaterialTheme.typography.bodySmall)
''',
    '''                                OutlinedButton(
                                    onClick = {
                                        val clipboard = this@MainActivity.getSystemService(
                                            android.content.Context.CLIPBOARD_SERVICE
                                        ) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(
                                            android.content.ClipData.newPlainText(
                                                "Pulse Lab — diagnostic v0.8", syncLog))
                                        message = "Journal copié. Colle son texte dans la conversation."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Copier le journal (sans fichier)") }
                                OutlinedButton(
                                    onClick = {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, syncLog)
                                        }
                                        this@MainActivity.startActivity(
                                            Intent.createChooser(share, "Partager le journal Pulse Lab"))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Partager le journal") }
                                Text(syncLog.takeLast(4600),
                                    style = MaterialTheme.typography.bodySmall)
''')
# Move import controls ABOVE the sync button; 0.7 buried them below the long log.
p=main
s=p.read_text(encoding="utf-8")
markerA='''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Anciennes séances : importer les identifiants",'''
markerB='''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Séances récupérées : " + state.recentWorkouts.size,'''
markerC='''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Synchronisation des séances",'''
assert s.count(markerA)==s.count(markerB)==s.count(markerC)==1
a=s.index(markerA);b=s.index(markerB,a)
card=s[a:b].replace("Anciennes séances : importer les identifiants",
     "Étape 1 : importer les anciennes séances")
s=s[:a]+s[b:]
c=s.index(markerC)
s=s[:c]+card+s[c:]
p.write_text(s,encoding="utf-8")

change(main,
    '''Text("Synchronisation des séances", style = MaterialTheme.typography.titleLarge)''',
    '''Text("Étape 2 : synchroniser les séances", style = MaterialTheme.typography.titleLarge)''')
change(main,
    '''Text("Sélectionne une seule fois le ZIP de journaux Mi Fitness. " +
                                    "L'application lit uniquement les identifiants des fichiers sportifs " +
                                    "et les conserve localement, sans envoyer de données sur Internet.")''',
    '''Text("Sélectionne le ZIP original Mi Fitness (ex. 1793831441754log.zip), " +
                                    "pas Brut.zip. Les fichiers .log.bak.1 sont désormais inclus. " +
                                    "Aucune donnée n'est envoyée sur Internet.")''')
change(main,
    '''Text("Puis appuie sur Synchroniser maintenant. " +
                                    "La présence de séances sur l'écran de la montre ne garantit pas " +
                                    "leur relecture après synchronisation par Mi Fitness.",''',
    '''Text("L'import doit annoncer un nombre d'identifiants > 0. " +
                                    "Ensuite utilise Synchroniser. Les séances déjà acquittées peuvent " +
                                    "ne plus être accessibles dans la montre.",''')

gradle=root/"app/build.gradle.kts"
change(gradle,
    'applicationId = "com.pulselab.watchconnector.historical07"',
    'applicationId = "com.pulselab.watchconnector.historical08"')
change(gradle,
    'versionName = "0.7.0-fr"',
    'versionName = "0.8.0-fr"')
manifest=root/"app/src/main/AndroidManifest.xml"
change(manifest,
    'android:label="PulseLab Watch 0.7"',
    'android:label="PulseLab Watch 0.8"')
print("v0.8 imported rotated Mi Fitness logs, fixed sport IDs, shareable diagnostics")
