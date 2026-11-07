"""Queue two local Fusion renders from the latest open L dock, without a phone.

Uses a separate presentation document; the working model is not recoloured.
Run render_queue.py next, then render_status.py to check the background jobs.
"""
import adsk.core, adsk.fusion, sys, types, os


def run(_context: str):
    app=adsk.core.Application.get()
    source=next(d for d in app.documents if d.name=='phone dock - L')
    source.activate()
    design=adsk.fusion.Design.cast(app.activeProduct)
    comp=next(o.component for o in design.rootComponent.occurrences if o.component.name=='Dock - printed parts')
    mgr=adsk.fusion.TemporaryBRepManager.get()
    shapes=[(b.name,mgr.copy(b)) for b in comp.bRepBodies]
    assert len(shapes)==2
    doc=next((doc for doc in app.documents if doc.name=='Phone dock L - white renders'),None)
    if doc:
        doc.activate()
    else:
        doc=app.documents.add(adsk.core.DocumentTypes.FusionDesignDocumentType)
        doc.name='Phone dock L - white renders'
    design=adsk.fusion.Design.cast(app.activeProduct)
    root=design.rootComponent
    for feature in list(root.features.baseFeatures):feature.deleteMe()
    library=next(lib for lib in app.materialLibraries if lib.appearances.itemByName('Plastique - Mat (blanc)'))
    white=design.appearances.itemByName('Dock - white plastic') or design.appearances.addByCopy(library.appearances.itemByName('Plastique - Mat (blanc)'),'Dock - white plastic')
    white.color=adsk.core.Color.create(245,245,242,255)
    white.roughness=.62
    rubber=design.appearances.itemByName('Feet - matte rubber') or design.appearances.addByCopy(library.appearances.itemByName('Plastique - Mat (noir)'),'Feet - matte rubber')
    rubber.color=adsk.core.Color.create(8,9,10,255)
    rubber.roughness=.9
    base=root.features.baseFeatures.add()
    base.startEdit()
    for _,shape in shapes:root.bRepBodies.add(shape,base)
    for x in [-33,33]:
        for z in [-14,146]:
            pad=mgr.createCylinderOrCone(adsk.core.Point3D.create(x/10,-.28,z/10),.4,adsk.core.Point3D.create(x/10,-.08,z/10),.4)
            root.bRepBodies.add(pad,base)
    base.finishEdit()
    for i,b in enumerate(root.bRepBodies):
        b.name=shapes[i][0] if i<2 else 'Rubber foot'
        b.appearance=white if i<2 else rubber
    root.isOriginFolderLightBulbOn=False
    rm=design.renderManager
    assert rm.activateRenderWorkspace()
    scene=rm.sceneSettings
    scene.isGroundDisplayed=False
    scene.backgroundSolidColor=adsk.core.Color.create(255,255,255,255)
    scene.cameraType=adsk.core.CameraTypes.OrthographicCameraType
    scene.cameraExposure=9.5
    scene.brightness=1800
    scene.isDepthOfFieldEnabled=False
    rendering=rm.rendering
    rendering.aspectRatio=adsk.fusion.RenderAspectRatios.CustomRenderAspectRatio
    rendering.resolutionWidth=1600
    rendering.resolutionHeight=1100
    rendering.renderQuality=75
    rendering.isBackgroundTransparent=False
    directory=r'C:\Users\baptiste\Documents\GitHub\clockinator\assets\renders'
    os.makedirs(directory,exist_ok=True)
    state=types.ModuleType('clockinator_dock_renders')
    state.document=doc
    state.jobs=[]
    sys.modules['clockinator_dock_renders']=state
    source.activate()
    print('White-background studio prepared. Run render_queue.py in a separate invocation to frame the committed geometry.')
