import adsk.core, adsk.fusion, os, math, json


def run(_context: str):
    app=adsk.core.Application.get()
    d=adsk.fusion.Design.cast(app.activeProduct)
    root=d.rootComponent
    directory=r'C:\Users\baptiste\Documents\GitHub\clockinator\case-v2\output'
    os.makedirs(directory,exist_ok=True)
    component=next(o.component for o in root.occurrences if 'Boitier 130' in o.component.name)
    exp=d.exportManager
    for b in component.bRepBodies:b.isLightBulbOn=True
    assert exp.execute(exp.createSTEPExportOptions(os.path.join(directory,'clockinator-130-pieces.step'),component))
    assert exp.execute(exp.createFusionArchiveExportOptions(os.path.join(directory,'clockinator-130-assemblage.f3d')))
    assert exp.execute(exp.createFusionArchiveExportOptions(os.path.join(directory,'clockinator-130-pieces.f3d'),component))
    mgr=adsk.fusion.TemporaryBRepManager.get()
    shapes=[mgr.copy(b) for b in component.bRepBodies]
    assert len(shapes)==12
    names=['boitier','couvercle','casquette-lumineuse','capot-esp32','touche-lumiere','touche-stop','touche-snooze','couvercle-led','cache-fils-led','diffuseur-0.8mm','reflecteur-blanc','platine-ks33']
    a=math.sqrt(3)/2
    def frontbox(x0,x1,v0,v1,n0,n1):
        vv=(v0+v1)/2
        nn=(n0+n1)/2
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(adsk.core.Point3D.create((x0+x1)/20,(a*vv+.5*nn)/10,(-.5*vv+a*nn)/10),adsk.core.Vector3D.create(1,0,0),adsk.core.Vector3D.create(0,a,-.5),(x1-x0)/10,(v1-v0)/10,(n1-n0)/10))
    thin=mgr.copy(shapes[9])
    assert mgr.booleanOperation(thin,frontbox(-24.5,24.5,28.2,31,3.5,7.5),adsk.fusion.BooleanTypes.DifferenceBooleanType)
    thick=mgr.copy(shapes[9])
    assert mgr.booleanOperation(thick,frontbox(-24.5,24.5,28.3,28.8,3.5,7.5),adsk.fusion.BooleanTypes.UnionBooleanType)
    shapes.extend([thin,thick])
    names.extend(['diffuseur-0.6mm','diffuseur-1.2mm'])
    YUP=[1,0,0,0,0,0,-1,0,0,1,0,0,0,0,0,1]
    YDOWN=[1,0,0,0,0,0,1,0,0,-1,0,0,0,0,0,1]
    NUP=[1,0,0,0,0,a,-.5,0,0,.5,a,0,0,0,0,1]
    NDOWN=[1,0,0,0,0,-a,.5,0,0,-.5,-a,0,0,0,0,1]
    VUP=[1,0,0,0,0,-.5,-a,0,0,a,-.5,0,0,0,0,1]
    occurrence=root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    comp=occurrence.component
    comp.name='Preparation export impression'
    base=comp.features.baseFeatures.add()
    base.startEdit()
    for i,body in enumerate(shapes):
        rotation=adsk.core.Matrix3D.create()
        data=YUP if i in [0,1] else YDOWN if i in [3,4,5,6,11] else NUP if i in [8,10] else NDOWN if i==7 else VUP
        rotation.setWithArray(data)
        assert mgr.transform(body,rotation)
        q=body.boundingBox
        shift=adsk.core.Matrix3D.create()
        shift.translation=adsk.core.Vector3D.create(-q.minPoint.x,-q.minPoint.y,-q.minPoint.z)
        assert mgr.transform(body,shift)
        comp.bRepBodies.add(body,base)
    base.finishEdit()
    for i,name in enumerate(names):comp.bRepBodies.item(i).name=name
    occurrence.isLightBulbOn=False
    print(json.dumps({'prepared_stls':names,'next':'Run export_stls.py in a separate Fusion script transaction.'}))
