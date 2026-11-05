"""Export the active, completed L dock. Preserve the current Fusion edits."""
import adsk.core, adsk.fusion, json, os


def run(_context: str):
    app=adsk.core.Application.get()
    design=adsk.fusion.Design.cast(app.activeProduct)
    component=next(o.component for o in design.rootComponent.occurrences if o.component.name=='Dock - printed parts')
    assert component.bRepBodies.count==2
    body=next(b for b in component.bRepBodies if b.name=='Dock body')
    cover=next(b for b in component.bRepBodies if b.name=='Bottom cover')
    pockets=[]
    for face in cover.faces:
        g=face.geometry
        if g.surfaceType==adsk.core.SurfaceTypes.CylinderSurfaceType and abs(g.radius-.42)<1e-6 and abs(g.axis.y)>.99:
            q=face.boundingBox
            assert abs(q.minPoint.y*10+1.8)<1e-4 and abs(q.maxPoint.y*10+.8)<1e-4
            pockets.append([round(g.origin.x*10,3),round(g.origin.z*10,3)])
    assert sorted(pockets)==[[-33.,-14.],[-33.,146.],[33.,-14.],[33.,146.]],pockets
    assert all(b.isSolid and b.lumps.count==1 for b in [body,cover])
    directory=r'C:\Users\baptiste\Documents\GitHub\clockinator\case-dock\output'
    os.makedirs(directory,exist_ok=True)
    exp=design.exportManager
    for part,name in [(body,'dock-L-body.stl'),(cover,'dock-L-bottom-cover.stl')]:
        path=os.path.join(directory,name)
        opts=exp.createSTLExportOptions(part,path)
        opts.isBinaryFormat=True
        opts.sendToPrintUtility=False
        opts.unitType=adsk.fusion.DistanceUnits.MillimeterDistanceUnits
        opts.meshRefinement=adsk.fusion.MeshRefinementSettings.MeshRefinementHigh
        assert exp.execute(opts) and os.path.isfile(path)
    assert exp.execute(exp.createFusionArchiveExportOptions(os.path.join(directory,'dock-L-assembly.f3d')))
    assert exp.execute(exp.createSTEPExportOptions(os.path.join(directory,'dock-L-parts.step'),component))
    report={'document':app.activeDocument.name,'pad_pockets':{'body':'Bottom cover','diameter_mm':8.4,'depth_mm':1,'foot_height_mm':2,'protrusion_mm':1,'remaining_cover_mm':.8,'centers_xz_mm':sorted(pockets)},'parts':[{'name':b.name,'volume_cm3':b.volume,'solid':b.isSolid,'lumps':b.lumps.count} for b in [body,cover]]}
    report['fillet_clearance_envelope_mm']=1.2
    pcb=next(b for o in design.rootComponent.occurrences if o.component.name=='Dock - references'
             for b in o.component.bRepBodies if b.name.startswith('Qi driver PCB 41x20'))
    bounds=pcb.boundingBox
    assert abs((bounds.maxPoint.z-bounds.minPoint.z)*10-20)<1e-6
    guides=[f.pointOnFace.z*10 for f in cover.faces
            if f.geometry.surfaceType==adsk.core.SurfaceTypes.PlaneSurfaceType
            and abs(f.geometry.normal.z)>.99 and f.boundingBox.maxPoint.y>.34]
    assert all(any(abs(z-target)<1e-6 for z in guides) for target in (-19.5,1.5)),guides
    # Confirm the two returns and the open wire passage on the actual cover.
    outside=adsk.fusion.PointContainment.PointOutsidePointContainment
    for z,open_passage in [(-15,False),(-14,True),(-7,True),(-2,True),(-1,False)]:
        point=adsk.core.Point3D.create(-.03,.15,z/10)
        assert (cover.pointContainment(point)==outside)==open_passage,z
    report['pcb_tray']={'bare_board_mm':[41,20],'clear_width_mm':21,'side_clearance_mm':.5,'micro_usb_center_z_mm':-9,'inner_stop_mm':3,'outer_stop_mm':5,'wire_opening_mm':13}
    generated=next((doc for doc in app.documents if doc.name=='Phone dock L - script verification'),None)
    if generated:
        gd=adsk.fusion.Design.cast(next(p for p in generated.products if adsk.fusion.Design.cast(p)))
        gc=next(o.component for o in gd.rootComponent.occurrences if o.component.name=='Dock - printed parts')
        generated_volumes={b.name:b.volume for b in gc.bRepBodies}
        report['generator_reproduces_live_parts']=all(abs(b.volume-generated_volumes[b.name])<1e-7 for b in [body,cover])
        report['generator_volume_tolerance_cm3']=1e-7
    with open(os.path.join(directory,'validation-dock-L.json'),'w',encoding='utf-8') as f:json.dump(report,f,indent=2)
    print(json.dumps(report))
