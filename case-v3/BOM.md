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
| 1 | ~10 kohm pull-up resistor to 3.3 V | For the light switch on GPIO34, which has no internal pull-up |
| - | Thin flexible wire, heat-shrink tubing, solder | |
| 1 | USB-C cable | Powers everything |

## Fasteners

| Qty | Part | Use |
| ---: | --- | --- |
| 4 | M3 x 25 screw | Lid + switch plate + shell (one screw per point) |
| 4 | M3 nut | Under the switch-plate brackets, slid in from the side |
| 4 | M3 x 25 screw | ESP32: 21.23 mm from the support top to the nut seat, leaving 3.77 mm for the PCB and nut |
| 4 | M3 nut | ESP32: all 4 inserted from underneath |
| 4 | M3 x 8 screw (provisional) | Speaker: enters from outside the rear wall, through the speaker's plastic tab; recheck length with the standoffs removed |
| 4 | M3 nut | Speaker, on the far side of the tab, inside the speaker's own space |
| 2 | M2 x 26 screw | MAX98357A: 22.63 mm from the support top to the nut seat. With a 1 mm PCB and a nut up to 2 mm thick, 26 mm passes through the full nut. Cut a longer screw to length if needed |
| 2 | M2 nut | MAX98357A, inserted from underneath |

Total: **12 M3 nuts** (4 switch plate + 4 ESP32 + 4 speaker) and **2 M2 nuts**. Keep a few spare screws and nuts. Use cylindrical (ISO 4762) or pan heads; M3 heads at most 5.5 mm across.

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
