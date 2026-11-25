# Essai complet des trois boutons

Imprimer les cinq STL de ce dossier, en millimètres à 100 % :

- `switch-plate.stl` : platine complète avec trois cadres et douze appuis.
- `key-light.stl`, `key-stop.stl`, `key-snooze.stl` : touches avec quatre pieds chacune.
- `lid.stl` : capot complet avec dégagement local des collerettes.

La coque et la visserie restent celles du modèle actuel. Les colonnes de
vissage ont leur dégagement dans les nouveaux cadres. Le capot fourni est
nécessaire pour reproduire l'ensemble vérifié ; l'ancien capot présente une
petite interférence avec les collerettes. La fixation du capot est inchangée.

Touches : face supérieure contre le plateau. Platine : peau inférieure sur le
plateau, cadres vers le haut. Capot : choisir l'orientation et les supports dans
le trancheur ; garder les passages des touches libres de marques de supports.
Installer les touches par dessous le capot puis les switches et la platine.

Jeu nominal autour des collerettes : 0,3 mm par côté. Les pieds rencontrent les
appuis à 3,3 mm de déplacement vertical. Le serrage sur les tiges des switches
est conservé afin de tester d'abord la prévention du basculement.

Vérifications : chaque pièce est un solide unique ; cinq positions par touche
entre 0 et 3,2 mm sans collision avec platine, capot, coque ou autres touches ;
interception de chaque touche par les butées à 3,5 mm ; platine sans collision
avec coque et capot. Les maillages STL sont vérifiés séparément.

Tester le déclenchement et le retour libre de chaque touche au centre, sur
chaque bord et dans chaque coin. Ces essais physiques restent nécessaires :
les contrôles ne simulent pas les déformations ni les efforts. Vérifier que les
switches restent clipsés et ne talonnent pas avant les appuis. La course et le
jeu devront être adaptés si la référence des switches ou l'impression l'exige.

`buttons-prototype.step` conserve les cinq pièces en position assemblée.
Les exports principaux du boîtier ne sont pas remplacés avant cet essai.
Génération : exécuter `../../fusion-work/prototype_buttons.py` dans Fusion.
