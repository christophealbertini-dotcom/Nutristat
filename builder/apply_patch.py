#!/usr/bin/env python3
"""Adapt upstream Pedometer to an isolated PulseLab JSON export bridge."""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1])
src=root/"app/src/main/java/com/pedometer"
dest=src/"pulselab/PulseLabExportWorker.kt"
dest.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2("builder/PulseLabExportWorker.kt", dest)

def change(path, old, new):
    path=Path(path)
    text=path.read_text(encoding="utf-8")
    count=text.count(old)
    if count!=1:
        raise ValueError(f"Unexpected upstream revision: {path}, anchor={old[:90]!r}, count={count}")
    path.write_text(text.replace(old,new),encoding="utf-8")

change(root/"app/build.gradle.kts",
    'dependencies {',
    'dependencies {\n    implementation("androidx.work:work-runtime-ktx:2.10.1")')
change(src/"PedometerApp.kt",
    "import com.pedometer.repo.WatchRepository",
    "import com.pedometer.pulselab.PulseLabExportWorker\nimport com.pedometer.repo.WatchRepository")
change(src/"PedometerApp.kt",
    '        repository = WatchRepository.get(this)',
    '        repository = WatchRepository.get(this)\n        PulseLabExportWorker.schedule(this)')
change(src/"repo/WatchRepository.kt",
    "import com.pedometer.health.ActivitySync",
    "import com.pedometer.pulselab.PulseLabExportWorker\nimport com.pedometer.health.ActivitySync")
change(src/"repo/WatchRepository.kt",
    '                        bumpRoom()\n                    } catch (e: Exception) { Log.e(TAG, "Save GPS failed", e) }',
    '                        bumpRoom()\n                        PulseLabExportWorker.changed(context)\n                    } catch (e: Exception) { Log.e(TAG, "Save GPS failed", e) }')
change(src/"repo/WatchRepository.kt",
    '                calibrateStepLength(w) // reads DB + EMA profile write, best effort — outside the transaction',
    '                PulseLabExportWorker.changed(context)\n                calibrateStepLength(w) // reads DB + EMA profile write, best effort — outside the transaction')
p=src/"repo/WatchRepository.kt"
body=p.read_text(encoding="utf-8")
body=body.replace('getBoolean(KEY_QUIET_ENABLED, true)','getBoolean(KEY_QUIET_ENABLED, false)')
body=body.replace('getBoolean(KEY_WIFI_GATE, true)','getBoolean(KEY_WIFI_GATE, false)')
p.write_text(body,encoding="utf-8")
p=src/"ui/SettingsTab.kt"
body=p.read_text(encoding="utf-8")
body=body.replace('getBoolean("quiet_enabled", true)','getBoolean("quiet_enabled", false)')
body=body.replace('getBoolean("wifi_gate_enabled", true)','getBoolean("wifi_gate_enabled", false)')
# Two focused French labels for the core connection flow.
body=body.replace('Text("Подключение",','Text("Connexion à la montre",')
body=body.replace('Text("Подключить")','Text("Connecter")')
body=body.replace('Text("Отключить")','Text("Déconnecter")')
p.write_text(body,encoding="utf-8")

p=root/"app/build.gradle.kts"
body=p.read_text(encoding="utf-8")
body=body.replace('applicationId = "com.pedometer"','applicationId = "com.pulselab.watchconnector"')
p.write_text(body, encoding="utf-8")

p=root/"app/src/main/AndroidManifest.xml"
body=p.read_text(encoding="utf-8")
body=body.replace('android:label="Шагомер"','android:label="PulseLab Watch Connector"')
p.write_text(body,encoding="utf-8")
print("PulseLab bridge installed with automatic JSON export")


# Interface française limitée aux fonctions du connecteur.
shutil.copy2("builder/MainActivityFR.kt", src/"MainActivity.kt")

# Traduction des notifications et des types de séances transmis au module export.
p=src/"service/WatchConnectionService.kt"
body=p.read_text(encoding="utf-8")
for old,new in {
    '"Часы отключены"': '"Montre déconnectée"',
    '"Часы подключены"': '"Montre connectée"',
    '"Шагомер"': '"PulseLab Watch Connector"',
}.items():
    body=body.replace(old,new)
p.write_text(body,encoding="utf-8")

p=src/"health/ActivitySync.kt"
body=p.read_text(encoding="utf-8")
for old,new in {
    '"Бег"': '"Course à pied"',
    '"Ходьба"': '"Marche"',
    '"Беговая дорожка"': '"Tapis de course"',
    '"Велосипед"': '"Vélo"',
    '"Велотренажёр"': '"Vélo d’intérieur"',
    '"Свободная"': '"Entraînement libre"',
    '"Плавание"': '"Natation"',
    '"Эллиптический"': '"Elliptique"',
    '"Гребля"': '"Rameur"',
    '"Скакалка"': '"Corde à sauter"',
    '"Тренировка #$subtype"': '"Entraînement #$subtype"',
}.items():
    body=body.replace(old,new)
p.write_text(body,encoding="utf-8")

# Variante .fr avec installation sans conflit avec l'ancienne APK debug signée
# sur un autre runner GitHub.
p=root/"app/build.gradle.kts"
body=p.read_text(encoding="utf-8")
body=body.replace('applicationId = "com.pulselab.watchconnector"',
                  'applicationId = "com.pulselab.watchconnector.fr"')
body=body.replace('versionName = "1.0.0"','versionName = "0.2.0-fr"')
p.write_text(body,encoding="utf-8")
print("Interface française et notifications françaises appliquées")
