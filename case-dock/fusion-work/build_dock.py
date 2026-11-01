import adsk.core, adsk.fusion, math, json

# Clockinator - phone dock with Qi coil + NFC tag. Prototype v1.
# Axes: X width, Y up, Z front (phone side). Units in the script: mm.
# The phone leans back on a sloped face; coil and NFC tag sit just behind a 1.2 mm skin.
# All components are loaded from the open rear; a flat cover closes it with 4 M3 screws.

TILT = math.radians(70)                       # face angle from horizontal
D = (-math.cos(TILT), math.sin(TILT))         # up the face (z, y)
N = (math.sin(TILT), math.cos(TILT))          # outward normal (z, y)


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

    def above_line(z0, y0, z1, y1):
        dz, dy = z1 - z0, y1 - y0
        length = math.hypot(dz, dy)
        d3 = adsk.core.Vector3D.create(0, dy / length, dz / length)
        up = (dz / length, -dy / length)
        cy, cz = (y0 + y1) / 2 + up[0] * 50, (z0 + z1) / 2 + up[1] * 50
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(point(0, cy, cz), U, d3, 60, 60, 10))

    for o in [o for o in root.occurrences if o.name.startswith(('Dock', 'tmp-dock'))]:
        o.deleteMe()

    # ----- parameters -----
    half = 45.0                      # half width
    A = (45.0, 14.0)                 # (z, y) where the phone's bottom edge meets the face
    top_y = 140.0
    rear = -10.0                     # rear plane (opening closed by the cover)
    wall = 2.4
    skin = 1.2                       # thickness in front of coil and tag
    cover_t = 1.8
    coil_s, tag_s = 70.0, 116.0      # distance along the face from A

    def on_face(s, a=0.0):           # point on the face, a = offset along the normal (negative = inside)
        return (A[0] + s * D[0] + a * N[0], A[1] + s * D[1] + a * N[1])

    # ----- outer envelope: side profile extruded across the width -----
    z_top = A[0] + ((top_y - A[1]) / D[1]) * D[0]
    prof = [(rear, 0), (60, 0), (60, 22), (57.7, 22), (57.7, A[1]), A, (z_top, top_y), (rear, top_y)]
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
    shell = mgr.copy(body)
    env = mgr.copy(body)
    tmp.deleteMe()

    # ----- cavity: open at the rear, 1.2 mm behind the face -----
    inner = on_face(0, -skin)
    cav = box(-half + wall, half - wall, wall, top_y - wall, rear - 5, 54.6)
    boolean(cav, above_line(inner[0], inner[1], inner[0] + D[0] * 10, inner[1] + D[1] * 10), intersect)
    boolean(shell, cav, cut)

    # ----- PCB tray on the floor: walls left, right and front; open at the rear -----
    pcb_z0, pcb_z1, pcb_hx = -9.5, 31.5, 7.0
    tray = box(-pcb_hx - .8 - 1.6, -pcb_hx - .8, wall - .2, wall + 3.5, pcb_z0, pcb_z1 + 2.4)
    boolean(tray, box(pcb_hx + .8, pcb_hx + .8 + 1.6, wall - .2, wall + 3.5, pcb_z0, pcb_z1 + 2.4), union)
    boolean(tray, box(-pcb_hx - .8, pcb_hx + .8, wall - .2, wall + 3.5, pcb_z1 + .8, pcb_z1 + 2.4), union)
    boolean(shell, tray, union)

    # ----- cover screws: blocks against the side walls, nut slides in from the inside -----
    screws = [(sx * 36.0, y) for sx in (-1, 1) for y in (6.6, top_y - wall - 4.2)]
    for x, y in screws:
        sx = 1 if x > 0 else -1
        xa, xb = sorted((sx * 31.8, sx * (half - wall + .3)))
        blk = box(xa, xb, y - 4.2, y + 4.2, rear, rear + 8)
        boolean(blk, mgr.copy(env), intersect)
        boolean(shell, blk, union)
        boolean(shell, cylinder(x, y, rear - 1, x, y, rear + 6, 1.7), cut)
        xs = sorted((sx * 30.8, x + sx * 2.9))   # open towards the centre: the nut slides in from the cavity
        boolean(shell, box(xs[0], xs[1], y - 2.9, y + 2.9, rear + 1.5, rear + 4.1), cut)

    # ----- cover -----
    cov = box(-half, half, 0, top_y, rear - cover_t, rear)
    for x, y in screws:
        boolean(cov, cylinder(x, y, rear - cover_t - 1, x, y, rear + 1, 1.7), cut)
        boolean(cov, cylinder(x, y, rear - cover_t - 1, x, y, rear - cover_t + 1.2, 3.1), cut)
    boolean(cov, box(-4.6, 4.6, wall + .2, wall + 5.2, rear - cover_t - 1, rear + 1), cut)   # micro-USB opening

    # ----- reference parts (assumed sizes) -----
    refs = []
    c0, c1 = on_face(coil_s, -skin), on_face(coil_s, -skin - 3.0)
    refs.append(('Qi coil 50 mm (ref)', cylinder(0, c0[1], c0[0], 0, c1[1], c1[0], 25.0)))
    c0, c1 = on_face(tag_s, -skin), on_face(tag_s, -skin - 1.0)
    refs.append(('NFC tag 25 mm (ref)', cylinder(0, c0[1], c0[0], 0, c1[1], c1[0], 12.5)))
    pcb = box(-pcb_hx, pcb_hx, wall, wall + 2.0, pcb_z0, pcb_z1)
    boolean(pcb, box(-3.75, 3.75, wall + 2.0, wall + 4.6, pcb_z0 - .4, pcb_z0 + 5.6), union)         # micro-USB
    boolean(pcb, cylinder(0, wall + 2.0, 22.0, 0, wall + 16.0, 22.0, 5.0), union)                    # tall capacitor (assumed 10 x 14 mm)
    refs.append(('Qi driver PCB 41x14 + capacitor (ref)', pcb))

    # phone 11 x 75 x 155 mm resting on the ledge, back against the face
    pc = on_face(77.5, 5.5)
    ph = mgr.createBox(adsk.core.OrientedBoundingBox3D.create(
        point(0, pc[1], pc[0]), U, adsk.core.Vector3D.create(0, D[1], D[0]), 7.5, 15.5, 1.1))
    refs.append(('Phone 11x75x155 (ref)', ph))

    occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    comp = occ.component
    comp.name = 'Dock - printed parts'
    base = comp.features.baseFeatures.add()
    base.startEdit()
    comp.bRepBodies.add(shell, base)
    comp.bRepBodies.add(cov, base)
    base.finishEdit()
    comp.bRepBodies.item(0).name = 'Dock body'
    comp.bRepBodies.item(1).name = 'Back cover'

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
