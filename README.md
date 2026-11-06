# Ghost Mod

Fabric mod for Hypixel SkyBlock (Minecraft 26.1.2). Step 1: Ghost efficiency tracker.

## Features (step 1)
- Efficiency HUD: Ghost Effiency %, Kills/h, Miss per Hour, Longer Path Per hour
- Kills are read from the "Kills: X" line (Book of Stats) on the item in your main hand
- Tracker pauses after 15 seconds without a kill (changeable) and resumes on your next kill
- A line from you to the Ghost you should go to next (color, thickness, start and end point on/off in settings)
- Player model with hotbar or full inventory in a corner of the screen
- Everything movable, everything saved to `config/ghostmod.json`

## Commands
- `/ghosttracker` opens the settings
- `/ghosttracker move` opens the Move GUIs screen (drag the boxes)
- `/ghosttracker reset` resets the stats

## Build
```
./gradlew build
```
The jar is in `build/libs/` (use `ghostmod-1.0.0.jar`, not the `-sources` one).

## Coming in step 2
Drop tracking, forge, money per hour.
