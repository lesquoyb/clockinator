import adsk.core, adsk.fusion, math, json


def run(_context: str):
    app = adsk.core.Application.get()
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    mgr = adsk.fusion.TemporaryBRepManager.get()
    U = adsk.core.Vector3D.create(1, 0, 0)
    V = adsk.core.Vector3D.create(0, math.sqrt(3)/2, -.5)
    N = adsk.core.Vector3D.create(0, .5, math.sqrt(3)/2)
    union = adsk.fusion.BooleanTypes.UnionBooleanType
    cut = adsk.fusion.BooleanTypes.DifferenceBooleanType
    intersect = adsk.fusion.BooleanTypes.IntersectionBooleanType

    def point(x,y,z):
        return adsk.core.Point3D.create(x/10,y/10,z/10)

    def box(x0,x1,y0,y1,z0,z1):
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(
            point((x0+x1)/2,(y0+y1)/2,(z0+z1)/2), U,
            adsk.core.Vector3D.create(0,1,0), (x1-x0)/10,(y1-y0)/10,(z1-z0)/10))

    def frontbox(x0,x1,v0,v1,n0,n1):
        v=(v0+v1)/2
        n=(n0+n1)/2
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(
            point((x0+x1)/2,V.y*v+N.y*n,V.z*v+N.z*n),U,V,
            (x1-x0)/10,(v1-v0)/10,(n1-n0)/10))

    def boolean(target,tool,operation):
        assert mgr.booleanOperation(target,tool,operation), 'Boolean failed'
        return target

    def cylinder(x,y,z,xx,yy,zz,r):
        return mgr.createCylinderOrCone(point(x,y,z),r/10,point(xx,yy,zz),r/10)

    def rounded(x,z,width,depth,y0,y1,r=3):
        b=box(x-width/2+r,x+width/2-r,y0,y1,z-depth/2,z+depth/2)
        boolean(b,box(x-width/2,x+width/2,y0,y1,z-depth/2+r,z+depth/2-r),union)
        for xx in [x-width/2+r,x+width/2-r]:
            for zz in [z-depth/2+r,z+depth/2-r]:
                boolean(b,cylinder(xx,y0,zz,xx,y1,zz,r),union)
        return b

    def stroke(x0,z0,x1,z1,y0,y1,width):
        length=math.hypot(x1-x0,z1-z0)
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(point((x0+x1)/2,(y0+y1)/2,(z0+z1)/2),adsk.core.Vector3D.create(x1-x0,0,z1-z0),adsk.core.Vector3D.create(0,1,0),length/10,(y1-y0)/10,width/10))

    # Preserve the source bodies and modify independent copies only.
    source_bodies=list(root.bRepBodies)
    main=mgr.copy(source_bodies[0])
    for body in source_bodies[1:]:
        boolean(main,mgr.copy(body),union)
    scale=130/154
    matrix=adsk.core.Matrix3D.create()
    for i in range(3): matrix.setCell(i,i,scale)
    assert mgr.transform(main,matrix)
    rear=-127.72996*scale
    front=-55.72996*scale
    floor=-50.44586*scale+2.4
    split=5
    roof_y=22
    lid_front=-29
    roof_slope=(33.36-roof_y)/(-18.22-front)
    def roofcut(offset=0):
        norm=math.sqrt(1+roof_slope**2)
        normal=adsk.core.Vector3D.create(0,1/norm,-roof_slope/norm)
        center=point(0,roof_y+offset+100/norm,front-100*roof_slope/norm)
        return mgr.createBox(adsk.core.OrientedBoundingBox3D.create(center,U,adsk.core.Vector3D.create(0,-roof_slope,-1),30,30,20))
    # Close the former front gap and open the internal partition completely.
    boolean(main,box(-65,65,floor-.2,roof_y,rear,front+.1),union)
    bridge=box(-65,65,floor-.2,60,front,17.79)
    boolean(bridge,frontbox(-200,200,-200,200,-1.7,200),cut)
    boolean(bridge,roofcut(),cut)
    boolean(main,bridge,union)
    lid=mgr.copy(main)
    lid_region=box(-100,100,split,100,rear-1,lid_front)
    boolean(lid,lid_region,intersect)
    boolean(main,lid_region,cut)
    cavity=box(-62.6,62.6,floor,roof_y-2.4,rear+2.4,front+.1)
    inner_bridge=box(-62.6,62.6,floor,60,front,20)
    boolean(inner_bridge,frontbox(-200,200,-200,200,-7.6,200),cut)
    boolean(inner_bridge,roofcut(-2.4),cut)
    boolean(cavity,inner_bridge,union)
    boolean(main,cavity,cut)
    boolean(lid,cavity,cut)

    # Screen board slides down from the top into a 106.4 x 54.4 guide.
    # Outer window uses 11 mm/12 mm side borders, and a 1.2 mm front lip.
    boolean(main,frontbox(-53.2,53.2,-27.2,45,-7.2,-1.2),cut)
    boolean(main,frontbox(-41.4,42.4,-24.4,24.4,-12,4),cut)
    # Side guides extend backwards and support the PCB without touching components.
    for xa,xb in [(-55.6,-53.2),(53.2,55.6)]:
        boolean(main,frontbox(xa,xb,-28.5,33.5,-10,-1.2),union)
    for xa,xb in [(-53.3,-51.3),(51.3,53.3)]:
        boolean(main,frontbox(xa,xb,-28.5,27.3,-7.3,-5.0),union)
    boolean(main,frontbox(-55.6,55.6,-29.6,-27.2,-10,-1.2),union)
    # Relief for the actual rear pin header, including its through-board tails.
    boolean(main,frontbox(46.5,53.4,-20.6,45,-14,-1.4),cut)
    boolean(main,frontbox(-58,58,27.5,42,-8,2),cut)
    screen_cap=frontbox(-58,58,27.6,42,-7.7,.9)
    boolean(screen_cap,roofcut(),cut)
    boolean(screen_cap,frontbox(-37,37,27.5,43,-9,2),cut)
    # LED mixing chamber above the screen, with no direct view of the LED die.
    boolean(main,frontbox(-37.2,37.2,27.4,43,-22,2),cut)
    boolean(lid,frontbox(-37.2,37.2,27.4,43,-22,2),cut)
    pod=frontbox(-37,37,27.6,38,-19.1,10.5)
    top_extension=frontbox(-37,37,36.3,43,-19.1,.9)
    boolean(top_extension,roofcut(),cut)
    boolean(pod,top_extension,union)
    boolean(pod,frontbox(-29.4,29.4,29.2,36.4,-20,8.9),cut)
    # The only optical outlet is underneath the overhang, facing down the screen.
    boolean(pod,frontbox(-26.4,26.4,28.3,30,2.6,8.4),cut)
    boolean(pod,frontbox(-25,25,25,28.5,3,8),cut)
    boolean(pod,frontbox(-31,31,25.8,27.7,8.9,10.5),union)
    for xa,xb in [(-31,-29.4),(29.4,31)]:
        boolean(pod,frontbox(xa,xb,25.8,27.7,2.6,10.5),union)
    boolean(pod,frontbox(-12,12,29.1,36.5,-7,-5.6),union)
    for x in [-34,34]:
        boolean(pod,frontbox(x-3,x+3,29.8,35.8,-19.1,-1),union)
    boolean(screen_cap,pod,union)
    led_cover=frontbox(-31,31,27.6,38,-21,-19.3)
    for x in [-34,34]:
        boolean(led_cover,frontbox(x-3,x+3,29.8,35.8,-21,-19.3),union)
        v=32.8
        boolean(screen_cap,cylinder(x,V.y*v+N.y*(-19.6),V.z*v+N.z*(-19.6),x,V.y*v+N.y*(-12),V.z*v+N.z*(-12),.85),cut)
        boolean(led_cover,cylinder(x,V.y*v+N.y*(-22),V.z*v+N.z*(-22),x,V.y*v+N.y*(-18),V.z*v+N.z*(-18),1.1),cut)
    lip=frontbox(-29.2,29.2,29.4,36.2,-19.4,-17.2)
    boolean(lip,frontbox(-28,28,30.6,35,-20,-16),cut)
    boolean(led_cover,lip,union)
    # Serviceable opaque boot behind the 5 mm LED; 9 x 6 mm wire outlet.
    boot=frontbox(-7,7,22.8,38,-31,-20.9)
    boolean(boot,frontbox(-5.4,5.4,24.4,36.4,-33,-21.5),cut)
    boolean(boot,frontbox(-4.5,4.5,21,25,-28.5,-22.5),cut)
    boolean(led_cover,boot,union)
    vv=32.8
    boolean(led_cover,cylinder(0,V.y*vv+N.y*(-23),V.z*vv+N.z*(-23),0,V.y*vv+N.y*(-17),V.z*vv+N.z*(-17),2.65),cut)
    boot_cap=frontbox(-7,7,22.8,38,-32.8,-31.2)
    ring=frontbox(-5.2,5.2,24.6,36.2,-31.3,-29.6)
    boolean(ring,frontbox(-4,4,25.8,35,-32,-29),cut)
    boolean(boot_cap,ring,union)
    for x in [-9,9]:
        v=31
        boolean(led_cover,cylinder(x,V.y*v+N.y*(-31),V.z*v+N.z*(-31),x,V.y*v+N.y*(-21),V.z*v+N.z*(-21),2.4),union)
        boolean(led_cover,cylinder(x,V.y*v+N.y*(-31.4),V.z*v+N.z*(-31.4),x,V.y*v+N.y*(-25),V.z*v+N.z*(-25),.85),cut)
        boolean(boot_cap,frontbox(x-2.5,x+2.5,28.5,33.5,-32.8,-31.2),union)
        boolean(boot_cap,cylinder(x,V.y*v+N.y*(-33),V.z*v+N.z*(-33),x,V.y*v+N.y*(-30.8),V.z*v+N.z*(-30.8),1.1),cut)
    diffuser=frontbox(-26.2,26.2,28.4,29.1,2.8,8.2)
    boolean(diffuser,frontbox(-24.8,24.8,27.6,28.6,3.2,7.8),union)
    boolean(diffuser,frontbox(-24.5,24.5,28.4,31,3.5,7.5),cut)
    liner=frontbox(-29.1,29.1,29.5,36.1,-17.2,8.6)
    boolean(liner,frontbox(-28.3,28.3,30.3,35.3,-18,9),cut)
    boolean(liner,frontbox(-12.3,12.3,29.2,36.4,-7.3,-5.3),cut)
    boolean(liner,frontbox(-12.2,12.2,29.5,36.1,-7.7,-7.1),union)
    boolean(liner,frontbox(-26.6,26.6,28,30.6,2.4,8.8),cut)
    for x in [-54.4,54.4]:
        v=30.5
        boolean(main,frontbox(x-3.4,x+3.4,v-3.4,v+3.4,-13,-7.8),union)
        for body,r,n0,n1 in [(main,1.25,-12,-1),(screen_cap,1.7,-9,2)]:
            boolean(body,cylinder(x,V.y*v+N.y*n0,V.z*v+N.z*n0,x,V.y*v+N.y*n1,V.z*v+N.z*n1,r),cut)

    # Cable trough: 14 mm usable width, 8 mm depth, open for assembly.
    trough=box(-19,-1,floor-.2,floor+10,front-3.4,5)
    boolean(trough,box(-17,-3,floor+2,floor+12,front-4.4,6),cut)
    boolean(main,trough,union)
    boolean(main,box(-18,-2,floor+2,floor+12,front-4,front+4),cut)

    # ESP32 exact hole locations measured from the supplied STEP.
    esp_x=-36
    esp_y=-27.5
    esp_z=rear+27.8
    for nx in [-11.6,11.6]:
        for ny in [-23.5,23.5]:
            x=esp_x-nx
            z=esp_z+ny
            boss=cylinder(x,floor-.2,z,x,esp_y-1.65,z,3)
            boolean(boss,cylinder(x,esp_y-9,z,x,esp_y,z,1.25),cut)
            boolean(main,boss,union)
    # USB-C housing and plug clearance, aligned with the real connector.
    usb=box(esp_x-4.8,esp_x+4.8,-28.94,-24.04,rear-2,rear+5)
    boolean(main,usb,cut)
    pcb_relief=box(-50.6,-21.4,-29.4,-27.1,rear+1.5,rear+3.4)
    boolean(main,pcb_relief,cut)

    # Removable opaque ESP shield; bottom exits remain accessible during wiring.
    shield=box(-54,-18,floor+.1,-17.5,rear+2.7,front-3.7)
    boolean(shield,box(-52.4,-19.6,floor-1,-19.1,rear+1,front-5.3),cut)
    boolean(shield,usb,cut)
    boolean(shield,pcb_relief,cut)
    boolean(shield,box(-20,-16,floor-.5,floor+7.1,-66,-60),cut)
    boolean(shield,box(-21.3,-20.5,floor+.1,-30,-68,front-4),union)
    for z in [-86,-62]:
        tab=box(-59.8,-53.5,floor+5,floor+7,z-4.2,z+4.2)
        boolean(tab,cylinder(-56.5,floor+4,z,-56.5,floor+8,z,1.7),cut)
        boolean(shield,tab,union)
        boss=cylinder(-56.5,floor-.2,z,-56.5,floor+4.7,z,3)
        boolean(boss,cylinder(-56.5,floor-.8,z,-56.5,floor+5,z,1.25),cut)
        boolean(main,boss,union)
        boolean(shield,cylinder(-56.5,floor-.5,z,-56.5,floor+4.9,z,3.3),cut)

    # Speaker: 70 x 30 x 15, M3 centres 2.5 mm from the four edges.
    for x in [-13.5,51.5]:
        for y in [-36.5,-11.5]:
            boss=cylinder(x,y,rear+2.2,x,y,rear+5.4,3.2)
            boolean(boss,cylinder(x,y,rear+.8,x,y,rear+6,1.25),cut)
            boolean(main,boss,union)
    for x in range(-7,47,5):
        for y in [-31,-26,-21,-16]:
            boolean(main,cylinder(x,y,rear-1,x,y,rear+3,1.25),cut)

    # Four lid screws accessible from above; pilot holes for M3 plastic screws.
    for x in [-58,58]:
        for z in [rear+7,lid_front-7]:
            boss=cylinder(x,floor-.2,z,x,split-.3,z,3.5)
            boolean(boss,cylinder(x,split-10,z,x,split+1,z,1.25),cut)
            boolean(main,boss,union)
            local_roof=roof_y+max(0,z-front)*roof_slope
            boolean(lid,cylinder(x,split+.3,z,x,local_roof-.2,z,4.1),union)
            boolean(lid,cylinder(x,split-1,z,x,45,z,1.7),cut)
            local_roof=roof_y+max(0,z-front)*roof_slope
            boolean(lid,cylinder(x,local_roof-2,z,x,45,z,3.1),cut)
    # Shallow internal registration skirt, with 0.3 mm lateral assembly clearance.
    skirt_top=split+.6
    skirt=box(-62.3,62.3,split-2,skirt_top,rear+2.7,lid_front-2.7)
    boolean(skirt,box(-60.8,60.8,split-3,skirt_top+1,rear+4.2,lid_front-4.2),cut)
    for x in [-58,58]:
        for z in [rear+7,lid_front-7]:
            boolean(skirt,cylinder(x,split-3,z,x,skirt_top+1,z,4),cut)
    for x in [-13.5,51.5]:
        boolean(skirt,cylinder(x,-11.5,rear+2,x,-11.5,rear+5,3.6),cut)
    boolean(lid,skirt,union)
    boolean(lid,box(-38,38,split-3,split+2,lid_front-4.3,lid_front-2.4),cut)
    for xa,xb in [(-64,-61),(61,64)]:
        for z in [rear+15,lid_front-15]:
            boolean(lid,box(xa,xb,split+.3,split+1,z-2,z+2),union)

    # KS33 low-profile switches clip into local 1.2 mm mounting lands.
    keycaps=[]
    mount_y=11.9907*scale-1.3
    carrier=box(-60.4,60.4,mount_y-1.2,mount_y,-98,-57)
    for z0,z1 in [(-98,-95),(-60,-57)]:
        boolean(carrier,box(-60.4,60.4,mount_y-4.4,mount_y-1.1,z0,z1),union)
    for xa,xb in [(-23,-21),(12,14)]:
        boolean(carrier,box(xa,xb,mount_y-4.4,mount_y-1.1,-95,-60),union)
    for x in [-58,58]:
        boolean(carrier,cylinder(x,mount_y-5,rear+7,x,mount_y+1,rear+7,4.4),cut)
    for x in [-53,53]:
        for z in [-94.7,-60.3]:
            boolean(lid,cylinder(x,mount_y+.3,z,x,roof_y-2.2,z,3),union)
            boolean(lid,cylinder(x,mount_y,z,x,roof_y-3.2,z,1.25),cut)
            boolean(carrier,cylinder(x,mount_y-5,z,x,mount_y+1,z,1.7),cut)
    for index,(x,width,label) in enumerate([(-39,26,'Lumiere'),(-5,30,'Stop'),(35,40,'Snooze')]):
        z=-77.5
        boolean(carrier,box(x-7.1,x+7.1,mount_y-5,mount_y+1,z-7.1,z+7.1),cut)
        boolean(carrier,box(x-3,x+3,mount_y-5,mount_y+1,-99,-93),cut)
        collar=rounded(x,z,width+3.8,27.8,16.5,roof_y+.1,4.9)
        boolean(collar,rounded(x,z,width+.6,24.6,16,roof_y+1,3.3),cut)
        boolean(lid,collar,union)
        boolean(lid,rounded(x,z,width+.6,24.6,16,roof_y+2,3.3),cut)
        key=rounded(x,z,width,24,16.3,23.3)
        boolean(key,rounded(x,z,width-3.2,20.8,15,21.5,1.4),cut)
        flange=rounded(x,z,width+2.6,26.6,16.4,17.4,4.3)
        boolean(flange,rounded(x,z,width-3.2,20.8,16,18,1.4),cut)
        boolean(key,flange,union)
        boolean(key,box(x-2.7,x+2.7,14.3,21.6,z-1.2,z+1.2),union)
        boolean(key,box(x-1.2,x+1.2,14.3,21.6,z-2.7,z+2.7),union)
        boolean(key,box(x-2.15,x+2.15,13.8,16.6,z-.8,z+.8),cut)
        boolean(key,box(x-.8,x+.8,13.8,16.6,z-2.15,z+2.15),cut)
        if index==0:
            ring=cylinder(x,22.8,z,x,23.5,z,3)
            boolean(ring,cylinder(x,22.7,z,x,23.6,z,2.2),cut)
            boolean(key,ring,cut)
            for angle in range(0,360,45):
                a=math.radians(angle)
                boolean(key,stroke(x+4*math.cos(a),z+4*math.sin(a),x+5.5*math.cos(a),z+5.5*math.sin(a),22.8,23.5,.9),cut)
        elif index==1:
            boolean(key,rounded(x,z,8,8,22.8,23.5,.8),cut)
        else:
            for xx,zz,w,h in [(x-4,z,6,7),(x+4,z+1,4,5)]:
                for x0,z0,x1,z1 in [(xx-w/2,zz-h/2,xx+w/2,zz-h/2),(xx+w/2,zz-h/2,xx-w/2,zz+h/2),(xx-w/2,zz+h/2,xx+w/2,zz+h/2)]:
                    boolean(key,stroke(x0,z0,x1,z1,22.8,23.5,.9),cut)
        shift=adsk.core.Matrix3D.create()
        shift.translation=adsk.core.Vector3D.create(0,-.13,0)
        assert mgr.transform(key,shift)
        # Relieve the real KS33 stem's moulded shoulders, beyond its top cross.
        sw_occ=next(o for o in root.allOccurrences if o.fullPathName.startswith('Reference KS33') and o.bRepBodies.count)
        negative=mgr.copy(sw_occ.bRepBodies.item(0))
        placement=adsk.core.Matrix3D.create()
        placement.setWithCoordinateSystem(point(x-60,mount_y-5.75+.01,-77.5),U,adsk.core.Vector3D.create(0,0,-1),adsk.core.Vector3D.create(0,1,0))
        assert mgr.transform(negative,placement)
        for dx,dy,dz in [(0,0,0),(.06,0,0),(-.06,0,0),(0,.04,0),(0,-.04,0),(0,0,.06),(0,0,-.06)]:
            tool=mgr.copy(negative)
            m=adsk.core.Matrix3D.create()
            m.translation=adsk.core.Vector3D.create(dx/10,dy/10,dz/10)
            assert mgr.transform(tool,m)
            boolean(key,tool,cut)
        keycaps.append((key,'Touche '+label+' - KS33'))
    # Push-in cable combs under the lid, with branches above each switch.
    for xa,xb in [(-49,-35),(-30,-16),(-11,3),(8,22),(28,42)]:
        channel=box(xa,xb,13,19.8,-100,-92)
        boolean(channel,box(xa-1,xb+1,14.4,20,-98.6,-93.4),cut)
        boolean(channel,box(xa-1,xb+1,12,14.5,-97.5,-94.5),cut)
        boolean(lid,channel,union)

    # The roof tongue beside the header belongs to the removable screen bar.
    tongue_region=box(37.1,46.6,26.6,30.2,-29.1,-25.3)
    tongue=mgr.copy(main)
    boolean(tongue,tongue_region,intersect)
    boolean(main,tongue_region,cut)
    boolean(screen_cap,tongue,union)
    joiner=frontbox(37.2,46.5,37,41,-8.1,-7.6)
    boolean(joiner,roofcut(),cut)
    boolean(screen_cap,joiner,union)

    occ=root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    comp=occ.component
    comp.name='Boitier 130 mm - montage electronique'
    base=comp.features.baseFeatures.add()
    base.name='Boitier adapte - ecran ESP32 HP'
    base.startEdit()
    shell=comp.bRepBodies.add(main,base)
    shell.name='Boitier - glissiere ecran et supports'
    cover=comp.bRepBodies.add(lid,base)
    cover.name='Couvercle arriere - 4 vis M3'
    cap=comp.bRepBodies.add(screen_cap,base)
    cap.name='Barrette ecran - 2 vis M3'
    hood=comp.bRepBodies.add(shield,base)
    hood.name='Capot opaque ESP32 - 2 vis M3'
    for key,name in keycaps:
        b=comp.bRepBodies.add(key,base)
        b.name=name
    for shape,name in [(led_cover,'Chambre LED - couvercle arriere M2'),(boot_cap,'Chambre LED - cache fils M2'),(diffuser,'Diffuseur blanc - membrane 0.8 mm'),(liner,'Reflecteur blanc - chambre LED')]:
        b=comp.bRepBodies.add(shape,base)
        b.name=name
    b=comp.bRepBodies.add(carrier,base)
    b.name='Platine KS33 - montage par dessous'
    base.finishEdit()
    names=['Boitier - glissiere ecran et supports','Couvercle superieur - 4 vis M3','Casquette lumineuse et maintien ecran','Capot opaque ESP32 - 2 vis M3','Touche Lumiere - KS33','Touche Stop - KS33','Touche Snooze - KS33','Chambre LED - couvercle arriere M2','Chambre LED - cache fils M2','Diffuseur blanc - membrane 0.8 mm','Reflecteur blanc - chambre LED','Platine KS33 - montage par dessous']
    for i,name in enumerate(names): comp.bRepBodies.item(i).name=name
    for body in source_bodies: body.isLightBulbOn=False

    # Run place_boards.py next to locate flattened STEP reference copies.
    root.isOriginFolderLightBulbOn=False
    for plane in root.constructionPlanes: plane.isLightBulbOn=False
    for sketch in root.sketches: sketch.isLightBulbOn=False
    app.activeViewport.fit()
    print(json.dumps({'width_mm':130,'rear_z_mm':rear,'floor_y_mm':floor,'lid_split_y_mm':split,'esp_origin_mm':[esp_x,esp_y,esp_z],'shell_solids':shell.lumps.count,'lid_solids':cover.lumps.count,'screen_slot_mm':[106.4,54.4,6],'speaker_mm':[70,30,15],'speaker_hole_spacing_mm':[65,25]}))
