import adsk.core, adsk.fusion, math, json

# Clockinator - horizontal phone dock (L shape). Prototype v1.
# Axes: X width, Y up, Z along the phone (phone lies flat on the platform, bottom edge against the block).
# Side profile is an L: a flat platform (z 0..172) with the electronics block upright at its rear end (z -30..0).
# Everything is loaded from below; a flat cover (with the PCB tray) closes the underside with 6 M3 screws.


def run(_context: str):
    app = adsk.core.Application.get()
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    mgr = adsk.fusion.TemporaryBRepManager.get()
    U = adsk.core.Vector3D.create(1, 0, 0)
    union = adsk.fusion.BooleanTypes.UnionBooleanType
    cut = adsk.fusion.BooleanTypes.DifferenceBooleanType
    intersect = adsk.fusion.BooleanTypes.IntersectionBooleanType

    def point(x, y, z):
        return adsk.core.Point3D.create(x / 10, y / 10, z / 10)

    def box(x0, x1, y0, y1, z0, z1):
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(
            point((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2), U,
            adsk.core.Vector3D.create(0, 1, 0), (x1 - x0) / 10, (y1 - y0) / 10, (z1 - z0) / 10))

    def cylinder(x, y, z, xx, yy, zz, r):
        return mgr.createCylinderOrCone(point(x, y, z), r / 10, point(xx, yy, zz), r / 10)

    def boolean(target, tool, op):
        assert mgr.booleanOperation(target, tool, op), 'Boolean failed'
        return target

    def near(a, b):
        return abs(a - b) < 0.02

    for o in [o for o in root.occurrences if o.name.startswith(('Dock', 'tmp-dock'))]:
        o.deleteMe()

    # ----- parameters -----
    half = 45.0
    z_rear, z_front = -30.0, 172.0       # block rear face / platform end
    plat_y, block_y = 12.0, 30.0         # platform top, block top
    wall, skin = 2.4, 1.2
    cover_t = 1.8
    rad = 6.0
    pad_diameter, pad_depth = 8.4, 1.0  # 8 x 2 mm adhesive feet; 1 mm protrudes
    pad_centers = [(x, z) for x in (-33.0, 33.0) for z in (-14.0, 146.0)]
    assert 0 < pad_depth < cover_t

    # ----- outer envelope: L profile extruded across the width, rounded with real fillets -----
    prof = [(z_rear, 0), (z_front, 0), (z_front, plat_y), (0, plat_y), (0, block_y), (z_rear, block_y)]
    tmp = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    tmp.component.name = 'tmp-dock'
    tc = tmp.component
    sk = tc.sketches.add(tc.yZConstructionPlane)          # sketch (x, y) -> world (y, z) = (y, -x)
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

    col = adsk.core.ObjectCollection.create()
    for e in body.edges:
        sp, ep = e.startVertex.geometry, e.endVertex.geometry
        if abs(sp.x - ep.x) > 1.0 and near(sp.y, ep.y) and near(sp.z, ep.z):            # profile corners (along X)
            for cz, cy in ((z_rear, block_y), (0.0, block_y), (z_front, plat_y)):
                if near(sp.z * 10, cz) and near(sp.y * 10, cy):
                    col.add(e)
        elif near(sp.x, ep.x) and abs(sp.x) > 4.4 and near(sp.z, ep.z) and abs(sp.y - ep.y) > 0.3:   # vertical corners
            if near(sp.z * 10, z_rear) or near(sp.z * 10, z_front):
                col.add(e)
        elif near(sp.x, ep.x) and abs(sp.x) > 4.4 and near(sp.y, ep.y) and abs(sp.z - ep.z) > 1.0:  # top side edges (along Z)
            if near(sp.y * 10, plat_y) or near(sp.y * 10, block_y):
                col.add(e)
    fi = tc.features.filletFeatures.createInput()
    fi.edgeSetInputs.addConstantRadiusEdgeSet(col, adsk.core.ValueInput.createByReal(rad / 10), True)
    tc.features.filletFeatures.add(fi)
    body = tc.bRepBodies.item(0)
    shell = mgr.copy(body)
    env = mgr.copy(body)
    # Derive a clearance envelope from the rounded exterior so that the
    # rectangular electronics cavity cannot break through any fillet.
    bottom_faces = adsk.core.ObjectCollection.create()
    for face in body.faces:
        bounds = face.boundingBox
        if abs(bounds.minPoint.y) < 1e-6 and abs(bounds.maxPoint.y) < 1e-6:
            bottom_faces.add(face)
    assert bottom_faces.count == 1
    hollow_input = tc.features.shellFeatures.createInput(bottom_faces, False)
    hollow_input.insideThickness = adsk.core.ValueInput.createByString('1.2 mm')
    tc.features.shellFeatures.add(hollow_input)
    clearance_envelope = mgr.copy(env)
    boolean(clearance_envelope, tc.bRepBodies.item(0), cut)
    tmp.deleteMe()

    # Keep the nominal wall and board clearances, trimming only near fillets.
    cav = box(-half + wall, half - wall, -1, plat_y - skin, z_rear + wall, z_front - wall)
    boolean(cav, box(-half + wall, half - wall, -1, block_y - wall, z_rear + wall, -wall), union)
    boolean(cav, clearance_envelope, intersect)
    boolean(shell, cav, cut)

    # ----- cover screws: columns on the side walls, nut slides in from the cavity -----
    screws = [(sx * 34.0, z) for sx in (-1, 1) for z in (-25.5, 8.0, 160.0)]
    for x, z in screws:
        sx = 1 if x > 0 else -1
        xa, xb = sorted((sx * 29.8, sx * 43.0))
        blk = box(xa, xb, 0, 8, z - 4.2, z + 4.2)
        boolean(blk, mgr.copy(env), intersect)
        boolean(shell, blk, union)
        boolean(shell, cylinder(x, -1, z, x, 6, z, 1.7), cut)
        xs = sorted((sx * 28.8, x + sx * 2.9))
        boolean(shell, box(xs[0], xs[1], 1.5, 4.1, z - 2.9, z + 2.9), cut)

    # USB opening in the right wall (port at x = 42.4, plug body stays outside the wall)
    pcb_length, pcb_width = 41.0, 20.0  # measured bare board, excluding the connector
    pcb_x1, zc = 42.0, -9.0             # shift the wider board away from the rear screw columns
    pcb_x0 = pcb_x1 - pcb_length
    pcb_z0, pcb_z1 = zc - pcb_width / 2, zc + pcb_width / 2
    boolean(shell, box(41.0, half + 1, 0.65, 5.85, zc - 4.6, zc + 4.6), cut)

    # ----- cover with PCB tray (rounded plate, same outline as the body) -----
    tmp2 = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    tc2 = tmp2.component
    sk2 = tc2.sketches.add(tc2.xYConstructionPlane)
    sk2.sketchCurves.sketchLines.addTwoPointRectangle(
        adsk.core.Point3D.create(-half / 10, 0, 0), adsk.core.Point3D.create(half / 10, (z_front - z_rear) / 10, 0))
    ei2 = tc2.features.extrudeFeatures.createInput(sk2.profiles.item(0), adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    ei2.setDistanceExtent(False, adsk.core.ValueInput.createByReal(cover_t / 10))
    b2 = tc2.features.extrudeFeatures.add(ei2).bodies.item(0)
    col2 = adsk.core.ObjectCollection.create()
    for e in b2.edges:
        if abs(e.startVertex.geometry.z - e.endVertex.geometry.z) > 0.05:
            col2.add(e)
    fi2 = tc2.features.filletFeatures.createInput()
    fi2.edgeSetInputs.addConstantRadiusEdgeSet(col2, adsk.core.ValueInput.createByReal(rad / 10), True)
    tc2.features.filletFeatures.add(fi2)
    cov = mgr.copy(tc2.bRepBodies.item(0))
    tmp2.deleteMe()
    rot = adsk.core.Matrix3D.create()
    rot.setToRotation(math.pi / 2, adsk.core.Vector3D.create(1, 0, 0), adsk.core.Point3D.create(0, 0, 0))
    assert mgr.transform(cov, rot)                                    # local (x, y, z) -> (x, -z, y): thickness goes down
    mv = adsk.core.Matrix3D.create()
    mv.translation = adsk.core.Vector3D.create(0, 0, z_rear / 10)
    assert mgr.transform(cov, mv)

    for x, z in screws:
        boolean(cov, cylinder(x, -cover_t - 1, z, x, 1, z, 1.7), cut)
        boolean(cov, cylinder(x, -cover_t - 1, z, x, -cover_t + 1.2, z, 3.1), cut)
    # Feet go on the large underside (negative Y), alongside the screw heads.
    # Do not cut the block's end face at Z=-30: that is the dock's side.
    before_pads = cov.volume
    for x, z in pad_centers:
        assert all(math.hypot(x-sx, z-sz) > pad_diameter/2 + 3.1 + .5 for sx, sz in screws)
        boolean(cov, cylinder(x, -cover_t-.1, z, x, -cover_t+pad_depth, z, pad_diameter/2), cut)
    removed_mm3 = (before_pads-cov.volume)*1000
    assert abs(removed_mm3-len(pad_centers)*math.pi*(pad_diameter/2)**2*pad_depth) < .1
    clearance, rail = .5, 1.6
    low, high = pcb_z0 - clearance, pcb_z1 + clearance
    stop_face = pcb_x0 - clearance
    left = stop_face - rail
    # Short returns at the wire end leave 13 mm between the stops.
    inner_stop, outer_stop = 3.0, 5.0
    boolean(cov, box(left, pcb_x1, 0, 3.5, high, high + rail), union)
    boolean(cov, box(left, pcb_x1, 0, 3.5, low - rail, low), union)
    boolean(cov, box(left, stop_face, 0, 3.5, high - inner_stop, high), union)
    boolean(cov, box(left, stop_face, 0, 3.5, low, low + outer_stop), union)

    # ----- reference parts (assumed sizes) -----
    refs = []
    refs.append(('Qi coil 50 mm (ref)', cylinder(0, plat_y - skin - 3.0, 85.0, 0, plat_y - skin, 85.0, 25.0)))
    refs.append(('NFC tag 25 mm (ref)', cylinder(0, plat_y - skin - 1.0, 150.0, 0, plat_y - skin, 150.0, 12.5)))
    pcb = box(pcb_x0, pcb_x1, 0, 2.0, pcb_z0, pcb_z1)
    boolean(pcb, box(pcb_x1 - 6, pcb_x1 + .4, 2.0, 4.6, zc - 3.75, zc + 3.75), union)                # micro-USB
    boolean(pcb, cylinder(14.0, 2.0, zc, 14.0, 18.0, zc, 5.0), union)                                # tall capacitor (assumed 10 x 16 mm)
    refs.append(('Qi driver PCB 41x20 + capacitor (ref)', pcb))

    # Check the loaded cover as it rises into the shell, including the connector.
    def assert_clear(a, b, label):
        overlap = mgr.copy(a)
        boolean(overlap, b, intersect)
        assert not overlap.faces.count or abs(overlap.volume) < 1e-6, label

    assert_clear(cov, pcb, 'PCB touches the tray')
    for drop in (0, 1, 3, 5, 10, 18):
        shift = adsk.core.Matrix3D.create()
        shift.translation = adsk.core.Vector3D.create(0, -drop / 10, 0)
        for part, label in ((cov, 'cover'), (pcb, 'PCB')):
            moving = mgr.copy(part)
            assert mgr.transform(moving, shift)
            assert_clear(moving, shell, f'{label} interferes with shell at {drop} mm below seat')
    refs.append(('Phone 11x75x155 (ref)', box(-37.5, 37.5, plat_y, plat_y + 11.0, 0.0, 155.0)))

    occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    comp = occ.component
    comp.name = 'Dock - printed parts'
    base = comp.features.baseFeatures.add()
    base.startEdit()
    comp.bRepBodies.add(shell, base)
    comp.bRepBodies.add(cov, base)
    base.finishEdit()
    comp.bRepBodies.item(0).name = 'Dock body'
    comp.bRepBodies.item(1).name = 'Bottom cover'

    ro = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    ro.component.name = 'Dock - references'
    rb = ro.component.features.baseFeatures.add()
    rb.startEdit()
    for n, s in refs:
        ro.component.bRepBodies.add(s, rb)
    rb.finishEdit()
    for i, (n, s) in enumerate(refs):
        ro.component.bRepBodies.item(i).name = n
    app.activeViewport.fit()
    print(json.dumps({b.name: [b.lumps.count, round(b.volume, 2)] for c in (comp, ro.component) for b in c.bRepBodies}))
