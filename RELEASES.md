# Releases Android et ESP32

## Version commune

`version.properties` fournit `versionName` et `versionCode` à Gradle.
`scripts/release_version.py 1.0.1` met à jour cette version et `esp32/clockinator/Version.h`,
et incrémente le code Android. Il refuse une version inférieure ou identique.
Le firmware communique aussi sa version dans `WIFI_STATUS`.

## Préparer GitHub une fois

Pousser le workflow et les modifications sur la branche par défaut. Dans
Settings → Secrets and variables → Actions, ajouter :

- `ANDROID_KEYSTORE_BASE64` : contenu Base64 du keystore de signature.
- `ANDROID_KEYSTORE_PASSWORD` : mot de passe du keystore.
- `ANDROID_KEY_ALIAS` : alias de la clé.
- `ANDROID_KEY_PASSWORD` : mot de passe de la clé.

Créer et conserver une clé de release si nécessaire :

```powershell
keytool -genkeypair -keystore clockinator-release.jks -alias clockinator -keyalg RSA -keysize 3072 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path ./clockinator-release.jks))) | Set-Clipboard
```

Garder ce fichier et ses mots de passe en dehors du dépôt. La même clé doit servir
à toutes les versions pour permettre les mises à jour Android. Une application
installée avec la clé debug Android Studio ne peut pas être mise à jour
par-dessus avec une nouvelle clé de release : conserver les données puis prévoir
une désinstallation/réinstallation lors de ce changement de signature.
La pipeline ne crée pas de clé jetable à chaque compilation.

Le workflow utilise `contents: write`. Les règles de protection de la branche
doivent autoriser le commit de version par le workflow ; sinon le push échoue,
sans forcer la branche. Les binaires restent dans les artifacts du run. Le commit
et le tag ne sont poussés qu'après les compilations et vérifications réussies.
Un changement concurrent de la branche fait échouer le push atomique : relancer
depuis la dernière version. Si seul `Publish release` échoue après le push, créer
la release sur le tag existant avec les artifacts ; ne pas refaire le bump.

## Créer une release

Actions → **Release Android and ESP32** → **Run workflow**, sur la branche par défaut.
Saisir par exemple `1.0.1`. La pipeline :

1. Valide la version, augmente `versionCode` et prépare le header ESP32.
2. Compile et teste Android, puis compile l'ESP32 classique de 4 Mo.
3. Génère un APK signé, une archive de binaires ESP32 et des sommes SHA-256.
4. Crée le commit `chore: release v1.0.1`, le tag `v1.0.1` et la release GitHub.

Android et l'horloge ont donc le même numéro de version. Il n'y a pas de
téléversement sur une carte physique depuis GitHub Actions.

## Installer le firmware sans Arduino IDE

Télécharger et extraire l'archive `clockinator-esp32-VERSION.zip`. Installer Python
et `python -m pip install esptool==5.1.0`, brancher la carte en USB et fermer le
moniteur série. Pour une carte déjà configurée en **No OTA (2MB APP/2MB SPIFFS)** :

```powershell
./flash-clock.ps1 -Port COM5
```

Remplacer COM5 par le port réel. Pour une première installation ou un changement
de partitionnement : `./flash-clock.ps1 -Port COM5 -InitialInstall`.
Ce script écrit les composants à leurs offsets, sans effacement global. Une mise
à jour du seul programme conserve NVS et LittleFS si le partitionnement correspond.
Changer de partitionnement peut rendre les WAV existants illisibles : conserver
les originaux et les renvoyer après installation. L'image fusionnée est aussi
fournie, mais ses zones de remplissage peuvent effacer les réglages ; préférer le script.

Ces binaires ciblent **ESP32 classique, flash 4 Mo, écran GDEY037T03 et brochage du
projet**, pas les ESP32-C3/S3/C6. Arduino IDE reste utilisable avec les sources.
Le partitionnement actuel ne permet pas l'OTA : ces releases se déploient par USB.

## Connexion Android en arrière-plan

Settings → Auto-sync propose deux options indépendantes :

- **Background checks**, activé par défaut : contrôle silencieux avec WorkManager
  environ toutes les 15 minutes, sans notification de détection. Android peut
  retarder les exécutions, notamment en veille. Fermer l'interface n'annule pas le job.
- **Persistent clock status**, désactivé par défaut : service au premier plan qui
  maintient la connexion et affiche l'état Bluetooth/Wi-Fi et les alarmes activées.
  La notification est silencieuse. L'activer depuis l'application avec les permissions
  Bluetooth ; autoriser les notifications pour voir son état. Après redémarrage du
  téléphone, rouvrir l'application pour relancer ce service. Le job périodique,
  lui, est conservé par WorkManager.

Un arrêt forcé dans les paramètres Android bloque les tâches jusqu'à réouverture.
Le transport est partagé entre l'écran, le job et le service pour éviter des
connexions BLE concurrentes. La synchronisation est envoyée après connexion, sans
obliger à ouvrir l'interface. L'ancien scan et ses notifications répétitives sont retirés.

Settings → Local Wi-Fi → **Wi-Fi connection and sync** permet de couper les tentatives
TCP et leurs erreurs côté Android. Le réglage est conservé et le Bluetooth reste
disponible. Il ne désactive pas la radio Wi-Fi de l'ESP32.

## Rafraîchissement e-paper

Settings → Clock screen → **Full screen refresh now** déclenche un nettoyage
complet sur demande, par Bluetooth ou Wi-Fi. Le bouton nécessite une connexion.
**Periodic full refresh**, désactivé par défaut, propose 10, 30, 60 ou 120 minutes.
Le réglage est conservé sur Android, transmis lors de la synchro et sauvegardé
dans la NVS de l'ESP32 ; l'horloge gère elle-même ce délai, même téléphone absent.

Après chaque démarrage, un seul nettoyage automatique suit la première synchro
valide. Il attend la fin de l'initialisation Wi-Fi (connexion ou délai de 20 secondes)
et deux secondes de stabilité des états. Les synchros suivantes et les changements
Wi-Fi/Bluetooth utilisent des rafraîchissements partiels. L'initialisation physique
du panneau peut elle-même nécessiter un premier effacement au démarrage.
