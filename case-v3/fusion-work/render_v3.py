"""Prepare a white-background presentation copy of the rebuilt v3 assembly.

Run render_queue.py in a separate invocation after this script completes.
"""
import adsk.core
import adsk.fusion
import sys
import types


def run(_context: str):
    app = adsk.core.Application.get()
    source = next(doc for doc in app.documents if doc.name == 'Clockinator v3 - updated assembly')
    source.activate()
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    enclosure = next(o.component for o in root.occurrences if o.name.startswith('Enclosure v3'))
    manager = adsk.fusion.TemporaryBRepManager.get()
    shapes = [(body.name, manager.copy(body)) for body in enclosure.bRepBodies
              if '(variant)' not in body.name]
    display = next(o.component for o in root.occurrences if o.name.startswith('Display 106'))
    board = max(display.bRepBodies,
                key=lambda body: body.boundingBox.maxPoint.x - body.boundingBox.minPoint.x)
    shapes.append(('Display reference', manager.copy(board)))
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
    dark = appearance('V3 display', (30, 32, 31), .65)
    base = root.features.baseFeatures.add()
    base.startEdit()
    for _, shape in shapes:
        root.bRepBodies.add(shape, base)
    base.finishEdit()
    for index, (name, _) in enumerate(shapes):
        body = root.bRepBodies.item(index)
        body.name = name
        if name == 'Display reference':
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
