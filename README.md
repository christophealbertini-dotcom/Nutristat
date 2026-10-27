# Coach Running Android (MVP v0.1)

Branche dédiée sans modification de Nutristat/main.

L'application propose :
- Objectifs multiples, proposition et validation des séances, calendrier modifiable et recalcul prudent.
- Import CSV et suivi Strava par jeton personnel temporaire, analyses des entraînements.
- Discussions OpenAI ou Claude **par clé API individuelle**, conversations locales par objectif.
- Sauvegarde et restauration locale JSON.

**Restrictions** : pas de connexion directe aux abonnements Claude.ai/ChatGPT ni aux Projets externes, pas d'OAuth Strava à renouvellement automatique, pas encore d'intégration directe Mi Fitness/Health Connect. Les requêtes IA sont facturables selon le fournisseur.

Le code source Android est conservé comme archive XZ décomposée en 7 parties texte dans `coach-parts/`. Le workflow GitHub Actions le réassemble, vérifie le SHA256 et construit une APK de test téléchargeable via Artifacts.

Version de test non signée pour publication Play Store : il s'agit d'une APK **debug** signée automatiquement pour installation manuelle.
