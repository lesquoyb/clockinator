import adsk.core, adsk.fusion, math, json


def run(_context: str):
    app=adsk.core.Application.get()
    d=adsk.fusion.Design.cast(app.activeProduct)
    root=d.rootComponent
    mgr=adsk.fusion.TemporaryBRepManager.get()
    def p(x,y,z): return adsk.core.Point3D.create(x/10,y/10,z/10)
    def v(x,y,z): return adsk.core.Vector3D.create(x,y,z)
    def flatten(comp):
        result=[(mgr.copy(b),b.appearance,b.name) for b in comp.bRepBodies]
        for child in comp.occurrences:
            for body,appearance,name in flatten(child.component):
                assert mgr.transform(body,child.transform2)
                result.append((body,appearance,name))
        return result
    sources=[o for o in root.occurrences if o.name.startswith('Reference ecran:') or o.name.startswith('Reference ESP32:')]
    for source in sources:
        m=adsk.core.Matrix3D.create()
        if source.name.startswith('Reference ecran:'):
            name='Ecran 106x54 - position de montage'
            a=math.sqrt(3)/2
            m.setWithCoordinateSystem(p(-269.339,a*177.362-.5*4.7,-.5*177.362-a*4.7),v(1,0,0),v(0,-a,.5),v(0,-.5,-a))
        else:
            name='ESP32 - position de montage'
            m.setWithCoordinateSystem(p(-36,-27.5,-107.8239922077922+27.8),v(-1,0,0),v(0,0,1),v(0,1,0))
        bodies=flatten(source.component)
        occurrence=root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        comp=occurrence.component
        comp.name=name
        base=comp.features.baseFeatures.add()
        base.name='Reference STEP positionnee'
        base.startEdit()
        for body,appearance,bname in bodies:
            assert mgr.transform(body,m)
            new=comp.bRepBodies.add(body,base)
            new.name=bname
            new.appearance=appearance
        base.finishEdit()
        source.isLightBulbOn=False
        q=occurrence.boundingBox
        print(name,len(bodies),[[round(val*10,2) for val in (pnt.x,pnt.y,pnt.z)] for pnt in (q.minPoint,q.maxPoint)])
    app.activeViewport.fit()
