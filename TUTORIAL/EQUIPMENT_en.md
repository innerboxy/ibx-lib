# Equipment base classes

[简体中文](EQUIPMENT.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

This mod provides a set of item base classes. Other mods use them directly or extend them to register weapons, shields, magic implements, armor and curios. Numbers are given as constructor parameters; the shared parts (attribute modifiers, damage resolution, tags, animations) are handled by the base classes.

| What you want to make | Class to use | See |
| --- | --- | --- |
| A melee weapon (sword, dagger, cutlass, claymore, axe, spear...) | one of the kind classes in `ibx.lib.item`, such as `IbxSwordItem`, `IbxAxeItem` | [2](#2-melee-weapons) |
| A bow | `ibx.lib.item.IbxBowItem` | [3](#3-ranged-weapons) |
| A crossbow | `ibx.lib.item.IbxCrossbowItem` | [3](#3-ranged-weapons) |
| A tome | `ibx.lib.item.IbxTomeItem` | [5](#5-magic-implements) |
| A staff | `ibx.lib.item.IbxStaffItem` | [5](#5-magic-implements) |
| Another kind of magic implement | extend `ibx.lib.item.general.IbxMagicWeaponItem` | [5](#5-magic-implements) |
| A plain shield (one-handed, blocks only) | `ibx.lib.item.IbxShieldItem` | [6](#6-shields) |
| A two-handed shield (normal attacks and shield bash) | `ibx.lib.item.IbxTwoHandedShieldItem` | [6](#6-shields) |
| 3D armor (worn look drawn by GeckoLib) | `ibx.lib.item.IbxGeoArmorItem` | [7](#7-3d-armor) |
| Armor material with spell resistance | `ibx.lib.item.general.IbxArmorMaterial` | [7](#armor-material) |
| Attributes beyond the material on an armor piece | `ibx.lib.item.general.IbxArmorProperties` | [7](#armor-item-properties) |
| A curio (wear only one, permanent effects, enderman mask, kept on death...) | `ibx.lib.item.IbxCurioItem` | [8](#8-curios) |
| A curio that triggers on attacks, hits, kills or casts | extend `IbxCurioItem` and override `ibx.lib.item.general.CurioTriggers` methods | [8](#trigger-hooks) |
| Elytra that also glide from a curio slot | add slots to the Curios slot `elytra` (0 by default) | [8](#elytra-slot) |

- **Registration is the same for all**: `Item.Properties` must carry the item id before an item can be constructed, so register with `DeferredRegister.Items#registerItem` and use the `Properties` passed into the factory; rarity, fire resistance and so on are set on it too.
- **Numbers go on the main hand**: the base classes bake their numbers into main-hand attribute modifiers with a "base id", so the tooltip shows them as the dark green merged line vanilla swords use (e.g. "8 Ranged Damage").
- **Extra passive attributes are written the usual way**: attribute modifiers you give with `attributes(...)` on the `Properties` at registration are merged with the base class's own, after them, and neither replaces the other (armor is the exception: use `add` on `IbxArmorProperties`, see [7](#armor-item-properties)). Writing the same attribute, modifier id and slot twice is an error at load time. When writing your own item base class that adds modifiers, call `ibx.lib.item.general.IbxAttributeModifiers.add(properties, modifiers)` before passing `properties` to the parent; to wrap a vanilla setup that replaces all attributes, such as `sword()`, use `IbxAttributeModifiers.keeping(properties, p -> p.sword(...))`.
- **Left-click moves come with the class**: with Better Combat installed, melee weapons, staves and two-handed shields bring their attack moves by kind; no `weapon_attributes` file to write (see [2](#hand-models-and-left-click-moves)).
- **Tags are added automatically**: items join common tags according to their base class; nothing to write in data (see [9](#9-automatic-item-tags)). 3D armor is the exception.
- **Usable as they are**: for plain equipment just `new` the base class; extend it and override the relevant methods only when the item has its own mechanics.

Not covered here:

- The full attribute list and the order of damage resolution: see [Attributes](ATTRIBUTE_en.md).
- Spells, the casting flow and mana: see [Spell system](SPELL_en.md).
- Targeting (factions and the fan shape used by the shield bash): see [Targeting](TARGETING_en.md).
- Config option details: see [Configuration](CONFIG_en.md).

---

## 2. Melee weapons

Melee weapons are split into one class per kind, all in the `ibx.lib.item` package and all extending `ibx.lib.item.general.IbxMeleeWeaponItem` (abstract, not usable directly). Plain weapons use the kind class directly; weapons with their own mechanics (bonus damage on hit, special actions) extend the kind class and put them in the subclass.

| Class | Kind | Vanilla rules | Left-click moves (Better Combat preset) | Kind tag |
| --- | --- | --- | --- | --- |
| `IbxSwordItem` | Sword | Sword | `bettercombat:sword`: one-handed, left and right slashes, a stab | `#c:tools/sword` |
| `IbxDaggerItem` | Dagger | Sword | `bettercombat:dagger`: one-handed, range -0.5; an extra two-handed stab when dual-wielding two | `#c:tools/dagger` |
| `IbxCutlassItem` | Cutlass | Sword | `bettercombat:cutlass`: one-handed, left and right slashes, range -0.25 | `#c:tools/cutlass` |
| `IbxClaymoreItem` | Claymore | Sword | `bettercombat:claymore`: two-handed, slash, stab, slam | `#c:tools/claymore` |
| `IbxScytheItem` | Scythe | Sword | `bettercombat:scythe`: two-handed, left and right sweeps, range +0.5 | `#c:tools/scythe` |
| `IbxFistItem` | Fist weapon | Sword | `bettercombat:fist`: one-handed, punch, range -0.75 | `#c:tools/fist` |
| `IbxHammerItem` | Hammer | Sword, and disables shields | `bettercombat:hammer`: two-handed, slam | `#c:tools/hammer` |
| `IbxAxeItem` | Axe | Axe | `bettercombat:axe`: one-handed, left and right slashes | `#c:tools/axe` |
| `IbxDoubleAxeItem` | Double axe (a subclass of the axe) | Axe | `bettercombat:double_axe`: two-handed, slash, spin | `#c:tools/axe`, `#c:tools/double_axe` |
| `IbxSpearItem` | Spear | Spear | `bettercombat:spear`: two-handed, stab, range +1 | `#c:tools/spear` |

The three sets of vanilla rules:

- **Sword** (`Properties#sword`): cuts cobwebs and bamboo fast; loses 1 durability per attack; cannot disable shields.
- **Axe** (`Properties#axe`): mines axe blocks at the material's speed, and blocks above its tier drop nothing; loses 2 durability per attack and 1 per block mined; a hit on someone raising a shield disables it for 5 seconds. Right-clicking a block works like the vanilla axe: it tries stripping, scraping and waxing off in that order and loses 1 durability on success; it does nothing while the off hand holds something that can block and you are not sneaking, so the right-click raises the shield instead.
- **Spear** (`Properties#spear`): left-click stabs and can hit several targets in a line, with a longer reach than a sword, and only once the attack bar is full; holding right-click charges, with damage from the relative speed of the wielder and the target, and when fast enough it also knocks back and dismounts riders. Attack damage is only the material's bonus with no damage base, and attack speed comes from how long one stab takes.

The hammer changes one thing on top of the sword rules: a hit on someone raising a shield disables it for `IbxHammerItem.DISABLE_BLOCKING_SECONDS` (5) seconds, like the vanilla axe.

Every kind: the material gives durability, repair items and enchantability; attack damage and attack speed go on the main hand with vanilla's base ids, shown as the dark green merged line in the tooltip; automatically in `#c:tools`, `#c:tools/melee_weapon` and the kind tag above.

> Items are not added to vanilla's `#minecraft:swords`, `#minecraft:axes` or `#minecraft:spears` automatically.

### Constructor parameters

Every kind except the spear takes `(properties, material, attackDamage, attackSpeed)`:

| Parameter | Type | Description |
| --- | --- | --- |
| `properties` | `Item.Properties` | The one passed into the registration factory. Durability comes from the material; no need to set it. |
| `material` | `ToolMaterial` | Durability, repair items, enchantability; its bonus is added to attack damage. The axe also uses its mining speed and tier. |
| `attackDamage` | `float` | Damage base. Displayed attack damage = this + material bonus + 1 for the bare hand. |
| `attackSpeed` | `float` | Attack speed base. Displayed attack speed = this + the player's 4. |

The spear's parameters match vanilla's `Properties#spear`; all times are in seconds:

| Constructor | Description |
| --- | --- |
| `IbxSpearItem(properties, material)` | The other nine numbers are the vanilla iron spear's (the `BASELINE_*` constants). |
| `IbxSpearItem(properties, material, attackDuration, damageMultiplier, delay, dismountTime, dismountThreshold, knockbackTime, knockbackThreshold, damageTime, damageThreshold)` | All parameters. |

| Parameter | Description |
| --- | --- |
| `attackDuration` | How long one left-click stab takes. Attack speed = 1 ÷ this, and the stab animation plays for this long. |
| `damageMultiplier` | Damage multiplier of the charge: damage = the wielder's base attack damage (1 for a player) + relative speed × this (rounded down). |
| `delay` | How long right-click must be held before the charge takes effect. |
| `dismountTime` / `dismountThreshold` | For this long after it takes effect, the charge dismounts riders while the wielder's speed is at least the threshold. |
| `knockbackTime` / `knockbackThreshold` | For this long after it takes effect, the charge knocks back while the wielder's speed is at least the threshold. |
| `damageTime` / `damageThreshold` | For this long after it takes effect, the charge deals damage while the relative speed is at least the threshold; after that this charge is over. |

The kind classes have no hooks of their own (apart from the axe's right-click); to add mechanics, override `Item` methods.

```java
public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("examplemod");

// shows 4 attack damage (1 + iron's 2 + bare hand 1), 2 attack speed
public static final DeferredItem<IbxDaggerItem> IRON_DIRK = ITEMS.registerItem("iron_dirk",
        p -> new IbxDaggerItem(p, ToolMaterial.IRON, 1.0F, -2.0F));

// the vanilla iron spear's numbers
public static final DeferredItem<IbxSpearItem> IRON_PIKE = ITEMS.registerItem("iron_pike",
        p -> new IbxSpearItem(p, ToolMaterial.IRON));
```

### Hand models and left-click moves

The in-hand display depends on the kind of weapon and is given in resources, not in the item class. This mod ships two generic hand models. Both have `minecraft:item/handheld` as parent and only change the first- and third-person display for both hands; the texture goes in `layer0`:

| Model | For |
| --- | --- |
| `ibx_lib:item/general_dagger_hand` | Daggers |
| `ibx_lib:item/general_cutlass_hand` | Cutlasses |

`assets/examplemod/models/item/iron_dirk.json`:

```json
{
  "parent": "ibx_lib:item/general_dagger_hand",
  "textures": {
    "layer0": "examplemod:item/iron_dirk"
  }
}
```

**Left-click moves** are left to Better Combat (this mod does not depend on it or reference its classes; without it you get the vanilla swing). With it installed, the kind classes write the preset from the table above into the item's default component `bettercombat:preset_id`, so no weapon needs its own `weapon_attributes` file; a `data/<namespace>/weapon_attributes/<item>.json` keyed by the item id no longer applies either.

To change the moves, call `ibx.lib.compat.BetterCombatCompat.preset(properties, preset id)` once on the `Properties` passed in at registration; the kind then no longer sets its default:

```java
// sword rules, katana moves
public static final DeferredItem<IbxSwordItem> IRON_KATANA = ITEMS.registerItem("iron_katana",
        p -> new IbxSwordItem(BetterCombatCompat.preset(p, BetterCombatCompat.builtin("katana")), ToolMaterial.IRON, 3.0F, -2.4F));
```

`BetterCombatCompat.builtin(name)` gives the id of a preset Better Combat ships; a preset file you write yourself, `data/examplemod/weapon_attributes/my_blade.json`, has the id `examplemod:my_blade`.

### Making a new kind

Extend `IbxMeleeWeaponItem` and hand it two things in the constructor: the rules this kind of weapon is set up with (`setup`) and its default left-click moves (`preset`).

```java
public class RapierItem extends IbxMeleeWeaponItem {
    public RapierItem(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed) {
        super(properties, p -> p.sword(material, attackDamage, attackSpeed), BetterCombatCompat.builtin("rapier"));
    }
}
```

- `setup` uses vanilla's `sword` / `axe` / `spear` and can change more components after that (the hammer replaces the `weapon` component after `sword` with one that disables shields). Passive attributes given at registration are merged with what `setup` writes by the base class.
- With a `null` `preset` no preset is set, and Better Combat picks the moves from the item id by itself (it recognises ids containing words such as `sword`, `axe` and `spear`).

---

## 3. Ranged weapons

Bows and crossbows extend the vanilla `BowItem` / `CrossbowItem`; what they share goes through the interface `ibx.lib.item.general.IbxRangedWeaponItem` and `ibx.lib.api.RangedWeaponApi`.

**Both have**:

- **Damage separate from arrow speed**: at the moment of firing the damage is computed and written onto the arrow (the damage snapshot); on hit it replaces the vanilla damage. Bow = the shooter's ranged damage × charge percent; crossbow = the shooter's ranged damage. Arrow speed only affects the trajectory, not the damage.
- **Draw time follows attributes**: ticks to full draw / to load = draw time (`ibx_lib:draw_time`) ÷ draw speed (`ibx_lib:draw_speed`) × 20, at least 1 tick.
- **Critical hits**: rolled once on the server when firing, by the shooter's critical chance. The result is stored in the arrow's vanilla crit flag (so the vanilla crit particles appear); the damage multiplier is applied on hit.
- **Ranged damage and draw time on the main hand**: modifier ids `ibx_lib:base_ranged_damage` and `ibx_lib:base_draw_time`, shown as dark green merged lines.
- **Main hand only**: the modifiers are main-hand only, and right-clicking with the off hand simply fails.
- **Auto fire**: a stack with the `ibx_lib:auto_fire` component releases by itself at full draw (see [Auto fire](#auto-fire) below).
- **Tags**: automatically in `#c:tools` and `#c:tools/ranged_weapon`; bows also in `#c:tools/bow`, crossbows also in `#c:tools/crossbow`.

For the attribute definitions and the resolution steps on hit, see [Attributes](ATTRIBUTE_en.md).

### Bow: IbxBowItem

| Constructor | Description |
| --- | --- |
| `IbxBowItem(properties, rangedDamage, drawTime)` | Baseline arrow speed; ammo is any arrow (`#minecraft:arrows`). |
| `IbxBowItem(properties, rangedDamage, drawTime, arrowVelocity)` | Ammo is any arrow. |
| `IbxBowItem(properties, rangedDamage, drawTime, arrowVelocity, ammo)` | All parameters. |

| Parameter | Type | Description |
| --- | --- | --- |
| `properties` | `Item.Properties` | The one passed into the registration factory. **Set durability yourself** (vanilla bow: 384); without it the item never takes damage. |
| `rangedDamage` | `float` | Ranged damage (at full draw). |
| `drawTime` | `float` | Seconds to full draw; values not above 0 use `BASELINE_DRAW_TIME` (1.0). |
| `arrowVelocity` | `float` | Initial speed at full draw; values not above 0 use `BASELINE_ARROW_VELOCITY` (3.0). |
| `ammo` | `Predicate<ItemStack>` | Ammo predicate, `ARROW_ONLY` by default. Custom ammo items must extend `ArrowItem`, otherwise they are shot as plain arrows. |

Behaviour:

- Charge percent is linear: ticks drawn ÷ full-draw ticks, capped at 1.
- Actual arrow speed = `getArrowVelocity` × charge percent.
- Below `getMinCharge` (default `MIN_CHARGE` = 0.1) nothing is fired and nothing is consumed.
- NeoForge's `ArrowLooseEvent` fires before the shot as usual.

Overridable methods:

| Method | Default | Description |
| --- | --- | --- |
| `protected float getArrowVelocity(ItemStack stack)` | The speed given to the constructor | Arrow speed at full draw. Override to change it per stack. |
| `protected float getMinCharge(ItemStack stack)` | `0.1` | Firing threshold (charge percent). |
| `protected @Nullable Consumer<Projectile> prepareShot(ServerLevel level, Player player, ItemStack stack, float chargePercent)` | `null` | Called once before firing (server side, past the threshold, about to shoot): settle any extra cost of this shot here. The returned handler is called for each projectile after its damage snapshot is written and before it is spawned (once per arrow with Multishot); it can change the snapshot or attach data to the projectile. Return `null` for no extra handling. |
| `public float getChargePercent(int chargeTicks, LivingEntity shooter, ItemStack stack)` | Linear | Charge percent. Read by player shots and by the AI of bow-wielding mobs. |
| `protected void shootWithDamage(...)` | — | The firing routine itself: spread, Multishot and durability as in vanilla, plus a damage snapshot on every arrow. Usually left alone. |

```java
public static final DeferredItem<IbxBowItem> LONGBOW = ITEMS.registerItem("longbow",
        p -> new IbxBowItem(p.durability(500), 8.0F, 1.2F));
```

A bow with a mechanic: +20% damage at full draw.

```java
public class HunterBowItem extends IbxBowItem {
    public HunterBowItem(Item.Properties properties) {
        super(properties, 8.0F, 1.2F);
    }

    @Override
    protected @Nullable Consumer<Projectile> prepareShot(ServerLevel level, Player player, ItemStack stack,
                                                         float chargePercent) {
        if (chargePercent < 1.0F) {
            return null;
        }
        return projectile -> RangedWeaponApi.getAmmoRangedDamage(projectile)
                .ifPresent(damage -> RangedWeaponApi.setAmmoRangedDamage(projectile, damage * 1.2F));
    }
}
```

### Crossbow: IbxCrossbowItem

| Constructor | Description |
| --- | --- |
| `IbxCrossbowItem(properties, rangedDamage, drawTime)` | Baseline arrow speed; ammo is any arrow, and a held firework rocket can be loaded too. |
| `IbxCrossbowItem(properties, rangedDamage, drawTime, arrowVelocity)` | Same ammo. |
| `IbxCrossbowItem(properties, rangedDamage, drawTime, arrowVelocity, ammo, allowFireworks)` | All parameters. |

| Parameter | Type | Description |
| --- | --- | --- |
| `properties` | `Item.Properties` | The one passed into the registration factory. **Set durability yourself** (vanilla crossbow: 465); without it the item never takes damage. |
| `rangedDamage` | `float` | Ranged damage. |
| `drawTime` | `float` | Seconds to load; values not above 0 use `BASELINE_DRAW_TIME` (1.25). |
| `arrowVelocity` | `float` | Initial speed; values not above 0 use `BASELINE_ARROW_VELOCITY` (3.15). |
| `ammo` | `Predicate<ItemStack>` | Ammo that can be taken from the inventory, `ARROW_ONLY` by default. Custom ammo items must extend `ArrowItem`. |
| `allowFireworks` | `boolean` | Whether a held firework rocket counts as ammo (as with the vanilla crossbow); `true` by default. |

Behaviour:

- It loads on the server the moment it is fully drawn; no need to release.
- Loading sounds play at 20%, at 50% and when loaded.
- A loaded firework rocket keeps its vanilla speed and gets no damage snapshot (snapshots are for arrows only).
- The firing routine (Multishot etc.) is vanilla's and has no equivalent of the bow's `prepareShot`; to change each arrow, override `createProjectile`.

Overridable methods:

| Method | Default | Description |
| --- | --- | --- |
| `protected float getArrowVelocity(ItemStack stack)` | The speed given to the constructor | Initial speed. Used for arrows shot by players and by mobs alike. |
| `protected ChargingSounds getChargingSoundsFor(ItemStack stack)` | The loading sounds given by enchantments, or the vanilla crossbow's three | Loading sounds. |
| `protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit)` | Rolls the crit and writes the damage snapshot onto the arrow | Creates one projectile. Call `super` first when overriding. |

```java
public static final DeferredItem<IbxCrossbowItem> ARBALEST = ITEMS.registerItem("arbalest",
        p -> new IbxCrossbowItem(p.durability(600), 10.0F, 1.5F));
```

### IbxRangedWeaponItem and IbxRangedWeaponStats

- `IbxRangedWeaponItem`: the interface bows and crossbows both implement. `stats()` returns the static numbers given to the constructor; `IbxRangedWeaponItem.appendAutoFireTooltip(stack, tooltip)` adds a gold "Auto Fire" line (lang key `tooltip.ibx_lib.auto_fire`) to stacks with auto fire on, and is already called by the bow's and crossbow's `appendHoverText`.
- `IbxRangedWeaponStats`: `rangedDamage()`, `drawTime()`, `arrowVelocity()`, `modifiers()` (the baked main-hand attribute modifiers).

> At runtime, do not read ranged damage and draw time from `stats()`. Always read the shooter's attributes through `RangedWeaponApi`, so that armor, curios, potion effects and per-stack component overrides all take effect. Arrow speed has no attribute; read `arrowVelocity()` directly.

### RangedWeaponApi

`ibx.lib.api.RangedWeaponApi`: where the ranged system's numbers are read. The item classes, hit resolution and the pull animation all take their values from here.

| Method | Description |
| --- | --- |
| `isManagedBow(weapon)` | Whether this bow uses this mod's ranged system: an `IbxBowItem` (including subclasses), or the vanilla bow when the takeover is on (see [4](#4-changes-to-vanilla-items)). Other mods' bows that extend the vanilla class do not count. |
| `isManagedCrossbow(weapon)` | The same for crossbows. |
| `getFullDrawTicks(shooter, weapon)` | Ticks to full draw (bow) / to load (crossbow) = draw time ÷ draw speed × 20, at least `MIN_FULL_DRAW_TICKS` (1). Callable on both sides. |
| `getChargePercent(chargeTicks, shooter, weapon)` | A bow's linear charge percent: ticks drawn ÷ full-draw ticks, capped at 1. |
| `getRangedDamage(shooter, weapon)` | The shooter's current ranged damage (attribute total). |
| `setAmmoRangedDamage(projectile, damage)` | Writes the damage snapshot onto a projectile. When an arrow hits, it replaces the vanilla damage. |
| `getAmmoRangedDamage(projectile)` | The damage snapshot on a projectile, an `Optional<Float>`; empty when there is none (arrows that did not go through this system). |
| `isAutoFire(weapon)` | Whether the stack's `ibx_lib:auto_fire` component is `true`; no component counts as `false`. |
| `tickAutoFire(shooter, weapon, remainingTicks)` | The per-tick auto-fire check, called from the item's `onUseTick`; returns whether it released. Players only. |

Ranged weapons that extend neither class can use the same resolution: write a damage snapshot onto the arrow (`AbstractArrow`) when firing, and the hit is resolved with it. If an arrow with a snapshot has the vanilla crit flag, the shooter's crit multiplier is applied on hit, so roll that flag yourself with `CriticalStrikeApi.roll`.

### Auto fire

A stack with `ibx_lib:auto_fire=true` releases by itself the moment it is fully drawn: a bow shoots, a crossbow finishes loading; holding right-click gives continuous fire.

- Players only; mobs' drawing and shooting are driven by their AI.
- The component is in no item's default components; only stacks that explicitly turn it on have it, e.g. `/give @s minecraft:bow[ibx_lib:auto_fire=true]`, or in code `stack.set(DataComponentRegistry.AUTO_FIRE.get(), true)`.
- Such items get a gold "Auto Fire" line right under the item name.

### Item models: the pull animation

Item model definitions live in `assets/<namespace>/items/<item>.json`. The vanilla bow's pull stages use `minecraft:use_duration` with `scale: 0.05`, which hard-codes a 20-tick full draw, so this mod registers a numeric property:

- **`ibx_lib:pull`** (`ibx.lib.client.RangedWeaponModelProperties.PULL`): ticks drawn ÷ full-draw ticks, 1 at full draw. It is 0 for a bow that is not being drawn and for a loaded Ibx crossbow.

**Bow**: copy the vanilla `items/bow.json` and replace `"property": "minecraft:use_duration"` and `"scale": 0.05` with `"property": "ibx_lib:pull"`; the thresholds stay.

```json
{
  "model": {
    "type": "minecraft:condition",
    "property": "minecraft:using_item",
    "on_false": {
      "type": "minecraft:model",
      "model": "examplemod:item/longbow"
    },
    "on_true": {
      "type": "minecraft:range_dispatch",
      "property": "ibx_lib:pull",
      "entries": [
        {
          "threshold": 0.65,
          "model": {
            "type": "minecraft:model",
            "model": "examplemod:item/longbow_pulling_1"
          }
        },
        {
          "threshold": 0.9,
          "model": {
            "type": "minecraft:model",
            "model": "examplemod:item/longbow_pulling_2"
          }
        }
      ],
      "fallback": {
        "type": "minecraft:model",
        "model": "examplemod:item/longbow_pulling_0"
      }
    }
  }
}
```

**Crossbow**: copy the vanilla `items/crossbow.json` as is and only change the model ids. For Ibx crossbows the vanilla `minecraft:crossbow/pull` already uses the attribute-based loading time; you may switch to `ibx_lib:pull` for consistency, with the same result.

```json
{
  "model": {
    "type": "minecraft:select",
    "property": "minecraft:charge_type",
    "cases": [
      {
        "when": "arrow",
        "model": {
          "type": "minecraft:model",
          "model": "examplemod:item/arbalest_arrow"
        }
      },
      {
        "when": "rocket",
        "model": {
          "type": "minecraft:model",
          "model": "examplemod:item/arbalest_firework"
        }
      }
    ],
    "fallback": {
      "type": "minecraft:condition",
      "property": "minecraft:using_item",
      "on_false": {
        "type": "minecraft:model",
        "model": "examplemod:item/arbalest"
      },
      "on_true": {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:crossbow/pull",
        "entries": [
          {
            "threshold": 0.58,
            "model": {
              "type": "minecraft:model",
              "model": "examplemod:item/arbalest_pulling_1"
            }
          },
          {
            "threshold": 1.0,
            "model": {
              "type": "minecraft:model",
              "model": "examplemod:item/arbalest_pulling_2"
            }
          }
        ],
        "fallback": {
          "type": "minecraft:model",
          "model": "examplemod:item/arbalest_pulling_0"
        }
      }
    }
  }
}
```

**Crossbow hold pose** (`ibx.lib.client.CrossbowArmPose`): in third person, vanilla only gives `minecraft:crossbow` the "holding a loaded crossbow" pose. This mod adds it for every `IbxCrossbowItem` automatically; you don't have to do anything.

- Exception: if you register a client extension (`IClientItemExtensions`, e.g. for custom rendering) for your crossbow, this mod no longer registers one for it (an item can only have one). In that case call `CrossbowArmPose.crossbowHold(entity, stack)` from your own `getArmPose`: it returns the hold pose when the crossbow is loaded and the arm is not swinging, otherwise `null`.

### Mobs using bows and crossbows

- **Mobs accept mod bows and crossbows**: the skeleton family (`AbstractSkeleton`) accepts any `BowItem` subclass, and pillagers and piglins accept any `CrossbowItem` subclass; no need to add them to NeoForge's `skeleton_usable_bows` and similar tags.
- **Bow-wielding mobs** (those using `RangedBowAttackGoal`, such as the skeleton family and the illusioner) holding an Ibx bow release only after the weapon's own draw time, and use the linear charge percent (`IbxBowItem#getChargePercent`); the arrow carries a damage snapshot = the mob's ranged damage × charge percent. Mobs do not go through the item's firing routine, so `prepareShot`, `getArrowVelocity` and `getMinCharge` only apply to players.
- **Crossbow-wielding mobs** holding an Ibx crossbow load for the attribute-based time and fire through the item's own routine, so the arrow flies at the weapon's speed (vanilla mobs use a fixed 1.6), carries a damage snapshot = the mob's ranged damage, and rolls its crit by the mob's critical chance.
- When a mob hits a player, the damage is scaled by difficulty as in vanilla.
- This mod's attributes are attached to every living entity, so a weapon in a mob's main hand applies its modifiers to the mob as well.

### Changed vanilla enchantments

This mod overrides three vanilla enchantment definitions under `data/minecraft/enchantment/`. These three files always apply; the startup config switches do not control them.

| Enchantment | File | What changed |
| --- | --- | --- |
| Power | `power.json` | One attribute effect added: main-hand `ibx_lib:ranged_damage` +3, and +1.5 per further level (I–V: 3 / 4.5 / 6 / 7.5 / 9). The vanilla arrow damage bonus is kept, so bows outside this mod's ranged system behave as before; arrows inside the system have their damage replaced by the snapshot on hit and only benefit from the attribute. |
| Quick Charge | `quick_charge.json` | One attribute effect added: main-hand `ibx_lib:draw_time` -0.25 seconds per level. The vanilla charge-time effect and loading sounds are kept, so crossbows outside this mod's system behave as before. Active slots changed from main hand + off hand to main hand only. |
| Thorns | `thorns.json` | The vanilla effect (a chance to deal 1–5 damage back, plus 2 extra durability loss) is replaced by an attribute effect: `ibx_lib:thorns` +1 per level. Active slots changed from `any` to `armor`. For how thorns is resolved, see [Attributes](ATTRIBUTE_en.md). |

> Whether an enchantment can be applied to your item still depends on the vanilla `#minecraft:enchantable/bow`, `#minecraft:enchantable/crossbow` etc. tags, which are not filled automatically.

---

## 4. Changes to vanilla items

Four classes in the `ibx.lib.item.vanilla` package change vanilla items. The first three are controlled by the startup config `config/ibx_lib-startup.toml`; the startup config is not synced, so server and client must be set the same, and changes need a restart (see [Configuration](CONFIG_en.md)).

| Class | What it changes | Switch |
| --- | --- | --- |
| `VanillaRangedWeapons` | The vanilla bow and crossbow use this mod's ranged system | `takeOverVanillaRangedWeapons`, default `true` |
| `VanillaArmors` | Attributes of netherite, golden and copper armor | `adjustVanillaArmors`, default `true` |
| `VanillaStackSizes` | Items that stack to 16 are raised to 64 | `raiseVanillaStackSizes`, default `true` |
| `VanillaSpawnEggs` | Wither and Ender Dragon spawn eggs are added to the creative tab | none |

### Vanilla bow and crossbow

Only the items `minecraft:bow` and `minecraft:crossbow` themselves are affected; other mods' bows and crossbows that extend the vanilla classes are left alone.

| | Ranged damage | Draw time | Arrow speed |
| --- | --- | --- | --- |
| Bow (`VanillaRangedWeapons.BOW`) | 6 | 1.0 s | 3.0 |
| Crossbow (`VanillaRangedWeapons.CROSSBOW`) | 7 | 1.25 s | 3.15 |

- **Numbers**: the ranged damage and draw time modifiers are baked into the two items' default components (`attribute_modifiers`), so the tooltip shows them; components overridden per stack (by commands or data packs) still win.
- **Behaviour** is the same as Ibx bows and crossbows (see [3](#3-ranged-weapons)): linear charge, damage snapshot, crits rolled by critical chance (vanilla: a bow only crits at full draw, a player's crossbow arrow always crits), no off-hand use, auto fire supported; mobs holding them are resolved by attributes too, and crossbow mobs shoot at speed 3.15.
- **Pull animation**: this mod's `assets/minecraft/items/bow.json` switches the vanilla bow's definition to `ibx_lib:pull`. With the switch off, the property uses 20 ticks for the vanilla bow, same as vanilla.
- To test a stack, use `RangedWeaponApi.isManagedBow` / `isManagedCrossbow`; to ask only about the switch, use `VanillaRangedWeapons.isEnabled()`.

### Vanilla armor

| Set | Armor | Spell resistance (head / chest / legs / feet) |
| --- | --- | --- |
| Netherite | Unchanged (toughness and knockback resistance too) | 3 / 8 / 6 / 3, equal to each piece's armor value |
| Golden | Removed | 3 / 8 / 6 / 3, the diamond set's armor values |
| Copper | Removed | 2 / 4 / 3 / 1, its former armor values |

Done the same way as the bow and crossbow: modifiers are baked into the default components, and components overridden per stack still win.

### Stack sizes

Vanilla items that only stack to 16 are raised to 64 (`VanillaStackSizes.STACK_SIZE`). Only these vanilla items themselves are affected; other mods' items are left alone:

| Category | Items |
| --- | --- |
| Signs | Signs of the 12 woods (oak, spruce, birch, jungle, acacia, cherry, dark oak, pale oak, mangrove, bamboo, crimson, warped) |
| Hanging signs | The same 12 |
| Banners | All 16 colors |
| Throwables | Snowball, egg, blue egg, brown egg, ender pearl |
| Others | Bucket (empty), armor stand, honey bottle |

- **Written books are not changed** and stay at 16.
- Done the same way as the bow and crossbow: the item's default component (`max_stack_size`) is changed, and components overridden per item stack still take priority.
- Filled buckets (water, lava and so on) already stack to 1 and are not affected.

### Spawn eggs

The Wither Spawn Egg and the Ender Dragon Spawn Egg are registered in vanilla but not in the creative inventory. This mod inserts them, in that order, after the Shulker Spawn Egg in the Spawn Eggs tab; eggs another mod has already added are not added again.

---

## 5. Magic implements

`ibx.lib.item.general.IbxMagicWeaponItem` is the base class of all casting items, with a subclass per kind: `ibx.lib.item.IbxTomeItem` (tomes) and `ibx.lib.item.IbxStaffItem` (staves). This section only covers the item side; for the casting flow see [Spell system](SPELL_en.md).

**What you get**:

- **Durability**: casting wears the item by the mana actually spent, 1 durability per 4 mana (`SpellCasting.MANA_PER_DURABILITY`); it breaks when used up. A negative durability makes it unbreakable: no durability bar, no wear from casting, and the tooltip shows vanilla's "Unbreakable". Not stackable.
- **Spell power**: `ibx_lib:spell_power` on the main hand, modifier id `ibx_lib:base_spell_power`, shown as the dark green merged line.
- **Movement speed while casting**: written into the vanilla `use_effects` component.
- **Built-in spells**: given by spell id; none if omitted.
- **Right-click casting**: automatically in `#ibx_lib:can_cast_spell`, so right-clicking with it in hand casts the selected spell.
- **Cast animations**: a player animation per cast type (needs Player Animation Library; without it the arms simply don't move).
- **Tags**: automatically in `#c:tools` and `#c:tools/magic_weapon`; tomes also in `#c:tools/tome`, staves also in `#c:tools/staff`.

### Constructor parameters

`IbxMagicWeaponItem` and `IbxTomeItem` have the same constructors:

| Constructor | Description |
| --- | --- |
| `(properties, durability, spellPower)` | Baseline cast move speed, no spells. |
| `(properties, durability, spellPower, castMoveSpeed)` | No spells. |
| `(properties, durability, spellPower, spells)` | Baseline cast move speed. |
| `(properties, durability, spellPower, castMoveSpeed, spells)` | All parameters. |

`IbxStaffItem` has the same four (with the default attack speed) plus one with attack speed: `(properties, durability, spellPower, castMoveSpeed, attackSpeed, spells)`. To change only the attack speed, pass `BASELINE_CAST_MOVE_SPEED` for `castMoveSpeed`.

| Parameter | Type | Description |
| --- | --- | --- |
| `properties` | `Item.Properties` | The one passed into the registration factory. Durability is set by the next parameter; don't set it yourself. |
| `durability` | `int` | Durability; negative means unbreakable. |
| `spellPower` | `float` | Spell power (main hand). |
| `castMoveSpeed` | `float` | Movement speed multiplier while casting, clamped to 0–1; 0 means standing still while casting; negative values use `BASELINE_CAST_MOVE_SPEED` (0.5). |
| `spells` | `List<Identifier>` | Ids of the built-in spells, in spell-wheel order. Ids that can't be found are skipped, so you can name another mod's spell without depending on it. |
| `attackSpeed` (staff only) | `float` | Main-hand attack speed base; displayed attack speed = this + 4 (the player's). Default `DEFAULT_ATTACK_SPEED` (-2.8, shown as 1.2). |

When extending `IbxMagicWeaponItem` for a new kind of implement that adds its own attribute modifiers, call `IbxAttributeModifiers.add(properties, modifiers)` before passing `properties` to the parent (this is how the staff adds attack speed). In the tooltip spell power comes first, then yours, then the ones given at registration.

```java
public static final DeferredItem<IbxTomeItem> EMBER_TOME = ITEMS.registerItem("ember_tome",
        p -> new IbxTomeItem(p, 1000, 6.0F, List.of(Identifier.fromNamespaceAndPath("examplemod", "fireball"))));

public static final DeferredItem<IbxStaffItem> OAK_STAFF = ITEMS.registerItem("oak_staff",
        p -> new IbxStaffItem(p, 800, 4.0F, 0.7F));
```

### Built-in spells

- The spells given to the constructor are the item's default. When a stack has the `ibx_lib:spells` component, the component wins, so commands and loot can override it; an empty list means no spells.
- `getSpells(stack)` returns the spells the item provides right now; `innateSpells(stack)` always returns the ones given to the constructor.
- Implements implement `ibx.lib.spell.SpellContainer`. For any other item to provide spells, put the `ibx_lib:spells` component on the stack; it does not need to extend an implement class.
- **Tooltip**: whenever an item provides spells, a "Spells:" line and each spell's name appear under the item name; nothing is shown when the item hides the `ibx_lib:spells` component with `tooltip_display`.

### Overridable methods

| Method | Default | Description |
| --- | --- | --- |
| `public Identifier castAnimation(CastType type)` | The tome's three: `ibx_lib:tome.cast_instant` / `tome.cast_hold` / `tome.cast_channel`; the staff uses `ibx_lib:staff.cast_*` | The player animation this implement plays when casting, by cast type (instant, cast, and one shared by channel and charge). A spell that names its own animation takes precedence. |
| `public List<Holder<Spell>> getSpells(ItemStack stack)` | The component if present, otherwise the constructor's spells | Override to vary by item state (charges, sockets, etc.). |

`IbxMagicWeaponItem.defaultCastAnimation(type)` is the tome's three; casting items that are not Ibx implements use it too. `spellPower()` returns the spell power given to the constructor.

### Tome hand models

This mod ships a pair of 3D hand models for tomes: the closed `ibx_lib:item/general_tome_hand` and the open `ibx_lib:item/general_tome_hand_open`. The pages use a texture bundled with this mod; you only supply the cover texture `cover`:

| Region of the `cover` texture (as 16×16) | Used for |
| --- | --- |
| (0, 0)–(8, 6) | Front cover |
| (8, 0)–(16, 6) | Back cover |
| The pixel at (0, 6) | Edges of the front and back cover |
| The pixel at (0, 9) | Spine |

`assets/examplemod/models/item/ember_tome_hand.json` (the open one is the same with `general_tome_hand_open` as parent):

```json
{
  "parent": "ibx_lib:item/general_tome_hand",
  "textures": {
    "cover": "examplemod:item/ember_tome_cover"
  }
}
```

In the item model definition, switch between closed and open with the condition **`ibx_lib:casting`**: true while this item is in hand and casting. Unlike vanilla's `minecraft:using_item`, instant spells count too (an instant spell does not start "using item", but the condition stays true while the cast animation plays; without Player Animation Library instant spells do not count).

`assets/examplemod/items/ember_tome.json`: the 3D model in hand, the flat icon everywhere else (inventory, dropped item, etc.).

```json
{
  "model": {
    "type": "minecraft:select",
    "property": "minecraft:display_context",
    "cases": [
      {
        "when": [
          "firstperson_righthand",
          "firstperson_lefthand",
          "thirdperson_righthand",
          "thirdperson_lefthand"
        ],
        "model": {
          "type": "minecraft:condition",
          "property": "ibx_lib:casting",
          "on_true": {
            "type": "minecraft:model",
            "model": "examplemod:item/ember_tome_hand_open"
          },
          "on_false": {
            "type": "minecraft:model",
            "model": "examplemod:item/ember_tome_hand"
          }
        }
      }
    ],
    "fallback": {
      "type": "minecraft:model",
      "model": "examplemod:item/ember_tome"
    }
  }
}
```

### Staff hand models

This mod ships no staff model. The staff cast animations (right hand raised, staff tilted forward) assume this grip: the hand model runs along +Y, the grip point is at the model's center, and the third-person display has no rotation, i.e. the staff is perpendicular to the arm. Set up a new staff's hand model with this grip and the bundled animations work as they are.

A staff carries a main-hand attack speed modifier; when it is used in melee, that is what sets the swing speed. With Better Combat installed a staff uses `bettercombat:wand` by default (one-handed stab, constant `IbxStaffItem.PRESET`), written into the item's default components, so no `weapon_attributes` file is needed; change it as for melee weapons (see [2](#hand-models-and-left-click-moves)).

---

## 6. Shields

The base class `ibx.lib.item.general.IbxAbstractShieldItem` (abstract) handles blocking; subclasses are split by one- or two-handed:

| Class | Description |
| --- | --- |
| `ibx.lib.item.IbxShieldItem` | Plain (one-handed) shield: blocks only, works like a vanilla shield in either hand, and can be held together with a one-handed weapon. |
| `ibx.lib.item.IbxTwoHandedShieldItem` | Two-handed shield: in the main hand it can also attack normally and shield bash. |

### Blocking

Blocking goes entirely through the vanilla `blocks_attacks` component, baked from the constructor parameters:

| Parameter | Type | Description |
| --- | --- | --- |
| `properties` | `Item.Properties` | Only set durability and repair items when registering; `blocks_attacks`, the off-hand equipment slot and the break sound are added by the base class. |
| `blockDelay` | `float` | Seconds after raising before it blocks; 0 blocks the moment it is raised; negative values use `BASELINE_BLOCK_DELAY` (0.25). |
| `blockFactor` | `float` | Fraction blocked, 0–1; values not above 0 use `BASELINE_BLOCK_FACTOR` (1.0, blocks everything). |
| `blockAngle` | `float` | Horizontal blocking angle: degrees to each side of straight ahead; values not above 0 use `BASELINE_BLOCK_ANGLE` (90). |

- Everything else matches the vanilla shield: damage in `#minecraft:bypasses_shield` is not blocked, and the durability loss on block, block sound and break sound are the same.
- It extends the vanilla `ShieldItem`, so the first-person raised display matches the vanilla shield.
- Tags: automatically in `#c:tools` and `#c:tools/shield`, plus `#c:tools/one_handed_shield` or `#c:tools/two_handed_shield` by subclass.

### One-handed shield

| Constructor | Description |
| --- | --- |
| `IbxShieldItem(properties)` | All blocking numbers are the vanilla shield's. |
| `IbxShieldItem(properties, blockDelay, blockFactor, blockAngle)` | Custom blocking numbers. |

```java
// quick to raise, but blocks only 60%, within 60° to each side
public static final DeferredItem<IbxShieldItem> BUCKLER = ITEMS.registerItem("buckler",
        p -> new IbxShieldItem(p.durability(250).repairable(ItemTags.IRON_TOOL_MATERIALS), 0.1F, 0.6F, 60.0F));
```

### Two-handed shield

| Constructor | Description |
| --- | --- |
| `(properties, attackDamage, attackSpeed)` | Vanilla blocking numbers. |
| `(properties, blockDelay, blockFactor, blockAngle, attackDamage, attackSpeed)` | All parameters. |

| Parameter | Type | Description |
| --- | --- | --- |
| `attackDamage` | `float` | Damage base of the main-hand normal attack (the display adds 1 for the bare hand). Shield bash damage uses it too. |
| `attackSpeed` | `float` | Main-hand attack speed base (the display adds the player's 4). |

- **Main-hand normal attack**: left-click is the vanilla normal attack, with damage and speed from the two parameters above.
- **Passive attributes** (e.g. spell resistance): give them with `attributes(...)` on the `Properties` at registration; they are merged with attack damage and attack speed, after them.
- **Two-handedness and attack moves** are left to Better Combat (see [Resource files](#resource-files-for-two-handed-shields) below): with it in the main hand, the off hand is treated as empty. Without Better Combat it is not two-handed, the off hand holds items as usual, and normal attacks are the vanilla swing.
- **Shield bash**: left-click while the shield is raised in the main hand. While it is raised, left-click can only be a shield bash; normal attacks during that time are cancelled.
- The shield bash is main hand only. In the off hand there is no bash: with Better Combat it is treated as an empty hand, without it it is a vanilla shield.

### Shield bash flow

1. **Client input** (`ibx.lib.client.ShieldInput`): while a two-handed shield is in the main hand and right-click is held, left-click is no longer passed to vanilla or Better Combat. Pressing left-click while the shield is raised and the bash is off cooldown sends a `ShieldBashPayload` (`ibx_lib:shield_bash`, no data).
2. **Server check** `ShieldApi.tryBash(player)`: only if the main hand holds a two-handed shield, the main hand is raising it and the cooldown is over does it call `shield.bash(player, stack)`.
3. **`IbxTwoHandedShieldItem#bash`**, in order:
   - `ShieldApi.markBash` records the bash and starts the cooldown (`bashCooldownTicks()`); the state is synced to the player and those nearby;
   - swings the arm and plays a sound;
   - calls `onBash` while the shield is still raised, then passes the mobs hit to the curio trigger hooks (see [8](#trigger-hooks));
   - disables the shield for `bashDisableTicks()` (item cooldown + stop using, without the break sound). If right-click is still held, the shield is raised again by itself once that ends.
4. **`onBash` by default**: rolls one crit, hits every target from `bashTargets` with `bashHit`, and calls `onBashHit` for those actually hit.
5. **The client** fires `ShieldBashEvent` when the synced bash state arrives; the bash animation is driven by it.

The bash cooldown is its own timer, not the item cooldown (an item cooldown would stop the shield from being raised).

### Overridable shield bash methods

Numbers (override per shield to tune):

| Method | Default | Description |
| --- | --- | --- |
| `protected int bashCooldownTicks()` | `BASH_COOLDOWN_TICKS` = 60 | Bash cooldown in ticks. |
| `protected int bashDisableTicks()` | `BASH_DISABLE_TICKS` = 10 | Ticks the shield is disabled after a bash. |
| `protected double bashRange()` | `BASH_RANGE` = 2.5 | Reach: blocks in front. |
| `protected float bashHalfAngle()` | `BASH_HALF_ANGLE` = 45 | Reach: degrees to each side of straight ahead. |
| `protected float bashKnockback()` | `BASH_KNOCKBACK` = 0.6 | Knockback on top of the hurt knockback; the knockback attribute and Knockback enchantment add more. |

Resolution:

| Method | Default | Description |
| --- | --- | --- |
| `protected void onBashHit(ServerPlayer player, ItemStack stack, LivingEntity target, boolean critical)` | Does nothing | Extra effects after one target is hit (damage and knockback already applied), e.g. root, ignite, slow. This is the one most often overridden. |
| `protected List<LivingEntity> bashTargets(ServerPlayer player)` | Visible enemies in the fan, nearest first; invulnerable entities and ones projectiles can't hit are excluded | Who the bash hits. |
| `protected boolean bashHit(ServerPlayer player, ItemStack stack, LivingEntity target, boolean critical)` | Damage = the player's attack damage, × (1 + critical damage) on a crit; enchantments, durability and stats as for a normal attack, no sweep. It only counts as a hit if the target actually took damage (not when blocked by a shield or during invulnerability frames); knockback is applied on a hit | Hits one target; returns whether it hit. |
| `protected List<LivingEntity> onBash(ServerPlayer player, ItemStack stack)` | All of the above, returning the mobs hit | Override to replace the bash effect entirely; the return value goes to curios' `onShieldBash`. |
| `public void bash(ServerPlayer player, ItemStack stack)` | Cooldown, swing, resolution, disabling the shield | The bash framework; usually not overridden. |
| `public Identifier bashAnimation()` | `ibx_lib:shield.bash` | The player animation played once for a bash. |
| `public Identifier guardAnimation()` | `ibx_lib:shield.guard` | The player animation looped while the shield is raised in the main hand. |

```java
public class TowerShieldItem extends IbxTwoHandedShieldItem {
    public TowerShieldItem(Item.Properties properties) {
        super(properties, 5.0F, -3.0F);
    }

    @Override
    protected int bashCooldownTicks() {
        return 100;
    }

    /** Targets hit by the bash are slowed for 2 seconds. */
    @Override
    protected void onBashHit(ServerPlayer player, ItemStack stack, LivingEntity target, boolean critical) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
    }
}

private static final Identifier TOWER_SHIELD_RESISTANCE = Identifier.fromNamespaceAndPath("examplemod", "tower_shield_resistance");

public static final DeferredItem<TowerShieldItem> TOWER_SHIELD = ITEMS.registerItem("tower_shield",
        p -> new TowerShieldItem(p.durability(600).repairable(ItemTags.IRON_TOOL_MATERIALS)
                .attributes(ItemAttributeModifiers.builder()
                        .add(AttributeRegistry.SPELL_RESISTANCE, new AttributeModifier(TOWER_SHIELD_RESISTANCE, 4.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build())));
```

### ShieldApi, ShieldBash and ShieldBashEvent

`ibx.lib.api.ShieldApi`:

| Method | Description |
| --- | --- |
| `isRaisingTwoHandedShield(entity)` | Whether the main hand is raising a two-handed shield (the precondition for a bash). Readable on both sides. |
| `isBashReady(entity)` | Whether the bash cooldown is over. Readable on both sides. |
| `getState(entity)` | The bash state `ShieldBash`; `null` if the entity has never bashed. |
| `tryBash(player)` | Checks, then bashes. Server only. |
| `markBash(entity, cooldownTicks)` | Records the time and cooldown of this bash and syncs it. Server only; `bash` already calls it. |

- `ibx.lib.api.ShieldBash`: a record of `lastBashTick` (game time of the last bash, -1 if none) and `cooldownUntil` (game time the cooldown ends). Not saved; synced to the player and those nearby. Read and write it through `ShieldApi`.
- `ibx.lib.api.event.ShieldBashEvent`: an entity performed a shield bash. Posted on `NeoForge.EVENT_BUS`, **client side only**, for the local player and nearby ones alike. `getEntity()`, `getState()`.

### Guard and bash animations

`ibx.lib.client.ShieldAnimations` plays the animations with Player Animation Library, visible to the player and those nearby. Player Animation Library is optional; without it no animation plays and everything else works.

- **Guard**: while a two-handed shield is raised in the main hand, `guardAnimation()` loops; on lowering it fades back to the previous pose. Third person only; first person draws the vanilla raised shield.
- **Bash**: on `ShieldBashEvent`, `bashAnimation()` plays once, starting from the guard pose, and shows in first person too.
- Animations are authored for the right hand and mirrored when the main hand is the left. If the animation file doesn't exist, nothing plays.
- To change them: override `guardAnimation()` / `bashAnimation()` and return your own animation id. Put the animation file under your mod's `assets/<namespace>/player_animations/`; the id is "namespace:animation name under `animations` in the file", independent of the file name.

This mod's `assets/ibx_lib/player_animations/shield_attack.json` has five: `shield.guard`, `shield.bash`, and `shield.pose`, `shield.attack_swipe`, `shield.attack_slam` for Better Combat.

### Resource files for two-handed shields

**Better Combat**: this mod ships a preset, `data/ibx_lib/weapon_attributes/two_handed_shield.json` (id `ibx_lib:two_handed_shield`, constant `IbxTwoHandedShieldItem.PRESET`): two-handed, idle pose `ibx_lib:shield.pose`, attack range -0.5, and a two-move normal attack: a swipe (120°) and a slam (90°, damage ×1.2). Two-handed shields use it by default, written into the item's default components, so no shield needs its own `weapon_attributes` file. To change it, call `BetterCombatCompat.preset` at registration, as for melee weapons (see [2](#hand-models-and-left-click-moves)).

**In-hand display**: this mod ships two parent models that only contain display transforms (`display`); geometry and textures come from your own models:

| Model | For |
| --- | --- |
| `ibx_lib:item/general_two_handed_shield_hand` | Not raised |
| `ibx_lib:item/general_two_handed_shield_block` | Raised. Differs from the other only in the first-person display (moved to the vanilla raised-shield position). |

The item model definition branches on `minecraft:using_item`, with each branch's model using one of these as parent:

```json
{
  "model": {
    "type": "minecraft:condition",
    "property": "minecraft:using_item",
    "on_false": {
      "type": "minecraft:model",
      "model": "examplemod:item/tower_shield"
    },
    "on_true": {
      "type": "minecraft:model",
      "model": "examplemod:item/tower_shield_blocking"
    }
  }
}
```

Apart from first person, the two branches must display the same:

- If third person differs, the bash stops the use and switches back to the not-raised display, and the shield turns in the hand for a moment.
- The shield in the inventory also takes the raised branch while it is raised; without a `gui` display there, its icon would change.

---

## 7. 3D armor

`ibx.lib.item.IbxGeoArmorItem`: an armor piece whose worn look is drawn by GeckoLib. It only covers "what it looks like when worn"; armor, durability, slot and repair material come from the item properties at registration as usual, see [Armor material](#armor-material) and [Armor item properties](#armor-item-properties) below.

> GeckoLib is required to use it: GeckoLib is an optional dependency of this mod, so your mod must require it itself.

| Constructor | Description |
| --- | --- |
| `IbxGeoArmorItem(properties, model)` | No glow texture. |
| `IbxGeoArmorItem(properties, model, glow)` | When `glow` is `true`, a glow texture is layered on top. |

| Parameter | Type | Description |
| --- | --- | --- |
| `properties` | `Item.Properties` | Set the material and slot at registration: use `IbxArmorProperties`, or vanilla `humanoidArmor`. |
| `model` | `Identifier` | Model id. Pieces of one set pass the same id and share one model and one texture. |
| `glow` | `boolean` | Whether there is a glow texture; `false` when omitted. Passing `true` without that file draws the missing texture. |

```java
private static final Identifier PLATE_MODEL = Identifier.fromNamespaceAndPath("examplemod", "bone_plate");

public static final DeferredItem<IbxGeoArmorItem> BONE_HELMET = ITEMS.registerItem("bone_helmet",
        p -> new IbxGeoArmorItem(IbxArmorProperties.of(p, BONE_MATERIAL, ArmorType.HELMET).build(), PLATE_MODEL, true));
public static final DeferredItem<IbxGeoArmorItem> BONE_CHESTPLATE = ITEMS.registerItem("bone_chestplate",
        p -> new IbxGeoArmorItem(IbxArmorProperties.of(p, BONE_MATERIAL, ArmorType.CHESTPLATE).build(), PLATE_MODEL, true));
```

### Armor material

`ibx.lib.item.general.IbxArmorMaterial`: the material shared by one armor set. It has everything the vanilla `ArmorMaterial` has, plus per-slot spell resistance right after armor. The vanilla material is a record and cannot be extended, so this is a class of its own, with the parameters in vanilla's order.

```java
public static final IbxArmorMaterial BONE_MATERIAL = new IbxArmorMaterial(
        37,                                       // durability
        IbxArmorMaterial.pieces(3, 8, 6, 3),      // defense
        IbxArmorMaterial.pieces(3, 8, 6, 3),      // spellResistance
        15,                                       // enchantmentValue
        SoundEvents.ARMOR_EQUIP_NETHERITE,        // equipSound
        3.0F,                                     // toughness
        0.1F,                                     // knockbackResistance
        ItemTags.REPAIRS_NETHERITE_ARMOR,         // repairIngredient
        ResourceKey.create(EquipmentAssets.ROOT_ID,
                Identifier.fromNamespaceAndPath("examplemod", "bone_plate")));  // assetId
```

| Parameter | Type | Description |
| --- | --- | --- |
| `durability` | `int` | Durability multiplier: each piece's durability = slot base (head 11, chest 16, legs 15, feet 13) x this value. |
| `defense` | `Map<ArmorType, Integer>` | Armor value per slot. |
| `spellResistance` | `Map<ArmorType, Integer>` | Spell resistance per slot. A slot that is missing or 0 does not get this attribute. |
| `enchantmentValue` | `int` | Enchantability. |
| `equipSound` | `Holder<SoundEvent>` | Equip sound. |
| `toughness` | `float` | Armor toughness, the same on every piece. |
| `knockbackResistance` | `float` | Knockback resistance, the same on every piece. 0 leaves this attribute out. |
| `repairIngredient` | `TagKey<Item>` | Repair material. |
| `assetId` | `ResourceKey<EquipmentAsset>` | Equipment asset id. |

| Method | Description |
| --- | --- |
| `IbxArmorMaterial.pieces(helmet, chestplate, leggings, boots)` | One value for each of the four slots; use it for both `defense` and `spellResistance`. |
| `IbxArmorMaterial.of(material, spellResistance)` | Copies a vanilla `ArmorMaterial` and adds per-slot spell resistance. |
| `toVanilla()` | The vanilla `ArmorMaterial` without spell resistance, for places that need a vanilla material. |
| `createAttributes(type)` | The attribute modifiers of this slot. |

- **Tooltip order**: armor, spell resistance, toughness, knockback resistance.
- It is only for registering humanoid armor. Wolf armor, horse armor and the like go through the vanilla `Item.Properties` methods with `toVanilla()`, which carries no spell resistance.

### Armor item properties

`ibx.lib.item.general.IbxArmorProperties`: builds the item properties of one humanoid armor piece. The material brings armor, spell resistance, toughness and knockback resistance; on top of that you add the piece's own attributes one by one. It is unrelated to 3D armor, so ordinary armor (drawn by the vanilla armor layer) can use it too.

```java
public static final DeferredItem<IbxGeoArmorItem> BONE_HELMET = ITEMS.registerItem("bone_helmet",
        p -> new IbxGeoArmorItem(IbxArmorProperties.of(p, BONE_MATERIAL, ArmorType.HELMET)
                .add(Attributes.MAX_HEALTH, 2)
                .fireResistant()
                .build(), PLATE_MODEL, true));
```

| Method | Description |
| --- | --- |
| `IbxArmorProperties.of(properties, material, type)` | Start: equivalent to `properties.humanoidArmor(material, type)`, with the attributes starting from what the material brings. `material` is an `IbxArmorMaterial`, or a vanilla `ArmorMaterial` (no spell resistance). |
| `add(attribute, amount)` | Adds one attribute as a flat value. |
| `add(attribute, amount, operation)` | Adds one attribute with your own operation (use `ADD_MULTIPLIED_BASE` for percentages). |
| `fireResistant()` | Fire resistant: does not burn in lava or fire. |
| `build()` | Returns the `Item.Properties`; you can keep setting rarity and so on afterwards. Do not call `Properties#attributes` after it, or everything added above is overwritten. |

- The added modifiers all use the vanilla armor id `minecraft:armor.<slot>` (a slot holds only one piece, so they never clash). That means **a piece can have only one modifier per attribute**: armor, spell resistance, toughness and knockback resistance already come from the material and cannot be `add`ed again; a duplicate throws at registration. To change them, change the material.
- When you only need the attribute modifiers and not an `Item.Properties` (changing an existing item in `ModifyDefaultComponentsEvent`, for example), use `IbxArmorProperties.modifiers(material, type)` (`material` takes the same two kinds): the same `add`, plus `remove(attribute)` to drop one of the material's attributes, and `build()` returns an `ItemAttributeModifiers`. This mod's vanilla armor adjustments (see [4](#4-changes-to-vanilla-items)) go through it.

### Resource files

The model id `examplemod:bone_plate` maps to:

| File | Description |
| --- | --- |
| `assets/examplemod/geckolib/models/armor/bone_plate.geo.json` | The model; all pieces live in this one file. |
| `assets/examplemod/textures/armor/bone_plate.png` | The texture. |
| `assets/examplemod/textures/armor/bone_plate_glowmask.png` | The glow texture, used only when `glow` is `true`. Glowing pixels copy the colors of the texture; the rest is transparent. |

Bone names follow GeckoLib's armor template; each piece draws only the bones of its own slot (with their child bones):

| Item slot | Bones drawn |
| --- | --- |
| Helmet | `armorHead` |
| Chestplate | `armorBody`, `armorRightArm`, `armorLeftArm` |
| Leggings | `armorRightLeg`, `armorLeftLeg` |
| Boots | `armorRightBoot`, `armorLeftBoot` |

- **The inventory icon** is an ordinary item model, unrelated to the 3D model; write `items/<item name>.json` and the item texture as usual.
- **Equipment asset id**: the `assetId` in the material still has to be filled in, but the vanilla armor layer does not draw this armor, so no `equipment/*.json` is needed. Armor trims go through the vanilla armor layer, so trims are not shown.
- **Tags**: armor is not among the automatic tags in [9](#9-automatic-item-tags). To make it enchantable, add the items to `#minecraft:head_armor`, `#minecraft:chest_armor`, `#minecraft:leg_armor` and `#minecraft:foot_armor` in your own data.
- **The vanilla first-person arm** does not show armor, same as vanilla and GeckoLib's default.

### First-person animations

When Player Animation Library plays a first-person animation (Better Combat's attacks, for example), it temporarily draws the player's own body. It only hides armor drawn by the vanilla armor layer; GeckoLib armor does not go through that path, so left alone the helmet ends up plastered over the camera.

The renderer `ibx.lib.client.renderer.IbxGeoArmorRenderer` treats that pass the way the library treats vanilla armor:

- Helmet, leggings and boots are not drawn.
- The chestplate does not draw the body; an arm is drawn only when the animation turns on "show armor in first person" and shows that arm.
- Third person, other players, the inventory player preview and sleeping are drawn as usual. Without Player Animation Library there is no such pass, and everything is drawn as usual.

To test for that pass yourself (for anything else that does not go through the vanilla armor layer), use `ibx.lib.compat.playeranim.PlayerAnimFirstPerson`, client side only:

| Method | Description |
| --- | --- |
| `isFirstPersonPass(state)` | Whether this entity render state is being drawn as the first-person pass. |
| `showsArmArmor(state, arm)` | Whether armor on this arm is drawn in that pass; returns `false` outside of it. |

Without Player Animation Library both methods return `false` and never touch its classes, so there is no need to check whether it is installed first. To just ask whether it is installed, use `ibx.lib.compat.PlayerAnimCompat.isLoaded()`.

### What you can override

| What you want | How |
| --- | --- |
| Add animations | Extend `IbxGeoArmorItem` and override `registerControllers` to add animation controllers; the animation file is `geckolib/animations/armor/<name>.animation.json`. |
| Change how it is drawn (extra render layers, a different bone mapping, etc.) | Extend `IbxGeoArmorRenderer` (same constructor parameters as the item: model id, `glow`; `glow` can be omitted here too), then override `createGeoRenderer` in an item subclass to use it. |
| Change which parts the first-person pass draws | Override `IbxGeoArmorRenderer#firstPersonSegments(wearer, slot, segments)` and return the parts to draw; `segments` is what the slot normally draws. |

---

## 8. Curios

Curios (mod id `curios`) is an optional dependency of this mod. A curio is a plain item; slots and attribute bonuses are set up the Curios way. This mod provides the following.

**`ibx.lib.item.IbxCurioItem`**: base class for curios. A plain curio is just `new`ed, with the common settings chained on; a curio with its own mechanics extends it and overrides methods from Curios' `ICurioItem` or the [trigger hooks](#trigger-hooks). Curios picks it up automatically; nothing else to attach.

```java
// Plain curios
public static final DeferredItem<IbxCurioItem> SIGNET_RING = ITEMS.registerItem("signet_ring",
        p -> new IbxCurioItem(p).unique(), p -> p.stacksTo(1));

public static final DeferredItem<IbxCurioItem> GOGGLES = ITEMS.registerItem("goggles",
        p -> new IbxCurioItem(p).unique().effect(MobEffects.NIGHT_VISION).enderMask().keepOnDeath(),
        p -> p.stacksTo(1));

// With its own mechanics: set things up in the constructor, and call super when overriding
public class WhoopieCushionItem extends IbxCurioItem {
    public WhoopieCushionItem(Properties properties) {
        super(properties);
        keepOnDeath();
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        // per-tick logic
    }
}
```

| Method | Description |
| --- | --- |
| `unique()` | Only one of the same curio can be worn. If another curio slot already holds the same item, it can't be equipped; putting it back into its own slot doesn't count. Without it, several can be worn and each one counts. |
| `effect(effect)` / `effect(effect, amplifier)` | The wearer always has this status effect: infinite, no flicker, no particles, no HUD icon; removed when taken off. Call it several times for several effects. `amplifier` is as in `MobEffectInstance`; 0 is level I. |
| `enderMask()` | Looking at endermen doesn't anger them, like wearing a carved pumpkin. |
| `keepOnDeath()` | Not dropped on death; stays in the curio slot. |
| `piglinNeutral()` | Piglins don't attack the wearer, like wearing gold armor. |

- **Finish the settings while constructing the item**: in the `registerItem` factory, or in the subclass constructor. Slots and attribute bonuses are still set up the Curios way (item tags, the `curios:attribute_modifiers` component).
- **Permanent effects and potions**: while a stronger potion of the same effect is active it is left alone, and the curio's effect comes back when the potion runs out; a potion that is not stronger is covered up. Taking the curio off only removes the effect it gave (infinite, no particles, same level); the same effect from elsewhere stays.
- **Call `super` when overriding**: override `canEquip`, `curioTick` or `onUnequip` without calling `super` and `unique()` / the permanent effects stop working. Overriding `isEnderMask`, `makesPiglinsNeutral` or `getDropRule` replaces the setting.
- **Mods using it must require Curios**: `IbxCurioItem` implements a Curios interface, so loading it without Curios installed throws `NoClassDefFoundError`.

**`ibx.lib.compat.CuriosCompat`**:

| Method | Description |
| --- | --- |
| `isLoaded()` | Whether Curios is installed. |
| `forEachCurio(entity, action)` | Passes each item in the curio slots (skipping empty ones), in Curios' slot order. Usable on both sides. |
| `anyCurio(entity, condition)` | Whether any item in the curio slots matches. Usable on both sides. |
| `onCurioChange(listener)` | Notifies when a curio changes; `listener` receives the wearer. Server side only. |

> Every `CuriosCompat` method except `isLoaded()` touches Curios classes. If Curios is optional for your mod too, check `CuriosCompat.isLoaded()` before calling them, or you get a `NoClassDefFoundError` when Curios isn't installed.

Curios can provide spells too: items in curio slots that carry the `ibx_lib:spells` component count towards the spells a player can cast (see [Spell system](SPELL_en.md)).

### Trigger hooks

**`ibx.lib.item.general.CurioTriggers`**: combat trigger points for curios. `IbxCurioItem` already implements it, so extend that and override the methods you need; the rest do nothing by default. A curio item class that doesn't extend `IbxCurioItem` can implement the interface itself.

```java
public class VampireFangItem extends IbxCurioItem {
    public VampireFangItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onDamageDealt(SlotContext slot, ItemStack stack, LivingEntity target, DamageSource source, float amount) {
        // heal 10% of the damage dealt
        slot.entity().heal(amount * 0.1F);
    }
}
```

| Method | When | What it can do |
| --- | --- | --- |
| `onDealDamage(slot, stack, target, source, amount)` | The wearer hits something: this mod's base damage (ranged damage, ranged crits) is settled, armor and spell resistance are not applied yet | Return the new damage amount |
| `onTakeDamage(slot, stack, source, amount)` | The wearer is hit, at the same point, after the attacker's curios | Return the new damage amount |
| `onDamageDealt(slot, stack, target, source, amount)` | After a hit, once damage is resolved and health is taken; `amount` is the final damage, called only when above 0 | Notification only |
| `onDamageTaken(slot, stack, source, amount)` | After being hit, as above | Notification only |
| `onKill(slot, stack, victim, source)` | The wearer kills a mob; kills by summons count for the owner | Notification only |
| `onSpellCastStart(slot, stack, event)` | Before a cast starts, once all conditions passed (`SpellCastEvent.Pre`) | `setManaCost`, `setCooldownTicks`, `setCanceled(true)` |
| `onSpellCast(slot, stack, event)` | After each time a spell takes effect, every pulse for channeled spells (`SpellCastEvent.Post`) | Notification only |
| `onSummon(slot, stack, summon)` | After a summon is summoned | Notification only; can give the summon effects |
| `onBlock(slot, stack, source, blocked)` | A raised shield blocks an attack, vanilla shields and this mod's shields alike; `blocked` is the amount blocked | Notification only |
| `onShieldBash(slot, stack, shield, hits)` | After a shield bash; `hits` are the mobs hit, possibly empty | Notification only |

- **Server side only**, and only for curios worn in curio slots (cosmetic slots don't count). `slot` is the slot the curio is in, `slot.entity()` is the wearer; `stack` is the curio itself.
- **Called piece by piece in slot order**: wear several of the same and it is called that many times, and damage changes stack one after another. Set `unique()` above for curios that should not stack.
- **Cooldowns and trigger chances are up to the curio** (for example `Player#getCooldowns`, `slot.entity().getRandom()`); the framework does not handle them.
- **Damage**: melee, projectiles (the wearer being the shooter) and spells all go through `onDealDamage` / `onDamageDealt`; tell them apart by `source`. Changed damage is still reduced by armor and spell resistance, like vanilla enchantment damage bonuses. On a killing blow, `onKill` is called before `onDamageDealt`.
- **Anything canceled by another mod does not trigger**: canceled damage, deaths and blocks are never passed on.
- **Shield bash**: called from the server flow of `IbxTwoHandedShieldItem`. For shields that override `onBash`, its return value is the `hits` passed here.
- Dispatch lives in `ibx.lib.compat.curios.CurioTriggerHandler`.

> `CurioTriggers` references Curios classes (`SlotContext`): the mod containing a curio that implements it must require Curios.

### Elytra slot

This mod registers the Curios slot `elytra`: an elytra in it glides and loses durability just like one worn in the chest slot.

**0 slots by default**: this mod only provides the slot and the behavior, so players don't see it. A mod that wants it adds the slots itself, either way:

- Write `data/<your mod id>/curios/slots/elytra.json` in your data. When several mods set `size`, the largest wins, so 1 means one slot:

  ```json
  {
    "size": 1
  }
  ```

- Use a Curios slot modifier per player, e.g. `ICuriosItemHandler#addPermanentSlotModifier("elytra", id, 1, AttributeModifier.Operation.ADD_VALUE)`.

Rules:

- **What fits is decided by tags, like other Curios slots**: items in `#curios:elytra` fit, and so do items in `#curios:curio` (which fit every slot). This mod puts the vanilla elytra in `#curios:elytra` (`data/curios/tags/item/elytra.json`); other mods add their own elytra to that tag, and can take the vanilla one out with `"remove"`.
- **Only gliders fly**: an item in the slot glides only if it has the `minecraft:glider` component, which the vanilla elytra and modded elytra built the vanilla way have; anything else just sits in the slot.
- **Gliding as in vanilla**: you can't take off on the ground, while riding, while levitating or while flying in creative; an elytra one point from breaking doesn't count (as in vanilla, an elytra never actually breaks).
- **Durability**: with usable elytra in both the chest slot and the elytra slot, each durability tick picks one of them at random, the same way vanilla handles several glider items. Mending still repairs it (Curios handles that).
- **Looks**: an elytra in the elytra slot is drawn on the player's back the vanilla way (enchantment glint, skin elytra textures and cape patterns included), and the cape is hidden while it is drawn. If the slot's cosmetic slot holds something, that is drawn instead; otherwise the slot's render toggle decides. With an elytra in the chest slot too, only the chest one is drawn; when invisible it is hidden along with other equipment per the hide-equipment-when-invisible config. The empty slot icon is `ibx_lib:slot/empty_elytra_slot` (`assets/ibx_lib/textures/slot/empty_elytra_slot.png`).
- The slot is only opened for players. The slot file is `data/ibx_lib/curios/slots/elytra.json`; the code is `ibx.lib.compat.curios.ElytraCurioSlot` (slot id `SLOT`, `isGlider`, `usableElytras`, `canGlide`), `ibx.lib.mixin.curios.LivingEntityElytraSlotMixin` hooks it into vanilla gliding, and `ibx.lib.compat.curios.client.ElytraCurioRendering` draws it on the back (`CapeLayerMixin` hides the cape); all of it is only loaded when Curios is installed.

---

## 9. Automatic item tags

Items registered with this mod's base classes and interfaces (subclasses included, other mods' items included) automatically join the tags below; no mod has to write them in its data. The rules are in `ibx.lib.registry.AutoItemTags`, and a subclass also satisfies its parent's rules.

| Base class / interface | Tags added automatically |
| --- | --- |
| `IbxMeleeWeaponItem` | `#c:tools`, `#c:tools/melee_weapon` |
| Melee kind classes | Those of `IbxMeleeWeaponItem`, plus their own kind tag (see [2](#2-melee-weapons)); `IbxDoubleAxeItem` also has the axe's |
| `IbxRangedWeaponItem` (interface) | `#c:tools`, `#c:tools/ranged_weapon` |
| `IbxBowItem` | Those of `IbxRangedWeaponItem`, plus `#c:tools/bow` |
| `IbxCrossbowItem` | Those of `IbxRangedWeaponItem`, plus `#c:tools/crossbow` |
| `IbxAbstractShieldItem` | `#c:tools`, `#c:tools/shield` |
| `IbxShieldItem` | Those of `IbxAbstractShieldItem`, plus `#c:tools/one_handed_shield` |
| `IbxTwoHandedShieldItem` | Those of `IbxAbstractShieldItem`, plus `#c:tools/two_handed_shield` |
| `IbxMagicWeaponItem` | `#ibx_lib:can_cast_spell`, `#c:tools`, `#c:tools/magic_weapon` |
| `IbxTomeItem` | Those of `IbxMagicWeaponItem`, plus `#c:tools/tome` |
| `IbxStaffItem` | Those of `IbxMagicWeaponItem`, plus `#c:tools/staff` |

- They are real tag members: synced to the client and recognised by commands, JEI and other mods.
- The items are inserted at the front of each tag, so a data pack can take one out with `"remove"`; `"replace": true` does not remove them.
- The ten melee kind tags, plus `#c:tools/magic_weapon`, `#c:tools/tome`, `#c:tools/staff`, `#c:tools/one_handed_shield` and `#c:tools/two_handed_shield`, are common tags created by this mod. They have no data files and are filled entirely automatically.
- The tag keys are in `ibx.lib.registry.TagRegistry`: `CAN_CAST_SPELL`, `TOOLS_MAGIC_WEAPON`, `TOOLS_TOME`, `TOOLS_STAFF`, `TOOLS_ONE_HANDED_SHIELD`, `TOOLS_TWO_HANDED_SHIELD`, and for melee `TOOLS_SWORD`, `TOOLS_DAGGER`, `TOOLS_CUTLASS`, `TOOLS_CLAYMORE`, `TOOLS_SCYTHE`, `TOOLS_FIST`, `TOOLS_HAMMER`, `TOOLS_AXE`, `TOOLS_DOUBLE_AXE`, `TOOLS_SPEAR`.

**`#ibx_lib:can_cast_spell`** (items that can cast): right-clicking with one in hand casts the selected spell, and its use duration is fixed at 72000 ticks (like a bow, so it can be held). For an item that is not an Ibx implement to cast, the mod that owns it adds it in `data/ibx_lib/tags/item/can_cast_spell.json`. Don't put items that already have a use duration, such as food and potions, in this tag, or they can never be finished.

---

## 10. Data components

`ibx.lib.registry.DataComponentRegistry` registers two item data components:

| id | Field | Type | Description |
| --- | --- | --- | --- |
| `ibx_lib:auto_fire` | `AUTO_FIRE` | Boolean | Auto fire (see [3](#3-ranged-weapons)). Works on Ibx bows and crossbows and on the vanilla ones when taken over. Missing counts as `false`; it is in no item's default components. Read it with `RangedWeaponApi.isAutoFire`. |
| `ibx_lib:spells` | `SPELLS` | List of spell ids | The spells an item provides, in spell-wheel order. An item with it is a spell container; on an implement it replaces the built-in spells given to the constructor (see [5](#5-magic-implements)). |

```
/give @s minecraft:bow[ibx_lib:auto_fire=true]
/give @s minecraft:book[ibx_lib:spells=["examplemod:fireball"]]
```

Vanilla components the base classes use:

| Component | Set by |
| --- | --- |
| `attribute_modifiers` | Every base class (the main-hand numbers); for the taken-over vanilla bow and crossbow and the adjusted vanilla armor, the default components are changed. |
| `tool`, `weapon` | The melee kind classes (as the vanilla sword, axe and spear; the hammer replaces `weapon`). |
| `kinetic_weapon`, `piercing_weapon`, `attack_range`, `swing_animation`, `minimum_attack_charge`, `damage_type` | `IbxSpearItem` (as the vanilla spear). |
| `use_effects`, `unbreakable` | `IbxMagicWeaponItem` (movement speed while casting; unbreakable when durability is negative). |
| `blocks_attacks`, `break_sound`, off-hand equipment slot | `IbxAbstractShieldItem`. |
| `bettercombat:preset_id` (Better Combat's, only with it installed) | The melee kind classes, `IbxStaffItem`, `IbxTwoHandedShieldItem` (left-click moves). |
