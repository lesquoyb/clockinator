"""Rebuild the v3 plate and replace it in the STEP export using CadQuery.

Run from any directory with Python + cadquery. The other STEP solids are kept
as-is. This does not update the proprietary Fusion archive.
"""
import argparse
import json
import math
import struct
from collections import Counter
from pathlib import Path

import cadquery as cq

from switch_plate import build_switch_plate


def make_plate():
    def box(x0, x1, y0, y1, z0, z1):
        return [cq.Solid.makeBox(x1 - x0, y1 - y0, z1 - z0,
                                 cq.Vector(x0, y0, z0))]

    def cylinder(x, y, z, xx, yy, zz, radius):
        direction = cq.Vector(xx - x, yy - y, zz - z)
        return [cq.Solid.makeCylinder(radius, direction.Length,
                                      cq.Vector(x, y, z), direction.normalized())]

    def boolean(target, tool, operation):
        target[0] = (target[0].fuse(tool[0]) if operation == 'union'
                     else target[0].cut(tool[0])).clean()

    return build_switch_plate(box, cylinder, boolean, 'union', 'cut')[0].clean()


def verify_plate(plate):
    assert plate.isValid() and len(plate.Solids()) == 1
    mount_y = 11.9907 * 130 / 154 - 1.3
    # The entire rear rib must be solid, including all former wire/corner
    # notches. Samples run along its full depth and height, not just one face.
    for x0, x1 in [(-42, -36), (-8, -2), (32, 38), (-60.4, -54.8), (54.8, 60.4)]:
        rib = cq.Solid.makeBox(x1 - x0, 4.4, 3,
                               cq.Vector(x0, mount_y - 4.4, -110))
        assert rib.cut(plate).Volume() < 1e-6, 'Rear rib has a recess'
    # The top skin behind the switches must also be continuous: the old
    # notches extended beyond the rib into this skin.
    for x in (-39, -5, 35):
        skin = cq.Solid.makeBox(6, 1.2, 6,
                                cq.Vector(x - 3, mount_y - 1.2, -110))
        assert skin.cut(plate).Volume() < 1e-6, 'Top skin has a recess'
        aperture = cq.Solid.makeBox(14.2, 6, 14.2,
                                    cq.Vector(x - 7.1, 3, -96.6))
        assert aperture.intersect(plate).Volume() < 1e-6
    for x in (-53, 53):
        for z in (-106.7, -72.3):
            hole = cq.Solid.makeCylinder(1.75, 7, cq.Vector(x, 3, z), cq.Vector(0, 1, 0))
            assert hole.intersect(plate).Volume() < 1e-6
    return {'valid': True, 'solids': 1, 'volume_mm3': plate.Volume(),
            'rear_rib_and_skin': 'continuous', 'switch_apertures_mm': [14.2, 14.2],
            'mounting_holes_diameter_mm': 3.5}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path,
                        default=Path(__file__).resolve().parents[1] / 'output')
    args = parser.parse_args()
    output = args.output_dir
    step_path = output / 'clockinator-v3-parts.step'
    solids = cq.importers.importStep(str(step_path)).solids().vals()
    # Select by dimensions rather than relying on STEP entity ordering.
    candidates = [i for i, s in enumerate(solids)
                  if abs(s.BoundingBox().xlen - 120.8) < .001
                  and abs(s.BoundingBox().zlen - 41) < .05
                  and s.BoundingBox().ylen < 5]
    assert len(candidates) == 1, 'Expected exactly one switch plate'
    plate_index = candidates[0]
    plate = make_plate()
    report = verify_plate(plate)
    solids[plate_index] = plate
    assembly = cq.Assembly(name='Enclosure v3 - smooth')
    names = ['Shell', 'Lid', 'Key Light - KS33', 'Key Stop - KS33',
             'Key Snooze - KS33', 'Switch plate - KS33']
    assert len(solids) == len(names) and plate_index == 5, 'Unexpected source assembly'
    for solid, name in zip(solids, names):
        assembly.add(solid, name=name)
    assembly.export(str(step_path))
    step_path.write_text('\n'.join(line.rstrip() for line in step_path.read_text().splitlines()) + '\n')
    # Same print orientation and centring as export_v3.py, in millimetres.
    printable = plate.rotate((0, 0, 0), (1, 0, 0), 90)
    bb = printable.BoundingBox()
    printable = printable.translate((-(bb.xmin + bb.xmax) / 2,
                                     -(bb.ymin + bb.ymax) / 2, -bb.zmin))
    cq.exporters.export(printable, str(output / 'switch-plate.stl'),
                        tolerance=.01, angularTolerance=math.radians(5))
    # Verify the exported STEP, including all retained parts.
    restored = cq.importers.importStep(str(step_path)).solids().vals()
    assert len(restored) == len(solids)
    for original, exported in zip(solids, restored):
        assert original.isValid() and exported.isValid()
        # STEP round-tripping spline surfaces introduces tiny integration
        # differences (under .003 mm3 on the keys). Coincident-surface boolean
        # subtraction is unreliable for those source surfaces in OCCT.
        assert abs(original.Volume() - exported.Volume()) < .01
        a, b = original.BoundingBox(), exported.BoundingBox()
        for key in ('xmin', 'xmax', 'ymin', 'ymax', 'zmin', 'zmax'):
            assert abs(getattr(a, key) - getattr(b, key)) < 1e-5
    verify_plate(restored[plate_index])
    data = (output / 'switch-plate.stl').read_bytes()
    triangles = struct.unpack_from('<I', data, 80)[0]
    assert len(data) == 84 + triangles * 50
    edges = Counter()
    mesh_volume = 0.0
    min_z = float('inf')
    for i in range(triangles):
        values = struct.unpack_from('<12fH', data, 84 + i * 50)
        vertices = [tuple(round(v, 5) for v in values[k:k + 3]) for k in (3, 6, 9)]
        p, q, r = vertices
        assert len(set(vertices)) == 3
        for j in range(3):
            edges[tuple(sorted((vertices[j], vertices[(j + 1) % 3])))] += 1
        mesh_volume += cq.Vector(*p).dot(cq.Vector(*q).cross(cq.Vector(*r))) / 6
        min_z = min(min_z, *(v[2] for v in vertices))
    assert all(count == 2 for count in edges.values()), 'STL is not watertight'
    assert abs(mesh_volume - plate.Volume()) < .1 and abs(min_z) < 1e-6
    report.update(stl_triangles=triangles, stl_watertight=True,
                  stl_volume_mm3=mesh_volume, print_min_z_mm=min_z)
    report['step_solids'] = len(restored)
    report['cadquery_version'] = cq.__version__
    (output / 'verification-plate.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
