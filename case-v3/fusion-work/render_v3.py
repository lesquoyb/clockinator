"""Prepare a white-background presentation copy of the rebuilt v3 assembly.

Run render_queue.py in a separate invocation after this script completes.
"""
import adsk.core
import adsk.fusion
import sys
import types


def run(_context: str):
    app = adsk.core.Application.get()
    source = list(doc for doc in app.documents if doc.name.startswith('Clockinator v3 - updated assembly'))[-1]
    source.activate()
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    enclosure = next(o.component for o in root.occurrences if o.name.startswith('Enclosure v3'))
    manager = adsk.fusion.TemporaryBRepManager.get()
    shapes = [(body.name, manager.copy(body)) for body in enclosure.bRepBodies
              if '(variant)' not in body.name]
    # E-paper mock display on the actual inclined screen plane, in millimetres.
    import math
    def ink_box(name, x0, x1, v0, v1, n0, n1):
        v, n = (v0 + v1) / 2, (n0 + n1) / 2
        shape = manager.createBox(adsk.core.OrientedBoundingBox3D.create(
            adsk.core.Point3D.create((x0+x1)/20, (math.sqrt(3)/2*v+.5*n)/10, (-.5*v+math.sqrt(3)/2*n)/10),
            adsk.core.Vector3D.create(1,0,0), adsk.core.Vector3D.create(0,math.sqrt(3)/2,-.5),
            (x1-x0)/10, (v1-v0)/10, (n1-n0)/10))
        shapes.append((name, shape))
    ink_box('E-paper background', -40.8,40.8,-24.3,24.7,-1.6,-1.35)
    segments = {'0':'ab cdef'.replace(' ',''), '7':'abc', '3':'abcdg'}
    rects = {'a':(1,11,20.5,22), 'b':(10.5,12,11,21), 'c':(10.5,12,1,11),
             'd':(1,11,0,1.5), 'e':(0,1.5,1,11), 'f':(0,1.5,11,21), 'g':(1,11,10.3,11.8)}
    for digit,x in zip('0730',[-30,-15,4,19]):
        for segment in segments[digit]:
            x0,x1,y0,y1=rects[segment]
            ink_box('E-paper ink',x+x0,x+x1,y0-8,y1-8,-1.34,-1.30)
    for y in (0,7):
        ink_box('E-paper ink',-.7,1.3,y,y+2,-1.34,-1.30)
    # Small status marks preserve a quiet monochrome layout.
    for x,width in [(-27,14),(-9,7),(2,10),(16,11)]:
        ink_box('E-paper ink',x,x+width,-17,-16,-1.34,-1.30)
    fasteners = next(o.component for o in root.occurrences if o.name.startswith('M3 fasteners v3'))
    shapes.extend(('M3 screw', manager.copy(body)) for body in fasteners.bRepBodies)

    studio = next((doc for doc in app.documents if doc.name == 'Clockinator v3 - renders'), None)
    if studio:
        studio.activate()
    else:
        studio = app.documents.add(adsk.core.DocumentTypes.FusionDesignDocumentType)
        studio.name = 'Clockinator v3 - renders'
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    for feature in list(root.features.baseFeatures):
        feature.deleteMe()
    library = next(lib for lib in app.materialLibraries
                   if lib.appearances.itemByName('Plastique - Mat (blanc)'))

    def appearance(name, colour, roughness):
        material = design.appearances.itemByName(name)
        if not material:
            material = design.appearances.addByCopy(
                library.appearances.itemByName('Plastique - Mat (blanc)'), name)
        material.color = adsk.core.Color.create(*colour, 255)
        material.roughness = roughness
        return material

    white = appearance('V3 white plastic', (245, 245, 242), .55)
    grey = appearance('V3 grey controls', (145, 148, 149), .5)
    dark = appearance('V3 e-paper ink', (32, 34, 32), .95)
    paper = appearance('V3 e-paper substrate', (204, 205, 191), .98)
    base = root.features.baseFeatures.add()
    base.startEdit()
    for _, shape in shapes:
        root.bRepBodies.add(shape, base)
    base.finishEdit()
    for index, (name, _) in enumerate(shapes):
        body = root.bRepBodies.item(index)
        body.name = name
        if name == 'E-paper background':
            body.appearance = paper
        elif name == 'E-paper ink':
            body.appearance = dark
        elif name.startswith('Key ') or name == 'M3 screw':
            body.appearance = grey
        else:
            body.appearance = white
    root.isOriginFolderLightBulbOn = False
    render = design.renderManager
    assert render.activateRenderWorkspace()
    scene = render.sceneSettings
    scene.isGroundDisplayed = False
    scene.backgroundSolidColor = adsk.core.Color.create(255, 255, 255, 255)
    scene.cameraType = adsk.core.CameraTypes.OrthographicCameraType
    scene.cameraExposure = 9.5
    scene.brightness = 1800
    scene.isDepthOfFieldEnabled = False
    state = types.ModuleType('clockinator_v3_renders')
    state.document = studio
    state.source = source
    state.jobs = []
    sys.modules['clockinator_v3_renders'] = state
    source.activate()
    print('V3 presentation copy ready. Run render_queue.py next.')
