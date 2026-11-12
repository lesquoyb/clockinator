# v3 exports

## Open light exit

The downward light exit is 52 × 10.6 mm, extended 1.5 mm toward the visor tip to open the path toward the lower part of the display. The LED baffle remains in place, and
closing the former side slot removes the need for a diffuser or plug. The
old diffuser STL files fit earlier lids. Optical performance should be checked
on a physical print.

## Lid fit revision

The lid guide has 0.8 mm clearance at each side and at the rear. Continuous
solid ribs run from the guide to the inner ceiling on both sides and at the rear.
The side ribs leave 0.5 mm clearance to the switch plate, with relief below
the seam around its brackets. The lid continues the 106.4 mm screen pocket from the
shell and adds clearance for the top edge and rear components.

`verification-lid.json` records the lid mesh and fit checks. The existing
renders show the exterior before this lid fit revision.

## Revision after the first shell print

The current shell incorporates the first physical assembly feedback:

- All four ESP32 nuts load from underneath. Their square pockets are 6.6 × 6.6 mm and 3.2 mm deep (increased from 5.8 × 5.8 × 2.6 mm after a tight-fit report). No side-entry nut pocket remains. The four underside-entry posts have sloping bases, 14 mm across at the floor and tapering to the 6.4 mm posts over 6.7 mm.
- Both amplifier posts have sloping bases, 14 mm across at the floor, tapering to the 7.2 mm posts over 6.7 mm.
- Both amplifier nuts use 4.8 × 4.8 mm pockets, widened from 4.4 × 4.4 mm, with the same 1.8 mm depth. They load from underneath, with through-bores instead of enclosed side-entry cavities. Longer M2 screws are required; measure the assembled stack before choosing their length.
- Viewed from the front, the display window's left edge moves 2 mm left and its right edge moves 4 mm left. The complete board pocket and window then shift 2.5 mm right to center the visible area on the facade. Final window X bounds are −40.9 to 40.9 mm (81.8 mm wide); the board center is X = 2.5 mm. The 52 mm light exit remains centered at X = 0, aligned with the visible window.
- The four internal speaker standoffs are removed. The exterior screw-head recesses are reduced to 0.8 mm deep to retain 1.6 mm of rear-wall material. Screw heads may protrude; recheck speaker clearance and screw length with the actual speaker.
- The two rear rubber-foot seats move 5 mm forward (Z = −99 mm instead of −104 mm) to clear the underside ESP32 nuts.
- Switch-plate brackets are unchanged.

**Native archive updated:** `clockinator-v3-assembly.f3d` includes all four
underside ESP32 nut pockets, relocated rear feet and widened M2 nut pockets.
`verification-fusion.json` confirms the shell and switch-plate volumes match
the current exports.

**Renders updated:** the five views in `../renders/` show the current enclosure
with a matte e-paper mock display reading 07:30. This is a presentation graphic,
not a screenshot of the firmware UI. The current shell STL and STEP are checked
by `verification-shell.json`.

`switch-plate.stl` and the switch plate in `clockinator-v3-parts.step` have been
regenerated without the old wire-routing notches. The plate is built directly;
no cable cutouts are created and then filled back in. The three 14.2 mm KS33
openings, four 3.5 mm mounting holes and ribs are retained. The STL is already
oriented for printing and rests at Z = 0.

The shared geometry lives in `../fusion-work/switch_plate.py` and is used by
both the Fusion build and the headless exporter:

```sh
python3 case-v3/fusion-work/export_plate_headless.py
```

Run this command from the repository root. It requires CadQuery (the version
used for validation is recorded in `verification-plate.json`). It replaces
only the switch plate in the STEP file and re-exports its STL, then checks the
volumes and dimensions of all six STEP solids and verifies that the mesh is
watertight. The other STL files remain unchanged. Results are recorded in
`verification-plate.json`.

## Shell and ESP32 mounting

`shell.stl` and the STEP shell are built directly from the outer profile,
without starting from a shell that needs repair. The board and its four mounts
are shifted **1.5 mm inward** compared with the previous export. This leaves
**0.9 mm between the edge of the supplied PCB and the inside face of the rear
wall**. The cutout at the end of the board has been removed: only the
9.6 × 4.9 mm USB-C opening remains in this area. All four supports have complete,
coplanar top faces at Y = −18.15 mm in model coordinates.

```sh
python3 case-v3/fusion-work/export_shell_headless.py
```

The geometry is shared with Fusion through `shell_geometry.py`. The CadQuery
adapter rebuilds the profile and its rounded edges in millimetres; no blocks
fill earlier cutouts. The Fusion generator no longer retains the disabled
fastener loops. The ESP32 reference is aligned to an absolute position, so
repeated builds do not accumulate an offset.

The exporter checks the four support faces and their holes, wall continuity
around the port, the USB-C opening and clearance from all **142 solids in the
supplied ESP32 STEP file**. The reimported STEP is checked again, then the STL
mesh is checked for watertightness, manifold topology and placement at Z = 0.
Results are recorded in `verification-shell.json`. The other STEP parts retain
their dimensions and volumes within re-export tolerance; their STL files are
not regenerated. A comparison at 30,000 sample positions against the previous
export found no differences outside the ESP32 area.

The printed assembly and access with the actual USB cable still need to be
tested; checking the model does not replace a physical test.

## Fusion archive and renders

The native archive includes the latest ESP32 and M2 changes.
`verification-fusion.json` records the verified native export.

To rebuild and export through Fusion's Scripts and Add-ins dialog, add the
`../fusion-work/RefreshNativeV3` folder and run `RefreshNativeV3`. It opens the
existing local archive in a new document, rebuilds it and exports the result.

To update only the native archive after a build, run
`../fusion-work/export_native_v3.py` in Fusion. The script checks the shell and
switch plate before exporting. Keep both geometry modules alongside
`build_v3.py`.

The five views in `../renders/` show the current assembly with an e-paper mock display.
`../fusion-work/render_v3.py` prepares the presentation copy, then
`../fusion-work/render_queue.py` starts the local renders in a separate script
invocation.

To reproduce the e-paper renders through the Fusion dialog, add the
`../fusion-work/RenderEinkV3` folder. Run it once to prepare the scene and
a second time to queue the renders after the geometry is committed.
