# Attributes

[简体中文](ATTRIBUTE.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

The mod registers 18 attributes (`ibx.lib.registry.AttributeRegistry`). Ranged combat, critical strikes, spells, mana and summons all read their numbers from them.

- **Every living entity has them**: players, vanilla mobs and other mods' mobs get these attributes automatically, so modifiers from gear and mob effects work on anyone.
- **All synced to the client**: tooltips, the HUD, the attribute panel and client-side prediction can read them directly.
- **Resolved in one place**: the damage steps run in a fixed order (see [3](#3-damage-and-healing-order)); other mods' multipliers and reductions stack after it.
- **Attribute panel**: a panel left of the inventory lists every attribute the player has (see [7](#7-attribute-panel)).

Reference an attribute with `AttributeRegistry.XXX` (a `DeferredHolder<Attribute, Attribute>`, usable wherever a `Holder<Attribute>` is expected); data packs and commands use the id, e.g. `/attribute @s ibx_lib:spell_power base set 10`.

---

## 2. Attribute list

| id | Field | Name | Default | Range | Description |
| --- | --- | --- | --- | --- | --- |
| `ibx_lib:ranged_damage` | `RANGED_DAMAGE` | Ranged Damage | 0 | 0–2048 | Damage dealt when a ranged weapon hits. Crossbow = this value, bow = this value × draw percentage. Provided by the weapon (see [Equipment base classes](EQUIPMENT_en.md)). |
| `ibx_lib:draw_time` | `DRAW_TIME` | Draw Time | 0 | -1024–1024 | The weapon's base draw time in seconds; actual seconds = this value ÷ draw speed. Normally provided only by the weapon; to make the shooter draw faster or slower, modify draw speed instead. Higher is worse (increases show red). |
| `ibx_lib:draw_speed` | `DRAW_SPEED` | Draw Speed | 1 | 0.01–1024 | How fast the shooter draws. It divides, so speed bonuses have diminishing returns and never reach zero. Use `add_multiplied_base` for bonuses. |
| `ibx_lib:critical_chance` | `CRITICAL_CHANCE` | Critical Chance | 0.05 | 0–1024 | Shared by melee, ranged and spells. 0–1 is a probability; **1 or more always crits**. |
| `ibx_lib:critical_damage` | `CRITICAL_DAMAGE` | Critical Damage | 0.5 | 0–2048 | The **extra** damage ratio on a crit: final damage = damage × (1 + this value). |
| `ibx_lib:armor_penetration` | `ARMOR_PENETRATION` | Armor Penetration | 0 | 0–1 | Actual armor reduction = armor reduction × (1 − this value). Read from the attacker; projectiles read the shooter. Only the armor step is discounted; protection enchantments and the Resistance effect still apply in full. |
| `ibx_lib:heal_efficiency` | `HEAL_EFFICIENCY` | Heal Efficiency | 1 | 0–1024 | Actual healing = healing × this value. 0 means no healing at all. |
| `ibx_lib:thorns` | `THORNS` | Thorns | 0 | 0–2048 | When attacked, deals this much `minecraft:thorns` damage to the attacker. The vanilla Thorns enchantment was changed to +1 of this attribute per level. |
| `ibx_lib:max_mana` | `MAX_MANA` | Max Mana | 100 | 0–1000000 | Mana cap. Lowering it truncates current mana; raising it does not refill. |
| `ibx_lib:mana_regeneration` | `MANA_REGENERATION` | Mana Regeneration | 2 | -1024–1024 | Mana regenerated per second (a flat amount, not a share of the cap); negative values drain mana. |
| `ibx_lib:mana_cost_multiplier` | `MANA_COST_MULTIPLIER` | Mana Cost Multiplier | 1 | 0–1024 | Actual cost = base cost × this value. Higher is worse. Use `add_multiplied_total` for cost reductions (they multiply together and never reach 0). |
| `ibx_lib:spell_power` | `SPELL_POWER` | Spell Power | 0 | 0–2048 | The input of spell damage and healing formulas; spells scale from it. Provided by the casting implement (see [Equipment base classes](EQUIPMENT_en.md)). |
| `ibx_lib:spell_cooldown_ratio` | `SPELL_COOLDOWN_RATIO` | Spell Cooldown Ratio | 1 | 0–1024 | Actual cooldown = spell cooldown × this value. Higher is worse. Use `add_multiplied_total` for cooldown reduction as well. |
| `ibx_lib:spell_cast_speed` | `SPELL_CAST_SPEED` | Cast Speed | 1 | 0.01–1024 | Actual cast time = spell cast time ÷ this value. Use `add_multiplied_base` for bonuses. |
| `ibx_lib:spell_resistance` | `SPELL_RESISTANCE` | Spell Resistance | 0 | 0–4096 | "Armor points" against spell damage, using the vanilla armor formula; reduces at most 80% (see [3](#3-damage-and-healing-order)). |
| `ibx_lib:spell_penetration` | `SPELL_PENETRATION` | Spell Penetration | 0 | 0–1 | Actual reduction = spell resistance reduction × (1 − this value). Read into the damage snapshot at cast time, so changing gear afterwards doesn't affect spells already cast. |
| `ibx_lib:can_spell_crit` | `CAN_SPELL_CRIT` | Spells Can Crit | 0 | 0–1 | A switch: when the total is 1 or more, spells (healing included) crit using critical chance / critical damage, rolled once per target. Gear turns it on with an `add_value` +1 modifier. |
| `ibx_lib:max_summons` | `MAX_SUMMONS` | Max Summons | 1 | 0–1024 | How many slots your summons may occupy at once (see [Summons](SUMMON_en.md)). |

- Reading and writing mana is covered in [Spell system](SPELL_en.md); only the attributes are listed here.
- The mod also marks vanilla `attack_damage`, `attack_knockback` and `knockback_resistance` as synced: vanilla doesn't sync these three, so the client could only read base values and the attribute panel wouldn't change when you hold a sword or wear armor.

---

## 3. Damage and healing order

Everything damage-related is resolved in `ibx.lib.event.DamageHandler`, listening at `HIGHEST` priority: the mod settles the base damage first, and other mods' multipliers and reductions stack on top. The convention is **base damage first, then multipliers, then reductions**.

On incoming damage (`LivingIncomingDamageEvent`), in order:

| Step | Condition | What happens |
| --- | --- | --- |
| 1. Ranged damage | The direct source is an arrow carrying this mod's ranged damage | The ranged damage recorded on the arrow when it was fired **replaces** the vanilla damage (the vanilla velocity-based amount is discarded). |
| 2. Ranged crit | Same as above, and the arrow has the crit flag | Damage × (1 + the shooter's critical damage); crit particles and sound play on the target. The roll already happened when the arrow was fired. |
| 3. Difficulty scaling | Same as 1, and the victim is a player | Step 1 also overwrote vanilla's difficulty scaling, so it is applied again here according to the damage type. |
| 4. Spell resistance | The damage type is in `#ibx_lib:is_spell` | Reduced by the formula below; the reduction ratio is then multiplied by (1 − spell penetration). |
| 5. Armor penetration | The attacker is a living entity with armor penetration above 0 | The armor step's reduction is multiplied by (1 − armor penetration). |

The spell resistance formula is the vanilla armor one (`CombatRules#getDamageAfterAbsorb`), where R is spell resistance and toughness is vanilla `armor_toughness`:

```
reduction ratio = clamp(R − damage ÷ (2 + toughness ÷ 4), min(R × 0.2, 20), 20) ÷ 25
```

At most 80% (20 points); beyond 20 points the cap doesn't rise, but large hits get harder to push through. The check goes by damage type tag, so another mod's magic damage added to `#ibx_lib:is_spell` is reduced by spell resistance too — such damage has no snapshot, so spell penetration is read from the attacker's attribute at that moment.

After damage is resolved (`LivingDamageEvent.Post`; the amount is no longer changed):

- **Spell on-hit effects**: ignite, freeze and lifesteal, with parameters from the spell damage snapshot (see [Spell system](SPELL_en.md)).
- **Thorns**: only "attacks" count — melee and projectiles (the damage goes to the shooter); spells, explosions and thorns damage itself don't trigger it. A hit fully blocked by a shield or eaten by invulnerability frames doesn't either.

Two more places:

- **Melee crits** (`CriticalHitEvent`): chance comes from critical chance and the multiplier from critical damage, no longer vanilla's fixed 1.5×. Vanilla's guaranteed jump crit is off by default; the server config option `enableVanillaCritical` decides whether to keep it (see [Configuration](CONFIG_en.md)).
- **Healing** (`ibx.lib.event.HealHandler`, `LivingHealEvent`): healing × heal efficiency. Any healing that goes through `LivingEntity#heal` passes here (natural regeneration, Regeneration, Instant Health…); things that set health directly (such as the Totem of Undying) don't.

---

## 4. Critical strikes

**`ibx.lib.api.CriticalStrikeApi`**: melee, ranged and spells all take their values from here.

| Method | Description |
| --- | --- |
| `getChance(attacker)` | Critical chance. |
| `getDamageBonus(attacker)` | Critical damage bonus, i.e. `critical_damage` in the formula. |
| `getMultiplier(attacker)` | Damage multiplier on a crit, equal to 1 + `getDamageBonus`. |
| `roll(attacker)` | Rolls one crit check. **Server-only**: rolling on the client as well would disagree with the server. Always returns `true` when critical chance is 1 or more. |
| `spawnEffects(target)` | Plays vanilla's melee crit particles and sound on the target, for hits that don't go through vanilla's crit flow such as ranged and spell hits. Server-only. |

To make your own damage follow this crit system:

```java
float damage = 8.0F;
if (CriticalStrikeApi.roll(attacker)) {
    damage *= CriticalStrikeApi.getMultiplier(attacker);
    CriticalStrikeApi.spawnEffects(target);
}
target.hurtServer(level, source, damage);
```

Melee, this mod's bows and crossbows, and spells are handled automatically; you don't need to write this for them.

---

## 5. Attribute types

Three `RangedAttribute` subclasses (`ibx.lib.attribute`) that differ only in how the tooltip is shown; you can use them when registering your own attributes:

| Class | Constructor | Tooltip |
| --- | --- | --- |
| `WeaponAttribute` | `(descriptionId, default, min, max, baseId, valueKey)` | An `ADD_VALUE` modifier on the weapon whose id equals `baseId` counts as the "weapon base value" and is shown as a dark-green merged line like a vanilla sword ("8 Ranged Damage"); modifiers of the same attribute from other sources merge into it. When `valueKey` is not `null`, the number is wrapped in that translation key (with one `%s`). |
| `UnitAttribute` | `(descriptionId, default, min, max, valueKey)` | The value of additive modifiers is wrapped in a translation key (e.g. `"%s/s"`); percentage modifiers are unchanged. |
| `FlagAttribute` | `(descriptionId)` | A switch: default 0, range 0–1. A positive additive modifier shows only the attribute name ("Spells Can Crit"), with no number. |

Critical chance, critical damage, armor penetration and spell penetration use NeoForge's `PercentageAttribute`, which shows additive modifiers as percentages (+0.15 → +15%).

The matching constants in `AttributeRegistry`:

| Constant | Value | Use |
| --- | --- | --- |
| `BASE_RANGED_DAMAGE_ID` | `ibx_lib:base_ranged_damage` | Id of the weapon base modifier for ranged damage. |
| `BASE_DRAW_TIME_ID` | `ibx_lib:base_draw_time` | Id of the weapon base modifier for draw time. |
| `BASE_SPELL_POWER_ID` | `ibx_lib:base_spell_power` | Id of the implement base modifier for spell power. |
| `SECONDS_VALUE_KEY` | `attribute.value.ibx_lib.seconds` | Translation key for "seconds" values. |
| `PER_SECOND_VALUE_KEY` | `attribute.value.ibx_lib.per_second` | Translation key for "per second" values. |

**`ibx.lib.util.AttributeUtil.read(entity, attribute)`**: reads an attribute's total value; if the entity doesn't have the attribute it returns the attribute's default instead of throwing. Recommended for reading any of this mod's attributes.

---

## 6. Adding attributes to gear

Same as vanilla attributes: use the item's attribute modifiers component. The base values of bows, crossbows and casting implements are added by their base classes (see [Equipment base classes](EQUIPMENT_en.md)); this is how to add extra bonuses:

```java
public static final DeferredItem<Item> EMBER_TALISMAN = ITEMS.registerItem("ember_talisman",
        properties -> new Item(properties.stacksTo(1).attributes(ItemAttributeModifiers.builder()
                // +15% critical chance
                .add(AttributeRegistry.CRITICAL_CHANCE,
                        new AttributeModifier(Identifier.fromNamespaceAndPath("examplemod", "ember_talisman_crit"),
                                0.15, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.OFFHAND)
                // spells can crit
                .add(AttributeRegistry.CAN_SPELL_CRIT,
                        new AttributeModifier(Identifier.fromNamespaceAndPath("examplemod", "ember_talisman_spell_crit"),
                                1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.OFFHAND)
                // -20% mana cost (multiplicative)
                .add(AttributeRegistry.MANA_COST_MULTIPLIER,
                        new AttributeModifier(Identifier.fromNamespaceAndPath("examplemod", "ember_talisman_mana_cost"),
                                -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                        EquipmentSlotGroup.OFFHAND)
                .build())));
```

Which operation to use for each attribute:

| Attribute | Recommended | Why |
| --- | --- | --- |
| Critical chance, critical damage, armor penetration, spell penetration | `ADD_VALUE` | The value is itself a ratio; the tooltip shows it as a percentage. |
| Draw speed, cast speed | `ADD_MULTIPLIED_BASE` | The base is 1 and the tooltip shows a percentage; they divide, so several bonuses never add up to instant. |
| Mana cost multiplier, spell cooldown ratio | `ADD_MULTIPLIED_TOTAL` | They multiply together and never reach 0; with `ADD_MULTIPLIED_BASE`, two -50% modifiers add up to free. |
| Spells can crit | `ADD_VALUE` +1 | A switch; on when the total is 1 or more. |
| Everything else | `ADD_VALUE` | Flat points. |

---

## 7. Attribute panel

Opened with the scroll button at the bottom-left of the inventory's player box, the panel sits left of the inventory and lists every attribute the player has (vanilla, this mod's and other mods'), one row each with icon + name + value. The gear at the panel's bottom-left opens a settings screen: what to show, how to sort, whether to group by mod — all stored in the client config (see [Configuration](CONFIG_en.md)). The creative inventory's "Survival Inventory" tab has it too; it shares its spot with the vanilla recipe book and isn't drawn while the recipe book is open.

Other mods' attributes show up in the panel automatically, and you can add two things for them:

- **Icon**: a 9×9 texture at `assets/ibx_lib/textures/gui/sprites/hud/icon/<path of the attribute id>.png`. Only the path matters, not the namespace: attribute `examplemod:frost_power` maps to `frost_power.png`, which you put in your own mod's resources. Attributes without an icon get the generic placeholder (`unknown.png`).
- **Group name**: when grouping by mod, the group name is looked up at translation key `gui.ibx_lib.attribute_panel.mod.<namespace>` first, falling back to the mod's display name.

The panel shows the values the client can read: an attribute that isn't synced (`Attribute#isClientSyncable` is `false`) can only show its base value. If you want your own attribute to follow gear changes in the panel, call `setSyncable(true)` when registering it.
