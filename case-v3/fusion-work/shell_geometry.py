"""Direct v3 shell geometry, shared by Fusion and CadQuery (millimetres).

The ESP32 board is positioned clear of the rear wall. No PCB-edge pocket or
post repair is needed: the rear wall has only the USB-C opening in this area.
"""

SCALE = 130 / 154
DZ = -12.0
REAR = -127.72996 * SCALE + DZ
BOTTOM = -50.44586 * SCALE
WALL = 2.4
SIDE_FILLET = 2.5
ESP_X = -36.0
ESP_Y = -16.5
# PCB half-length 26 mm + rear wall 2.4 mm + .9 mm clearance.
ESP_CENTER_FROM_REAR = 29.3
ESP_SUPPORT_TOP = ESP_Y - 1.65


def split_enclosure(outer, geometry):
    box, frontbox, cylinder = geometry.box, geometry.frontbox, geometry.cylinder
    boolean, copy = geometry.boolean, geometry.copy
    union, cut, intersect = geometry.union, geometry.cut, geometry.intersect
    half, wall, floor, y_bot, rear = 65.0, WALL, BOTTOM + WALL, BOTTOM, REAR
    roof_y, roof_t, zf, split, v_split = 22.0, 2.0, 17.8, 5.0, 24.8
    env = copy(outer)
    # ----- volume interieur unique -----
    cav = box(-half + wall, half - wall, floor, roof_y - roof_t, rear + wall, zf - wall)
    boolean(cav, frontbox(-300, 300, -300, 300, -300, -6.2), intersect)
    corner = box(-half - 5, half + 5, y_bot - 1, y_bot + 10, zf - 10, zf + 5)       # le bas avant est arrondi : la cavite suit
    boolean(corner, cylinder(-half - 6, y_bot + 10, zf - 10, half + 6, y_bot + 10, zf - 10, 10 - wall), cut)
    boolean(cav, corner, cut)
    boolean(env, cav, cut)

    # ----- logement de l'ecran : glisse par le haut, retenu par le couvercle -----
    # Rainures en bas et a gauche seulement (levre avant 1,2 mm + appui arriere) ;
    # le haut et le cote droit (connecteur) restent degages, le couvercle ferme le haut.
    boolean(env, frontbox(-53.2, 53.2, -27.2, 27.4, -4.9, -1.2), cut)      # poche 106,4 mm
    boolean(env, frontbox(-41.4, 42.4, -24.4, v_split, -1.2, 1.5), cut)    # fenetre avant : levres gauche, droite, bas ; le haut est dans le couvercle
    boolean(env, frontbox(-41.4, 53.2, -24.4, 27.4, -6.7, -4.9), cut)      # fenetre arriere, puce et connecteur
    boolean(env, frontbox(-53.2, 53.2, v_split, 27.4, -8, -4.9), cut)      # le couvercle ne pousse pas la dalle

    # ----- separation coque / couvercle -----
    lid_region = box(-100, 100, split, 100, rear - 1, 100)
    boolean(lid_region, frontbox(-300, 300, v_split, 300, -300, 300), intersect)
    lid = copy(env)
    boolean(lid, lid_region, intersect)
    shell = env
    boolean(shell, lid_region, cut)

    return shell, lid


def add_shell_features(shell, outer, geometry):
    box, cylinder = geometry.box, geometry.cylinder
    boolean, copy = geometry.boolean, geometry.copy
    union, cut, intersect = geometry.union, geometry.cut, geometry.intersect
    rear, floor, y_bot = REAR, BOTTOM + WALL, BOTTOM
    ZA, ZB = -94.7 + DZ, -60.3 + DZ
    # ----- coque : ESP32, haut-parleur, vis, patins -----
    esp_x, esp_y, esp_z = ESP_X, ESP_Y, rear + ESP_CENTER_FROM_REAR
    for rear_post, offset_z in ((True, -23.5), (False, 23.5)):
        z = esp_z + offset_z
        for offset_x in (-11.6, 11.6):
            x = esp_x + offset_x
            boss = cylinder(x, floor - .2, z, x, ESP_SUPPORT_TOP, z, 3.2)
            boolean(boss, copy(outer), intersect)
            boolean(shell, boss, union)
            if rear_post:
                # Reinforced foot and side-entry nut, above the rounded floor.
                collar = cylinder(x, floor - .05, z, x, floor + 5.5, z, 4.2)
                boolean(collar, copy(outer), intersect)
                boolean(shell, collar, union)
                boolean(shell, cylinder(x, floor + .2, z, x, esp_y, z, 1.7), cut)
                boolean(shell, box(x - 2.9, x + 2.9, floor + .6, floor + 3.2, z, z + 8), cut)
            else:
                # Front fasteners enter through the flat underside.
                boolean(shell, cylinder(x, y_bot - 1, z, x, esp_y, z, 1.7), cut)
                boolean(shell, box(x - 2.9, x + 2.9, y_bot - 1, y_bot + 2.0, z - 2.9, z + 2.9), cut)
    # amplificateur MAX98357A : deux plots M2 a la hauteur de l'ESP32, de l'autre cote ; broches vers l'ESP32,
    # rangee de vis le long de la paroi (bornier cote paroi). L'ecrou M2 glisse de cote dans le plot.
    amp_top = ESP_SUPPORT_TOP
    for z in (-84.5, -71.5):
        x = 54.2
        post = cylinder(x, floor - .2, z, x, amp_top, z, 3.6)
        boolean(post, copy(outer), intersect)
        boolean(shell, post, union)
        boolean(shell, cylinder(x, amp_top - 9, z, x, amp_top + .5, z, 1.2), cut)
        boolean(shell, box(x - 4.5, x + 2.1, amp_top - 4.2, amp_top - 2.4, z - 2.1, z + 2.1), cut)   # ecrou M2 glisse de cote dans le plot, dessus intact
    boolean(shell, box(esp_x - 4.8, esp_x + 4.8, esp_y - 1.44, esp_y + 3.46, rear - 2, rear + 5), cut)   # USB-C
    spk_up = 4.0                       # haut-parleur remonte : trous hors de l'arrondi du bas
    spk_dx = 0
    for x in [-13.5 + spk_dx, 51.5 + spk_dx]:
        for y in [-36.5 + spk_up, -11.5 + spk_up]:
            boss = cylinder(x, y, rear + 2.2, x, y, rear + 5.4, 3.2)
            boolean(shell, boss, union)
            boolean(shell, cylinder(x, y, rear - 1, x, y, rear + 6, 1.7), cut)
            boolean(shell, cylinder(x, y, rear - 1, x, y, rear + 2.0, 3.1), cut)      # lamage de tete : la vis entre par l'exterieur, dans le filetage du haut-parleur
    for x in range(-7 + spk_dx, 47 + spk_dx, 5):
        for y in [-31 + spk_up, -26 + spk_up, -21 + spk_up, -16 + spk_up]:
            boolean(shell, cylinder(x, y, rear - 1, x, y, rear + 3, 1.25), cut)
    # equerres de la platine : barre + plateforme percee, issues des parois ; ecrou depose par le dessus
    for sx in (-1, 1):
        for z in (ZA, ZB):
            x0, x1 = sorted((sx * 48.5, sx * 63.0))
            blk = box(x0, x1, -4, 4.42, z - 3.7, z + 3.7)
            boolean(shell, blk, union)
            xs = sorted((sx * 53 + sx * 2.9, sx * 53 - sx * 9))
            boolean(shell, box(xs[0], xs[1], -3.2, -0.8, z - 2.9, z + 2.9), cut)      # ecrou sous l'equerre, entre de cote
            boolean(shell, cylinder(sx * 53, -6, z, sx * 53, 4.6, z, 1.7), cut)
    # patins : 4 logements dessous pour des patins adhesifs de 12 mm
    for x in (-50, 50):
        for z in (-104, -6):      # hors des arrondis avant et arriere
            boolean(shell, cylinder(x, y_bot - 1, z, x, y_bot + 1.2, z, 6.4), cut)

    return shell
