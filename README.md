# Create: Ore Excavation Fly

Ore veins and a drill powered by rotational force, for **Create Fly** on **Minecraft 26.3 (Fabric)**.

- **Ore veins** in every world, generated from the world seed. Vein types are JSON files in `config/oreexcavationfly/veins/`.
- **Ore Drill**: a single block. Place it on a vein, rotate it, and it outputs ore into the vault, barrel or chest in front of it.
- **Ore Vein Finder**: a prospecting tool. Far away it gives a rough direction, closer the vein type, very close the exact chunk. Ranges are configurable.
- Optional: Mod Menu and Cloth Config screen, Jade tooltip, JEI info pages.

Requires Create Fly and Fabric API. Built and tested against the unofficial Create Fly 26.3 port.

## Origin

A stripped-down port of [Create: Ore Excavation](https://github.com/tom5454/Create-Ore-Excavation) by tom5454 (MIT). Fluid extraction, sample drills and several integrations are intentionally not ported. The original NeoForge 1.21.1 sources are kept in `reference/` as a porting aid and are not built.

## How this mod is made

The port was written by Claude AI. Knito58 decides what goes in and tests every build in game.
Builds come from GitHub Actions (the number in the version, e.g. `0.0.1+mc26.3.build47`, is the CI run).

See `Credits.md` and `LICENSE`.
