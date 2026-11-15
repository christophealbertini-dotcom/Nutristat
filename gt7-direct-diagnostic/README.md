# Pulse Lab GT7 Bluetooth – diagnostic indépendant v0.1

Le programme de test est une compilation modifiée de **Gadgetbridge (AGPL-3.0-or-later)**, distribuant ses modifications en code source via `builder/patch_gt7_direct_diag.py` et utilisant la base amont `https://codeberg.org/Freeyourgadget/Gadgetbridge`.

Source Codeberg clonée par GitHub Actions; le commit exact de l'amont s'affiche dans les journaux GitHub Actions.

## Objectif

GT7 déjà synchronisée avec **Huawei Santé** → essayer de relire la même activité depuis le Bluetooth de Gadgetbridge sans désassocier/réinitialiser la montre. **Ce test ne garantit pas l'absence d'ACK ou de modification d'état de la montre** (pas de réécriture du moteur de Gadgetbridge). Il convient de suivre strictement l'ordre de test.

## Étapes sur Samsung

1. Garder les activités déjà enregistrées et synchronisées dans Huawei Santé.
2. **Force-arrêter Huawei Santé**, sans désinstaller ni dissocier la montre. Éviter toute autre application Bluetooth concurrente.
3. Installer l'APK expérimentale signée par GitHub Actions (application ID séparé `com.pulselab.gt7directtest`).
4. Ouvrir **Pulse Lab GT7 • Journal** pour activer l'enregistrement de log.
5. Ouvrir **Pulse Lab GT7 - Gadgetbridge** / bouton "Ouvrir Gadgetbridge".
6. Découvrir la montre; l'association sans réinitialisation peut demander l'identifiant Huawei à 17 chiffres (`huid`) qui doit rester privé. Voir : https://gadgetbridge.org/basics/pairing/huawei-honor-pairing/
7. Dans les entraînements (Sports), actualiser pour lancer la récupération de la séance **déjà synchronisée**.
8. Si rien : Sports → menu → **Reset fetch time** (date avant la séance), puis retenter une seule fois.
9. Ouvrir à nouveau **Pulse Lab GT7 • Journal** → **Copier le journal complet**. Masquer MAC/huid et toute donnée sensible avant de le partager.
10. Si une activité est récupérée, l'exporter en **FIT** ou en fichiers bruts depuis Sports, pour conversion avec Pulse Lab Huawei v2.0 existant.

Gadgetbridge peut effectuer des échanges non purement passifs. Ne pas utiliser les boutons de suppression, de restauration usine ou de changement de firmware. L'application possède les fonctionnalités générales de Gadgetbridge ; cette APK n'est pas encore limitée aux seules activités.

## Logs
Le journal est stocké par Gadgetbridge à son emplacement de stockage applicatif. Le bouton **Copier le journal complet** et **Partager le journal** évitent d'utiliser un gestionnaire de fichiers.

## Limites
- Test matériel GT7 non réalisé par les auteurs du patch, résulats non garantis.
- Huawei Santé reste installée mais doit être arrêtée lors des transferts Bluetooth.
- L'accès après association Huawei peut demander un identifiant de compte Huawei et la répétition de la procédure d'association après retour dans Huawei Santé.
- Aucun fichier JSON automatiquement transféré à l'application principale : la première phase teste l'accès Bluetooth et l'export FIT/GPX/brut.

## Licences
AGPL-3.0-or-later pour les modifications. Les fichiers d'amont conservent leurs licences déclarées. Le code du patch est accessible publiquement dans le dépôt qui publie l'APK.
