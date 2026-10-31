# Clockinator - boitier ferme 130 mm

Cette version remplace les premiers exports de travail. Elle ferme le volume entre la facade inclinee et le compartiment arriere. La cloison interieure a ete ouverte. Le grand couvercle superieur se retire verticalement apres retrait de ses quatre vis.

## Fichiers

- `clockinator-130-assemblage.f3d` : assemblage Fusion avec les cartes et les switches places, le volume de reference du haut-parleur et la base d'origine masquee.
- `clockinator-130-pieces.f3d` : les douze pieces fabriquees, sans les composants electroniques.
- `clockinator-130-pieces.step` : ces memes pieces au format STEP.
- Quatorze STL en millimetres : douze pieces, dont le diffuseur de 0,8 mm, et deux variantes de diffuseur de 0,6 et 1,2 mm. Imprimer un seul diffuseur.
- `verification-geometrie.json` : controles d'interference, de course des touches et d'ouverture.

## Dimensions et assemblage

Largeur 130 mm, profondeur environ 126 mm, hauteur maximale environ 81 mm avec la casquette lumineuse. Les composants electroniques ne sont pas redimensionnes.

1. Monter le haut-parleur de 70 x 30 x 15 mm contre la paroi arriere perforee. Entraxes des quatre vis : 65 x 25 mm, correspondant a des centres a 2,5 mm des bords. Le cote droit offre environ 8,6 mm pour son cable.
2. Poser l'ESP32 sur les quatre entretoises. Les trous viennent du STEP fourni : entraxes de 23,2 x 47 mm. Le connecteur USB-C est presque affleurant a l'arriere ; l'ouverture mesure 9,6 x 4,9 mm.
3. Passer les cables vers la facade dans la goulotte ouverte, de 14 mm de largeur utile. Le capot opaque de l'ESP32 se demonte avec deux vis ; sa sortie de fils comporte une chicane.
4. Glisser l'ensemble ecran/carte de 106 x 54 x environ 3 mm depuis le haut de la facade, avant de monter la casquette. Le logement en largeur mesure 106,4 mm. Un degagement continu permet le passage du connecteur arriere pendant l'insertion.
5. Placer le reflecteur blanc et le diffuseur dans la chambre lumineuse. Installer la LED RGB de 5 mm depuis l'arriere, souder/isoler ses quatre fils, puis fermer le couvercle de la chambre et le petit cache-fils. Le passage de fils mesure 9 x 6 mm. La longueur exacte du corps de la LED et de ses soudures reste a verifier sur la piece reelle.
6. Inserer les trois touches depuis le dessous du couvercle. Leurs collerettes restent capturees sous les guides. Clipser les KS33 dans la platine separee, puis visser cette platine sous le couvercle. Les dessus des touches sont au meme niveau que le boitier ; jeu lateral nominal 0,3 mm par cote. Les collerettes limitent leur soulevement a environ 0,4 mm.
7. Souder les fils sous les switches. Utiliser les encoches de 6 mm de la platine et les cinq retenues sous le couvercle pour les guider. Leur passage interieur fait environ 5,2 x 5,2 mm avec une fente de chargement de 3 mm. Garder une boucle de service pour pouvoir soulever le couvercle, ou ajouter un connecteur debranchable.
8. Monter la casquette avec ses deux vis et fermer le grand couvercle avec quatre vis accessibles par dessus.

Les reperes graves sont un soleil, un carre stop et « Zz ». Les touches font respectivement 26, 30 et 40 mm de large, pour 24 mm de profondeur. Elles utilisent le modele KS33 Low Profile 2.0 corrige fourni dans `case`, et non le premier fichier KS-3X.

## Lumiere

Le diffuseur est sous la casquette et eclaire vers le bas le long de la dalle. La face avant de la casquette est opaque. Un ecran opaque de 24 mm masque la vue directe de la LED, avec un parement blanc et une chambre de melange. Il ne s'agit pas d'une simulation photometrique : l'uniformite et la luminosite sur l'ecran doivent etre essayees avec la LED et le filament reels.

Imprimer la coque, la casquette et les caches dans un filament reellement opaque. Imprimer le reflecteur en blanc et commencer les essais avec le diffuseur blanc de 0,8 mm, en couches de 0,2 mm et parois pleines. Comparer ensuite 0,6 et 1,2 mm. Un blanc tres opaque peut attenuer fortement la lumiere ; un blanc translucide peut etre necessaire. Un faible remplissage n'est pas un substitut fiable a une membrane homogene et peut faire apparaitre une trame.

Les chicanes et les caches limitent les fuites, mais les jeux d'impression et la translucidite du filament imposent un controle dans le noir. Si necessaire, un fin joint opaque ou du ruban noir sur les raccords interieurs assure l'etancheite lumineuse. Ne pas recouvrir le trou de prise ni les broches de l'ESP32.

## Vis et impression

Les fixations principales ont des avant-trous de 2,5 mm pour vis M3 dans le plastique et des passages de 3,4 mm dans les pieces demontables. Il n'y a pas de filetage metallique ni d'insert incorpore. La chambre LED utilise des avant-trous de 1,7 mm et passages de 2,2 mm pour vis M2.

Choisir les longueurs apres mesure des empilements, en gardant une marge avant le fond des trous et les parois exterieures. Les longueurs indicatives sont M3 x 25 pour le grand couvercle, M3 x 10 pour la platine de boutons et M2 x 6 pour les caches LED ; elles ne remplacent pas cette verification. Les vis du haut-parleur dependent de l'epaisseur reelle de ses oreilles.

Les STL sont poses a Z=0 dans une orientation de depart. Le boitier, le couvercle et la casquette peuvent necessiter des supports : verifier les surplombs dans le trancheur. Les touches sont exportees face dessus contre le plateau, avec l'emboitement vers le haut. Imprimer d'abord une touche et un diffuseur pour verifier l'ajustement avant la serie complete. Les jeux prevus restent a adapter a l'imprimante.

## Validation et raccordement

Les douze pieces sont des solides uniques. Les controles geometriques portent sur les cartes importees, les KS33, un encombrement de LED de 5 mm et le volume rectangulaire du haut-parleur. La course des touches de 3,2 mm et le retrait du couvercle ont ete verifies sans interference. Cela ne remplace pas un montage physique et un essai d'eclairage.

Le firmware n'a pas ete modifie. Dans le code actuel, les boutons Stop et Snooze sont prevus sur GPIO32 et GPIO33 ; le bouton lumiere GPIO34 est present mais desactive par `LIGHT_BUTTON_ENABLED = false`. Son activation et sa resistance de rappel externe doivent etre traitees au raccordement, conformement au montage electronique retenu.
