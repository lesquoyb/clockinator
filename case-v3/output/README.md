# Exports v3

`switch-plate.stl` et la platine dans `clockinator-v3-parts.step` ont été
régénérés sans les anciennes encoches de passage de fils. La platine est
construite directement ; aucune découpe de câble n’est créée puis rebouchée.
Les trois ouvertures KS33 de 14,2 mm, les quatre perçages de 3,5 mm et les
nervures sont conservés. Le STL est déjà orienté et posé à Z = 0 pour imprimer.

La géométrie commune est dans `../fusion-work/switch_plate.py` ; elle est
utilisée par le build Fusion et par l’export sans interface graphique :

```sh
python3 case-v3/fusion-work/export_plate_headless.py
```

Cette commande, depuis la racine du dépôt, nécessite CadQuery (validation
effectuée avec la version indiquée dans `verification-plate.json`). Elle
remplace uniquement la platine dans le STEP et réexporte son STL, puis vérifie
les volumes et dimensions des huit solides STEP ainsi que la fermeture du
maillage. Les autres STL restent inchangés. Les résultats sont enregistrés
dans `verification-plate.json`.

## Coque et montage ESP32

`shell.stl` et la coque du STEP sont construits directement depuis le profil
extérieur, sans repartir d'une coque à réparer. La carte et ses quatre
fixations sont décalées de **1,5 mm vers l'intérieur** par rapport à l'export
précédent. Cela laisse **0,9 mm entre le bord du PCB fourni et la face intérieure
de la paroi arrière**. La découpe « fin de carte » a été supprimée : il ne reste
dans cette zone que l'ouverture USB-C de 9,6 × 4,9 mm. Les quatre appuis ont un
dessus complet et coplanaire à Y = −18,15 mm dans le repère du modèle.

```sh
python3 case-v3/fusion-work/export_shell_headless.py
```

La géométrie est partagée avec Fusion dans `shell_geometry.py`. L'adaptateur
CadQuery reconstruit le profil et ses arrondis en millimètres ; aucun bloc
ne comble une découpe antérieure. Le générateur Fusion ne conserve plus les
boucles de visserie désactivées. La position de la référence ESP32 est alignée
de manière absolue, sans accumuler un déplacement à chaque build.

L'export vérifie les quatre surfaces d'appui et leurs perçages, la continuité
de la paroi autour du port, l'ouverture USB-C et l'absence d'intersection avec
les **142 solides du STEP ESP32 fourni**. Le STEP réimporté est vérifié à
nouveau, puis le maillage STL est contrôlé fermé, manifold et posé à Z = 0.
Les résultats sont dans `verification-shell.json`. Les autres pièces du STEP
gardent leurs dimensions et volumes à la tolérance de réexport près ; leurs
STL ne sont pas régénérés. La comparaison ponctuelle de 30 000 positions avec
l'ancien export n'a détecté aucune différence hors de la zone ESP32.

Il reste à vérifier le montage imprimé et l'accès avec le câble USB réellement
utilisé ; un contrôle du modèle ne remplace pas cet essai.

**Archive Fusion à réexporter :** `clockinator-v3-assembly.f3d` est l’ancienne
archive et contient encore l’ancienne platine et l’ancienne coque. Elle n’a pas
été modifiée par
l’export CadQuery. Pour la mettre à jour, ouvrir cette archive dans Fusion,
exécuter `../fusion-work/build_v3.py` puis `../fusion-work/export_v3.py`.
Conserver `switch_plate.py` et `shell_geometry.py` à côté de `build_v3.py`. Ne
pas exporter les pièces
depuis l’ancienne archive avant d’avoir exécuté le build corrigé. Le STEP et
le STL sont les exports corrigés à utiliser en attendant.
