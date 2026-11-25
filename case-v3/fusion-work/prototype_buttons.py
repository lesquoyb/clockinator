"""Fusion script: complete three-button guide/stop trial; production exports untouched.

Run in Fusion. Imports the current STEP into a new, unsaved document.
Dimensions in mm; model Y is vertical. See output/prototype-buttons/README.md.
"""
import os
import json
import math
import adsk.core
import adsk.fusion


def run(_context: str):
    app = adsk.core.Application.get()
    output = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'output'))
    destination = os.path.join(output, 'prototype-buttons')
    os.makedirs(destination, exist_ok=True)
    options = app.importManager.createSTEPImportOptions(os.path.join(output, 'clockinator-v3-parts.step'))
    app.importManager.importToNewDocument(options)
    design = adsk.fusion.Design.cast(app.activeProduct)
    root = design.rootComponent
    manager = adsk.fusion.TemporaryBRepManager.get()
    bodies = list(root.bRepBodies)
    assert len(bodies) == 6, 'Expected the six-part v3 STEP'
    union = adsk.fusion.BooleanTypes.UnionBooleanType
    cut = adsk.fusion.BooleanTypes.DifferenceBooleanType
    intersect = adsk.fusion.BooleanTypes.IntersectionBooleanType

    def point(x, y, z):
        return adsk.core.Point3D.create(x / 10, y / 10, z / 10)

    def box(x0, x1, y0, y1, z0, z1):
        return manager.createBox(adsk.core.OrientedBoundingBox3D.create(
            point((x0+x1)/2, (y0+y1)/2, (z0+z1)/2),
            adsk.core.Vector3D.create(1, 0, 0), adsk.core.Vector3D.create(0, 1, 0),
            (x1-x0)/10, (y1-y0)/10, (z1-z0)/10))

    def boolean(a, b, operation):
        assert manager.booleanOperation(a, b, operation), 'Boolean failed'
        return a

    def overlap(a, b):
        shape = boolean(manager.copy(a), manager.copy(b), intersect)
        return shape.volume * 1000 if shape.isSolid else 0

    plate = manager.copy(bodies[5])
    lid = manager.copy(bodies[1])
    keys = []
    for index, (cx, width, label) in enumerate(((-39, 26, 'light'), (-5, 30, 'stop'), (35, 40, 'snooze'))):
        key = manager.copy(bodies[index+2])
        for x in (cx-width/2+4, cx+width/2-4):
            for z in (-101, -78):
                boolean(key, box(x-2, x+2, 12.3, 15.3, z-1, z+1), union)
                boolean(plate, box(x-2.4, x+2.4, 8.7, 9.0, z-1.4, z+1.4), union)
        half = width/2
        frame = box(cx-half-3.6, cx+half+3.6, 8.7, 16.3, -105.1, -73.9)
        boolean(frame, box(cx-half-1.6, cx+half+1.6, 8.6, 16.4, -103.1, -75.9), cut)
        boolean(plate, frame, union)
        for step in range(9):
            for dx, dz in ((0, 0), (-0.1, 0), (0.1, 0), (0, -0.1), (0, 0.1)):
                tool = manager.copy(key)
                transform = adsk.core.Matrix3D.create()
                transform.translation = adsk.core.Vector3D.create(dx/10, -step*0.4/10, dz/10)
                assert manager.transform(tool, transform)
                boolean(lid, tool, cut)
        keys.append(('key-'+label, key))
    # Keep the existing screw columns and heads accessible above the plate.
    for x in (-53, 53):
        for z in (-106.7, -72.3):
            tool = manager.createCylinderOrCone(point(x, 8.82201948051948, z), 0.43,
                                                point(x, 17, z), 0.43)
            boolean(plate, tool, cut)
    parts = keys + [('switch-plate', plate), ('lid', lid)]
    report = {'prototype_only': True, 'nominal_side_clearance_mm': 0.3,
              'stop_travel_mm': 3.3, 'travel_checks': []}
    for name, key in keys:
        for distance in (0, 0.8, 1.6, 2.4, 3.2):
            moved = manager.copy(key)
            transform = adsk.core.Matrix3D.create()
            transform.translation = adsk.core.Vector3D.create(0, -distance/10, 0)
            assert manager.transform(moved, transform)
            others = [plate, lid, bodies[0]] + [k for n,k in keys if n != name]
            values = [overlap(moved, other) for other in others]
            assert max(values) < 0.001, ('Travel collision', name, distance, values)
            report['travel_checks'].append({'key': name, 'travel_mm': distance, 'intersection_mm3': values})
        moved = manager.copy(key)
        transform = adsk.core.Matrix3D.create()
        transform.translation = adsk.core.Vector3D.create(0, -3.5/10, 0)
        assert manager.transform(moved, transform)
        assert overlap(moved, plate) > 1, 'Lower stops must intercept excessive travel'
    assert overlap(plate, lid) < 0.001, 'Plate/lid collision'
    assert overlap(plate, bodies[0]) < 0.001, 'Plate/shell collision'
    # The inherited stem fit stays unchanged: calibrate it independently.
    report['stem_fit'] = 'unchanged; physical calibration required'
    report['corner_press'] = 'physical test required; no force/elastic simulation'
    for body in bodies:
        body.isLightBulbOn = False
    occurrence = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    component = occurrence.component
    component.name = 'Complete button guide prototype'
    base = component.features.baseFeatures.add()
    base.startEdit()
    for name, shape in parts:
        assert shape.isSolid and shape.lumps.count == 1, name
        component.bRepBodies.add(shape, base)
    base.finishEdit()
    for index, (name, shape) in enumerate(parts):
        component.bRepBodies.item(index).name = name
    design.exportManager.execute(design.exportManager.createSTEPExportOptions(
        os.path.join(destination, 'buttons-prototype.step'), component))
    scratch = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    for name, original in parts:
        shape = manager.copy(original)
        transform = adsk.core.Matrix3D.create()
        transform.setToRotation(-math.pi/2 if name.startswith('key-') else math.pi/2,
                                adsk.core.Vector3D.create(1, 0, 0), point(0, 0, 0))
        assert manager.transform(shape, transform)
        bounds = shape.boundingBox
        transform = adsk.core.Matrix3D.create()
        transform.translation = adsk.core.Vector3D.create(
            -(bounds.minPoint.x+bounds.maxPoint.x)/2,
            -(bounds.minPoint.y+bounds.maxPoint.y)/2, -bounds.minPoint.z)
        assert manager.transform(shape, transform)
        export_base = scratch.component.features.baseFeatures.add()
        export_base.startEdit()
        body = scratch.component.bRepBodies.add(shape, export_base)
        export_base.finishEdit()
        options = design.exportManager.createSTLExportOptions(body, os.path.join(destination, name+'.stl'))
        options.sendToPrintUtility = False
        options.meshRefinement = adsk.fusion.MeshRefinementSettings.MeshRefinementHigh
        assert design.exportManager.execute(options), 'STL export failed'
        # Write the calculated mesh synchronously; some Fusion releases defer
        # export jobs until after the temporary export component is deleted.
        calculator = body.meshManager.createMeshCalculator()
        mesh = calculator.calculate()
        nodes, indices = mesh.nodeCoordinates, mesh.nodeIndices
        with open(os.path.join(destination, name+'.stl'), 'w', encoding='ascii') as stream:
            stream.write('solid '+name+'\n')
            for triangle in range(0, len(indices), 3):
                stream.write('facet normal 0 0 0\nouter loop\n')
                for index in indices[triangle:triangle+3]:
                    p = nodes[index]
                    stream.write('vertex %.9f %.9f %.9f\n' % (p.x*10, p.y*10, p.z*10))
                stream.write('endloop\nendfacet\n')
            stream.write('endsolid '+name+'\n')
    scratch.deleteMe()
    with open(os.path.join(destination, 'verification.json'), 'w', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2)
    print(json.dumps(report))

