# Boutons v2 : retour sans accrochage

## Pièces à réimprimer

Réimprimer **le capot et les trois touches** de ce dossier :

- `lid.stl`
- `key-light.stl`
- `key-stop.stl`
- `key-snooze.stl`

**Conserver la platine guidée déjà imprimée** du dossier `prototype-buttons`.
La platine présente ici a exactement la même géométrie : la différence booléenne
des deux STEP est nulle dans les deux sens, voir `verification-plate-compatibility.json`.
Ne pas utiliser l'ancienne platine sans cadres de guidage.

Les anciens exports sont conservés. Le capot principal dans `../lid.stl`
ne contient pas cette correction. La coque et la fixation du capot ne changent pas.

## Modifications

- Entrée évasée sous chaque ouverture : transition continue entre Y=16,2 et
  Y=18,3 mm, avec arrondis coniques. Ce n'est pas un escalier de découpes.
- Jeu du passage supérieur : 0,5 mm par côté au lieu de 0,3 mm.
- Poches autour des collerettes élargies et prolongées jusqu'à Y=16,5 mm,
  notamment près des vis de Snooze ; la lèvre de retenue au-dessus reste présente.
- Jupe périphérique sous chaque touche, prolongeant sa collerette vers le bas :
  hauteur guidée totale de 3,8 mm au lieu d'une collerette d'environ 1 mm.
  Cela augmente la surface de guidage en position basse.
- Bas des jupes conservé à Y=12,3 mm ; mêmes appuis et course de butée de 3,3 mm.
- Empreinte sur le switch inchangée : ce n'est pas une correction de son serrage.

## Impression et montage

Millimètres, échelle 100 %. Touches : dessus contre le plateau, jupe vers le
haut. Capot : vérifier l'orientation dans le trancheur ; éviter les supports
et leurs marques sur les surfaces de coulissement et l'entrée évasée.
Installer les touches par dessous le capot, puis les switches et la platine.
Ne pas forcer pour compenser un frottement.

## Vérifications et limites

- Cinq positions verticales par touche, entre 0 et 3,2 mm : pas de collision
  avec le capot, la platine, la coque ou les autres touches.
- Butées interceptant une descente de 3,5 mm.
- 96 poses inclinées : rotations de +/-1 degré autour des axes X et Z, seules
  et combinées, à quatre profondeurs. 72 poses n'intersectent pas la platine
  et passent sans collision du capot. Les 24 autres sont interceptées par la
  platine ; elles ne sont pas présentées comme des poses libres validées.
- Cinq maillages fermés, orientation cohérente, posés à Z=0.
- Tolérance de maillage de 0,01 mm pour préserver les surfaces arrondies.

Ces tests ne simulent ni le frottement, ni la déformation, ni le ressort du
switch. Ils ne garantissent pas le retour pour des inclinaisons supérieures à
celles échantillonnées. Vérifier physiquement le retour libre au centre, sur
chaque bord et dans chaque coin, avec les vis serrées normalement, sans bloquer
la platine en flexion. Noter la touche et le bord si un accrochage subsiste.

Le STEP contient les cinq pièces en position assemblée. Génération dans Fusion :
`../../fusion-work/prototype_buttons_v2.py`.
