# Clockinator v3 - Bill of materials (BOM)

What to buy and what to print for the smooth enclosure. No prices. Screw lengths are computed from the model: confirm them against the real parts.

## Electronics

| Qty | Part | Notes |
| ---: | --- | --- |
| 1 | ESP32 WROOM 30-pin board, USB-C | Mounting holes assumed M3: measure |
| 1 | WeAct 3.7" e-paper display (GDEY037T03), 106 x 54 mm | |
| 1 | MAX98357A I2S amplifier, 18 x 19 mm board | 2 mounting holes, M2 |
| 1 | Speaker 70 x 30 x 15 mm, 4 M3 holes | Check impedance and power against the amplifier |
| 3 | Gateron KS-33 low-profile switch | Light, stop, snooze |
| 1 | 5 mm RGB LED, 4 legs | Diffused preferred; common anode or cathode as set by `COMMON_ANODE` in the firmware |
| 3 | 330 ohm resistor | One per LED colour (GPIO 13, 12, 14) |
| 1 | LM393 light sensor module (photoresistor) | Used by `lighting_test`; **no place for it in the enclosure yet** |
| - | Thin flexible wire, heat-shrink tubing, solder | |
| 1 | USB-C cable | Powers everything |

## Fasteners

| Qty | Part | Use |
| ---: | --- | --- |
| 4 | M3 x 25 screw | Lid + switch plate + shell (one screw per point) |
| 4 | M3 nut | Under the switch-plate brackets, slid in from the side |
| 4 | ESP32 screws: length pending correction | Current +2 mm supports do NOT accommodate M3 x 30 without protrusion for a 1.6 mm PCB. Maximum flush length is 26.43 mm + actual PCB thickness; further support height adjustment is needed for 30 mm screws |
| 4 | M3 nut | ESP32: all 4 inserted from underneath |
| 4 | M3 x 8 screw (provisional) | Speaker: enters from outside the rear wall, through the speaker's plastic tab; recheck length with the standoffs removed |
| 4 | M3 nut | Speaker, on the far side of the tab, inside the speaker's own space |
| 2 | M2 x 25 screw (candidate; measure PCB/nut) | MAX98357A: maximum flush length is 24.43 mm + actual PCB thickness. With a 1 mm PCB, M2 x 26 protrudes 0.57 mm; M2 x 25 is recessed 0.43 mm. Confirm sufficient nut engagement |
| 2 | M2 nut | MAX98357A, inserted from underneath; actual thickness must not exceed the 1.8 mm pocket depth |

Total: **12 M3 nuts** (4 switch plate + 4 ESP32 + 4 speaker) and **2 M2 nuts**. Keep a few spare screws and nuts. Use cylindrical (ISO 4762) or pan heads; M3 heads at most 5.5 mm across.

ESP32 length calculation: the 23.23 mm dimension ends at the inner ceiling of
the underside nut pocket, not at the enclosure underside. The pocket is another
3.2 mm deep. Add the actual PCB thickness to obtain the under-head length that
reaches the outside bottom plane: 26.43 mm + PCB thickness. Before the 2 mm
raise this was 24.43 mm + PCB thickness. A screw can engage the nut before it
reaches that plane; full thread engagement depends on the actual nut thickness
and position. The model's 1.65 mm board placement offset is not a measured PCB
thickness. Washers, if used, add to the required screw length.

Neither nuts nor screw tips may project below the enclosure. Previous
recommendations of ESP32 M3 x 30 with only a 2 mm raise and amplifier M2 x 26
did not satisfy this constraint. Measure PCB and nut thickness before fixing
the final heights. Nuts must fit entirely within their pockets (ESP32: 3.2 mm;
amplifier: 1.8 mm). This correction changes documentation only, not geometry.

## Other

| Qty | Part | Notes |
| ---: | --- | --- |
| 4 | Round adhesive feet, 12 mm diameter, 1.2 mm thick at most | Recesses are 12.8 mm wide, under the enclosure |
| - | Cyanoacrylate or plastic glue | LED, if needed |

## Printed parts

| Qty | Part | File | Filament |
| ---: | --- | --- | --- |
| 1 | Shell | `shell.stl` | Opaque |
| 1 | Lid | `lid.stl` | Opaque (the LED shines inside it) |
| 3 | Keys | `key-light.stl`, `key-stop.stl`, `key-snooze.stl` | Your choice |
| 1 | Switch plate | `switch-plate.stl` | Your choice |

STL files are in `case-v3/output`.

## Tools

Soldering iron, screwdriver (Phillips or hex, depending on the head), a long screwdriver for the M3 x 25 lid screws, fine pliers.
