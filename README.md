# Specialities

An mcMMO-inspired skill mod for **Minecraft 26.2 (Fabric)**. Do things, get better at them: every skill levels from 0 to 100 through normal vanilla gameplay and grants passive bonuses on that same activity — no new ores, dimensions, or menus to learn.

Works in singleplayer and multiplayer (all skill logic is server-authoritative; progress is per-player and survives death and restarts).

## Skills

Every skill levels 0-100 and makes you better at that same activity:

- **Mining** — use pickaxes more efficiently, and find more from ores.
- **Woodcutting** — chop trees faster, and get more logs.
- **Harvesting** — farm faster, and get bigger crop yields.
- **Excavation** — dig faster, and dig up more.
- **Fishing** — catch fish sooner, and catch better things.
- **Combat** — hit harder with any weapon, and get more mob drops.
- **Arms Mastery** — swing melee weapons faster and hit wider.
- **Archery** — draw bows faster, and ricochet arrows between enemies.
- **Defence** — survive more punishment.
- **Acrobatics** — take less damage from falling.
- **Athletics** — sprint faster and for longer.
- **Sneaking** — stay unseen, and strike harder from the shadows.
- **Smithing** — get materials back when crafting gear, and more bars when smelting.
- **Alchemy** — brew potions without always using up the ingredient.
- **Enchanting** — enchant for less, and sometimes get more than you paid for.

Exact numbers, and where each skill's XP comes from, are shown in-game on the skills screen ("S" button in the inventory).

All bonuses stack **additively** with the matching vanilla enchantments (e.g. a Fortune II pickaxe at Mining 60 digs like Fortune V).

### UI

- Skill XP bar right above the vanilla XP bar, showing the skill of your held tool/weapon; XP gains animate with converging tool icons.
- Advancement-style level-up toasts (the big jingle only at levels 50/100).
- Skills overview screen via the "S" button in the inventory: every skill is listed (greyed out until started), hovering shows its current bonuses, and the arrow beside each one opens a note on where its XP comes from.
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
