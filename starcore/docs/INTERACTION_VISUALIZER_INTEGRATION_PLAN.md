# StarCore Interaction Visualizer Integration Plan

This document tracks the clean-room StarCore implementation that targets feature parity with LOOHP/InteractionVisualizer without copying GPL-licensed source code into StarCore.

## Licensing Boundary

- InteractionVisualizer is GPL-3.0-or-later. StarCore currently declares MIT licensing.
- StarCore must not copy source files, package names, class structures, or implementation details from InteractionVisualizer.
- Feature parity is allowed by independently implementing the same user-facing concepts: block/entity visual displays, player preferences, commands, config, and version adapters.
- Any future optional bridge to an installed InteractionVisualizer jar must be implemented by reflection or public API calls and kept separate from StarCore native code.

## Goal

Provide a native `interaction_visualizer` module with the same operational surface players expect from InteractionVisualizer:

- Hologram-style status text.
- Item stand/item preview displays.
- Item drop/entity displays.
- Per-player toggles for modules and entries.
- Configurable block/entity entries.
- Cleanup and reload commands.
- Performance guards for scan radius, update period, display caps, disabled worlds, and movement speed.
- A version-adapter path for future packet-only/per-player rendering.

## Feature Matrix

| InteractionVisualizer entry | StarCore entry key | Initial native behavior | Follow-up parity target |
| --- | --- | --- | --- |
| Crafting Table | `interactionvisualizer:crafting_table` | Interaction pulse text | Show crafting inputs/results during active use |
| Crafter | `interactionvisualizer:crafter` | Block status text | Slot lock/output visualization |
| Loom | `interactionvisualizer:loom` | Interaction pulse text | Pattern/result preview |
| Enchantment Table | `interactionvisualizer:enchantment_table` | Interaction pulse text | Enchant option animation |
| Cartography Table | `interactionvisualizer:cartography_table` | Interaction pulse text | Map/result preview |
| Anvil | `interactionvisualizer:anvil` | Interaction pulse text | Repair/rename input-output preview |
| Grindstone | `interactionvisualizer:grindstone` | Interaction pulse text | Disenchant result preview |
| Stonecutter | `interactionvisualizer:stonecutter` | Interaction pulse text | Cutting result preview |
| Brewing Stand | `interactionvisualizer:brewing_stand` | Brewing/fuel hologram and bottle item preview | Exact bottle-path animation |
| Chest | `interactionvisualizer:chest` | Container count and top item preview | Open-player synchronized item stand |
| Double Chest | `interactionvisualizer:double_chest` | Container count and top item preview | Exact double-chest layout |
| Barrel | `interactionvisualizer:barrel` | Container count and top item preview | Barrel open-state animation |
| Furnace | `interactionvisualizer:furnace` | Cook/fuel progress and item preview | Exact progress colors/config |
| Blast Furnace | `interactionvisualizer:blast_furnace` | Cook/fuel progress and item preview | Exact progress colors/config |
| Smoker | `interactionvisualizer:smoker` | Cook/fuel progress and item preview | Exact progress colors/config |
| Ender Chest | `interactionvisualizer:ender_chest` | Interaction pulse text | Player-specific inventory preview via packets |
| Shulker Box | `interactionvisualizer:shulker_box` | Container count and top item preview | Open-state and color-specific layout |
| Dispenser | `interactionvisualizer:dispenser` | Container count and top item preview | Face-aware slot animation |
| Dropper | `interactionvisualizer:dropper` | Container count and top item preview | Face-aware slot animation |
| Hopper | `interactionvisualizer:hopper` | Container count and top item preview | Transfer animation |
| Beacon | `interactionvisualizer:beacon` | Tier/effect hologram | Beam/path animation |
| Note Block | `interactionvisualizer:note_block` | Note/instrument pulse | Sound-note animation |
| Jukebox | `interactionvisualizer:jukebox` | Disc hologram | Disc item rotation |
| Smithing Table | `interactionvisualizer:smithing_table` | Interaction pulse text | Template/input/result preview |
| Bee Nest | `interactionvisualizer:bee_nest` | Bee/honey hologram | Honey bar/campfire status colors |
| Beehive | `interactionvisualizer:beehive` | Bee/honey hologram | Honey bar/campfire status colors |
| Lectern | `interactionvisualizer:lectern` | Book title/author if available | Page display and author formatting |
| Campfire | `interactionvisualizer:campfire` | Cooking item preview | Exact four-slot progress bars |
| Soul Campfire | `interactionvisualizer:soul_campfire` | Cooking item preview | Exact four-slot progress bars |
| Spawner | `interactionvisualizer:spawner` | Mob/delay/range hologram | Spawn countdown path animation |
| Conduit | `interactionvisualizer:conduit` | Active/range hologram | Circle path animation |
| Banner | `interactionvisualizer:banner` | Pattern count hologram | Blacklist and exact banner name rules |
| Dropped Item | `interactionvisualizer:item` | Item name/amount/despawn text | Packet-only per-player item text |
| Villager | `interactionvisualizer:villager` | Profession/level text | Trades/demand/restock visualization |
| Bookshelf | `bookshelf:bookshelf` | Chiseled bookshelf interaction pulse | Slot-specific book display |

## Implementation Phases

1. Native module foundation: module lifecycle, config, command, player preferences, scan/render/cleanup loop.
2. Core block displays: containers, furnaces, brewing stands, campfires, beehives, beacons, spawners, jukeboxes, lecterns, banners, note blocks.
3. Entity displays: dropped items and villagers.
4. Per-player packet backend: hide disabled entries from players who opted out and remove global entity leakage.
5. Exact InteractionVisualizer-style animations: path types, pickup animation, hand swing animation, item stand layouts.
6. Version adapter matrix: Paper 1.21.11 first, then 1.21.x/1.22.x compatibility.

## Maintenance Rules

- Keep all native code under `dev.starcore.starcore.module.visualizer`.
- Add new entries through `VisualizerEntry`; do not spread material matching across listeners.
- Keep display generation in renderer classes and command behavior in `VisualizerCommand`.
- Document every unsupported parity item in this file before adding behavior.
- Verify with at least `mvn -DskipTests compile` after each implementation batch.

