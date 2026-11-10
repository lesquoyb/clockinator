import adsk.core, adsk.fusion, math, json, importlib.util, os

# Clockinator v3 - boitier lisse (arrondis, pente, arriere rallonge de 12 mm).
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

    def cone(x, y, z, xx, yy, zz, radius0, radius1):
        return mgr.createCylinderOrCone(point(x, y, z), radius0 / 10,
                                        point(xx, yy, zz), radius1 / 10)

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

    for o in [o for o in root.occurrences if o.name.startswith(('Enclosure v3', 'Boitier v3', 'LED 5 mm v3', 'M3 fasteners v3', 'Visserie M3 v3', 'tmp-env'))]:
        o.deleteMe()

    spec = importlib.util.spec_from_file_location(
        'shell_geometry_v3', os.path.join(os.path.dirname(__file__), 'shell_geometry.py'))
    shell_geometry = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(shell_geometry)
    # ----- parametres -----
    half = 65.0
    DZ = shell_geometry.DZ                         # arriere rallonge : electronique, platine et touches reculent de 12 mm
    ZA, ZB = -94.7 + DZ, -60.3 + DZ    # trous de la platine
    scale = shell_geometry.SCALE
    rear = shell_geometry.REAR     # paroi arriere
    y_bot = shell_geometry.BOTTOM          # dessous
    zf = 17.8                          # devant du boitier (menton)
    roof_y = 22.0                      # dessus plat : touches a fleur
    split = 5.0                        # plan de coupe coque / couvercle
    y_u = 27.0                        # dessous horizontal de la visiere
    z_vis = -3.37                      # bord avant de la visiere
    y_top = 36.0                       # sommet de la visiere

    # ----- enveloppe exterieure : profil lateral extrude, puis arrondis -----
    z0 = -60.0                                   # sommet bas de la rampe
    z1 = z0 + (y_top - roof_y) / math.tan(math.radians(30))
    y_chin = -zf / (0.5 / S3)
    z_u = -.5 * y_u / S3
    tmp = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    tmp.component.name = 'tmp-env'
    tc = tmp.component
    sk = tc.sketches.add(tc.yZConstructionPlane)     # croquis (x, y) -> monde (y, z) = (y, -x)
    prof = [(rear, y_bot), (zf, y_bot), (zf, y_chin), (z_u, y_u), (z_vis, y_u), (z_vis, y_top),
            (z1, y_top), (z0, roof_y), (rear, roof_y)]
    pts = [adsk.core.Point3D.create(-z / 10, y / 10, 0) for z, y in prof]
    lines = sk.sketchCurves.sketchLines
    prev = lines.addByTwoPoints(pts[0], pts[1])
    first = prev
    for p in pts[2:]:
        prev = lines.addByTwoPoints(prev.endSketchPoint, p)
    lines.addByTwoPoints(prev.endSketchPoint, first.startSketchPoint)
    ei = tc.features.extrudeFeatures.createInput(sk.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    ei.setSymmetricExtent(adsk.core.ValueInput.createByReal(half / 10), False)
    body = tc.features.extrudeFeatures.add(ei).bodies.item(0)
    radii = {(rear, y_bot): 6, (zf, y_bot): 10, (zf, y_chin): 5, (z_vis, y_top): 4, (z_vis, y_u): 2.5,
             (z1, y_top): 40, (z0, roof_y): 40, (rear, roof_y): 5}
    fi = tc.features.filletFeatures.createInput()
    for (z, y), r in radii.items():
        es = adsk.core.ObjectCollection.create()
        for e in body.edges:
            if e.geometry.objectType.endswith('Line3D') and abs(e.startVertex.geometry.x - e.endVertex.geometry.x) > .5:
                g = e.startVertex.geometry
                if abs(g.y * 10 - y) < .05 and abs(g.z * 10 - z) < .05:
                    es.add(e)
        assert es.count == 1, (z, y, es.count)
        fi.edgeSetInputs.addConstantRadiusEdgeSet(es, adsk.core.ValueInput.createByReal(r / 10), True)
    tc.features.filletFeatures.add(fi)
    body = tc.bRepBodies.item(0)
    sides = adsk.core.ObjectCollection.create()
    for f in body.faces:
        if f.geometry.surfaceType == adsk.core.SurfaceTypes.PlaneSurfaceType and abs(f.geometry.normal.x) > .999 and abs(f.geometry.origin.x * 10) > half - .1:
            for e in f.edges:
                sides.add(e)
    fs = tc.features.filletFeatures.createInput()
    fs.edgeSetInputs.addConstantRadiusEdgeSet(sides, adsk.core.ValueInput.createByReal(shell_geometry.SIDE_FILLET / 10), True)
    tc.features.filletFeatures.add(fs)
    outer = mgr.copy(tc.bRepBodies.item(0))                        # enveloppe arrondie, pour borner les plots
    tmp.deleteMe()

    # Common direct shell construction; no inherited cutouts or repair solids.
    from types import SimpleNamespace
    geometry = SimpleNamespace(box=box, frontbox=frontbox, cylinder=cylinder, cone=cone,
        boolean=boolean, copy=mgr.copy, union=union, cut=cut, intersect=intersect)
    shell, lid = shell_geometry.split_enclosure(outer, geometry)
    shell_geometry.add_shell_features(shell, outer, geometry)
    joints = [(sx * 53.0, z) for sx in (-1, 1) for z in (ZA, ZB)]

    # ----- couvercle : vis, jupe de centrage -----
    skirt = box(-62.6, -60.8, split - 2, split + .6, rear + 2.4, -46)
    boolean(skirt, box(60.8, 62.6, split - 2, split + .6, rear + 2.4, -46), union)
    boolean(skirt, box(-62.6, 62.6, split - 2, split + .6, rear + 2.4, rear + 4.2), union)
    boolean(skirt, box(-63, 63, 1, 5.7, -98.6 + DZ, -56.4 + DZ), cut)               # la jupe laisse la place aux equerres de la platine
    boolean(lid, skirt, union)

    # ----- couvercle : chambre LED, visiere, diffuseur -----
    led_y = 29.5                      # LED abaissee (etait 31)
    bay = box(-22, 22, 19, y_top - 2.0, -52, -30.4)                          # baie ouverte sous le couvercle, paroi arriere en pente
    boolean(bay, above_line(-50.5, 19, -33, y_top - 2.0), cut)
    boolean(lid, bay, cut)
    boolean(lid, box(-26, 26, 28.2, 33.8, -28, -7.4), cut)                    # chambre
    boolean(lid, box(-26, 26, 26.6, 28.3, -30.0, -18.4), cut)                # la chambre descend sous la LED abaissee
    boolean(lid, box(-26, 26, 26, 28.3, -15.2, -7.4), cut)                    # fenetre de sortie
    boolean(lid, box(-27.4, 66, 28.2, 29.7, -16.0, -6.2), cut)                # glissiere du diffuseur, entree par le cote droit
    boolean(lid, box(-12, 12, 28.2, 31, -18, -16.8), union)                   # ecran contre la vue directe
    boolean(lid, cylinder(0, led_y, -31.4, 0, led_y, -27.9, 2.6), cut)        # trou LED 5 mm
    boolean(lid, cylinder(0, led_y, -31.4, 0, led_y, -29.4, 3.1), cut)        # logement de la collerette

    # ----- couvercle : boutons -----
    mount_y = 11.9907 * scale - 1.3
    old = next(o for o in root.occurrences if o.name.startswith('Enclosure 130'))
    old_bodies = {b.name: b for b in old.component.bRepBodies}
    for x, z in joints:      # colonne du capot : appuie sur la platine, la vis traverse capot, platine, puis ecrou de l'equerre
        boolean(lid, cylinder(x, mount_y, z, x, roof_y - .2, z, 4.1), union)
    z = -77.5 + DZ
    for x, width in [(-39, 26), (-5, 30), (35, 40)]:
        collar = rounded(x, z, width + 3.8, 27.8, 16.5, roof_y + .1, 4.9)
        boolean(collar, rounded(x, z, width + .6, 24.6, 16, roof_y + 1, 3.3), cut)
        boolean(lid, collar, union)
        boolean(lid, rounded(x, z, width + .6, 24.6, 14.8, roof_y + 2, 3.3), cut)
    for x, z in joints:
        boolean(lid, cylinder(x, mount_y - 1, z, x, roof_y + 5, z, 1.7), cut)
        boolean(lid, cylinder(x, roof_y - 1.8, z, x, roof_y + 5, z, 3.1), cut)

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
    for name in ['Touche Lumiere - KS33', 'Touche Stop - KS33', 'Touche Snooze - KS33']:
        keep[name] = mgr.copy(old_bodies[name])
        tm = adsk.core.Matrix3D.create()
        tm.translation = adsk.core.Vector3D.create(0, 0, DZ / 10)
        assert mgr.transform(keep[name], tm)
    spec = importlib.util.spec_from_file_location(
        'switch_plate_v3', os.path.join(os.path.dirname(__file__), 'switch_plate.py'))
    plate_geometry = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(plate_geometry)
    keep['Platine KS33 - montage par dessous'] = plate_geometry.build_switch_plate(
        box, cylinder, boolean, union, cut)
    occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    comp = occ.component
    comp.name = 'Enclosure v3 - smooth'
    base = comp.features.baseFeatures.add()
    base.name = 'Boitier v3'
    base.startEdit()
    parts = [('Boitier - coque', shell), ('Couvercle - dessus, boutons, LED, retenue ecran', lid)]
    parts += list(keep.items())
    parts += [('Bouchon rainure diffuseur', plug)]
    parts += [('Diffuseur blanc - 0.8 mm', diffuser(.8)),
              ('Diffuseur blanc - 0.6 mm (variante)', diffuser(.6, 70)),
              ('Diffuseur blanc - 1.2 mm (variante)', diffuser(1.2, 90))]
    EN = {'Boitier - coque': 'Shell',
          'Couvercle - dessus, boutons, LED, retenue ecran': 'Lid',
          'Touche Lumiere - KS33': 'Key Light - KS33',
          'Touche Stop - KS33': 'Key Stop - KS33',
          'Touche Snooze - KS33': 'Key Snooze - KS33',
          'Platine KS33 - montage par dessous': 'Switch plate - KS33',
          'Bouchon rainure diffuseur': 'Diffuser slot plug',
          'Diffuseur blanc - 0.8 mm': 'Diffuser white - 0.8 mm',
          'Diffuseur blanc - 0.6 mm (variante)': 'Diffuser white - 0.6 mm (variant)',
          'Diffuseur blanc - 1.2 mm (variante)': 'Diffuser white - 1.2 mm (variant)'}
    for name, shape in parts:
        comp.bRepBodies.add(shape, base)
    base.finishEdit()
    for i, (name, shape) in enumerate(parts):
        comp.bRepBodies.item(i).name = EN.get(name, name)

    led_occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    led_occ.component.name = 'LED 5 mm v3 - reference'
    lb = led_occ.component.features.baseFeatures.add()
    lb.startEdit()
    b = led_occ.component.bRepBodies.add(led, lb)
    b.name = 'LED 5 mm'
    lb.finishEdit()

    # visserie de reference : montre comment les pieces se vissent (une seule face accessible)
    vis = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    vis.component.name = 'M3 fasteners v3 - reference'
    vb = vis.component.features.baseFeatures.add()
    vb.startEdit()
    shapes = []
    for x, z in joints:      # capot + platine + equerre de la coque, depuis le dessus
        sh = cylinder(x, roof_y - 1.8, z, x, roof_y, z, 2.75)
        boolean(sh, cylinder(x, -4.8, z, x, roof_y - 1.8, z, 1.5), union)
        shapes.append(('M3x25 screw lid+plate+shell', sh))
    for _, shape in shapes:
        vis.component.bRepBodies.add(shape, vb)
    vb.finishEdit()
    for i, (name, _) in enumerate(shapes):
        vis.component.bRepBodies.item(i).name = name

    # Keep the displayed ESP32 reference aligned with the same placement as
    # its supports. Use absolute bounds so repeated builds do not accumulate
    # another 1.5 mm translation. Source STEP bounds: X +/-14.25, Y min -27.7,
    # Z min -9.7 mm; axes are mapped by place_boards.py.
    for reference in root.occurrences:
        if reference.name.startswith(('ESP32 - mounting', 'ESP32 - position de montage')):
            bb = reference.boundingBox
            target_min = point(shell_geometry.ESP_X - 14.25,
                               shell_geometry.ESP_Y - 9.7,
                               shell_geometry.REAR + shell_geometry.ESP_CENTER_FROM_REAR - 27.7)
            transform = reference.transform2
            translation = transform.translation
            translation.x += target_min.x - bb.minPoint.x
            translation.y += target_min.y - bb.minPoint.y
            translation.z += target_min.z - bb.minPoint.z
            transform.translation = translation
            reference.transform2 = transform

    # visibilite
    old.isLightBulbOn = False
    for o in root.occurrences:
        if o.name.startswith(('Enclosure v2', 'LED 5 mm v2', 'M3 fasteners v2')):
            o.isLightBulbOn = False
    for o in root.occurrences:
        if o.name.startswith('LED 5 mm - mounting'):
            o.isLightBulbOn = False
        elif o.name.startswith(('Speaker', 'ESP32 - mounting', 'Display 106', 'KS33 Light', 'KS33 Stop', 'KS33 Snooze', 'MAX98357A')):
            o.isLightBulbOn = True
    app.activeViewport.fit()
    out = {b.name: [b.lumps.count, round(b.volume, 3)] for b in comp.bRepBodies}
    print(json.dumps(out, ensure_ascii=False))
