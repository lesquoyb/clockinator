"""Build the shell from primitives, validate, then atomically replace its exports.

Usage: python3 case-v3/fusion-work/export_shell_headless.py [--output-dir DIR]
Requires CadQuery 2.7.0. The native Fusion archive is not modified.
"""
import argparse
from collections import Counter
import json
import math
from pathlib import Path
import struct
import tempfile

import cadquery as cq
from cad_backend import Geometry, make_outer
import shell_geometry as design

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'case-v3' / 'output'


def make_shell():
    outer = make_outer()
    shell, _ = design.split_enclosure(outer, Geometry)
    design.add_shell_features(shell, outer, Geometry)
    return shell[0]


def verify_shell(shell):
    assert shell.isValid() and len(shell.Solids()) == 1
    top = design.ESP_SUPPORT_TOP
    rear = design.REAR
    centers = [(design.ESP_X + dx, rear + design.ESP_CENTER_FROM_REAR + dz)
               for dx in (-11.6, 11.6) for dz in (-23.5, 23.5)]
    for x, z in centers:
        # Test the full annulus, not just a point on the inward side.
        ring = cq.Solid.makeCylinder(3.15, .2, cq.Vector(x, top - .2, z), cq.Vector(0, 1, 0))
        bore = cq.Solid.makeCylinder(1.75, .4, cq.Vector(x, top - .3, z), cq.Vector(0, 1, 0))
        assert ring.cut(bore).cut(shell).Volume() < 1e-6, 'Incomplete ESP32 support top'
        hole = cq.Solid.makeCylinder(1.7, 10, cq.Vector(x, top - 9, z), cq.Vector(0, 1, 0))
        assert hole.intersect(shell).Volume() < 1e-6
    # A continuous wall below and beside USB-C, with no rectangular PCB relief.
    under_usb = Geometry.box(design.ESP_X - 14.5, design.ESP_X + 14.5,
                             design.ESP_Y - 1.85, design.ESP_Y - 1.5,
                             rear + 1.5, rear + 2.4)[0]
    assert under_usb.cut(shell).Volume() < 1e-6, 'Recess below USB-C'
    for x0, x1 in ((-14.5, -5), (5, 14.5)):
        flank = Geometry.box(design.ESP_X + x0, design.ESP_X + x1,
                              design.ESP_Y - 1.4, design.ESP_Y + .3,
                              rear + 1.5, rear + 2.4)[0]
        assert flank.cut(shell).Volume() < 1e-6, 'Recess beside USB-C'
    port = Geometry.box(design.ESP_X - 4.8, design.ESP_X + 4.8,
                         design.ESP_Y - 1.44, design.ESP_Y + 3.46,
                         rear - .1, rear + 2.5)[0]
    assert port.intersect(shell).Volume() < 1e-6, 'USB-C opening obstructed'
    board_parts = cq.importers.importStep(str(ROOT / 'case-v2' / 'esp32_Wroom_30pins_C-Type.STEP')).solids().vals()
    collisions = []
    for index, part in enumerate(board_parts):
        # Source X -> -X, source Y -> model Z, source Z -> model Y.
        placed = part.rotate((0, 0, 0), (0, 1, 1), 180).translate(
            (design.ESP_X, design.ESP_Y, rear + design.ESP_CENTER_FROM_REAR))
        volume = shell.intersect(placed).Volume()
        if volume > .001:
            collisions.append((index, volume))
    assert not collisions, collisions
    return dict(valid=True, solids=1, volume_mm3=shell.Volume(),
                esp32_support_top_mm=top, support_tops='four complete coplanar annuli',
                pcb_rear_clearance_mm=design.ESP_CENTER_FROM_REAR - 26 - design.WALL,
                usb_opening_mm=[9.6, 4.9], pcb_relief_present=False,
                esp32_reference_solids_checked=len(board_parts), esp32_collisions=collisions,
                cadquery_version=cq.__version__)


def verify_mesh(path, expected_volume):
    data = path.read_bytes()
    triangles = struct.unpack_from('<I', data, 80)[0]
    assert len(data) == 84 + 50 * triangles
    edges, volume, min_z = Counter(), 0.0, float('inf')
    for i in range(triangles):
        values = struct.unpack_from('<12fH', data, 84 + i * 50)
        vertices = [tuple(round(v, 5) for v in values[k:k + 3]) for k in (3, 6, 9)]
        assert len(set(vertices)) == 3
        for j in range(3):
            edges[tuple(sorted((vertices[j], vertices[(j + 1) % 3])))] += 1
        a, b, c = [cq.Vector(*v) for v in vertices]
        volume += a.dot(b.cross(c)) / 6
        min_z = min(min_z, *(v[2] for v in vertices))
    assert all(n == 2 for n in edges.values()), 'Non-manifold STL'
    assert abs(min_z) < 1e-5
    assert abs(volume - expected_volume) < expected_volume * 1e-4
    return dict(stl_triangles=triangles, stl_watertight=True,
                stl_volume_mm3=volume, print_min_z_mm=min_z)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path, default=OUTPUT)
    args = parser.parse_args()
    shell = make_shell()
    report = verify_shell(shell)
    source = cq.importers.importStep(str(OUTPUT / 'clockinator-v3-parts.step')).solids().vals()
    assert len(source) == 8 and source[0].BoundingBox().ylen > 60
    source[0] = shell
    names = ['Shell', 'Lid', 'Key Light - KS33', 'Key Stop - KS33',
             'Key Snooze - KS33', 'Switch plate - KS33', 'Diffuser slot plug',
             'Diffuser white - 0.8 mm']
    args.output_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output_dir) as temporary:
        temporary = Path(temporary)
        assembly = cq.Assembly(name='Enclosure v3 - smooth')
        for solid, name in zip(source, names):
            assembly.add(solid, name=name)
        step = temporary / 'clockinator-v3-parts.step'
        assembly.export(str(step))
        step.write_text('\n'.join(line.rstrip() for line in step.read_text().splitlines()) + '\n')
        restored = cq.importers.importStep(str(step)).solids().vals()
        assert len(restored) == 8
        for before, after in zip(source, restored):
            assert after.isValid() and abs(before.Volume() - after.Volume()) < .01
            a, b = before.BoundingBox(), after.BoundingBox()
            for key in ('xmin', 'xmax', 'ymin', 'ymax', 'zmin', 'zmax'):
                assert abs(getattr(a, key) - getattr(b, key)) < 1e-5
        # Regression checks also cover the shape read back from the STEP file.
        verify_shell(restored[0])
        printable = shell.rotate((0, 0, 0), (1, 0, 0), 90)
        bb = printable.BoundingBox()
        printable = printable.translate((-(bb.xmin + bb.xmax) / 2,
                                          -(bb.ymin + bb.ymax) / 2, -bb.zmin))
        stl = temporary / 'shell.stl'
        cq.exporters.export(printable, str(stl), tolerance=.01, angularTolerance=math.radians(5))
        report.update(verify_mesh(stl, shell.Volume()))
        report['step_solids'] = 8
        (temporary / 'verification-shell.json').write_text(json.dumps(report, indent=2) + '\n')
        for filename in ('clockinator-v3-parts.step', 'shell.stl', 'verification-shell.json'):
            (temporary / filename).replace(args.output_dir / filename)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
