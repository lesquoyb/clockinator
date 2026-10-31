import adsk.core, adsk.fusion, json, math


def run(_context: str):
    d=adsk.fusion.Design.cast(adsk.core.Application.get().activeProduct)
    mgr=adsk.fusion.TemporaryBRepManager.get()
    root=d.rootComponent
    housing=next(o.component for o in root.occurrences if 'Boitier 130' in o.component.name)
    parts=list(housing.bRepBodies)
    references=[o for o in root.occurrences if 'position de montage' in o.component.name or 'Haut-parleur' in o.component.name]
    collisions=[]
    checked=0
    def overlap(a,b):
        aa=a.boundingBox
        bb=b.boundingBox
        if not (aa.minPoint.x<bb.maxPoint.x and aa.maxPoint.x>bb.minPoint.x and aa.minPoint.y<bb.maxPoint.y and aa.maxPoint.y>bb.minPoint.y and aa.minPoint.z<bb.maxPoint.z and aa.maxPoint.z>bb.minPoint.z): return 0
        temp=mgr.copy(a)
        ok=mgr.booleanOperation(temp,mgr.copy(b),adsk.fusion.BooleanTypes.IntersectionBooleanType)
        assert ok, 'Intersection failed'
        if temp.faces.count and temp.isSolid: return temp.volume*1000
        return 0
    for i,part in enumerate(parts):
        assert part.isSolid and part.lumps.count==1,part.name
        for other in parts[i+1:]:
            volume=overlap(part,other)
            checked+=1
            if volume>.001: collisions.append([part.name,other.name,volume])
        for reference in references:
            for body in reference.component.bRepBodies:
                volume=overlap(part,body)
                checked+=1
                if volume>.001: collisions.append([part.name,reference.name,body.name,round(volume,5)])
    # Check the screen insertion at successive positions, with its barrette removed.
    screen=next(o.component for o in references if o.component.name.startswith('Ecran'))
    shell=parts[0]
    for distance in [5,15,30,60]:
        transform=adsk.core.Matrix3D.create()
        transform.translation=adsk.core.Vector3D.create(0,math.sqrt(3)/2*distance/10,-.5*distance/10)
        for body in screen.bRepBodies:
            shifted=mgr.copy(body)
            assert mgr.transform(shifted,transform)
            volume=overlap(shell,shifted)
            checked+=1
            if volume>.001: collisions.append(['screen insertion',distance,body.name,round(volume,5)])
    # Full switch travel, checking guides and neighbouring printed pieces.
    for index in [4,5,6]:
        for distance in [.8,1.6,2.4,3.2]:
            t=adsk.core.Matrix3D.create()
            t.translation=adsk.core.Vector3D.create(0,-distance/10,0)
            shifted=mgr.copy(parts[index])
            assert mgr.transform(shifted,t)
            for j,other in enumerate(parts):
                if j==index:continue
                volume=overlap(shifted,other)
                checked+=1
                if volume>.001:collisions.append(['button travel',index,distance,other.name,round(volume,5)])
    # Lid and switch carrier must lift clear of the fixed enclosure and light pod.
    for index in [1,11]:
        for distance in [1,3,10,30]:
            t=adsk.core.Matrix3D.create()
            t.translation=adsk.core.Vector3D.create(0,distance/10,0)
            shifted=mgr.copy(parts[index])
            assert mgr.transform(shifted,t)
            for j in [0,2,3,7,8,9,10]:
                volume=overlap(shifted,parts[j])
                checked+=1
                if volume>.001:collisions.append(['lid removal',index,distance,parts[j].name,round(volume,5)])
    report={'checked_pairs':checked,'collisions':collisions,'button_travel_mm':3.2,'lid_lift_samples_mm':[1,3,10,30],'parts':[{'name':p.name,'solids':p.lumps.count,'volume_cm3':round(p.volume,3)} for p in parts]}
    print(json.dumps(report))
    assert not collisions,'Fit verification failed'
