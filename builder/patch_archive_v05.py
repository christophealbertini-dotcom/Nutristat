#!/usr/bin/env python3
"""Add fully local Xiaomi archive importer to the French Bluetooth connector."""
from pathlib import Path
import sys, shutil

root=Path(sys.argv[1])
src=root/"app/src/main/java/com/pedometer"
dest=src/"pulselab/MiFitnessArchiveImporter.kt"
dest.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2("builder/MiFitnessArchiveImporter.kt",dest)

def change(path,old,new):
    p=Path(path)
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n != 1: raise RuntimeError(f"Patch import mismatch in {p}: {n} anchors for {old[:110]!r}")
    p.write_text(s.replace(old,new),encoding="utf-8")

main=src/"MainActivity.kt"
change(main,
    "import com.pedometer.pulselab.PulseLabExportWorker\n",
    "import com.pedometer.pulselab.PulseLabExportWorker\nimport com.pedometer.pulselab.MiFitnessArchiveImporter\n")
change(main,
    "import java.util.Locale\n",
    "import java.util.Locale\nimport kotlinx.coroutines.launch\n")
change(main,
    '                val syncLog by PedometerApp.repository.syncLog.collectAsState()\n',
    '''                val syncLog by PedometerApp.repository.syncLog.collectAsState()
                var importBusy by remember { mutableStateOf(false) }
                var importInfo by remember { mutableStateOf(
                    "Aucun export Xiaomi importé sur ce téléphone."
                ) }
                val importScope = rememberCoroutineScope()
                val importPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { selected ->
                    if (selected.isNotEmpty()) {
                        importBusy = true
                        importInfo = "Analyse locale des données Xiaomi en cours…"
                        importScope.launch {
                            val report = MiFitnessArchiveImporter.importFiles(
                                this@MainActivity, selected
                            )
                            importInfo = report.frenchSummary()
                            importBusy = false
                        }
                    }
                }
''')
change(main,
'''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Séances récupérées : " + state.recentWorkouts.size,
''',
'''                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Importer les anciennes séances Mi Fitness",
                                    style = MaterialTheme.typography.titleLarge)
                                Text("Import entièrement local, gratuit et indépendant du Bluetooth.")
                                Text(
                                    "Depuis le compte Xiaomi : Confidentialité → Gérer vos données " +
                                    "→ Mi Fitness → Télécharger. " +
                                    "Sélectionne ensuite l’archive ZIP ou le CSV des entraînements."
                                )
                                Button(
                                    onClick = { importPicker.launch(arrayOf("*/*")) },
                                    enabled = !importBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (importBusy) "Importation en cours…" else
                                        "Importer un export Xiaomi (ZIP ou CSV)")
                                }
                                Text(importInfo)
                                Text(
                                    "Dossier : Téléchargements/PulseLab/WatchInbox/MiFitnessImport. " +
                                    "Sans doublons à chaque nouvel import. " +
                                    "Les parcours GPS ne sont pas garantis dans l’export Xiaomi.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Si le ZIP Xiaomi est protégé par mot de passe, extrais-le " +
                                    "d’abord puis sélectionne le fichier hlth_center_sport_record.csv.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Séances récupérées par Bluetooth : " + state.recentWorkouts.size,
''')
gradle=root/"app/build.gradle.kts"
change(gradle,
    'applicationId = "com.pulselab.watchconnector.diagv04"',
    'applicationId = "com.pulselab.watchconnector.archivev05"')
change(gradle,
    'versionName = "0.4.0-fr"',
    'versionName = "0.5.0-fr"')
print("Mi Fitness archive importer integrated into the standalone French connector.")
