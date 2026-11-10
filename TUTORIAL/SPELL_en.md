# Spell system

[简体中文](SPELL.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

ibx_lib splits "casting a spell" into a few parts, each with its own job:

- **Spells** (`ibx.lib.spell.Spell`): the registration line holds numbers only (mana cost, cooldown, cast time); the effect is written in the spell class's hooks (see [3](#3-spells)).
- **Casting** (`SpellCasting`): the single entry point. Checks, target selection, cast progress, mana, cooldown and syncing are all done there, and players and mobs share the same path (see [4](#4-casting)).
- **Spell containers**: players never "learn" spells. What they can cast is whatever the items on them provide (see [5](#5-spell-containers)).
- **Spell damage** (`SpellDamageApi`): damage is computed into a snapshot at cast time and settled on hit. Friendly fire, crits, spell resistance and fire / freeze / lifesteal are handled by the framework (see [8](#8-spell-damage)).
- **Spell entity base classes**: projectile, area effect and beam; you only write what differs (see [9](#9-spell-entity-base-classes)).
- **Mana** (`ManaApi`): current value, consumption, regeneration (see [10](#10-mana)).
- **Events**: casting, spell damage, the spell list and mana can all be listened to and changed (see [11](#11-events)).

All settlement happens on the server; the client only does presentation (sounds, particles, the cast bar, cast animations).

Not covered here:

| Topic | Document |
| --- | --- |
| Attribute list (spell power, spell resistance, max mana...) | [ATTRIBUTE_en.md](ATTRIBUTE_en.md) |
| Summons and summon spells | [SUMMON_en.md](SUMMON_en.md) |
| Targeting (`Targeting`, `TargetApi`, how a spell aims) | [TARGETING_en.md](TARGETING_en.md) |
| Item base classes such as tomes and staves | [EQUIPMENT_en.md](EQUIPMENT_en.md) |
| Ground warnings, block waves, recolored fire | [EFFECT_en.md](EFFECT_en.md) |
| Config options | [CONFIG_en.md](CONFIG_en.md) |
| Commands | [COMMAND_en.md](COMMAND_en.md) |

---

## 2. Quick start

Register a cast-time spell called "Spark" and a tome that carries it. The mod id is `examplemod`.

```java
// Spell registry: every mod uses its own DeferredRegister
public final class ExampleSpells {
    public static final DeferredRegister<Spell> SPELLS =
            DeferredRegister.create(SpellRegistries.SPELL_KEY, "examplemod");

    // Numbers only: 12 mana, 3 s cooldown, 0.8 s cast time (a cast time makes it a CAST spell)
    public static final DeferredHolder<Spell, SparkSpell> SPARK = SPELLS.register("spark",
            () -> new SparkSpell(Spell.props().manaCost(12).cooldown(3.0F).castTime(0.8F)));
}
```

```java
// The spell class: the effect only
public final class SparkSpell extends Spell {
    public SparkSpell(Properties props) {
        // Lock one enemy under the crosshair within 16 blocks; nothing aimed at = can't cast
        super(props, Targeting.unit(Faction.ENEMY).range(16.0));
    }

    @Override
    public void onCast(CastContext ctx) {   // server only, mana already paid
        LivingEntity target = ctx.target();
        if (target != null) {
            // 4 + 80% spell power, sets the target on fire for 3 s
            SpellDamageApi.hurt(target, ctx.damage(c -> 4.0F + 0.8F * c.spellPower()).withFire(60),
                    ctx.caster(), ctx.caster(), null);
        }
    }
}
```

```java
// The tome: 500 durability, +4 spell power in the main hand, carries examplemod:spark
public final class ExampleItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("examplemod");

    public static final DeferredItem<IbxTomeItem> SPARK_TOME = ITEMS.registerItem("spark_tome",
            p -> new IbxTomeItem(p, 500, 4.0F, List.of(Identifier.fromNamespaceAndPath("examplemod", "spark"))));
}

// In the mod constructor
ExampleSpells.SPELLS.register(modEventBus);
ExampleItems.ITEMS.register(modEventBus);
```

Resources you also need:

| Resource | Location |
| --- | --- |
| Spell name / description | Language file: `spell.examplemod.spark`, `spell.examplemod.spark.desc` |
| Spell icon | `assets/examplemod/textures/spell/spark.png`, 16×16; a placeholder icon is used when missing |
| Item name, item model | Same as any item (for the tome's held model see [EQUIPMENT_en.md](EQUIPMENT_en.md)) |

No tag is needed: `IbxTomeItem` is added to `#ibx_lib:can_cast_spell` automatically. In game, hold the tome, aim at an enemy within 16 blocks and right-click to cast Spark.

---

## 3. Spells

### Registration

- **Registry**: `ibx_lib:spell`. The key is `SpellRegistries.SPELL_KEY`, the registry object is `SpellRegistries.SPELL`. Synced to clients; cannot be overridden by data packs.
- **Spell id**: the registered name plus namespace, e.g. `examplemod:spark`. Data components, commands and saves all use it.
- **Language keys**: derived from the id, `spell.<namespace>.<path>` and `spell.<namespace>.<path>.desc`.
- **Tags**: the registry supports tags; files go in `data/<namespace>/tags/ibx_lib/spell/`.

The lib itself registers only one spell, `ibx_lib:dismiss_summons` (see [SUMMON_en.md](SUMMON_en.md)). Actual spells are registered by content mods.

### Numbers: `Spell.Properties`

`Spell.props()` creates a new set of numbers; chain the setters and pass it to the spell's constructor.

| Method | Default | Description |
| --- | --- | --- |
| `manaCost(float)` | `0` | Base mana cost. The actual cost is multiplied by the attribute `ibx_lib:mana_cost_multiplier`. For channeled spells this is the cost per effect. |
| `cooldown(float seconds)` | `0` | Base cooldown. The actual cooldown is multiplied by the attribute `ibx_lib:spell_cooldown_ratio`. |
| `castTime(float seconds)` | `0` | Cast time / full charge time. The actual time is divided by the attribute `ibx_lib:spell_cast_speed`. |
| `castType(CastType)` | Inferred from cast time | When omitted: a cast time of 0 is instant, above 0 is a cast-time spell. Channel and charge must be set explicitly. |
| `tickInterval(int ticks)` | `10` | How often a channeled spell takes effect, at least 1. Not affected by cast speed. |
| `minCharge(float)` | `0.1` | Releasing below this charge ratio (0–1) counts as cancelling. |
| `interruptible(boolean)` | `true` | Whether taking damage during the cast interrupts it. |
| `castAnimation(Identifier)` | None | A cast animation of its own, overriding the casting item's default (see [12](#12-cast-animations)). |
| `summonCount(int)` | `0` | How many are summoned per cast; setting it makes this a summon spell (see [SUMMON_en.md](SUMMON_en.md)). |
| `summonCost(int)` | `1` | Summon spells: how many summon slots each one takes. |
| `summonDuration(float seconds)` | `0` | Summon spells: lifetime of a timed summon; 0 is permanent. |

> Watch out for invulnerability frames on multi-hit channeled spells: spell damage respects vanilla i-frames by default, and with an interval of 10 ticks or less hits get dropped at random. Turn on `withLeaveNoIFrames(true)` on the snapshot (see [8](#8-spell-damage)) and let `tickInterval` control the hit rate.

`Spell` has getters with the same names (`manaCost()`, `cooldown()`, `castTime()`, `castType()`, `tickInterval()`, `minCharge()`, `interruptible()`, `castAnimation()`, `isSummon()`, `summonCount()`, `summonCost()`, `summonDuration()`) that return the raw values from the registration line. For the actual per-caster values use `SpellCasting.getCastTicks` / `getCooldownTicks` and `ManaApi.getActualCost`.

Other getters:

| Method | Description |
| --- | --- |
| `id()` | The registry id; null before registration. |
| `holder()` | The `Holder<Spell>` in the registry. |
| `descriptionId()` / `getName()` / `getDescription()` | Language key, name, description. |
| `targeting()` | The targeting declared in the constructor; defaults to `Targeting.self()` (no target). |

### Cast types: `CastType`

| Type | Description |
| --- | --- |
| `INSTANT` | Takes effect immediately, no cast bar. |
| `CAST` | Takes effect when the cast bar completes. Releasing early or being interrupted cancels it: no mana spent, no cooldown. |
| `CHANNEL` | Takes effect on the starting tick, then once every `tickInterval`, paying mana each time. Ends on release or when mana runs out. |
| `CHARGE` | Hold to charge; takes effect once on release, with the charge ratio in `ctx.charge()`. Below `minCharge` it counts as cancelled. |

### Hooks

A spell class extends `Spell`. `onCast` must be implemented; override the others as needed.

| Hook | Side | When |
| --- | --- | --- |
| `canCast(ctx)` | Server | Extra conditions beyond mana and cooldown; `true` by default. Called once when casting starts (the target is already chosen), and again before mana is paid when a cast completes, on every channel effect and when a charge is released. If it fails the cast is cancelled and that effect costs no mana. Only check here; don't change anything. Use `ctx.fail(reason)` to tell the player why. |
| `onCastStart(ctx)` | Both | Casting starts; also called for instant spells. Put start-up sounds and particles here; channeled spells spawn their beam entity here. |
| `onCastTick(ctx)` | Both | Every tick while casting / channeling / charging. On the server, the tick an effect fires calls `onCast` instead of this. |
| `onCast(ctx)` | Server | The effect; mana is already paid. Once for instant / cast; once per `tickInterval` for channel; once on release for charge. |
| `onCastEnd(ctx, reason)` | Both | The cast ends; for `reason` see [4](#4-casting). Clean up here (remove the beam entity a channel spawned, etc.). |
| `getTooltip(caster)` | Client | Per-caster value lines in the spell wheel's description box ("Damage 16"). The mana, cooldown and cast time lines are shown by the framework. Empty by default. |

For hooks called on both sides, the client call is triggered by the synced cast state; tell the sides apart with `ctx.level().isClientSide()`.

A spell whose cooldown or cast time depends on the caster (shorter with more spell power, say) overrides the two methods below; the framework applies the attributes on top of what they return. Both sides call them:

```java
@Override
public float cooldown(LivingEntity caster) {   // seconds; returns cooldown() by default
    float power = (float) caster.getAttributeValue(AttributeRegistry.SPELL_POWER);
    return Math.max(2.0F, this.cooldown() - 0.1F * power);
}

@Override
public float castTime(LivingEntity caster) {   // seconds; returns castTime() by default
    return this.castTime();
}
```

### Cast context: `CastContext`

One cast uses the same `CastContext` from start to end; it is passed to every hook.

| Method | Description |
| --- | --- |
| `caster()` / `level()` / `spell()` / `source()` | Caster, level, spell, cast source. |
| `triggerItem()` | The casting item that was right-clicked; empty for mob and command casts. This is the item that loses durability. |
| `sourceItem()` | The item that provides the spell; empty when there is none. |
| `startTick()` / `elapsed()` | Game time when the cast started / ticks cast so far (0 on the starting tick). |
| `castTicks()` | Ticks needed to finish the cast / fully charge (cast speed already applied, at least 1); 0 for instant and channel. |
| `charge()` | Charge ratio 0–1; only meaningful when a charge spell fires on release, always 1 otherwise. |
| `effects()` | How many times the spell has taken effect (which channel tick this is). |
| `target()` | The entity a unit-targeted spell acts on; a target given directly by mob AI is also here. Null for spells without a target. |
| `targetPos()` | The landing point of a point-targeted spell; null for other spells. |
| `aimPoint()` | Where a direction spell aims: for mobs the body center of the AI-given target or attack target, for players the point under the crosshair. The projectile and beam base classes aim by themselves, so this is rarely needed. |
| `damage(formula)` / `damage(damageType, formula)` | Builds a spell damage snapshot from the caster's current attributes, tagged with this spell's id (see [8](#8-spell-damage)). |
| `fail(reason)` | In `canCast`, says why the spell can't be cast; the framework sends it to the player's action bar. |
| `data()` / `setData(obj)` | A temporary object for this cast (the beam entity a channel spawned, etc.): set it in `onCastStart`, read it in later hooks; dropped automatically when the cast ends. |

The server and the client each have their own context. The client one only has the caster, spell, source, start tick and cast length: no items, `target()` / `targetPos()` are null, and the `data` slot is separate on each side.

Targeting is declared in the constructor (`super(props, Targeting.xxx())`); see [TARGETING_en.md](TARGETING_en.md).

---

## 4. Casting

### Entry points

`ibx.lib.spell.SpellCasting`, all static methods.

| Method | Description |
| --- | --- |
| `cast(caster, spell)` | Cast, with the source inferred from the caster: `ITEM` for players, `MOB` for other entities. `spell` can be a `Spell` or a `Holder<Spell>`. |
| `cast(caster, spell, target)` | Mob AI casts at a given target (see [7](#7-how-mobs-cast)). |
| `cast(caster, spell, source)` | Cast with an explicit source. |
| `cast(caster, spell, source, triggerItem, sourceItem)` | Same, with the casting item and the item providing the spell; pass `ItemStack.EMPTY` when there is none. There is also a variant with a trailing `target` parameter. |
| `castSelected(player, hand)` | Casts the player's selected spell, source `ITEM`. The lib calls this itself when a casting item is right-clicked. |
| `isCasting(caster)` | Whether the entity is casting. Works on both sides. |
| `getCurrentCast(caster)` | The `CastContext` of the cast in progress; null when not casting. Works on both sides. |
| `getLastEndReason(caster)` | How the previous cast ended; null while casting or before the first cast. |
| `stopCasting(caster)` | Release / AI stop: a cast is cancelled, a channel ends, a charge fires at its current ratio. Server only. |
| `cancelCasting(caster, reason)` | Force an end without settling a charge; ends with `reason`. Server only. |
| `getCastTicks(caster, spell)` | Actual cast / full charge ticks = `castTime(caster)` × 20 ÷ `spell_cast_speed`, at least 1; 0 for instant and channel. |
| `getCooldownTicks(caster, spell)` | Actual cooldown ticks = `cooldown(caster)` × 20 × `spell_cooldown_ratio`. |

For the player spell list and selection methods see [5](#5-spell-containers).

### Flow and results

On the server `cast` runs these checks in order; the first one that fails decides the returned `CastResult`:

| Order | Check | Returned on failure |
| --- | --- | --- |
| 1 | Already casting another spell (with source `COMMAND` the current cast is cancelled and the new one proceeds) | `ALREADY_CASTING` |
| 2 | Cooldown (only for sources that trigger cooldowns) | `ON_COOLDOWN` |
| 3 | Mana (only for sources that consume mana) | `NOT_ENOUGH_MANA` |
| 4 | Target selection by the spell's targeting found nothing | `CONDITION_FAILED` |
| 5 | The spell's own `canCast` | `CONDITION_FAILED` |
| 6 | Summon spells: not enough summon slots | `CONDITION_FAILED` |
| 7 | `SpellCastEvent.Pre` was cancelled | `CONDITION_FAILED` |

If everything passes the result is `SUCCESS`: an instant spell has taken effect, any other type has started casting. `NO_SPELL` is only returned by `castSelected` (no spells at all). An instant spell that can no longer pay after the pre-cast event raised its cost also returns `NOT_ENOUGH_MANA`.

- On failure the player sees the reason in the action bar (language key `message.ibx_lib.cast.<result name in lower case>`, or the reason given to `ctx.fail`) and hears a failure sound; callers don't need to report it again. A cast cancelled by the event shows nothing.
- `CastResult.isSuccess()` tells whether it worked.
- Calling `cast` on the client only checks (already casting, cooldown, mana) and changes no data.

After it starts, each type runs like this:

| Type | Flow |
| --- | --- |
| Instant | Pay mana → `onCastStart` → `onCast` → `onCastEnd` (completed) |
| Cast | `onCastStart` → `onCastTick` every tick → at the end: pay mana, `onCast` → `onCastEnd` (completed) |
| Channel | `onCastStart` → first effect immediately → then every `tickInterval`: pay mana, `onCast`; `onCastTick` on the other ticks → `onCastEnd` on release or when mana runs out |
| Charge | `onCastStart` → `onCastTick` every tick → on release: pay mana, `onCast` → `onCastEnd` (completed); cancelled below `minCharge` |

- **Re-check before the effect** (a cast completing, every channel effect, a charge released): the target is resolved again (a target locked at start is checked for validity, a target picked at effect time is re-picked from the crosshair), then `canCast` is called again. If it fails the cast is cancelled and that effect costs no mana.
- **Cooldown**: starts at the end only if the spell took effect at least once; a cancelled or interrupted cast has no cooldown.
- **Mana**: paid once per effect: the registered cost (which `SpellCastEvent.Pre` may change) times the mana cost multiplier. Charge spells are not prorated; the full cost is always paid.
- **Durability**: when mana is paid and `triggerItem` is damageable it loses durability: 1 point per `SpellCasting.MANA_PER_DURABILITY` (4) mana, rounded, with the remainder carried to the next effect of the same cast. Nothing is taken if the item is no longer in hand.
- **Creative mode**: no mana, no cooldown, and therefore no durability loss.
- Cast progress is advanced by the framework every tick; mob AI doesn't need its own timer.

### Cast source: `CastSource`

| Source | Consumes mana | Triggers cooldown | Description |
| --- | --- | --- | --- |
| `ITEM` | Yes | Yes | A held casting item. The default for players. |
| `SCROLL` | No | No | Scroll: once the spell has taken effect, 1 of `sourceItem` is consumed. The lib's right-click casting always uses `ITEM`; to make a scroll, call `cast` yourself with this source and the item. |
| `MOB` | No | No | Mobs (bosses included). The default for non-players. |
| `COMMAND` | No | No | Commands. Cancels the cast in progress and casts the new one. |

`consumesMana()` / `triggersCooldown()` read these two columns.

### End reason: `CastEndReason`

| Reason | Description |
| --- | --- |
| `COMPLETED` | Finished normally: an instant / cast spell took effect, a charge fired on release. |
| `CANCELLED` | Ended on purpose or a condition was lost: release (cast cancelled, channel stopped, charge too low), the pre-effect check failed, the locked target was lost, the casting item left the hand, the equipment providing the spell was removed, death, leaving the level. |
| `INTERRUPTED` | Hit while casting. |
| `OUT_OF_MANA` | Mana ran out during a channel, or a cast / charge couldn't pay when it was about to take effect. |

A channeled spell never "completes": releasing it ends with `CANCELLED`. To know whether it took effect, check `ctx.effects()`.

**Damage interrupts** only cast-time (`CAST`) spells that are `interruptible`. It does not interrupt when:

- the damage is 0, or another listener cancelled it;
- the damage type is in `#ibx_lib:cast_ignore` (damage over time such as poison, wither, burning, freezing, starvation, drowning, falling);
- the caster's entity type is in `#c:bosses`.

### Cooldowns: `SpellCooldowns`

Cooldowns are stored on the caster as "spell id → game time when it ends". They are saved, synced only to the entity itself, and cleared when a player dies and respawns.

| Method | Description |
| --- | --- |
| `isReady(entity, spell)` | Whether the cooldown is over. |
| `getRemaining(entity, spell)` | Remaining cooldown ticks; 0 means ready. |
| `getRemainingFraction(entity, spell)` | Remaining fraction 1 → 0, for drawing a cooldown overlay. |
| `start(entity, spell, ticks)` | Starts a cooldown, replacing any existing one. Server only. |
| `clear(entity)` | Clears all cooldowns. Server only. |

The read methods work on both sides; the client can only read the local player.

### Data attachments

Casting state lives in data attachments on the entity. Read and write them through the classes above, not with `getData` directly.

| id | Content | Saved | Synced |
| --- | --- | --- | --- |
| `ibx_lib:active_cast` | The cast in progress (`ActiveCast`) | No | Self and nearby players |
| `ibx_lib:spell_cooldowns` | Spell cooldowns (`SpellCooldowns`) | Yes | Self only |
| `ibx_lib:selected_spell` | The player's selected spell id (`SelectedSpell`); kept on death | Yes | Self only |
| `ibx_lib:mana` | Current mana (`ManaStorage`) | Yes | Self only |
| `ibx_lib:spell_snapshot` | The damage snapshot on a spell entity | Yes | No |

The cast state is not saved: logging out, changing dimension and dying all end the cast, and `onCastEnd` is still called.

---

## 5. Spell containers

### Which items provide spells

The spells a player can cast are the union of what every **spell container** on them provides. An item is a container in one of two ways:

- **Data component `ibx_lib:spells`** (`DataComponentRegistry.SPELLS`): a list of spell ids, in spell wheel order. Any item carrying it is a container; no code needed.
- **Implementing `SpellContainer`**: the item class decides what it provides right now (charges, sockets and so on). Its result takes priority over the component.

```
/give @s minecraft:book[ibx_lib:spells=["examplemod:spark"]]
```

```java
// Setting the component on a stack in code
stack.set(DataComponentRegistry.SPELLS.get(), List.of(ExampleSpells.SPARK.get().holder()));
```

| `SpellContainer` method | Description |
| --- | --- |
| `getSpells(stack)` | The spells this item provides right now, in display order. |
| `innateSpells(stack)` | The item's built-in spells (what it provides when the component hasn't changed them). None by default. |
| `SpellContainer.spellsOf(stack)` (static) | The spells any item provides: asks the item class if it implements the interface, otherwise reads the component; empty if neither. |

The lib's casting item base class `IbxMagicWeaponItem` (the tome `IbxTomeItem`, the staff `IbxStaffItem`) already implements `SpellContainer`: built-in spell ids are given in the constructor, and a stack carrying the `ibx_lib:spells` component uses the component instead (an empty list means no spells). Ids that can't be found are skipped, so you can list another mod's spell without depending on it. See [EQUIPMENT_en.md](EQUIPMENT_en.md).

A container's tooltip lists the spells it provides below the name, unless the item hides the `ibx_lib:spells` component with `tooltip_display`.

### The player's spell list

`SpellCasting.getSpells(player)` scans the containers on the player in a fixed order:

**Main hand → off hand → curio slots (only with Curios installed) → head → chest → legs → feet**

A spell is listed only the first time it appears. Finally `SpellListEvent` is posted, and listeners may add or remove entries (see [11](#11-events)). Take the equipment off and its spells are gone.

| Method | Description |
| --- | --- |
| `getSpells(player)` | The spells the player can cast right now, each a `SpellEntry`. Works on both sides. |
| `getSpellsExcept(player, excluded)` | Same, but ignoring the one stack `excluded`. |
| `getSelectedId(player)` | The selected spell id, not checked against the list; null if nothing was ever selected. |
| `getSelectedSpell(player)` | The entry that will actually be cast: the selected one if it is still in the list, otherwise the first; null with no spells. |
| `resolveSelected(player, spells)` | Same, using a list you already computed. |
| `select(player, id)` | Change the selection: written only if the spell is in that player's list; returns whether it worked. Server only. |

- `SpellEntry` is a record: `spell()` (`Holder<Spell>`), `sourceItem()` (the item providing it; `ItemStack.EMPTY` for entries added by the event that don't come from an item), `value()` (the `Spell` directly).
- The selection is a **spell id** (`SelectedSpell`), not an index: swapping equipment changes the list order. It is saved and kept on death.
- If the selected spell is no longer in the list, `castSelected` falls back to the first entry and tells the player.

### Items that can cast

Being allowed to cast by right-clicking is separate from providing spells:

- **`#ibx_lib:can_cast_spell`** (`TagRegistry.CAN_CAST_SPELL`): right-clicking while holding an item in this tag casts the selected spell. Every `IbxMagicWeaponItem` is in it automatically; for any other item, add it in your own mod's `data/ibx_lib/tags/item/can_cast_spell.json` (`"replace": false`).
- Items in the tag get a use duration of 72000, and right-click / release no longer go through the item's own `use` / `releaseUsing`. Don't put food, potions or other items that need to "finish using" in this tag.
- An item that provides spells can't necessarily cast: armor with `ibx_lib:spells` only adds spells to the list; a casting item still has to be held.

### Spell picker: `SpellPicker`

When an item class implements `SpellPicker` (which extends `SpellContainer`), sneaking and left-clicking with it in the main hand opens the spell picker screen: the item's current spells on the left, the available spells on the right. The choice is written to the stack's `ibx_lib:spells` component (after server-side validation), so the item must read that component for its spells — items extending `IbxMagicWeaponItem` already do.

```java
public class ExampleGrimoire extends IbxTomeItem implements SpellPicker {
    public ExampleGrimoire(Item.Properties properties) {
        super(properties, 800, 5.0F);
    }

    @Override
    public SpellPickerSource pickerSource(ItemStack stack) {
        return SpellPickerSource.CARRIED;
    }
}
```

| `SpellPickerSource` | What the right side lists |
| --- | --- |
| `REGISTERED` | Every registered spell, in registration order. |
| `CARRIED` | The item's built-in spells (`innateSpells`) plus the spells the player's other equipment provides right now. |

Neither lists `ibx_lib:dismiss_summons`. `SpellPickerSource.spells(player, stack)` returns the right-side list; `SpellPickerSource.pickable(holder)` tells whether a spell may be put into an item.

---

## 6. How players cast

- **Right-click casting**: right-click while holding an item in `#ibx_lib:can_cast_spell` to cast the selected spell. Instant spells just swing the hand; other types are held to cast and end on release (cast cancelled, channel stopped, charge fired), and switching items or opening a container also cancels. Cast and channel can each be changed to "Toggle" in Options → Controls (press once to start, again to end); charge is always hold.
- **Spell wheel**: hold the wheel key (R by default, rebindable in key binds) to bring it up. Its slots are the result of `getSpells`; left-click a slot or release the key to select. The description box next to it shows the spell description, the value lines from `Spell#getTooltip`, and mana cost, cooldown and cast time. The wheel only selects; it never casts.
- **HUD**: while a casting item is held, the selected spell is shown outside the off-hand slot (with overlays for cooldown and insufficient mana). The cast bar is on the left and the mana bar on the right, stacked above the health and hunger bars. Instant spells have no cast bar.
- **Icons**: a spell's icon is looked up by id at `assets/<namespace>/textures/spell/<path>.png` (16×16), shared by the wheel and the HUD.

For the related client settings see [CONFIG_en.md](CONFIG_en.md).

---

## 7. How mobs cast

Mobs (bosses included) use the same entry point as players, with source `CastSource.MOB`: **no mana and no spell cooldown**; how often they cast is up to the mob's own move cooldowns. Cast progress is advanced by the framework; the AI only needs `isCasting`, and `stopCasting` when necessary.

```java
// In the mob's AI (server side)
if (!SpellCasting.isCasting(mob) && --this.cooldown <= 0) {
    LivingEntity target = mob.getTarget();
    if (target != null && SpellCasting.cast(mob, ExampleSpells.SPARK, target).isSuccess()) {
        this.cooldown = 100;
    }
}
```

- **`cast(caster, spell, target)`**: the AI hands over the target directly. Unit spells lock it, point spells land at its feet, direction spells aim at it. Only reach is checked (alive, same dimension, within the spell's range), not faction; if it becomes invalid later the cast is cancelled. With a null `target` this is the same as `cast(caster, spell)`.
- **Without a given target**: enemy unit spells and point spells prefer the mob's attack target; projectiles and beams aim at the body center of the attack target.
- **Interrupts**: casters whose entity type is in `#c:bosses` are never interrupted by damage.
- **Result**: after the cast ends, `getLastEndReason(caster)` tells whether it completed or was interrupted.
- **Launch position**: a mob implementing `SpellOrigin` decides where projectiles and beams start (see [9](#9-spell-entity-base-classes)).
- **Numbers**: a mob's `ibx_lib:spell_power` is 0 by default, so a formula that only multiplies spell power deals no damage. Give the formula a flat part, or give the mob the attribute.
- A mob's cast state is synced to nearby players as well, so the client-side `onCastStart` / `onCastTick` / `onCastEnd` and `SpellCastStateEvent` fire as usual.

---

## 8. Spell damage

### Snapshot and settlement

Spell damage happens in two steps:

1. **At cast time** a `SpellDamageSnapshot` is built: the damage formula is run against the caster's attributes at that moment and stored together with the crit parameters and spell penetration. Swapping gear, buffs expiring or the caster dying afterwards don't affect it.
2. **On hit** `SpellDamageApi.hurt` settles it. Projectiles, area effects and beams carry the snapshot on the entity, so the damage no longer changes after launch.

```java
// In a spell: the usual way, tagged with this spell's id
SpellDamageSnapshot snapshot = ctx.damage(c -> 6.0F + 1.2F * c.spellPower());

// Outside a cast context (boss moves, etc.)
SpellDamageSnapshot snapshot = SpellDamageApi.snapshot(caster, c -> 10.0F);
```

The formula is a `SpellDamageApi.Formula`: it receives a `SpellDamageContext` and returns the base amount (without crit):

| `SpellDamageContext` field | Description |
| --- | --- |
| `caster()` | The caster. Read any other value the formula needs (max health, armor...) from it. |
| `spellPower()` | Spell power (total of `ibx_lib:spell_power`). |
| `canCrit()` | Whether spells can crit (total of `ibx_lib:can_spell_crit` is at least 1). |
| `critChance()` | Crit chance (`ibx_lib:critical_chance`); 1 or more always crits. |
| `critMultiplier()` | Damage multiplier on a crit, i.e. 1 + `ibx_lib:critical_damage`. |
| `spellPenetration()` | Spell penetration (`ibx_lib:spell_penetration`). |

`SpellDamageApi` methods:

| Method | Description |
| --- | --- |
| `snapshot(caster, formula)` | Builds a snapshot with the default damage type `ibx_lib:spell_general`. Works on both sides (the client can use it for estimates). |
| `snapshot(caster, damageType, formula)` | With an explicit damage type. |
| `hurt(target, snapshot, directEntity, caster, sourcePosition)` | Settles one spell hit; returns whether damage was dealt. `directEntity` is the entity directly causing the damage (projectile, area entity; the caster or null for instant spells); `caster` may be null; `sourcePosition` is the source position used for shield facing, and null falls back to `directEntity`'s position. |
| `setProjectileSnapshot(entity, snapshot)` / `getProjectileSnapshot(entity)` | Writes the snapshot onto an entity (any entity; it survives saving) / reads it back. |
| `hurtFromProjectile(projectile, target)` | Settles a projectile hit: takes the snapshot on it and the projectile's owner as caster. Returns false if there is no snapshot. |
| `heal(target, snapshot)` | Settles one spell heal; returns the health actually restored. Rolls crit from the snapshot; no friendly-fire check. |
| `knockback(target, from, strength, up)` | Knockback helper: spell damage has no knockback by default, so spells that want it call this after hitting. `strength` is the horizontal force (vanilla melee is 0.4), `up` an extra upward speed; both respect knockback resistance. |

All of it settles on the server only; `hurt` / `heal` return immediately on the client.

### Snapshot switches

A snapshot is an immutable record; each `withXxx` returns a changed copy and they can be chained: `ctx.damage(...).withFire(80).withLifesteal(0.2F)`.

| Method | Default | Description |
| --- | --- | --- |
| `withDamageType(key)` | `ibx_lib:spell_general` | Changes the damage type. It should be in `#ibx_lib:is_spell`, otherwise it is reduced by armor and ignores spell resistance. |
| `withSpell(id)` | None (`ctx.damage` fills it in) | Which spell it came from, for events. |
| `withFriendlyFire(boolean)` | `false` | Whether allies can be hit. By default friendly-fire protection is on: pets, summons, teammates and players with PvP off can't be hit. |
| `withIgnoreIFrames(boolean)` | `false` | Pierce existing i-frames: the target's invulnerable time is zeroed before the hit. |
| `withLeaveNoIFrames(boolean)` | `false` | Leave no i-frames: the target's invulnerable time is restored after the hit, so this hit doesn't block other attacks. Use it with `HitTracker` for multi-hit damage. |
| `withFire(ticks)` | `0` | Ticks of fire applied on hit. |
| `withFreeze(ticks)` | `0` | Adds roughly this many ticks of freezing on hit (the powder-snow slowdown; it only deals damage once it builds up to the vanilla freezing threshold). No effect on targets that can't freeze (`canFreeze()` is false). |
| `withLifesteal(ratio)` | `0` | Lifesteal ratio: heals the caster by a share of the damage actually dealt. |
| `scaled(factor)` | — | Multiplies the base amount, for falloff or splitting across hits (`scaled(0.5F)` at the edge of an explosion). |

The other fields (`baseAmount`, `canCrit`, `critChance`, `critMultiplier`, `spellPenetration`) are fixed when the snapshot is built and are read-only.

### Order of one hit

For a living target, `SpellDamageApi.hurt` does the following in order:

1. **Friendly-fire check**: unless the snapshot has `friendlyFire`, a target the caster can't harm returns false right away. Without a caster (the projectile's owner logged out) there is no check.
2. **Crit roll**: rolled separately for every target; a crit multiplies by the snapshot's crit multiplier.
3. **`SpellDamageEvent`**: cancellable, and the amount can be changed (see [11](#11-events)).
4. **I-frame switches**: `ignoreIFrames`, `leaveNoIFrames`.
5. **Vanilla damage flow**: spell resistance is applied in it (see below), followed by vanilla reductions such as Resistance, Protection enchantments and absorption.
6. **After the hit**: crits play the crit particles and sound on the target; fire, freeze and lifesteal apply once the damage has been dealt.

Non-living targets (end crystals, boats...) only go through vanilla damage with the snapshot's base amount; none of the friendly-fire, crit, event or i-frame handling applies.

Spell damage doesn't trigger "post-attack" enchantment effects such as Thorns or Fire Aspect, nor the reflect damage of the attribute `ibx_lib:thorns`.

The damage source is a `SpellDamageSource` (extends vanilla `DamageSource`) with two extras: `snapshot()` and `isCritical()`. **To test "is this spell damage", use the tag `#ibx_lib:is_spell`, not `instanceof SpellDamageSource`** — another mod's magic damage counts as spell damage once it is in the tag.

### Resistance, penetration, crits, healing

- **Spell resistance**: damage whose type is in `#ibx_lib:is_spell` is reduced by the target's `ibx_lib:spell_resistance` (R below) through the vanilla armor formula, with vanilla `armor_toughness` as toughness:

  ```
  reduction = clamp(R − damage ÷ (2 + toughness ÷ 4), min(R × 0.2, 20), 20) ÷ 25 × (1 − spell penetration)
  ```

  At most 80%. The check is by tag, so damage that doesn't go through `SpellDamageApi` but is in the tag is reduced by spell resistance as well.
- **Spell penetration**: a `SpellDamageSource` uses the value read into the snapshot at cast time; other tagged damage reads the attacker's current `ibx_lib:spell_penetration`.
- **Crits**: spells only crit when the caster's `ibx_lib:can_spell_crit` is at least 1. Chance and multiplier are the `critical_chance` and 1 + `critical_damage` stored in the snapshot at cast time, the same attributes melee and ranged use. Heals crit too, without the crit effects.
- **Fire / freeze / lifesteal**: only for `SpellDamageSource`, applied after damage was actually dealt. Lifesteal uses the damage actually dealt (after all reductions) and does nothing if the caster is dead.
- **Healing**: everything that goes through vanilla `LivingEntity#heal` (including `SpellDamageApi.heal` and lifesteal) is multiplied by the healed entity's `ibx_lib:heal_efficiency`.

For attribute defaults and ranges see [ATTRIBUTE_en.md](ATTRIBUTE_en.md).

### Multi-hit pacing: `HitTracker`

Area and channeled spells look for targets every tick, and once `leaveNoIFrames` is on, vanilla i-frames no longer throttle them. Use `ibx.lib.api.HitTracker` to control how often each target is hit:

```java
private final HitTracker hits = new HitTracker(10);   // at least 10 ticks between hits on the same target

if (this.hits.tryHit(target)) {                       // records the hit and returns true if it may hit now
    SpellDamageApi.hurt(target, snapshot.withLeaveNoIFrames(true), this, owner, null);
}
```

`clear()` empties the record. It is not saved. `SpellAoe` and `SpellBeam` already have one built in.

### Damage types and tags

The keys are in `DamageTypeRegistry`; the definitions are in `data/ibx_lib/damage_type/`.

| Damage type | Spell damage | Traits |
| --- | --- | --- |
| `ibx_lib:spell_general` | Yes | General spell damage; used when a spell doesn't specify a type. |
| `ibx_lib:digest` | No | Ignores armor, shields and i-frames; no knockback. |
| `ibx_lib:bleeding` | No | Ignores armor, Resistance and Protection enchantments; no knockback. |
| `ibx_lib:soul_link` | No | Ignores armor, Resistance, Protection enchantments, shields and i-frames; no knockback. |

Damage type tags (keys in `TagRegistry`):

| Tag | Description |
| --- | --- |
| `#ibx_lib:is_spell` | Spell damage. Every "is this spell damage" check looks at this tag only. Vanilla `bypasses_armor`, `no_knockback` and `avoids_guardian_thorns` all include it, so types in it automatically ignore armor, get no vanilla knockback and don't trigger guardian thorns. |
| `#ibx_lib:cast_ignore` | Damage that doesn't interrupt casting. Ships with `neoforge:poison`, `minecraft:wither`, `on_fire`, `freeze`, `starve`, `drown`, `fall`. |

Adding your own spell damage type (frost, say):

1. Define the damage type in `data/examplemod/damage_type/frost.json`.
2. Add it to `data/ibx_lib/tags/damage_type/is_spell.json` (`"replace": false`).
3. In the spell use `ctx.damage(FROST, formula)`, or `snapshot.withDamageType(FROST)`.

```json
{
  "replace": false,
  "values": [ "examplemod:frost" ]
}
```

If a snapshot's damage type can't be found (the data pack providing it was removed), `ibx_lib:spell_general` is used instead. Spell damage can be blocked by a shield from the front; to prevent that, use a damage type that is also in vanilla `bypasses_shield`.

---

## 9. Spell entity base classes

`ibx.lib.entity` has three base classes: a projectile **flies**, an area effect **stays in place**, a beam **follows the caster**. Their damage always comes from the snapshot stored on the entity at cast time, and each mod registers its own entity types.

What the three have in common:

- They never hit the caster. With friendly-fire protection on (the snapshot default) they don't affect allies; projectiles and beams pass straight through them.
- `affects()` returns who is affected (`Faction`): enemies by default, everyone if the snapshot has `friendlyFire`. Healing variants override it to allies.
- Two constructors: the `(EntityType, Level)` one is for the entity type factory, the one taking a caster is for spells (server only).

### Projectile: `SpellProjectile`

> **`SpellProjectile` needs GeckoLib**: it implements GeckoLib's `GeoEntity`. GeckoLib is an optional dependency of ibx_lib, and only this class and its renderers use it. If your mod registers spell projectiles, declare GeckoLib as a required dependency in your own `neoforge.mods.toml`.

```java
public class FireBolt extends SpellProjectile {
    public FireBolt(EntityType<? extends FireBolt> type, Level level) {
        super(type, level);
    }

    public FireBolt(LivingEntity caster, SpellDamageSnapshot snapshot) {
        super(ExampleEntities.FIRE_BOLT.get(), caster, snapshot);
    }

    @Override protected int getLifetime() { return 60; }
    @Override protected boolean isExtinguishedByWater() { return true; }
    @Override protected void trailParticles() {
        this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
    }
}

// In the spell
@Override
public void onCast(CastContext ctx) {
    FireBolt bolt = new FireBolt(ctx.caster(), ctx.damage(c -> 6.0F + 1.2F * c.spellPower()).withFire(60));
    bolt.shootFromCaster(1.5F, 0.0F);   // speed (blocks / tick), inaccuracy (0 is dead on)
    ctx.level().addFreshEntity(bolt);
}

// Entity type
public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities("examplemod");
public static final DeferredHolder<EntityType<?>, EntityType<FireBolt>> FIRE_BOLT = ENTITIES.registerEntityType(
        "fire_bolt", FireBolt::new, MobCategory.MISC,
        b -> b.noLootTable().sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(10));
```

`shootFromCaster` fires at the point under the owner's crosshair (the projectile starts at the chin, not the eyes, so it flies at an angle towards the crosshair). Mobs aim at the body center of the AI-given target or their attack target, and a projectile with gravity raises its aim automatically to compensate for the drop.

| Parameter (override) | Default | Description |
| --- | --- | --- |
| `getLifetime()` | `100` | Maximum flight time in ticks. |
| `getDrag()` / `getWaterDrag()` | `1.0` | Share of speed kept per tick in air / water; 1 means no slowdown. |
| `getDefaultGravity()` (vanilla) | `0` | Gravity; a snowball has 0.03. |
| `isExtinguishedByWater()` | `false` | Goes out as soon as it enters water. |
| `getPierce()` | `0` | How many targets it can pass through; 0 ends on the first hit. A target is only hit once. |
| `getHomingStrength()` | `0` | Homing strength: how far the velocity turns towards the target each tick (0–1); 0 is no homing. It homes on the nearest affectable entity within 60° ahead. |
| `getHomingRange()` | `16.0` | Homing search range in blocks. |
| `flyAnimation()` | `null` | The GeckoLib animation looped in flight when rendered as a 3D model. |

| Hook | When |
| --- | --- |
| `onHitTarget(target, damaged)` | After hitting a target, damage already settled. `damaged` false means it was blocked (i-frames, shield, immunity). |
| `onImpact(hit)` | The final hit: it struck a block, or the last target it can hit. Put explosions or a lingering area effect here. The projectile is removed afterwards. |
| `onExpire()` | Its lifetime ran out, or it was extinguished by water. The projectile is removed afterwards. |
| `trailParticles()` | Client, every tick: trail particles. |
| `impactParticles()` | Client: particles for the final hit. |

Renderers (`ibx.lib.client.renderer`) are registered in `EntityRenderersEvent.RegisterRenderers`:

```java
// A camera-facing sprite: texture, side length (blocks), full-bright or not
event.registerEntityRenderer(ExampleEntities.FIRE_BOLT.get(), ctx -> new SpellProjectileRenderer<>(ctx,
        Identifier.fromNamespaceAndPath("examplemod", "textures/entity/spell/fire_bolt.png"), 0.5F, true));

// A GeckoLib 3D model pointing along the flight direction; files use GeckoLib's default paths
event.registerEntityRenderer(ExampleEntities.ICE_SHARD.get(), ctx -> new GeoSpellProjectileRenderer<>(ctx,
        Identifier.fromNamespaceAndPath("examplemod", "spell/ice_shard")));
```

File locations for `GeoSpellProjectileRenderer`: `geckolib/models/entity/<path>.geo.json`, `geckolib/animations/entity/<path>.animation.json`, `textures/entity/<path>.png`. Model with the tip facing north (-Z) in Blockbench and the model centered on the origin.

### Area effect: `SpellAoe`

Stays in place for a while and keeps affecting the entities in range: fire walls, poison clouds, healing circles. Its shape is an upright cylinder: the base center is the entity position, the horizontal radius is `getRadius()`, the height is `getHeight()`.

```java
public class FireField extends SpellAoe {
    public FireField(EntityType<? extends FireField> type, Level level) {
        super(type, level);
    }

    public FireField(LivingEntity caster, SpellDamageSnapshot snapshot, Vec3 pos, float radius, int duration) {
        super(ExampleEntities.FIRE_FIELD.get(), caster, snapshot, pos, radius, duration);
    }

    @Override protected ParticleOptions getParticle() { return ParticleTypes.FLAME; }
    @Override protected void onAffect(LivingEntity target, boolean hit) {
        if (hit) target.igniteForSeconds(2.0F);
    }
}

// The spell: pick a landing point with the crosshair; a radius-3 circle is drawn on the ground while casting
public final class FireFieldSpell extends Spell {
    public FireFieldSpell(Properties props) {
        super(props, Targeting.point().range(24.0).circle(3.0F, Faction.ENEMY));
    }

    @Override
    public void onCast(CastContext ctx) {
        Vec3 pos = ctx.targetPos();
        if (pos != null) {
            ctx.level().addFreshEntity(new FireField(ctx.caster(),
                    ctx.damage(c -> 2.0F + 0.3F * c.spellPower()), pos, 3.0F, 100));
        }
    }
}
```

| Parameter (override) | Default | Description |
| --- | --- | --- |
| `getHitInterval()` | `10` | Minimum ticks between two effects on the same target. |
| `getHeight()` | `1.0` | Cylinder height in blocks, upwards from the entity position. |
| `getRadiusPerTick()` | `0` | Radius change per tick (positive grows, negative shrinks); it ends early once below 0.5 blocks. |
| `leavesNoIFrames()` | `true` | Leave no i-frames after a hit: the pace is set by `getHitInterval()`, and the damage over time doesn't block other attacks. |
| `getParticle()` | The witch's purple particle | The particle spawned at random on the disc on the client. |

| Hook | Description |
| --- | --- |
| `affects()` | Who is affected. Return `Faction.NONE` when you only borrow the shell (warning, duration, radius sync) and settle things yourself; it then stops searching for targets every tick. |
| `canAffect(target)` | Whether this target should be affected: by default not the caster, otherwise per `affects()`. |
| `affect(target)` | One effect: deals the snapshot's damage by default and returns whether it hit. Healing circles change it to `SpellDamageApi.heal`. |
| `onAffect(target, hit)` | After one effect (side effects). `hit` false means it was blocked. |
| `onExpire()` | The duration is over, or the radius shrank away. The entity is removed afterwards. |
| `ambientParticles()` | The client's per-tick particles; override it to draw everything yourself. |

| Method | Description |
| --- | --- |
| `getRadius()` / `setRadius(radius)` | The radius, synced to clients, at most 32. |
| `setWaitTime(ticks)` | How long to "warn" before taking effect; set it before adding the entity to the level. Nothing is affected during the warning and particles are sparse. |
| `isWaiting()` | Whether it is still warning. |
| `getOwner()` / `snapshot()` | Caster / damage snapshot; either may be null. |

- There is also a constructor without a snapshot, `(type, caster, pos, radius, durationTicks)`, to use with `affects()` returning `Faction.NONE`.
- `durationTicks` is how long it is active, not counting the warning.
- It has no model; register vanilla `NoopRenderer::new` as its renderer. The `sized` values at registration don't matter, as the bounding box follows the radius.
- The damage source position is the circle's center: a shield raised towards the center blocks it.
- It is saved. It keeps working after the caster logs out or is unloaded, just without the friendly-fire check.

### Beam: `SpellBeam`

A beam shot from the caster's chest that follows their view and keeps affecting the entities along it. Mainly for channeled spells: create it in `onCastStart`, store it with `ctx.setData`, call `end()` in `onCastEnd`.

```java
// Registration line: Spell.props().castType(CastType.CHANNEL).tickInterval(10).manaCost(2)
public final class FrostRaySpell extends Spell {
    public FrostRaySpell(Properties props) {
        super(props);
    }

    @Override
    public void onCastStart(CastContext ctx) {
        if (!ctx.level().isClientSide()) {
            FrostBeam beam = new FrostBeam(ctx.caster(), ctx.damage(c -> 1.0F + 0.2F * c.spellPower()));
            ctx.level().addFreshEntity(beam);
            ctx.setData(beam);
        }
    }

    @Override
    public void onCast(CastContext ctx) {
        // The framework pays the mana; the beam deals the damage itself
    }

    @Override
    public void onCastEnd(CastContext ctx, CastEndReason reason) {
        SpellBeam beam = ctx.data();
        if (beam != null) {
            beam.end();
        }
    }
}

public class FrostBeam extends SpellBeam {
    public FrostBeam(EntityType<? extends FrostBeam> type, Level level) {
        super(type, level);
    }

    public FrostBeam(LivingEntity caster, SpellDamageSnapshot snapshot) {
        super(ExampleEntities.FROST_BEAM.get(), caster, snapshot.withFreeze(60), 0);   // 0 = no time limit
    }

    @Override protected double getRange() { return 12.0; }
}

// Entity type: not saved, not summonable; the bounding box doesn't matter, hits use the ray
public static final DeferredHolder<EntityType<?>, EntityType<FrostBeam>> FROST_BEAM = ENTITIES.registerEntityType(
        "frost_beam", FrostBeam::new, MobCategory.MISC,
        b -> b.noSave().noSummon().noLootTable().fireImmune().sized(0.1F, 0.1F).clientTrackingRange(10).updateInterval(1));
```

Set the spell's `tickInterval` and the beam's `getHitInterval()` to the same value and each mana payment matches one hit.

| Parameter (override) | Default | Description |
| --- | --- | --- |
| `getRange()` | `16.0` | Maximum range in blocks. |
| `getWidth()` | `0.3` | Thickness in blocks: target bounding boxes are inflated by half of it for hit tests, and it is also the side length of the drawn tube. |
| `isPiercing()` | `false` | False stops at the first affectable target; true hits every affectable target on the line. |
| `stopsAtBlocks()` | `true` | Whether it stops at blocks; false goes through walls. |
| `getHitInterval()` | `10` | Minimum ticks between two effects on the same target. |
| `leavesNoIFrames()` | `true` | Leave no i-frames after a hit. |

| Hook | Description |
| --- | --- |
| `origin(owner, partialTick)` | Start point. The chest by default (0.65 of the body height above the feet); a caster implementing `SpellOrigin` supplies its own. |
| `direction(owner, start, partialTick)` | Direction. By default from the start point towards the point under the crosshair; mobs aim at the body center of the AI-given target or attack target. |
| `aimPoint(owner, partialTick)` | The point under the crosshair. |
| `affects()` / `canAffect(target)` / `affect(target)` / `onAffect(target, hit)` | Same as `SpellAoe`. |
| `onBlockHit(hit)` | Every tick the beam ends on a block. |
| `onExpire()` | Time is up, the caster is gone, or the spell called `end()`. The entity is removed afterwards. |
| `beamParticles(start, end)` | Client, every tick: particles along the beam. |

| Method | Description |
| --- | --- |
| `end()` | Ends it: calls `onExpire()` and removes the entity. A plain `discard()` skips the clean-up hook. Server only. |
| `getStart(partialTick)` / `getEnd(partialTick)` / `getLength()` | The two ends and the length at this moment. |
| `getEndKind()` | What the beam stopped on: `NONE` (full range), `BLOCK`, `ENTITY`. |
| `getOwner()` / `snapshot()` | Caster / damage snapshot. |

- The constructor's `durationTicks`: maximum lifetime in ticks; 0 means no limit (the spell calls `end()`).
- The beam removes itself when the caster dies, logs out or changes dimension. It is not saved.
- The damage source position is the start point: a shield raised towards the caster blocks it.

The renderer `SpellBeamRenderer` draws one or more coaxial textured square tubes from start to end.

```java
// One layer: texture, full-bright, blocks per vertical texture repeat, blocks the texture slides outwards per tick
event.registerEntityRenderer(ExampleEntities.FROST_BEAM.get(), ctx -> new SpellBeamRenderer<>(ctx,
        Identifier.fromNamespaceAndPath("examplemod", "textures/entity/spell/frost_beam.png"), true, 1.0F, 0.1F));

// Two layers: outer glow first, thin core last (later layers draw on top)
event.registerEntityRenderer(ExampleEntities.FROST_BEAM.get(), ctx -> new SpellBeamRenderer<>(ctx, true,
        new SpellBeamRenderer.Layer(GLOW_TEXTURE, 1.0F, 1.2F, 0.2F),
        new SpellBeamRenderer.Layer(CORE_TEXTURE, 0.33F, 0.8F, 0.3F)));
```

The four `Layer` parameters: texture, thickness (relative to the beam's width), blocks per vertical texture repeat, blocks it slides outwards per tick. Don't paint the texture bright in the middle and dark at the sides (seen at an angle it splits into two lines); for a bright middle add a thin core layer.

### `SpellOrigin` and `PhasedEntity`

Two interfaces for caster entities to implement:

| Interface | Method | Description |
| --- | --- | --- |
| `SpellOrigin` | `getSpellOrigin()` | Where spell projectiles and beams start (world coordinates), for mobs that shoot from the mouth or hands. Without it, projectiles start at the chin and beams at the chest. |
| `SpellOrigin` | `getSpellAimPoint()` | The point a beam aims at; null by default, which aims at the attack target / crosshair as usual. Use it to make a beam turn slowly with the body. |
| `PhasedEntity` | `getPhase()` | The current phase of a phased entity (a boss entering phase two at half health); the first phase is 0. Spells can vary by it: `ctx.caster() instanceof PhasedEntity boss && boss.getPhase() >= 1`. |

---

## 10. Mana

- **Maximum**: the attribute `ibx_lib:max_mana`.
- **Regeneration**: the attribute `ibx_lib:mana_regeneration`, in points per second; negative drains. Settled every tick on the server by `ManaHandler`. Players always take part; other entities only once they have used mana (they carry mana data).
- **Cost**: base cost × the attribute `ibx_lib:mana_cost_multiplier`.
- **Current value**: the data attachment `ibx_lib:mana` (`ManaStorage`), saved and synced only to the entity itself — the client can only read the local player's mana. New players and respawned players start at 0.
- **Creative mode**: consuming always succeeds and takes nothing.

`ibx.lib.api.ManaApi`:

| Method | Description |
| --- | --- |
| `getMana(entity)` | Current mana. 0 for entities without mana data. |
| `getMaxMana(entity)` | Maximum mana. |
| `getActualCost(entity, baseCost)` | Actual cost = base cost × mana cost multiplier. For UI previews. |
| `canAfford(entity, baseCost)` | Whether it can be paid. Always true in creative mode. |
| `tryConsume(entity, baseCost)` | Pays and returns true if there is enough; otherwise returns false and takes nothing. Posts `ManaEvent.Consume` first. On the client it only checks. |
| `add(entity, amount)` | Adds mana (potions, mana on hit, refunds), clamped to [0, max]; negative subtracts. Posts `ManaEvent.Gain` first. Returns the actual change. Server only. |
| `set(entity, value)` | Sets the value directly, clamped to [0, max], without `ManaEvent.Gain`. Server only. |

`tickRegeneration` is for `ManaHandler`; other code shouldn't call it. `ManaStorage` only has the getter `get()`; always read and write through `ManaApi`.

```java
// Mana on hit
ManaApi.add(player, 5.0F);

// A cost of your own (not a spell): only act if it can be paid
if (ManaApi.tryConsume(player, 20.0F)) {
    // ...
}
```

A spell's mana cost doesn't need to be paid by hand; `SpellCasting` calls `tryConsume` before every effect.

---

## 11. Events

All are in the package `ibx.lib.api.event` and posted on `NeoForge.EVENT_BUS`.

| Event | Cancellable | Side | When | What can be changed |
| --- | --- | --- | --- | --- |
| `SpellCastEvent.Pre` | Yes | Server | Before a cast starts; cooldown, mana, target and spell conditions have all passed. Cancelling fails the cast (`CONDITION_FAILED`, no message). | `setManaCost` (the base value, still multiplied by the mana cost multiplier when paid; per effect for channels), `setCooldownTicks` (ticks with the cooldown ratio already applied). |
| `SpellCastEvent.Post` | No | Server | After every effect (after `onCast`; every time for channels). `getContext()` returns the context. | Notification only. |
| `SpellCastStateEvent.Start` | No | Client | An entity starts casting (when the cast state sync arrives); fired for yourself and nearby entities. Instant spells fire it too, followed immediately by the end. | Notification only. |
| `SpellCastStateEvent.End` | No | Client | An entity stops casting; `getReason()` is the end reason. | Notification only. |
| `SpellDamageEvent` | Yes | Server | Before spell damage lands (`SpellDamageApi.hurt`, living targets): the friendly-fire check has passed and the crit is rolled (the amount already includes the crit multiplier), but the vanilla damage flow hasn't started. | `setAmount`; `getSource()` returns the `SpellDamageSource` (snapshot, crit or not). |
| `SpellListEvent` | No | Both | After a player's spell list has been gathered (`SpellCasting.getSpells`). | `add(spell, sourceItem)`, `remove(spell)`, `contains(spell)`, or edit `getSpells()` directly. |
| `ManaEvent.Consume` | Yes | Server | Before mana is consumed (`ManaApi.tryConsume`). Cancelling fails the payment and takes nothing. Not fired in creative mode. | `setCost` (the value received already includes the mana cost multiplier). |
| `ManaEvent.Gain` | No | Server | Before mana is gained (`ManaApi.add`). Not fired by natural regeneration. | `setAmount`; negative subtracts. |
| `ManaEvent.Changed` | No | Server | After mana changed, natural regeneration included — it may fire every tick while regenerating. `getOldValue()` / `getNewValue()`. | Notification only. |

- Every `SpellCastEvent` has `getCaster()`, `getSpell()`, `getSource()`; `SpellCastStateEvent` has `getCaster()`, `getContext()`; `ManaEvent` has `getEntity()`; `SpellDamageEvent`'s `getEntity()` is the damaged target.
- The client-side `CastContext` has no items; to know which hand is casting, look at what the caster is holding.
- **`SpellListEvent` must produce the same result on both sides**, or spells selectable in the wheel get rejected by the server. Listeners should only look at data both sides have: items, mob effects, synced attachments.

```java
// Grant an extra spell while a mob effect is active
NeoForge.EVENT_BUS.addListener((SpellListEvent event) -> {
    if (event.getPlayer().hasEffect(ExampleEffects.ARCANE_INSIGHT)) {
        event.add(ExampleSpells.SPARK, ItemStack.EMPTY);
    }
});

// Half mana cost when casting in water
NeoForge.EVENT_BUS.addListener((SpellCastEvent.Pre event) -> {
    if (event.getCaster().isInWater()) {
        event.setManaCost(event.getManaCost() * 0.5F);
    }
});
```

---

## 12. Cast animations

A player's arm movement while casting is played by Player Animation Library. It is an **optional dependency** of ibx_lib: without it spells work as usual, the arms just don't move. Both you and nearby players see the animation, and it shows in first person too.

Which animation plays is looked up in this order:

1. The spell's own: `Spell.props().castAnimation(id)`.
2. The casting item in the casting hand: `IbxMagicWeaponItem#castAnimation(CastType)`. Tomes and staves each have a set, split by cast type (one for instant, one for cast, one shared by channel and charge).
3. A casting item that isn't an Ibx casting item: `IbxMagicWeaponItem.defaultCastAnimation(CastType)` (the tome set).

- Animation files go in `assets/<namespace>/player_animations/`; `castAnimation` takes the animation id.
- Animations are authored for a right-hand cast and mirrored when the left hand casts. The main hand is used if it holds a casting item, otherwise the off hand.
- The animation defines its own looping: play-once animations stop when they finish; looping ones play until the cast ends and then fade out, for at least 0.6 s (so instant casts are visible too).
- The item model condition `ibx_lib:casting` is true while this item is being held to cast (including while the cast animation is still playing). Tome item models use it to switch between closed and open.

For other client-side presentation of your own (particles, camera, custom animations), listen to `SpellCastStateEvent` (see [11](#11-events)) or write it directly in the spell's `onCastStart` / `onCastTick` / `onCastEnd`.
