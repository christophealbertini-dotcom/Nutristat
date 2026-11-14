# Pulse Lab Huawei WATCH GT 7 v2.0

Application Android indépendante du connecteur Redmi et de l’application Nutristat.

## Fonctionnement

1. Synchroniser sa GT 7 avec l’application officielle **Huawei Santé**.
2. Choisir **Huawei Santé** comme source et **Google Drive** ou **Huawei Drive** comme destination d’activités FIT dans **Health Sync**. Les exports GPS supplémentaires sont GPX/TCX.
3. Dans l’application Pulse Lab GT 7, importer plusieurs fichiers **FIT / GPX / TCX** depuis le sélecteur de documents Android ou via Partager.
4. Les fichiers JSON apparaissent sous **Téléchargements/PulseLab/WatchInbox**, avec données GPS et pulsations horodatées quand elles existent dans la source.
5. Optionnel : choisir un dossier accessible par Android pour des analyses manuelles et des analyses périodiques environ toutes les 15 minutes, non garanties par Android ni par le fournisseur Google Drive.

Aucune connexion Bluetooth, ACK, clé Huawei ni permission Internet. La montre reste exclusivement connectée à Huawei Santé.
Les fichiers entrants sont fusionnés par horodatage, pour préserver les données plus détaillées d’un FIT si un GPX est importé ensuite.

**Limite :** le programme ne peut pas extraire directement les données de Huawei Santé sans autorisation développeur du programme Huawei Health Kit. Nous n’avons pas encore de fichier FIT réel d’une GT 7 pour confirmer tous les champs. Le format JSON reprend schemaVersion=1 de Pulse Lab avec heartRateSamples et samples en plus.

Références :
- https://healthsync.app/about-fr/
- https://consumer.huawei.com/en/support/content/en-us15856822/
- https://github.com/garmin/fit-java-sdk
- https://github.com/CTHRU/Hitrava
- https://github.com/aricooperdavis/Huawei-TCX-Converter
