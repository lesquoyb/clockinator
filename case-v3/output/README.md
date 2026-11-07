# v3 exports

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
volumes and dimensions of all eight STEP solids and verifies that the mesh is
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

`clockinator-v3-assembly.f3d` has been rebuilt with the current versions of
`build_v3.py`, `shell_geometry.py` and `switch_plate.py`. The shell and switch
plate in the archive match the volumes of the latest exports, as checked in
`verification-fusion.json`.

To update only the native archive after a build, run
`../fusion-work/export_native_v3.py` in Fusion. The script checks the shell and
switch plate before exporting. Keep both geometry modules alongside
`build_v3.py`.

The five views in `../renders/` come from the rebuilt assembly.
`../fusion-work/render_v3.py` prepares the presentation copy, then
`../fusion-work/render_queue.py` starts the local renders in a separate script
invocation. The diffuser variants are hidden in the presentation.
