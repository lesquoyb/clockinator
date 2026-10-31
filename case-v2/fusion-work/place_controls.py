import adsk.core, adsk.fusion, math


def run(_context: str):
    d=adsk.fusion.Design.cast(adsk.core.Application.get().activeProduct)
    root=d.rootComponent
    mgr=adsk.fusion.TemporaryBRepManager.get()
    reference=next(o for o in root.allOccurrences if o.fullPathName.startswith('Reference KS33') and o.bRepBodies.count)
    switch=mgr.copy(reference.bRepBodies.item(0))
    def p(x,y,z): return adsk.core.Point3D.create(x/10,y/10,z/10)
    def v(x,y,z): return adsk.core.Vector3D.create(x,y,z)
    for x,label in [(-39,'Lumiere'),(-5,'Stop'),(35,'Snooze')]:
        body=mgr.copy(switch)
        m=adsk.core.Matrix3D.create()
        m.setWithCoordinateSystem(p(x-60,11.9907*130/154-1.3-5.75+.01,-77.5),v(1,0,0),v(0,0,-1),v(0,1,0))
        assert mgr.transform(body,m)
        o=root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
        o.component.name='KS33 '+label+' - position de montage'
        base=o.component.features.baseFeatures.add()
        base.startEdit()
        o.component.bRepBodies.add(body,base)
        base.finishEdit()
    a=math.sqrt(3)/2
    def optical_point(x,vv,n): return p(x,a*vv+.5*n,-.5*vv+a*n)
    bulb=mgr.createCylinderOrCone(optical_point(0,32.8,-21.2),.25,optical_point(0,32.8,-13.2),.25)
    o=root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    o.component.name='LED 5 mm - position de montage'
    base=o.component.features.baseFeatures.add()
    base.startEdit()
    b=o.component.bRepBodies.add(bulb,base)
    base.finishEdit()
    b.opacity=.5
    adsk.core.Application.get().activeViewport.fit()
    print('Three KS33 references and LED envelope positioned')
