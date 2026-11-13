# Printing the enclosure

[Back to the project](../README.md) · [Build guide](build-guide.md) · [Parts list](../case-v3/BOM.md)

The STL files in [`case-v3/output/`](../case-v3/output/) use **millimetres**.
Print at **100% scale**. These are starting settings for prototype trials,
not a validated print profile. Printer tolerances and the actual switches,
display and electronics still need a fit check.

## Parts and orientation

| Part | File | Placement / checks |
| --- | --- | --- |
| Shell | `shell.stl` | Floor on the bed; inspect the rear openings and nut pockets in layer preview |
| Lid | `lid.stl` | Inspect the visor, LED chamber and diffuser slot; choose support placement locally without filling functional slots |
| Switch plate | `switch-plate.stl` | Prefer the broad flat skin on the bed, with the reinforcing ribs facing up; flip the supplied STL in the slicer if needed to avoid bridging the skin between ribs |
| Keys | `key-light.stl`, `key-stop.stl`, `key-snooze.stl` | The export flips the keys onto their top faces; check the recessed symbols and stem cavities in preview |
| Diffuser | `diffuser-0.6mm.stl`, `diffuser-0.8mm.stl`, `diffuser-1.2mm.stl` | Flat on the bed; choose one thickness, not all three for the final assembly |
| Slot plug | `diffuser-plug.stl` | Keep its insertion surfaces free of support scars |

The corrected plate has no wire notches. Use its current STL or STEP;
the [export notes](../case-v3/output/README.md) explain why the existing native
Fusion archive must be rebuilt before re-exporting it.

## Starting settings

| Setting | Suggested first trial |
| --- | --- |
| Nozzle | 0.4 mm |
| Layer height | 0.20 mm for shell/lid; 0.16–0.20 mm for keys and plate |
| Walls | 3 perimeters |
| Infill | 15–20% for shell and lid; inspect thin regions rather than relying on infill alone |
| Diffuser | White filament, solid fill; 0.20 mm layers divide all three supplied thicknesses evenly |
| Supports | Determine from layer preview for each orientation; avoid supports inside switch clips, nut pockets and the diffuser rail |
| Temperature / cooling | Use the filament manufacturer's range and a calibrated printer profile |
| Material | Opaque filament for shell/lid; white for the diffuser. PLA is a reasonable fit-test material; final material choice depends on measured operating temperature |

Do not apply a global scale correction to make one pocket fit: this also
changes screw spacing and the display opening. Calibrate extrusion and
dimensional compensation first, then adjust an individual CAD clearance if
measurements establish that it is necessary.

## Before printing the full enclosure

1. Print the plate and one key. Check the real KS33 switch clips, stem fit and free travel.
2. Try the 0.8 mm diffuser first; compare the thinner/thicker variants for light transmission and stiffness.
3. Inspect the shell/lid layer preview: no unexpected recesses, isolated islands or support material trapped in channels.
4. Check screw/nut pockets with the actual fasteners, and verify the display and board dimensions against the BOM.
5. Record material, printer, slicer version, profile and measured fit. Update this guide when a complete assembly has been tested.
