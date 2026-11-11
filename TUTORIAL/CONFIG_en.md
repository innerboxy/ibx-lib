# Configuration

[简体中文](CONFIG.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Server config

Config type **SERVER**, saved with the world at `<world>/serverconfig/ibx_lib-server.toml` and synced to clients.

```toml
#是否保留原版跳劈暴击（下落攻击必定暴击）。
#false = 暴击完全由 critical_chance 属性决定。
#true  = 跳劈仍必定暴击，平地攻击另按 critical_chance 判定。
#两种情况下暴击倍率都取 critical_damage，而非原版写死的 1.5 倍。
enableVanillaCritical = false
#隐身时是否连装备一起隐藏。
#true  = 看不见的生物，身上的盔甲、鞘翅、头上戴的南瓜 / 头颅、手里的物品也都不显示；
#false = 原版：只藏身体，装备和手里的物品照常显示。
#旁观者、开了「看见隐身队友」的队友两种情况下都看得见；盔甲架不受影响。
hideEquipmentWhenInvisible = true
```

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `enableVanillaCritical` | boolean | `false` | Whether to keep vanilla's guaranteed jump crit. When on, jump attacks always crit and other attacks still roll critical chance (either one counts). Either way the crit multiplier is `critical_damage` (see [Attributes 3](ATTRIBUTE_en.md#3-damage-and-healing-order)). |
| `hideEquipmentWhenInvisible` | boolean | `true` | Whether invisible mobs and players also hide their armor, elytra, worn pumpkins / heads and held items; `false` is vanilla, which only hides the body. Viewers who can see invisible entities (spectators, teammates with "see friendly invisibles") still see them as in vanilla; armor stands are not affected; your own held item in first person still shows. The server's value is used, so players cannot change it on their side. |

> SERVER configs are only available after a world is loaded. Developers must read `Config` only while the game is running, never during registration or class initialization, or it throws.

---

## 2. Startup config

Config type **STARTUP**, at `config/ibx_lib-startup.toml`. These options must be settled when items are registered (vanilla items' default attributes and stack sizes are baked in at that point), so they live in the startup config.

```toml
#是否接管原版弓与弩，让它们按本模组的远程属性结算。
takeOverVanillaRangedWeapons = true
#是否调整原版盔甲套装的属性。
adjustVanillaArmors = true
#是否把原版里堆叠上限为 16 的物品改成 64。
raiseVanillaStackSizes = true
```

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `takeOverVanillaRangedWeapons` | boolean | `true` | Whether to take over the vanilla bow and crossbow. When on, they get ranged damage / draw time attributes, and damage, drawing, loading and crits work like this mod's bows and crossbows; they can't be used from the off hand. Mobs holding vanilla bows and crossbows (skeletons, pillagers…) resolve by attributes too. When off, vanilla behavior is kept (see [Equipment base classes](EQUIPMENT_en.md)). |
| `adjustVanillaArmors` | boolean | `true` | Whether to adjust vanilla armor sets: netherite gets extra spell resistance equal to its armor value; gold's armor is replaced by spell resistance equal to diamond's armor values (head 3, chest 8, legs 6, feet 3); copper's armor is replaced by the same amount of spell resistance (head 2, chest 4, legs 3, feet 1). |
| `raiseVanillaStackSizes` | boolean | `true` | Whether to raise vanilla items that stack to 16 up to 64: signs, hanging signs, banners, snowballs, eggs, ender pearls, buckets, armor stands and honey bottles. Written books stay at 16 (see [Equipment base classes](EQUIPMENT_en.md)). |

- **Restart the game** for changes to take effect.
- **Not synced**: server and client each read their own file. Keep them identical, or the numbers shown on the client (tooltips, draw animation, stack sizes) won't match what the server actually resolves. In multiplayer, modpacks should ship the same file to the server and players.

---

## 3. Client config

Each player's own display and control settings, at `config/ibx_lib-client.toml`, not synced. You can also edit it via this mod's "Config" button in the mod list; the five casting options are also under **Options → Controls**, right after vanilla's "Use: Toggle / Hold". All are read live, so changes in game apply immediately.

### HUD and screen

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `vanillaHud` | boolean | `true` | Whether to use vanilla's health, hunger, armor, air and level display. When `false`, hearts / drumsticks become health and hunger bars (air is drawn on the health bar's frame), the vanilla armor row is hidden and the level number moves to the middle of the XP bar. The cast bar and mana bar don't depend on this option; they show either way, stacked above the health / hunger rows. |
| `alwaysShowManaBar` | boolean | `false` | Whether the mana bar is always shown. When `false` it shows only while mana isn't full and fades out 3 seconds after refilling. |
| `replaceFireOverlay` | boolean | `true` | When burning in first person: `true` shows a fire border at the screen corners (nothing in the middle); `false` shows vanilla's two large sheets of fire (see [Visual effects 4](EFFECT_en.md#4-colored-fire)). |
| `spellWheelScale` | double (1.0–4.0) | `2.3` | Spell wheel size, where 1 is "just fits 16×16 icons". It shrinks automatically if the screen is too small, but never below 1. |

### Casting controls (also in Options → Controls)

| Option | Name in Controls | Default | Description |
| --- | --- | --- | --- |
| `toggleCastSpell` | Cast Spells | `true` (Toggle) | Toggle: press right-click once to start; the spell fires when the cast completes, and pressing again while casting cancels. Hold: hold right-click to cast, release to cancel. |
| `toggleChannelSpell` | Channel Spells | `false` (Hold) | Toggle: press right-click once to start; stops when out of mana or when pressed again. Hold: hold right-click to channel, release to stop. |
| `spellWheelSelectOnRelease` | Spell Wheel | `true` (Click or Release) | Click or Release: releasing the wheel key selects the slot under the mouse (the empty center changes nothing); left-clicking a slot also works. Click Only: only left-clicking a slot selects it. |
| `targetUnitLockAtStart` | Unit Targeting | `false` (Pick on Effect) | When unit-targeted spells and items (locking one mob) pick their target. Pick on Effect: the target picked at the start is only a fallback; it's picked again under the crosshair when the cast completes or you release. Lock at Start: the target picked at the start is locked; the cast is cancelled if it dies or gets out of range. |
| `targetPointLockAtStart` | Point Targeting | `false` (Pick on Effect) | When point-targeted spells and items pick their point. Pick on Effect: picked again under the crosshair when the cast completes or you release. Lock at Start: fixed when the cast starts. |

The last two are sent to the server and apply immediately (see [Target selection](TARGETING_en.md)).

### Target indicators

Ground indicators while casting (under the locked target, the circle / fan of an area); see [Target selection](TARGETING_en.md).

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `showTargetSelf` | boolean | `true` | Whether to show your own. |
| `showTargetPlayerAlly` | boolean | `true` | Whether to show those of allied players (ally / enemy is relative to you, by the server's PvP rules and teams). |
| `showTargetPlayerEnemy` | boolean | `true` | Whether to show those of enemy players. |
| `showTargetMobAlly` | boolean | `true` | Whether to show those of allied mobs (your pets, summons…). |
| `showTargetMobEnemy` | boolean | `true` | Whether to show those of enemy mobs (monsters, bosses…). |
| `targetColorAlly` | `#RRGGBB` | `#55D16A` | Color for effects on allies (healing, shields…). |
| `targetColorEnemy` | `#RRGGBB` | `#E5484D` | Color for effects on enemies (damage, debuffs…). |
| `targetColorAll` | `#RRGGBB` | `#F2C230` | Color for effects on everyone, and for point-only markers. |

The three colors are also used by ground warnings (see [Visual effects 2](EFFECT_en.md#2-ground-warnings)); ground warnings ignore the show switches above. An invalid color falls back to the default.

### Attribute panel

The attribute panel left of the inventory (see [Attributes 7](ATTRIBUTE_en.md#7-attribute-panel)); usually changed from the panel's settings screen.

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `attributePanelOpen` | boolean | `false` | Whether the panel is open (toggled by the scroll button and remembered). |
| `attributePanelShowIcons` | boolean | `true` | Whether each row draws an icon. |
| `attributePanelShowNames` | boolean | `true` | Whether each row shows the attribute name. |
| `attributePanelGroupByMod` | boolean | `false` | Whether to group by mod (with group names); `false` lists everything in one column. |
| `attributePanelHideDefault` | boolean | `false` | Whether to hide attributes still at their default value (listing only those changed by gear, effects, etc.). |
| `attributePanelSort` | `DEFAULT` / `NAME` / `CUSTOM` | `DEFAULT` | Default order / by name / custom (the order set with ▲▼ in the settings screen). |
| `attributePanelOrder` | list of attribute ids | empty | The custom order, used when sorting is `CUSTOM`; unlisted attributes follow in default order. |
| `attributePanelHidden` | list of attribute ids | see description | Attributes not shown in the panel. Hidden by default: camera distance, waypoint transmit / receive range, name tag distance, creative flight. |

### Other

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `jadeBossesHiddenOnce` | boolean | `false` | Internal record, normally left alone: whether Jade's "Show Bosses" has already been turned off for you (only matters with Jade installed). Jade shows a boss's health again when you aim at it, duplicating the boss bar, so it's turned off once the first time you join a world; if you turn it back on in Jade it won't be turned off again. Set back to `false` to have it turned off again next time you join. |
