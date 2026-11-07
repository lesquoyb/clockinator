"""Frame committed studio geometry and queue the two local renders."""
import adsk.core, adsk.fusion, sys, os


def run(_context: str):
    app=adsk.core.Application.get()
    source=next(doc for doc in app.documents if doc.name=='phone dock - L')
    state=sys.modules['clockinator_dock_renders']
    state.document.activate()
    design=adsk.fusion.Design.cast(app.activeProduct)
    assert design.rootComponent.bRepBodies.count==6
    rendering=design.renderManager.rendering
    directory=r'C:\Users\baptiste\Documents\GitHub\clockinator\assets\renders'
    state.jobs=[]
    for name,eye in [('dock-L-white-front',(17,24,26)),('dock-L-white-rear',(24,20,-18))]:
        camera=app.activeViewport.camera
        camera.cameraType=adsk.core.CameraTypes.OrthographicCameraType
        camera.eye=adsk.core.Point3D.create(*eye)
        camera.target=adsk.core.Point3D.create(0,.6,6.6)
        camera.upVector=adsk.core.Vector3D.create(0,1,0)
        camera.isSmoothTransition=False
        app.activeViewport.camera=camera
        # Explicit 150 mm viewing radius: fit() depends on the live UI viewport
        # aspect ratio and can leave the product tiny in a landscape render.
        camera=app.activeViewport.camera
        camera.viewExtents=15
        app.activeViewport.camera=camera
        path=os.path.join(directory,name+'.png')
        # Fusion resets job options when the document/workspace is activated.
        # Set them after camera framing, immediately before starting each job.
        rendering.aspectRatio=adsk.fusion.RenderAspectRatios.CustomRenderAspectRatio
        rendering.resolutionWidth=1600
        rendering.resolutionHeight=1100
        rendering.renderQuality=75
        rendering.isBackgroundTransparent=False
        assert rendering.resolutionWidth==1600 and rendering.resolutionHeight==1100
        future=rendering.startLocalRender(path,camera)
        assert future
        state.jobs.append((name,path,future))
        print(name,'camera extent cm',camera.viewExtents)
    source.activate()
    print('Queued two tightly framed white-background renders; source model restored')
