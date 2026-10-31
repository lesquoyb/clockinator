# Clockinator - boitier v2 (simplifie)

Copie de travail de `case/` : l'original n'est pas modifie. Seuls les fichiers `clockinator-v2-*`, `coque.stl`, `couvercle.stl`, `touche-*.stl`, `platine-ks33.stl` et `diffuseur-*.stl` de ce dossier sont de la v2. Les autres fichiers (`boitier.stl`, `capot-esp32.stl`, `casquette-lumineuse.stl`, `couvercle-led.stl`, `cache-fils-led.stl`, `reflecteur-blanc.stl`, `clockinator-130-*`, `LISEZ-MOI.md`, `apercu-*.png`, `verification-*.json`) sont des copies de la v1, a supprimer.

## Pieces

Coque (avec les 4 equerres de platine), couvercle (dessus + boutons + visiere LED + retenue de l'ecran en une seule piece), 3 touches, platine des switches, diffuseur (3 epaisseurs au choix), bouchon de rainure. Fond plat avec 4 logements ronds de 12,8 mm pour des patins (x = +-50 mm, z = -92 et +6 mm). Touches a fleur du dessus.

## Ecran

Il se pose par le haut, couvercle retire. Il est retenu par un rebord visible a gauche, a droite et en bas ; le rebord du haut est forme par le couvercle. Le haut du rebord est donc secondaire : si ca complique l'impression, il suffit d'agrandir l'ouverture.

## Vissage : boulons et ecrous captifs

Plus aucun filetage imprime. Chaque vis a un ecrou M3 (5,5 mm sur plats) dans une poche carree de 5,8 mm, accessible quand le boitier est ouvert.

- Couvercle -> coque : 4 vis M3 x 20 par le dessus (tete dans un lamage du couvercle). Les ecrous glissent de cote dans les fentes des plots de la coque, depuis l'interieur, couvercle retire.
- Platine -> coque : la platine ne tient plus au couvercle. Elle repose sur 4 equerres (barre + plateforme percee) qui partent des parois de la coque. Poser un ecrou M3 dans la poche ouverte de chaque plateforme, poser la platine, puis 4 vis M3 x 8 par le dessus (tetes dans un lamage de la platine). Le couvercle se pose ensuite par-dessus.
- ESP32 : vis M3 depuis le dessus de la carte, ecrous dans des poches sous le fond (le diametre des trous de la carte est suppose a 3 mm : a verifier).
- Haut-parleur : vis M3 depuis le haut-parleur, ecrous dans des poches sur la face arriere.

Les vis sont representees dans le composant `Visserie M3 - reference`.

## LED et diffuseur : montage

La LED 5 mm n'est plus enfermee. Un puits est ouvert sous le couvercle, derriere la LED, avec une paroi arriere en pente pour passer la LED plus facilement. Le capot est aussi plus mince (dessus 2 mm, sommet a 36 mm).

1. Couvercle retire : pousser la LED dans son trou depuis le puits, collerette dans son lamage. Elle tient par friction, ou avec une goutte de colle.
2. Faire passer les 4 fils dans le puits, puis dans le boitier.
3. Le diffuseur (plaquette blanche) se glisse de cote dans sa rainure, par l'extremite droite du couvercle, jusqu'a la butee.
4. Fermer la rainure avec le bouchon `bouchon-diffuseur.stl` (a imprimer en noir/opaque) : il se pousse dans la rainure et sa tete reste dehors pour le retirer. Sans lui, la lumiere fuit par le cote.

Alternative : le diffuseur peut s'imprimer dans le couvercle, avec une pause a la couche de la rainure, ou en multi-filament. Dans ce cas il faut modifier le modele pour combler la rainure : demander la variante.

Essayer d'abord 0,8 mm en blanc plein, puis 0,6 et 1,2 mm. L'uniformite de la lumiere n'est pas simulee. Filament vraiment opaque pour la coque et le couvercle.

## Impression

L'orientation est a verifier dans le trancheur.

- Coque : fond sur le plateau.
- Couvercle : dessous ouvert sur le plateau, supports pour la visiere.
- Touches : face dessus contre le plateau.
- Diffuseurs : un seul des trois pour commencer.

## Verifications

- Fusion : aucune interference entre les pieces du boitier, la carte ESP32, l'ecran, le haut-parleur, les trois KS33 et la LED.
- Pas d'essai d'impression, d'eclairage ni de montage reel.
- Le firmware n'est pas touche : le bouton lumiere reste a activer (voir la v1).
