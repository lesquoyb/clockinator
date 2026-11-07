"""Queue the five v3 views after render_v3.py has committed its geometry."""
import adsk.core
import adsk.fusion
import os
import sys


def run(_context: str):
    app = adsk.core.Application.get()
    state = sys.modules['clockinator_v3_renders']
    state.document.activate()
    design = adsk.fusion.Design.cast(app.activeProduct)
    rendering = design.renderManager.rendering
    directory = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'renders'))
    os.makedirs(directory, exist_ok=True)
    views = [('front-right', (18, 16, 20), (0, 1, 0)),
             ('front-left', (-18, 16, 20), (0, 1, 0)),
             ('rear', (18, 16, -28), (0, 1, 0)),
             ('side', (26, 6, -5), (0, 1, 0)),
             ('top', (0, 32, -5), (0, 0, 1))]
    state.jobs = []
    for name, eye, up in views:
        camera = app.activeViewport.camera
        camera.cameraType = adsk.core.CameraTypes.OrthographicCameraType
        camera.eye = adsk.core.Point3D.create(*eye)
        camera.target = adsk.core.Point3D.create(0, -.4, -4.8)
        camera.upVector = adsk.core.Vector3D.create(*up)
        camera.isSmoothTransition = False
        app.activeViewport.camera = camera
        camera = app.activeViewport.camera
        camera.viewExtents = 12.5
        app.activeViewport.camera = camera
        rendering.aspectRatio = adsk.fusion.RenderAspectRatios.CustomRenderAspectRatio
        rendering.resolutionWidth = 1600
        rendering.resolutionHeight = 1100
        rendering.renderQuality = 75
        rendering.isBackgroundTransparent = False
        path = os.path.join(directory, name + '.png')
        future = rendering.startLocalRender(path, camera)
        assert future
        state.jobs.append((name, path, future))
    state.source.activate()
    print('Queued five 1600 x 1100 local v3 renders')
