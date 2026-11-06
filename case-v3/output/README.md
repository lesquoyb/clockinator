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

**Archive Fusion à réexporter :** `clockinator-v3-assembly.f3d` est l’ancienne
archive et contient encore l’ancienne platine. Elle n’a pas été modifiée par
l’export CadQuery. Pour la mettre à jour, ouvrir cette archive dans Fusion,
exécuter `../fusion-work/build_v3.py` puis `../fusion-work/export_v3.py`.
Conserver `switch_plate.py` à côté de `build_v3.py`. Ne pas exporter la platine
depuis l’ancienne archive avant d’avoir exécuté le build corrigé. Le STEP et
le STL sont les exports corrigés à utiliser en attendant.
