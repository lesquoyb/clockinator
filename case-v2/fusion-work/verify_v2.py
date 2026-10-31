import adsk.core, adsk.fusion, json


def run(_context: str):
    app = adsk.core.Application.get()
    d = adsk.fusion.Design.cast(app.activeProduct)
    r = d.rootComponent
    ents = adsk.core.ObjectCollection.create()
    mine = set()
    for o in r.occurrences:
        if o.name.startswith('Boitier v2'):
            for b in o.bRepBodies:
                if 'variante' in b.name:
                    continue
                ents.add(b)
                mine.add(b.entityToken)
        elif o.name.startswith(('Haut', 'Ecran', 'ESP32 - pos', 'KS33', 'LED 5 mm v2')):
            ents.add(o)
            mine.add(o.entityToken)
    inp = d.createInterferenceInput(ents)
    inp.areCoincidentFacesConsideredInterference = False
    res = d.analyzeInterference(inp)
    rows = []
    for i in range(res.count):
        it = res.item(i)
        a, b = it.entityOne, it.entityTwo
        va = a.entityToken in mine and getattr(a, 'name', '').startswith(('Boitier', 'Couvercle', 'Touche', 'Platine', 'Diffuseur', 'Bouchon'))
        vb = b.entityToken in mine and getattr(b, 'name', '').startswith(('Boitier', 'Couvercle', 'Touche', 'Platine', 'Diffuseur', 'Bouchon'))
        if va or vb:
            bb = it.interferenceBody.boundingBox
            rows.append([getattr(a, 'name', '?'), getattr(b, 'name', '?'), round(it.interferenceBody.volume * 1000, 2),
                         [round(v * 10, 1) for v in (bb.minPoint.x, bb.minPoint.y, bb.minPoint.z, bb.maxPoint.x, bb.maxPoint.y, bb.maxPoint.z)]])
    print(json.dumps({'total_pairs': res.count, 'involving_v2_parts': rows}, ensure_ascii=False))
