# Phone dock (Qi coil + NFC tag) - prototype v1

Fusion document: "phone dock" (not saved yet). Script: `fusion-work/build_dock.py`. STL: `output/dock-body.stl`, `output/back-cover.stl`.

## Capacitor

No need to desolder it. The PCB sits in a tray on the floor and the capacitor stands up in the open cavity (the dock is about 140 mm tall, so there is plenty of room). Desolder only if your capacitor is taller than ~35 mm.

## Assembly

1. Print the body face-down (phone face on the bed) and the cover flat.
2. Slide the 4 M3 nuts into the side slots from inside the body.
3. Stick the coil and the NFC tag with tape against the inside of the thin 1.2 mm skin, positioned for your phone (the model has no seat for them, only reference positions).
4. Drop the PCB in its tray, micro-USB towards the rear opening in the cover; route coil wires to the PCB.
5. Close with the cover and 4 M3 x 6 screws.

## Assumptions to check (sizes are guesses)

- Coil 50 mm diameter, about 3 mm thick; tag 25 mm, 1 mm thick; PCB 41 x 14 x 2 mm; capacitor 10 mm diameter.
- Phone 11 x 75 x 155 mm, leaning at 70 degrees from horizontal on a 12.7 mm deep ledge with a front lip 8 mm high; no side guides.
- Circuit board: held down with tape or a small dab of neutral silicone (no mechanical retention).
- Interference check against the reference bodies: none. No test print.

## Bill of materials

- 4 x M3 x 6 screws, 4 x M3 nuts, Qi coil (50 mm), Qi driver PCB with micro-USB, NFC tag (25 mm), micro-USB cable.

## Smooth version (inclined)

`fusion-work/build_dock_smooth.py` rounds the inclined dock like the clockinator v3. STL: `output/dock-body-smooth.stl`, `output/back-cover-smooth.stl`. The cover screws were moved inwards (x = +-34 mm) so no hole breaks the top rounding.

## L version (horizontal) - prototype

`fusion-work/build_dock_L.py`, Fusion document "phone dock - L". STL: `output/dock-L-body.stl`, `output/dock-L-bottom-cover.stl`.

- The phone lies flat on a 12 mm platform (172 mm long, 90 mm wide); its bottom edge touches the upright block (30 mm high) that holds the Qi driver PCB and the tall capacitor.
- The coil (reference at z = 85 mm) and NFC tag (z = 150 mm) are taped under the 1.2 mm top skin; move them to suit your phone.
- Everything is loaded from below. The bottom cover carries the PCB tray, so the PCB is mounted on the cover and slid in with it. The micro-USB port faces the right side wall: 2.4 mm wall, plug body stays outside.
- 6 x M3 x 6 screws and 6 M3 nuts (nuts slide in from the cavity). Print the body top-down, the cover flat.
- Interference check against phone, coil, tag, PCB and capacitor: none. No test print.
