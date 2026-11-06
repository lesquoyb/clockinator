import adsk.core, adsk.fusion, math, os

OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'output'))

# body name -> (file name, flipped so the face lies on the bed)
FILES = {
    'Shell': ('shell', False),
    'Lid': ('lid', False),
    'Key Light - KS33': ('key-light', True),
    'Key Stop - KS33': ('key-stop', True),
    'Key Snooze - KS33': ('key-snooze', True),
    'Switch plate - KS33': ('switch-plate', False),
    'Diffuser slot plug': ('diffuser-plug', False),
    'Diffuser white - 0.8 mm': ('diffuser-0.8mm', False),
    'Diffuser white - 0.6 mm (variant)': ('diffuser-0.6mm', False),
    'Diffuser white - 1.2 mm (variant)': ('diffuser-1.2mm', False),
}


def run(_context: str):
    os.makedirs(OUT, exist_ok=True)
    app = adsk.core.Application.get()
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    mgr = adsk.fusion.TemporaryBRepManager.get()
    src = next(o for o in root.occurrences if o.name.startswith('Enclosure v3'))

    scratch = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    comp = scratch.component
    comp.name = 'export-tmp'
    base = comp.features.baseFeatures.add()
    base.startEdit()
    order = []
    for b in src.component.bRepBodies:
        fname, flip = FILES[b.name]
        t = mgr.copy(b)
        m = adsk.core.Matrix3D.create()
        # model Y (up) -> print Z (up), or -Z for the keys
        m.setToRotation(math.pi / 2 * (-1 if flip else 1), adsk.core.Vector3D.create(1, 0, 0), adsk.core.Point3D.create(0, 0, 0))
        assert mgr.transform(t, m)
        bb = t.boundingBox
        s = adsk.core.Matrix3D.create()
        s.translation = adsk.core.Vector3D.create(-(bb.minPoint.x + bb.maxPoint.x) / 2, -(bb.minPoint.y + bb.maxPoint.y) / 2, -bb.minPoint.z)
        assert mgr.transform(t, s)
        comp.bRepBodies.add(t, base)
        order.append(fname)
    base.finishEdit()
    for i, fname in enumerate(order):
        comp.bRepBodies.item(i).name = fname
    for i in range(comp.bRepBodies.count):
        nb = comp.bRepBodies.item(i)
        opt = design.exportManager.createSTLExportOptions(nb, os.path.join(OUT, nb.name + '.stl'))
        opt.sendToPrintUtility = False
        opt.meshRefinement = adsk.fusion.MeshRefinementSettings.MeshRefinementHigh
        design.exportManager.execute(opt)
    scratch.deleteMe()

    design.exportManager.execute(design.exportManager.createSTEPExportOptions(os.path.join(OUT, 'clockinator-v3-parts.step'), src.component))
    design.exportManager.execute(design.exportManager.createFusionArchiveExportOptions(os.path.join(OUT, 'clockinator-v3-assembly.f3d')))
    print(sorted(os.listdir(OUT)))
