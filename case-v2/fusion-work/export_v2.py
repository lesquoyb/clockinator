import adsk.core, adsk.fusion, math, os

OUT = r'C:\Users\baptiste\Documents\GitHub\clockinator\case-v2\output'

# nom de piece -> (fichier, retournee face dessus contre le plateau)
FILES = {
    'Boitier - coque': ('coque', False),
    'Couvercle - dessus, boutons, LED, retenue ecran': ('couvercle', False),
    'Touche Lumiere - KS33': ('touche-lumiere', True),
    'Touche Stop - KS33': ('touche-stop', True),
    'Touche Snooze - KS33': ('touche-snooze', True),
    'Platine KS33 - montage par dessous': ('platine-ks33', False),
    'Bouchon rainure diffuseur': ('bouchon-diffuseur', False),
    'Diffuseur blanc - 0.8 mm': ('diffuseur-0.8mm', False),
    'Diffuseur blanc - 0.6 mm (variante)': ('diffuseur-0.6mm', False),
    'Diffuseur blanc - 1.2 mm (variante)': ('diffuseur-1.2mm', False),
}


def run(_context: str):
    app = adsk.core.Application.get()
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    mgr = adsk.fusion.TemporaryBRepManager.get()
    src = next(o for o in root.occurrences if o.name.startswith('Boitier v2'))

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
        # Y (haut du modele) -> Z (haut du plateau), ou -Z pour les touches
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

    design.exportManager.execute(design.exportManager.createSTEPExportOptions(os.path.join(OUT, 'clockinator-v2-pieces.step'), src.component))
    design.exportManager.execute(design.exportManager.createFusionArchiveExportOptions(os.path.join(OUT, 'clockinator-v2-assemblage.f3d')))
    print(sorted(os.listdir(OUT)))
