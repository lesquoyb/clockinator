"""CadQuery primitives matching the Fusion temporary-BRep backend, in mm."""
import math
import cadquery as cq
from OCP.BRepFilletAPI import BRepFilletAPI_MakeFillet
import shell_geometry as dimensions


class Geometry:
    union, cut, intersect = 'union', 'cut', 'intersect'

    @staticmethod
    def box(x0, x1, y0, y1, z0, z1):
        return [cq.Solid.makeBox(x1 - x0, y1 - y0, z1 - z0, cq.Vector(x0, y0, z0))]

    @staticmethod
    def frontbox(x0, x1, v0, v1, n0, n1):
        return [Geometry.box(x0, x1, v0, v1, n0, n1)[0].rotate((0, 0, 0), (1, 0, 0), -30)]

    @staticmethod
    def cylinder(x, y, z, xx, yy, zz, radius):
        direction = cq.Vector(xx - x, yy - y, zz - z)
        return [cq.Solid.makeCylinder(radius, direction.Length, cq.Vector(x, y, z), direction.normalized())]

    @staticmethod
    def copy(body):
        return [body[0].copy()]

    @staticmethod
    def cone(x, y, z, xx, yy, zz, radius0, radius1):
        direction = cq.Vector(xx - x, yy - y, zz - z)
        return [cq.Solid.makeCone(radius0, radius1, direction.Length,
                                 cq.Vector(x, y, z), direction.normalized())]

    @staticmethod
    def boolean(target, tool, operation):
        methods = {'union': 'fuse', 'cut': 'cut', 'intersect': 'intersect'}
        target[0] = getattr(target[0], methods[operation])(tool[0]).clean()
        assert target[0].isValid(), f'Invalid {operation}'
        return target


def make_outer():
    """Same side profile and two fillet passes as build_v3.py, from scratch."""
    rear, bottom = dimensions.REAR, dimensions.BOTTOM
    front, roof, top, visor, visor_bottom = 17.8, 22.0, 36.0, -3.37, 27.0
    ramp_start = -60.0
    ramp_end = ramp_start + (top - roof) / math.tan(math.radians(30))
    chin = -front / (.5 / (math.sqrt(3) / 2))
    underside = -.5 * visor_bottom / (math.sqrt(3) / 2)
    profile = [(rear, bottom), (front, bottom), (front, chin), (underside, visor_bottom),
               (visor, visor_bottom), (visor, top), (ramp_end, top), (ramp_start, roof), (rear, roof)]
    body = cq.Workplane('YZ').polyline([(y, z) for z, y in profile]).close().extrude(130).translate((-65, 0, 0)).val()
    radii = {(rear, bottom): 6, (front, bottom): 10, (front, chin): 5,
             (visor, top): 4, (visor, visor_bottom): 2.5,
             (ramp_end, top): 40, (ramp_start, roof): 40, (rear, roof): 5}
    fillet = BRepFilletAPI_MakeFillet(body.wrapped)
    for (z, y), radius in radii.items():
        edges = [e for e in body.Edges() if e.geomType() == 'LINE' and abs(e.Length() - 130) < 1e-5
                 and abs(e.Center().y - y) < 1e-5 and abs(e.Center().z - z) < 1e-5]
        assert len(edges) == 1
        fillet.Add(radius, edges[0].wrapped)
    fillet.Build()
    assert fillet.IsDone()
    body = cq.Shape.cast(fillet.Shape())
    edges = [e for e in body.Edges() if abs(e.Center().x) > 64.9
             and all(abs(abs(v.X) - 65) < 1e-5 for v in e.Vertices())]
    fillet = BRepFilletAPI_MakeFillet(body.wrapped)
    for edge in edges:
        fillet.Add(dimensions.SIDE_FILLET, edge.wrapped)
    fillet.Build()
    assert fillet.IsDone()
    return [cq.Shape.cast(fillet.Shape())]
