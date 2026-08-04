# Skill Proficiencies

*(formerly “Specialities” — same mod, same mod id, your worlds and skill progress carry over untouched)*

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

## Config

Settings live in `config/skill-proficiencies.json`, written on first launch. With **Mod Menu + Cloth Config** installed there is an in-game screen for them; without those (and on the Forge/NeoForge builds, which ship no config screen) edit the file directly.

*Upgrading from 1.6.1 or older?* The file used to be `config/specialities.json`. It is renamed for you on the first launch, settings and all — nothing to copy across.

| Key | Default | What it does |
| --- | --- | --- |
| `combatDamageMaxBonus` | `0.5` | Extra weapon damage at Combat 100, as a fraction. `1.0` = the old +100%. |
| `attackSpeedMaxReduction` | `0.3` | Fraction of attack-recovery / bow-draw time removed at level 100. |
| `miningSpeedMaxBonus` | `1.0` | Extra block-breaking speed at Mining 100, as a fraction. |
| `xpRateMultiplier` | `1.0` | Multiplier on every skill XP gain. `0.0` disables gains entirely. |
| `luckLevelsPerBonus` | `20` | Skill levels needed per +1 passive Fortune/Looting. |
| `showXpHudBar` | `true` | Draw the skill XP bar above the hotbar. |

The five balance keys are read wherever the skill logic runs — the server's copy of the file is the one that counts in multiplayer.

**`showXpHudBar` is the exception: it is yours alone.** It is read only by your own client, out of your own file, and is never sent either way — a server cannot hide your bar and turning it off changes nothing for anyone else. Off also drops the raise the mod applies to the vanilla XP bar, hearts, food and armor, so they go back where vanilla draws them; level-up toasts, the skills screen and the stealth vignette are unaffected. This holds with **Archetypes** installed too — since Skill Proficiencies 1.6.1 and Archetypes 1.2.0 its mana and banked-hunger rows follow the raise instead of assuming it.

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
