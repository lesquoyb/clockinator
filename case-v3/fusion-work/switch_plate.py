"""Switch plate geometry in model coordinates (mm), without legacy notches.

The primitives are supplied by the CAD backend so Fusion and the headless
exporter construct exactly the same solid, rather than repairing an old plate.
"""


def build_switch_plate(box, cylinder, boolean, union, cut):
    mount_y = 11.9907 * (130 / 154) - 1.3
    dz = -12.0
    plate = box(-60.4, 60.4, mount_y - 1.2, mount_y, -98 + dz, -57 + dz)
    for z0, z1 in [(-98, -95), (-60, -57)]:
        boolean(plate, box(-60.4, 60.4, mount_y - 4.4, mount_y - 1.1,
                           z0 + dz, z1 + dz), union)
    for x0, x1 in [(-23, -21), (12, 14)]:
        boolean(plate, box(x0, x1, mount_y - 4.4, mount_y - 1.1,
                           -95 + dz, -60 + dz), union)
    for x in (-39, -5, 35):
        z = -77.5 + dz
        boolean(plate, box(x - 7.1, x + 7.1, mount_y - 5, mount_y + 1,
                           z - 7.1, z + 7.1), cut)
    for x in (-53, 53):
        for z in (-94.7 + dz, -60.3 + dz):
            boolean(plate, cylinder(x, 3, z, x, 10, z, 1.75), cut)
    return plate
