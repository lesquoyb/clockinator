"""Refresh the native archive from the latest build and check shared geometry."""
import adsk.core
import adsk.fusion
import json
import os


def run(_context: str):
    app = adsk.core.Application.get()
    design = adsk.fusion.Design.cast(app.activeProduct)
    component = next(o.component for o in design.rootComponent.occurrences
                     if o.name.startswith('Enclosure v3'))
    directory = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'output'))
    checks = {}
    for name, report in [('Shell', 'verification-shell.json'),
                         ('Switch plate - KS33', 'verification-plate.json')]:
        with open(os.path.join(directory, report), encoding='utf-8') as handle:
            expected = json.load(handle)['volume_mm3']
        body = component.bRepBodies.itemByName(name)
        volume = body.volume * 1000
        assert abs(volume - expected) < .2, (name, volume, expected)
        checks[name] = {'volume_mm3': volume, 'matches_current_geometry': True, 'volume_tolerance_mm3': .2, 'volume_difference_mm3': volume - expected}
    diffuser = component.bRepBodies.itemByName('Diffuser white - 0.8 mm').boundingBox
    diffuser_center = (diffuser.minPoint.x + diffuser.maxPoint.x) * 5
    assert abs(diffuser_center) < .001, 'Diffuser is not centered on the facade'
    bodies = []
    for body in component.bRepBodies:
        assert body.isSolid and body.lumps.count == 1, body.name
        bodies.append({'name': body.name, 'volume_mm3': body.volume * 1000})
        if '(variant)' in body.name:
            body.isLightBulbOn = False
    path = os.path.join(directory, 'clockinator-v3-assembly.f3d')
    assert design.exportManager.execute(
        design.exportManager.createFusionArchiveExportOptions(path))
    result = {'parts': checks, 'native_body_count': len(bodies),
              'native_parts': bodies, 'archive_updated': True, 'diffuser_center_x_mm': diffuser_center}
    with open(os.path.join(directory, 'verification-fusion.json'), 'w', encoding='utf-8') as handle:
        json.dump(result, handle, indent=2)
    print(json.dumps(result))
