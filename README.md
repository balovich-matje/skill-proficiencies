# Specialities

An mcMMO-inspired skill mod for **Minecraft 26.2 (Fabric)**. Do things, get better at them: every skill levels from 0 to 100 through normal vanilla gameplay and grants passive bonuses on that same activity — no new ores, dimensions, or menus to learn.

Works in singleplayer and multiplayer (all skill logic is server-authoritative; progress is per-player and survives death and restarts).

## Skills

| Skill | Levelled by | Passives |
| --- | --- | --- |
| **Mining** | Breaking ores/stone with the right tool | +1%/level break speed with pickaxes, +1 Fortune per 20 levels |
| **Woodcutting** | Chopping logs | +1%/level speed with axes, +1 Fortune per 20 levels — works on logs |
| **Harvesting** | Breaking mature crops | +1%/level speed with hoes, +1 Fortune per 20 levels |
| **Excavation** | Digging with shovels | +1%/level dig speed, +1 Fortune per 20 levels |
| **Fishing** | Catching fish | +1 Luck of the Sea per 20 levels, +1 Lure at 50 / +2 at 100 |
| **Combat** | Dealing any weapon damage | Up to +100% weapon damage, +1 Looting per 20 levels |
| **Arms Mastery** | Melee weapon damage | Up to -50% attack recovery time, +1 Sweeping Edge per 25 levels |
| **Archery** | Bow/crossbow damage | Up to -50% draw time, arrow ricochets to nearby hostiles at 50/100 (never hits friendlies) |
| **Defence** | Taking damage (mobs x1, environment x0.5, creepers x2) | +1 armor toughness per 25 levels, +1 heart per 10 levels |
| **Acrobatics** | Surviving fall damage | Fall damage reduction that stacks with Feather Falling — full immunity at level 100 + FF IV |
| **Athletics** | Sprinting | Up to -50% sprint hunger drain, Swiftness I/II at 50/100 (capped, no FOV zoom) |
| **Sneaking** | Sneaking near unaware hostiles (closer = more XP) | Up to -90% detection range (heavy armor dampens it), stealth crits x2.0–x3.0 (once per enemy) |
| **Smithing** | Crafting tools, weapons and armor (XP scales with material value) | Resourcefulness returns some crafting materials; smelted metals can multicraft x2/x4/x8 |
| **Alchemy** | Brewing potions | Up to 50% chance the brewing ingredient isn't consumed |
| **Enchanting** | Enchanting at a table | Up to 100% chance an enchant costs half the XP; up to 50% chance of a bonus or upgraded enchantment |

All bonuses stack **additively** with the matching vanilla enchantments (e.g. a Fortune II pickaxe at Mining 60 digs like Fortune V).

### UI

- Skill XP bar right above the vanilla XP bar, showing the skill of your held tool/weapon; XP gains animate with converging tool icons.
- Advancement-style level-up toasts (the big jingle only at levels 50/100).
- Skills overview screen via the "S" button in the inventory, with per-skill bonus tooltips.
- Stealth vignette: sneaking near unaware hostiles tints the screen edges violet; being spotted flashes them light.
- Testing/creative: knowledge books (+25 / +100 levels per skill) in the Tools & Utilities tab.

## Versions

| Component | Version |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | required (0.154.2+26.2 or newer) |
| Java | 25 |

## Building

```bash
./gradlew build        # jar lands in build/libs/
./gradlew runClient    # dev client
```

## License

[MIT](LICENSE). Modpacks welcome — no permission needed. Forks and redistributions must keep the copyright notice.
