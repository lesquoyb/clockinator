# Prototype de guidage Snooze

Essai séparé : aucun changement aux pièces de production ni à la fixation du capot.

Imprimer les trois STL à 100 %, en millimètres. La touche est face supérieure
sur le plateau ; le support repose sur sa peau inférieure, cadre vers le haut.
La fenêtre de capot est un coupon à tenir en position avec un montage provisoire,
pas un remplacement du capot complet. Le STEP conserve les positions assemblées.
Placer un KS33 dans la platine et emboîter la touche avant de positionner la fenêtre.

Le cadre guide la collerette avec 0,3 mm de jeu nominal par côté. Quatre pieds
sous celle-ci rencontrent quatre appuis après 3,3 mm de déplacement. Le cadre
s'arrête 0,2 mm sous le guide existant du capot. La fenêtre reçoit un dégagement
local de la collerette pour supprimer une interférence héritée.

Contrôles réalisés : solides uniques, absence de collision à cinq positions
entre 0 et 3,2 mm, interception par les butées à 3,5 mm. Ce contrôle ne simule
ni la déformation ni les efforts, et ne valide pas encore les appuis inclinés.

Tester le déclenchement et le retour au centre, sur les quatre bords et les quatre
coins. Arrêter si le switch talonne avant les appuis. Vérifier aussi que le cadre
ne frotte pas et que le switch reste clipsé. La référence exacte du switch et
les tolérances d'impression peuvent imposer une adaptation de la course/du jeu.
Le serrage de l'empreinte en croix est conservé pour isoler cet essai mécanique.

Ne généraliser aux autres touches qu'après validation physique de ce prototype.
Reproduction : exécuter `../../fusion-work/prototype_snooze.py` dans Fusion.
