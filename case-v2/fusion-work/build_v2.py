import adsk.core, adsk.fusion, math, json

# Clockinator v2 - boitier simplifie.
# Deux pieces principales : coque (fond + parois + facade avec logement d'ecran)
# et couvercle unique (dessus + boutons + visiere LED + retenue de l'ecran).
# Repere : X largeur, Y haut, Z avant positif. Unites du script : mm.

S3 = math.sqrt(3) / 2


def run(_context: str):
    app = adsk.core.Application.get()
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    mgr = adsk.fusion.TemporaryBRepManager.get()
    U = adsk.core.Vector3D.create(1, 0, 0)
    V = adsk.core.Vector3D.create(0, S3, -.5)   # haut de la dalle
    N = adsk.core.Vector3D.create(0, .5, S3)    # normale de la dalle
    union = adsk.fusion.BooleanTypes.UnionBooleanType
    cut = adsk.fusion.BooleanTypes.DifferenceBooleanType
    intersect = adsk.fusion.BooleanTypes.IntersectionBooleanType

    def point(x, y, z):
        return adsk.core.Point3D.create(x / 10, y / 10, z / 10)

    def box(x0, x1, y0, y1, z0, z1):
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(
            point((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2), U,
            adsk.core.Vector3D.create(0, 1, 0), (x1 - x0) / 10, (y1 - y0) / 10, (z1 - z0) / 10))

    def frontbox(x0, x1, v0, v1, n0, n1):
        v = (v0 + v1) / 2
        n = (n0 + n1) / 2
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(
            point((x0 + x1) / 2, V.y * v + N.y * n, V.z * v + N.z * n), U, V,
            (x1 - x0) / 10, (v1 - v0) / 10, (n1 - n0) / 10))

    def boolean(target, tool, operation):
        assert mgr.booleanOperation(target, tool, operation), 'Boolean failed'
        return target

    def cylinder(x, y, z, xx, yy, zz, r):
        return mgr.createCylinderOrCone(point(x, y, z), r / 10, point(xx, yy, zz), r / 10)

    def rounded(x, z, width, depth, y0, y1, r=3):
        b = box(x - width / 2 + r, x + width / 2 - r, y0, y1, z - depth / 2, z + depth / 2)
        boolean(b, box(x - width / 2, x + width / 2, y0, y1, z - depth / 2 + r, z + depth / 2 - r), union)
        for xx in [x - width / 2 + r, x + width / 2 - r]:
            for zz in [z - depth / 2 + r, z + depth / 2 - r]:
                boolean(b, cylinder(xx, y0, zz, xx, y1, zz, r), union)
        return b

    def above_line(z0, y0, z1, y1):
        """Demi-espace au-dessus de la droite (z0,y0)-(z1,y1) dans le plan YZ."""
        dz, dy = z1 - z0, y1 - y0
        length = math.hypot(dz, dy)
        d3 = adsk.core.Vector3D.create(0, dy / length, dz / length)
        up = (dz / length, -dy / length)  # (y, z)
        cy, cz = (y0 + y1) / 2 + up[0] * 50, (z0 + z1) / 2 + up[1] * 50
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(
            point(0, cy, cz), U, d3, 60, 60, 10))

    for o in [o for o in root.occurrences if o.name.startswith(('Boitier v2', 'LED 5 mm v2', 'Visserie M3'))]:
        o.deleteMe()

    # ----- parametres -----
    half = 65.0
    scale = 130 / 154
    rear = -127.72996 * scale          # paroi arriere
    y_bot = -50.44586 * scale          # dessous
    floor = y_bot + 2.4                # dessus du fond
    zf = 17.8                          # devant du boitier (menton)
    roof_y = 22.0                      # dessus plat : touches a fleur
    split = 5.0                        # plan de coupe coque / couvercle
    v_split = 24.8                     # coupe inclinee le long de la dalle
    wall = 2.4
    roof_t = 2.0                       # epaisseur du dessus du couvercle
    y_u = 27.0                        # dessous horizontal de la visiere
    z_vis = -3.37                      # bord avant de la visiere
    y_top = 36.0                       # sommet de la visiere

    # ----- enveloppe exterieure -----
    env = box(-half, half, y_bot, roof_y, rear, zf)
    boolean(env, frontbox(-300, 300, -300, 300, 0, 300), cut)
    hill = box(-half, half, 20, y_top, -48, z_vis)
    under = frontbox(-300, 300, -300, 300, 0, 100)
    boolean(under, box(-300, 300, 10, y_u, -100, 10), intersect)
    boolean(hill, under, cut)
    boolean(env, hill, union)

    # ----- volume interieur unique -----
    cav = box(-half + wall, half - wall, floor, roof_y - roof_t, rear + wall, zf - wall)
    boolean(cav, frontbox(-300, 300, -300, 300, -300, -6.2), intersect)
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
    lid = mgr.copy(env)
    boolean(lid, lid_region, intersect)
    shell = env
    boolean(shell, lid_region, cut)

    # ----- coque : ESP32, haut-parleur, vis, patins -----
    esp_lift = 8.0                     # carte ESP32 surelevee (passage des fils dessous)
    esp_x, esp_y, esp_z = -36, -27.5 + esp_lift, rear + 27.8
    for nx in [-11.6, 11.6]:
        for ny in [-23.5, 23.5]:
            x, z = esp_x - nx, esp_z + ny
            boss = cylinder(x, floor - .2, z, x, esp_y - 1.65, z, 3.2)
            boolean(shell, boss, union)
            boolean(shell, cylinder(x, y_bot - 1, z, x, esp_y, z, 1.7), cut)
            boolean(shell, box(x - 2.9, x + 2.9, y_bot - 1, y_bot + 2.0, z - 2.9, z + 2.9), cut)
    boolean(shell, box(esp_x - 4.8, esp_x + 4.8, -28.94 + esp_lift, -24.04 + esp_lift, rear - 2, rear + 5), cut)   # USB-C
    boolean(shell, box(-50.6, -21.4, -29.4 + esp_lift, -27.1 + esp_lift, rear + 1.5, rear + 3.4), cut)            # fin de carte
    for x in [-13.5, 51.5]:
        for y in [-36.5, -11.5]:
            boss = cylinder(x, y, rear + 2.2, x, y, rear + 5.4, 3.2)
            boolean(shell, boss, union)
            boolean(shell, cylinder(x, y, rear - 1, x, y, rear + 6, 1.7), cut)
            boolean(shell, box(x - 2.9, x + 2.9, y - 2.9, y + 2.9, rear - 1, rear + 2.0), cut)
    for x in range(-7, 47, 5):
        for y in [-31, -26, -21, -16]:
            boolean(shell, cylinder(x, y, rear - 1, x, y, rear + 3, 1.25), cut)
    screws = [(sx, sz) for sx in (-58, 58) for sz in (rear + 6, -52.5)]
    for x, z in screws:
        boss = cylinder(x, floor - .2, z, x, split, z, 4.0)
        boolean(boss, cylinder(x, 0, z, x, split + 1, z, 1.7), cut)
        d = -1 if x > 0 else 1                      # ecrou M3 glisse de cote, depuis l'interieur
        xa, xb = sorted((x - 3.2, x + 3.2, x + d * 7))[0], sorted((x - 3.2, x + 3.2, x + d * 7))[2]
        boolean(boss, box(xa, xb, 1.0, 3.6, z - 2.9, z + 2.9), cut)
        boolean(shell, boss, union)
    # equerres de la platine : barre + plateforme percee, issues des parois ; ecrou depose par le dessus
    pads = []
    for sx in (-1, 1):
        for z in (-94.7, -60.3):
            x0, x1 = sorted((sx * 48.5, sx * 63.0))
            blk = box(x0, x1, -6, 4.42, z - 3.7, z + 3.7)
            boolean(shell, blk, union)
            boolean(shell, box(sx * 53 - 2.9, sx * 53 + 2.9, 1.8, 4.6, z - 2.9, z + 2.9), cut)
            boolean(shell, cylinder(sx * 53, -7, z, sx * 53, 4.6, z, 1.7), cut)
            pads.append((x0, x1, z))
    # patins : 4 logements dessous pour des patins adhesifs de 12 mm
    for x in (-50, 50):
        for z in (-92, 6):
            boolean(shell, cylinder(x, y_bot - 1, z, x, y_bot + 1.2, z, 6.4), cut)

    # ----- couvercle : vis, jupe de centrage -----
    for x, z in screws:
        boolean(lid, cylinder(x, split, z, x, roof_y - .2, z, 4.1), union)
        boolean(lid, cylinder(x, split - 1, z, x, roof_y + 5, z, 1.7), cut)
        boolean(lid, cylinder(x, roof_y - 1.8, z, x, roof_y + 5, z, 3.1), cut)
    skirt = box(-62.3, -60.8, split - 2, split + .6, rear + 2.7, -46)
    boolean(skirt, box(60.8, 62.3, split - 2, split + .6, rear + 2.7, -46), union)
    boolean(skirt, box(-62.3, 62.3, split - 2, split + .6, rear + 2.7, rear + 4.2), union)
    for x, z in screws:
        boolean(skirt, cylinder(x, split - 3, z, x, split + 1, z, 4), cut)
    boolean(skirt, box(-63, 63, 1, 5.7, -98.6, -56.4), cut)               # la jupe laisse la place aux equerres de la platine
    boolean(lid, skirt, union)

    # ----- couvercle : chambre LED, visiere, diffuseur -----
    led_y = 31.0
    bay = box(-22, 22, 19, y_top - 2.0, -52, -30.4)                          # baie ouverte sous le couvercle, paroi arriere en pente
    boolean(bay, above_line(-50.5, 19, -33, y_top - 2.0), cut)
    boolean(lid, bay, cut)
    boolean(lid, box(-26, 26, 28.2, 33.8, -28, -7.4), cut)                    # chambre
    boolean(lid, box(-26, 26, 26, 28.3, -15.2, -7.4), cut)                    # fenetre de sortie
    boolean(lid, box(-27.4, 66, 28.2, 29.7, -16.0, -6.2), cut)                # glissiere du diffuseur, entree par le cote droit
    boolean(lid, box(-12, 12, 28.2, 31, -18, -16.8), union)                   # ecran contre la vue directe
    boolean(lid, cylinder(0, led_y, -31.4, 0, led_y, -27.9, 2.6), cut)        # trou LED 5 mm
    boolean(lid, cylinder(0, led_y, -31.4, 0, led_y, -29.4, 3.1), cut)        # logement de la collerette

    # ----- couvercle : boutons -----
    mount_y = 11.9907 * scale - 1.3
    old = next(o for o in root.occurrences if o.name.startswith('Boitier 130'))
    old_bodies = {b.name: b for b in old.component.bRepBodies}
    z = -77.5
    for x, width in [(-39, 26), (-5, 30), (35, 40)]:
        collar = rounded(x, z, width + 3.8, 27.8, 16.5, roof_y + .1, 4.9)
        boolean(collar, rounded(x, z, width + .6, 24.6, 16, roof_y + 1, 3.3), cut)
        boolean(lid, collar, union)
        boolean(lid, rounded(x, z, width + .6, 24.6, 14.8, roof_y + 2, 3.3), cut)
    for xa, xb in [(-49, -35), (-30, -16), (-11, 3), (8, 22), (28, 42)]:
        channel = box(xa, xb, 13, roof_y - roof_t + .2, -100, -92)
        boolean(channel, box(xa - 1, xb + 1, 14.4, roof_y, -98.6, -93.4), cut)
        boolean(channel, box(xa - 1, xb + 1, 12, 14.5, -97.5, -94.5), cut)
        boolean(lid, channel, union)

    # ----- bouchon opaque de la rainure du diffuseur -----
    plug = box(27.1, 65.0, 28.35, 29.55, -15.85, -6.35)
    boolean(plug, box(65.0, 66.8, 27.6, 30.3, -16.6, -5.6), union)

    # ----- diffuseurs a essayer -----
    def diffuser(t, dz=0):
        d = box(-26.9, 26.9, 28.3, 28.3 + t, -15.9, -6.7)
        if dz:
            m = adsk.core.Matrix3D.create()
            m.translation = adsk.core.Vector3D.create(0, 0, dz / 10)
            assert mgr.transform(d, m)
        return d

    # ----- reference LED 5 mm -----
    led = cylinder(0, led_y, -30.4, 0, led_y, -21.0, 2.5)
    boolean(led, cylinder(0, led_y, -30.4, 0, led_y, -29.4, 2.9), union)

    # ----- assemblage dans un nouveau composant -----
    keep = {}
    for name in ['Touche Lumiere - KS33', 'Touche Stop - KS33', 'Touche Snooze - KS33', 'Platine KS33 - montage par dessous']:
        keep[name] = mgr.copy(old_bodies[name])
    for x in (-53, 53):      # trou + lamage de tete, vis par le dessus
        for z in (-94.7, -60.3):
            boolean(keep['Platine KS33 - montage par dessous'], cylinder(x, 3, z, x, 10, z, 1.7), cut)
            boolean(keep['Platine KS33 - montage par dessous'], cylinder(x, 6.9, z, x, 10, z, 3.1), cut)
    occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    comp = occ.component
    comp.name = 'Boitier v2 - simplifie'
    base = comp.features.baseFeatures.add()
    base.name = 'Boitier v2'
    base.startEdit()
    parts = [('Boitier - coque', shell), ('Couvercle - dessus, boutons, LED, retenue ecran', lid)]
    parts += list(keep.items())
    parts += [('Bouchon rainure diffuseur', plug)]
    parts += [('Diffuseur blanc - 0.8 mm', diffuser(.8)),
              ('Diffuseur blanc - 0.6 mm (variante)', diffuser(.6, 70)),
              ('Diffuseur blanc - 1.2 mm (variante)', diffuser(1.2, 90))]
    for name, shape in parts:
        comp.bRepBodies.add(shape, base)
    base.finishEdit()
    for i, (name, shape) in enumerate(parts):
        comp.bRepBodies.item(i).name = name

    led_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    led_occ.component.name = 'LED 5 mm v2 - reference'
    lb = led_occ.component.features.baseFeatures.add()
    lb.startEdit()
    b = led_occ.component.bRepBodies.add(led, lb)
    b.name = 'LED 5 mm'
    lb.finishEdit()

    # visserie de reference : montre comment les pieces se vissent (une seule face accessible)
    vis = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    vis.component.name = 'Visserie M3 - reference'
    vb = vis.component.features.baseFeatures.add()
    vb.startEdit()
    plate_bottom = mount_y - 1.2
    shapes = []
    for x, z in screws:      # couvercle -> coque, depuis le dessus
        s = cylinder(x, roof_y - 1.8, z, x, roof_y, z, 2.75)
        boolean(s, cylinder(x, 0.2, z, x, roof_y - 1.8, z, 1.5), union)
        shapes.append(('Vis couvercle M3x20', s))
    for x in (-53, 53):      # platine -> equerres de la coque, par le dessus
        for z in (-94.7, -60.3):
            s = cylinder(x, 6.9, z, x, 8.7, z, 2.75)
            boolean(s, cylinder(x, -1.1, z, x, 6.9, z, 1.5), union)
            shapes.append(('Vis platine M3x8', s))
    for _, shape in shapes:
        vis.component.bRepBodies.add(shape, vb)
    vb.finishEdit()
    for i, (name, _) in enumerate(shapes):
        vis.component.bRepBodies.item(i).name = name

    # visibilite
    old.isLightBulbOn = False
    for o in root.occurrences:
        if o.name.startswith('LED 5 mm - position'):
            o.isLightBulbOn = False
        elif o.name.startswith(('Haut-parleur', 'ESP32 - position', 'Ecran 106', 'KS33')):
            o.isLightBulbOn = True
    app.activeViewport.fit()
    out = {b.name: [b.lumps.count, round(b.volume, 3)] for b in comp.bRepBodies}
    print(json.dumps(out, ensure_ascii=False))
