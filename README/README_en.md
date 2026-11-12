# ibx_lib

[简体中文](../README.md) | **English**

---

## Overview

This mod is a library: it holds the frameworks and no content of its own. Spells, summons, target selection, equipment base classes, attributes, visual effects and creative sub-tabs live here, and other mods register their own spells, weapons and mobs on top of it.

### Spells

A spell is a registry entry carried by casting items (staves, tomes). Players pick one on the spell wheel and cast with right-click; mobs and bosses go through the same entry points. There are four cast types (instant, chanted, channeled, charged). Damage is computed into a snapshot at cast time and resolved on hit, and mana, cooldowns, spell resistance and crits are all handled by the framework. Three entity base classes are included: spell projectiles, areas of effect and beams.

See [Spell system](../TUTORIAL/SPELL_en.md).

### Summons

Summons are counted in slots: the owner's "Max Summons" is the total, each summon takes as many slots as its spell says, and the oldest one is dismissed when slots run out. All summons leave when the owner dies or logs out; friendly-fire protection, marked targets and kill credit going to the owner are automatic, and a set of AI goals for following and fighting alongside the owner is included.

See [Summons](../TUTORIAL/SUMMON_en.md).

### Target selection

A general "who / where does this hit" module: point at a mob, pick a spot or aim in a direction, with indicators drawn under the target and on the ground while casting. Friend-or-foe is judged one way across the whole mod (teams, PvP, pets and summons following their owner), and both spells and items can use it.

See [Target selection](../TUTORIAL/TARGETING_en.md).

### Equipment base classes

Item base classes for melee weapons, bows, crossbows, staves, tomes, one-handed and two-handed shields, 3D armor, and curios; extend one and write only your own numbers and hooks. Bows and crossbows resolve by attributes (ranged damage, draw time), two-handed shields can bash, 3D armor is drawn by GeckoLib with one model shared by a set, armor materials can carry per-slot spell resistance, and the vanilla bow and crossbow and a few vanilla armor sets can be taken over too, and vanilla items that only stack to 16 can be raised to 64. With Curios installed there is also an elytra slot: an elytra in it glides like one worn in the chest slot; it has 0 slots by default and mods that want it add their own.

See [Equipment base classes](../TUTORIAL/EQUIPMENT_en.md).

### Attributes

18 attributes: ranged damage, draw, critical strikes, armor penetration, heal efficiency, thorns, mana, spell power, spell resistance and penetration, max summons… Every living entity has them and all are synced to the client. An attribute panel left of the inventory lists every attribute the player has; other mods can register their attributes' default order and default hiding, and get icons just by putting images at the right path.

See [Attributes](../TUTORIAL/ATTRIBUTE_en.md).

### Visual effects

Three purely visual effects with no resolution of their own: ground warnings (a circle or strip marked on the ground), block waves (surface blocks popping up like a wave) and colored fire (a different fire texture on an entity and on the screen). One call on the server; the client draws the rest. Also, invisible mobs and players hide their armor and held items too (vanilla only hides the body; the server config can switch back to vanilla).

See [Visual effects](../TUTORIAL/EFFECT_en.md).

### Creative sub-tabs

Splits the items of one creative tab into groups: a column of icons appears left of the creative inventory, clicking a group shows only its items, and clicking it again goes back to the whole tab. Swap the tab's `displayItems` for `CreativeSubTabs` and `add` one group at a time; the search tab and JEI still get every item.

See [Creative sub-tabs](../TUTORIAL/CREATIVE_TAB_en.md).

---

## Documentation

| Document | Contents |
| --- | --- |
| [Spell system](../TUTORIAL/SPELL_en.md) | Registering spells, the cast flow, spell containers, spell damage, mana, spell entity base classes, events |
| [Summons](../TUTORIAL/SUMMON_en.md) | `SummonApi`, slots and lifetime, the `Summoned` interface, AI goals, events |
| [Target selection](../TUTORIAL/TARGETING_en.md) | `TargetApi`, factions, targeting declarations, pick timing, casting indicators, using it in spells and items |
| [Equipment base classes](../TUTORIAL/EQUIPMENT_en.md) | Base classes for melee, bows and crossbows, implements, shields, 3D armor and curios; `RangedWeaponApi`, `ShieldApi`; automatic item tags |
| [Attributes](../TUTORIAL/ATTRIBUTE_en.md) | Attribute list, damage and healing order, `CriticalStrikeApi`, attribute types, the attribute panel |
| [Visual effects](../TUTORIAL/EFFECT_en.md) | `GroundWarnings`, `BlockWave`, `CustomFire` |
| [Creative sub-tabs](../TUTORIAL/CREATIVE_TAB_en.md) | `CreativeSubTabs`, grouping rules |
| [Configuration](../TUTORIAL/CONFIG_en.md) | Server config, startup config, client config |
| [Commands](../TUTORIAL/COMMAND_en.md) | `/ibx mana`, `spell`, `summon` |

---

## Dependencies

| Mod | Type | Notes |
| --- | --- | --- |
| NeoForge `26.1.2.112+` | **Required** | — |
| Minecraft `26.1.2` | **Required** | — |
| [GeckoLib](https://modrinth.com/mod/geckolib) `5.5.2+` | Optional | Used only by 3D-model spell projectiles `GeoSpellProjectile`, the 3D armor class `IbxGeoArmorItem` and their renderers; mods that use these must require it themselves |
| [Player Animation Library](https://modrinth.com/mod/player-animation-library) `1.2.6+` | Optional (client) | Adds player animations for casting, guarding and shield bashing; without it the arms just don't move. While it plays a first-person animation, 3D armor is hidden the way it hides vanilla armor |
| [Curios API](https://modrinth.com/mod/curios) `15.0.0+` | Optional | Curio base class (wear only one, permanent effects, etc.), curio trigger hooks, the elytra slot, casting items in curio slots, the attribute panel button on the curios screen |
| [Jade](https://modrinth.com/mod/jade) | Optional (client) | Shows spell resistance after armor in the tooltip; turns Jade's "Show Bosses" off once the first time you join a world so it doesn't duplicate boss bars |
| [MmmMmmMmmMmm](https://modrinth.com/mod/mmmmmmmmmmmm) (Target Dummy) | Optional | Ranged and spell crits show as crit numbers, and spell damage numbers get their own blue color |
| [JEI](https://modrinth.com/mod/jei) | Optional (client) | Keeps JEI's bookmark panel clear of the attribute panel, curio slots and creative sub-tabs |
| [InventoryHUD+](https://modrinth.com/mod/inventoryhudplus) | Optional (client) | The current-spell frame moves out of the way of its armor HUD drawn beside the hotbar |

---

## License

This mod is open-sourced under the [MIT License](../LICENSE). You are free to use, modify, and distribute the mod and its source code, or bundle it into modpacks (including commercial ones), as long as you retain the copyright and license notice.
