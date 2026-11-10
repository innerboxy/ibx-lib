# Summons

[简体中文](SUMMON.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

The summon framework lets one living entity (a player or a boss) be the **owner** of other mobs. Hand a `Mob` to `SummonApi.summon` and it becomes a summon; the framework then takes care of the following:

- **Slots**: the owner's attribute `ibx_lib:max_summons` is the total budget, and each summon's cost is given when it is summoned. When the budget is short, the oldest summons are dismissed first.
- **Lifetime**: permanent by default. All summons are dismissed when the owner dies, logs out or can't be found; they follow the owner across dimensions.
- **Friendly fire**: a summon, its owner and the owner's other summons can't hurt each other; a player's summons follow player-versus-player rules.
- **Marked target**: summons prefer whatever their owner last hit.
- **Kill credit**: when the owner is a player, kills made by summons count as the player's.
- **Owner transfer**: a summon can be handed over to another owner.
- **Dismissing**: while a player has summons, a "Dismiss Summons" spell is added to their spell list automatically.

Where things are:

| Location | What |
| --- | --- |
| `ibx.lib.api.SummonApi` | Entry point: summoning, dismissing, queries, owner transfer, marked target, stat snapshots, spawn positions (see [4](#4-summonapi)) |
| `ibx.lib.summon.Summoned` | Interface implemented by summon entities (see [5](#5-summon-entities)) |
| `ibx.lib.summon.goal` | AI goals for summons (see [6](#6-ai-goals)) |
| `Spell.props().summonCount(...)` etc. | Declares a spell as a summoning spell (see [7](#7-summoning-spells)) |
| `ibx.lib.api.event.SummonEvent` | Events for summoning, owner transfer and leaving (see [8](#8-events)) |
| `ibx.lib.summon.SummonData` / `OwnerSummons` | Data stored on the summon and on the owner (see [9](#9-attachments)) |

The rules are enforced by event listeners in `ibx.lib.summon.SummonHandler`, which the mod registers on load; you never call it yourself. The whole framework only acts on the server.

> Not covered here: the spell system itself is in [SPELL_en.md](SPELL_en.md), the attribute list in [ATTRIBUTE_en.md](ATTRIBUTE_en.md), targeting and friend-or-foe checks (`TargetApi`) in [TARGETING_en.md](TARGETING_en.md), and the summon-related commands in [COMMAND_en.md](COMMAND_en.md). The summon framework has no config options of its own; the mod's config is in [CONFIG_en.md](CONFIG_en.md).

---

## 2. Quick start

A summoning spell needs three things: a summon entity, a spell class, and one spell registration line. The example below summons a "Spirit Hound" whose health and attack scale with the caster's spell power.

**The summon entity**: implement `Summoned`, override `considersEntityAsAlly` and `getTeam` (both delegate to `SummonApi`), and add the framework's goals in `registerGoals`.

```java
public class SpiritHoundEntity extends PathfinderMob implements Summoned {
    public SpiritHoundEntity(EntityType<? extends SpiritHoundEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    // base values; health and attack are replaced by the spell's numbers when summoned
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        // start following 6+ blocks away from the owner, stop within 2.5 blocks
        this.goalSelector.addGoal(3, new SummonFollowOwnerGoal(this, 1.2, 6.0F, 2.5F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // owner's marked target → whoever hurt the owner → whoever hurt me → copy a mob owner's target
        SummonGoals.addTargetGoals(this, this.targetSelector, 1);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        return SummonApi.isAlly(this, other) || super.considersEntityAsAlly(other);
    }

    @Override
    public @Nullable PlayerTeam getTeam() {
        PlayerTeam own = super.getTeam();
        return own != null ? own : SummonApi.ownerTeam(this);
    }
}
```

Register the entity type and its attributes as usual (you also need a renderer, omitted here):

```java
public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities("examplemod");
public static final DeferredHolder<EntityType<?>, EntityType<SpiritHoundEntity>> SPIRIT_HOUND = ENTITIES.registerEntityType(
        "spirit_hound", SpiritHoundEntity::new, MobCategory.MISC,
        builder -> builder.sized(0.6F, 0.85F).noLootTable().clientTrackingRange(8));

// EntityAttributeCreationEvent (mod event bus)
event.put(SPIRIT_HOUND.get(), SpiritHoundEntity.createAttributes().build());
```

**The spell**: in `onCast`, `create` the entity, position it, write its stats with `setStat`, then hand it to `SummonApi.summon`. Slot cost and duration come from the registration line.

```java
public final class SummonSpiritHoundSpell extends Spell {
    public SummonSpiritHoundSpell(Properties properties) {
        super(properties);
    }

    @Override
    public void onCast(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        if (!(ctx.level() instanceof ServerLevel level)) return;
        double spellPower = caster.getAttributeValue(AttributeRegistry.SPELL_POWER);
        for (int i = 0; i < this.summonCount(); i++) {
            // must be created in the owner's level
            SpiritHoundEntity hound = ExampleEntities.SPIRIT_HOUND.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (hound == null) return;
            // in front of the owner, within 60 degrees to either side, 1.5 to 3 blocks away
            Vec3 pos = SummonApi.findSpawnPosInFront(caster, hound, 1.5, 3.0, 60.0);
            hound.snapTo(pos.x, pos.y, pos.z, caster.getYRot(), 0.0F);
            // computed from the caster's spell power right now; later gear changes don't affect it
            SummonApi.setStat(hound, Attributes.MAX_HEALTH, 20.0 + 1.0 * spellPower);
            SummonApi.setStat(hound, Attributes.ATTACK_DAMAGE, 4.0 + 0.3 * spellPower);
            SummonApi.summon(caster, hound, this);
        }
    }
}
```

**The registration line**: setting `summonCount` makes it a summoning spell.

```java
public static final DeferredRegister<Spell> SPELLS = DeferredRegister.create(SpellRegistries.SPELL_KEY, "examplemod");
public static final DeferredHolder<Spell, SummonSpiritHoundSpell> SUMMON_SPIRIT_HOUND = SPELLS.register("summon_spirit_hound",
        () -> new SummonSpiritHoundSpell(Spell.props().manaCost(30).cooldown(10.0F).castTime(1.5F).summonCount(1).summonCost(1)));
```

**Summoning without a spell** (a boss ability, for example): call the five-argument `summon` with `null` for the spell. A mob owner also has a default budget of 1; to summon more, raise `ibx_lib:max_summons` in its attributes.

```java
// the boss's attributes
.add(AttributeRegistry.MAX_SUMMONS, 4.0)

// in the ability: not from a spell, permanent, costs 1 slot
SummonApi.summon(boss, minion, null, 0, 1);
```

---

## 3. Rules

### Slots

- The total budget is the owner's attribute `ibx_lib:max_summons` (`AttributeRegistry.MAX_SUMMONS`, displayed as "Max Summons"), rounded down. Default 1, range 0 to 1024; every living entity has this attribute.
- Each summon's cost is given when it is summoned: the registration line's `summonCost` (default 1) for spells, the `cost` argument for direct `summon` calls. It can be 0, which takes no slots.
- Used slots are the sum of the costs of every summon on the owner's list, including ones that aren't loaded at the moment.
- When there isn't enough room, summons are pushed out starting from the **oldest** (leave reason `REPLACED`) until the new one fits.
- If a single summon costs more than the total budget, it can't be summoned: `summon` returns `false` and nothing is pushed out.
- Lowering the limit (taking off gear that raises it, for example) does not dismiss existing summons right away; the new limit is applied on the next summon.

### Lifetime and leaving

- **Permanent**: no time limit by default. Summoning marks the entity as persistent so it never despawns naturally.
- **Timed**: if `summon`'s `durationTicks` is greater than 0, or the spell's registration line sets `summonDuration`, the summon is timed and leaves with `EXPIRED` when its time is up. The expiry is a world game time, so time spent unloaded counts too.
- **Owner dies**: all summons leave (`OWNER_DIED`).
- **Player owner logs out**: all summons leave (`OWNER_LEFT`).
- **Owner can't be found**: a summon checks for its owner once per second. After 3 failed checks in a row (owner removed, or in an unloaded chunk), or if the owner's list no longer contains it, it leaves (`OWNER_GONE`).
- **Owner changes dimension**: summons are moved to a spot 1.5 to 3 blocks from the owner. After the move it is a new entity object (same UUID and attachments), so don't hold on to `Mob` references for long; call `getSummons` when you need them.
- **Summons don't use portals on their own**: stepping into a Nether or End portal does nothing.
- **Killed**: the vanilla death sequence runs as usual. The framework only removes it from the owner's list at the moment of death (freeing its slots immediately) and fires the event (`KILLED`).
- **Removed by other code** (`discard()` and the like): it is removed from the owner's list and the event fires with `DISMISSED` (or `KILLED` if the entity's removal reason is `KILLED`).
- **Unloaded summons**: list entries that can't be found for 5 seconds in a row are dropped on the next `getSummons` call. When such a summon loads later and finds itself missing from the owner's list, it leaves on its own (`OWNER_GONE`).

When the framework makes a summon leave (anything but being killed), a `Summoned` entity gets its `onUnsummon` called; other mobs are removed with a puff of smoke. Leave reasons are listed in [8](#8-events).

### Friendly fire

The following damage is cancelled outright (based on the damage source's causing entity, so projectiles shot by a summon count too):

- a summon hitting its own owner;
- a summon hitting another summon of the same owner;
- an owner hitting their own summon.

When the owner is a player, the summon is also **treated as the owner** and judged by player-versus-player rules, in both directions. If the server has PvP off, or the two players are on the same team with friendly fire off:

- the other player (and their summons) can't hurt your summons;
- your summons can't hurt that player or their summons either.

This does not apply to mob owners.

`Summoned` extends vanilla `OwnableEntity`, and the owner comes from the framework's data, so any check that resolves ownership through `getRootOwner()` (including this mod's `TargetApi.canHarm`, see [TARGETING_en.md](TARGETING_en.md)) automatically puts a summon on its owner's side.

Summon AI never picks allies as targets; that relies on the `considersEntityAsAlly` and `getTeam` overrides in the entity class (see [5](#5-summon-entities)).

### Marked target

- When an owner damages another living entity (damage actually dealt, not itself, not one of its own summons) while it has summons, that entity becomes the owner's **marked target**.
- Every hit restarts the timer; the mark lasts 10 seconds after the last hit (`SummonApi.MARK_TICKS` = 200 ticks).
- The mark is dropped when the target dies, disappears, or is not in the owner's dimension.
- Summons with `SummonMarkedTargetGoal` attack the marked target first, and switch when the mark changes.
- You can also call `SummonApi.mark(owner, target)` yourself.
- The marked target is neither saved nor synced to clients.

### Kill credit

When the owner is a player, every hit a summon lands records the target as "recently hurt by this player" (100 ticks, as in vanilla). So mobs killed by summons drop experience and player-kill-only loot, and count towards the player's statistics. This does not apply to mob owners.

### Owner transfer

`SummonApi.transferOwner(summon, newOwner)` hands a summon over to a new owner. Afterwards it is the new owner's summon: it follows and fights for the new owner, and friendly fire, team, marked target and kill credit all go by the new owner.

- It is removed from the old owner's list and appended to the end of the new owner's list (it counts as the new owner's newest). Its cost, the spell that summoned it, its expiry time and the stats written by `setStat` stay the same.
- It takes the new owner's slots: if the new owner's remaining budget can't hold it, the transfer fails; it never pushes out the new owner's existing summons.
- The new owner can't be a summon, nor the summon itself; both the new owner and the summon must be alive.
- A cancellable `SummonEvent.Transfer` fires before the transfer; `Pre` / `Post` / `Unsummon` do not fire.
- The summon's attack target and its "last hurt by" record are cleared, and it picks targets for the new owner from the next tick.

### Stats

A summon's strength is fixed at the moment it is summoned: the spell computes values from the caster's attributes at that time and writes them onto the summon with `SummonApi.setStat`. Later gear changes on the owner don't affect summons already out.

`setStat(summon, attribute, value)` puts an additive modifier with a fixed id (`ibx_lib:summon_snapshot`) on the attribute, making up the difference between `value` and the base value:

- calling it again replaces the previous one;
- with no other modifiers on the attribute, the final value is exactly `value`; other bonuses (gear, potions) stack on top of `value`;
- when the attribute is max health, the summon is also healed to full;
- the modifier is saved with the entity.

---

## 4. SummonApi

`ibx.lib.api.SummonApi`, all static methods. Methods that change things only act on the server. On the client, summon data isn't synced, so `data` / `isSummon` / `ownerId` find nothing there (see [9](#9-attachments)).

**Queries**

| Method | Returns | Description |
| --- | --- | --- |
| `data(entity)` | `SummonData` | The entity's summon data; `SummonData.NONE` if it isn't a summon. |
| `isSummon(entity)` | `boolean` | Whether it is a summon. |
| `ownerId(entity)` | `UUID`, nullable | The owner's UUID; null if it isn't a summon. |
| `getOwner(summon)` | `LivingEntity`, nullable | The owner, looked up across all dimensions on the server; null if it isn't a summon or the owner isn't loaded. |
| `isOwnedBy(summon, owner)` | `boolean` | Whether `summon` belongs to `owner`. |
| `capacity(owner)` | `int` | Total budget (`ibx_lib:max_summons` rounded down). Works on both sides. |
| `used(owner)` | `int` | Used slots, including summons that aren't loaded. The client only has the local player's own data. |
| `fits(owner, cost)` | `boolean` | Whether a summon costing `cost` can be summoned. Only compares against the total budget, not the used slots (a full list pushes out the oldest). Works on both sides. |
| `getSummons(owner)` | `List<Mob>` | The owner's present (loaded) summons, oldest first. Also drops list entries that haven't been found for 5 seconds in a row or that now belong to someone else. Returns an empty list on the client. |

**Summoning**

| Method | Returns | Description |
| --- | --- | --- |
| `summon(owner, summon, spell)` | `boolean` | Summons using the spell's registered `summonCost` and `summonDuration` (no duration = permanent). Use this in a summoning spell's `onCast`. |
| `summon(owner, summon, spell, durationTicks, cost)` | `boolean` | Puts `summon` into the owner's level as `owner`'s summon. Pass null for `spell` when it isn't from a spell; `durationTicks` of 0 or less = permanent; `cost` is at least 0. |

Before calling `summon`, the mob must already be created (**in the owner's level**), positioned and given its stats, but not yet added to the world; `summon` adds it. In order, it: checks that the single cost doesn't exceed the total budget → fires `SummonEvent.Pre` → pushes out the oldest if there isn't room → writes the summon data, marks it persistent and adds it to the world → records it on the owner's list → fires `SummonEvent.Post`.

It returns `false` (nothing summoned) when: called on the client, the mob and the owner aren't in the same level, the single cost exceeds the total budget, `Pre` is cancelled, or the entity can't be added to the world.

**Leaving**

| Method | Returns | Description |
| --- | --- | --- |
| `dismiss(summon, reason)` | — | Makes one summon leave: removes it from the owner's list → fires `SummonEvent.Unsummon` → calls its `Summoned.onUnsummon` (mobs that don't implement `Summoned` are removed with a puff of smoke). |
| `dismissAll(owner, reason)` | `int` | Makes all of the owner's summons leave, and clears the list and the marked target. Returns how many were sent away; unloaded ones are just dropped from the list, aren't counted and fire no event. |
| `forget(summon, reason)` | — | Only removes it from the owner's list without removing the entity; fires `SummonEvent.Unsummon` only if it was actually on the list. The framework calls this itself when a summon is killed or removed by other code; you rarely need it. |

**Owner transfer**

| Method | Returns | Description |
| --- | --- | --- |
| `transferOwner(summon, newOwner)` | `boolean` | Hands the summon over to `newOwner`; rules in [3](#3-rules). Returns `false` if it isn't a summon, already belongs to the new owner, the new owner is dead or is itself a summon, the new owner lacks slots, or `SummonEvent.Transfer` is cancelled. |

**Allies and team** (for the overrides in the summon entity class, see [5](#5-summon-entities))

| Method | Returns | Description |
| --- | --- | --- |
| `isAlly(summon, other)` | `boolean` | Whether the summon treats `other` as an ally: its owner, other summons of the same owner, and the owner's allies (the owner's pets, teammates and so on). Someone else's summon never counts. |
| `ownerTeam(summon)` | `PlayerTeam`, nullable | The owner's team; null if the owner can't be found. |

**Marked target**

| Method | Returns | Description |
| --- | --- | --- |
| `mark(owner, target)` | — | Sets `target` as the owner's marked target and restarts the timer. |
| `markedTarget(owner)` | `LivingEntity`, nullable | The owner's current marked target; if it has expired, died, disappeared or isn't in the same dimension, the mark is cleared and null is returned. |

**Stats and spawn positions**

| Method | Returns | Description |
| --- | --- | --- |
| `setStat(summon, attribute, value)` | — | Fixes one of the summon's attributes at `value`; rules in [3](#3-rules). Does nothing if the summon doesn't have the attribute. |
| `findSpawnPos(owner, summon, minRadius, maxRadius)` | `Vec3` | Finds a place to stand in a ring `minRadius` to `maxRadius` blocks around the owner: the block below has a solid top face, the body doesn't collide with blocks, and it isn't in water or lava. Tries 16 random spots and falls back to the owner's position. Only computes a position; it doesn't move the entity. |
| `findSpawnPos(level, center, summon, minRadius, maxRadius)` | `Vec3` | Same, around any point; returns `center` if nothing is found. |
| `findSpawnPosInFront(owner, summon, minRadius, maxRadius, halfAngle)` | `Vec3` | Searches only the sector in front of the owner (`halfAngle` degrees to either side of where they face), and requires no blocks between the owner's eyes and the spot, so summons don't appear behind walls or under the floor. Falls back to the ring around the owner, then to the owner's position. |

**Constants**

| Constant | Value | Description |
| --- | --- | --- |
| `SNAPSHOT_MODIFIER_ID` | `ibx_lib:summon_snapshot` | Id of the attribute modifier added by `setStat`. |
| `MARK_TICKS` | `200` | How long a marked target lasts (ticks). |

---

## 5. Summon entities

`ibx.lib.summon.Summoned` is the interface summon entities implement. It extends vanilla `OwnableEntity`.

| Method | Description |
| --- | --- |
| `getOwnerReference()` | Default implementation: takes the owner from the framework's data. With it, `OwnableEntity`'s `getOwner()` and `getRootOwner()` work without storing the owner yourself. |
| `onUnsummon(reason)` | Called when the framework makes it leave (expired, replaced, dismissed, owner dead / logged out / gone). Server-only; being killed doesn't go through here. By default it emits a small puff of smoke (`POOF` particles) and calls `discard()`. |

For your own leaving effect, override `onUnsummon`, play particles and sounds there, and finish with `discard()`. If the entity still isn't removed when the method returns, the framework removes it for you, so you can't keep the entity around to play an animation here.

```java
@Override
public void onUnsummon(UnsummonReason reason) {
    if (this.level() instanceof ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
    }
    this.discard();
}
```

An interface can't override the entity class's own methods, so the implementing class **must also override two methods**, both delegating to `SummonApi`:

```java
@Override
protected boolean considersEntityAsAlly(Entity other) {
    return SummonApi.isAlly(this, other) || super.considersEntityAsAlly(other);
}

@Override
public @Nullable PlayerTeam getTeam() {
    PlayerTeam own = super.getTeam();
    return own != null ? own : SummonApi.ownerTeam(this);
}
```

- `considersEntityAsAlly`: makes vanilla `isAlliedTo` treat the owner, fellow summons and the owner's allies (the owner's pets, teammates and so on) as allies. Vanilla target selection uses it to skip allies.
- `getTeam`: a summon that isn't on a team itself counts as being on its owner's team, so team colour and team friendly-fire rules follow the owner.

**Mobs that don't implement `Summoned`** can also be handed to `SummonApi.summon` (the parameter type is `Mob`): slots, lifetime, following across dimensions, the friendly-fire rules in [3](#3-rules) and kill credit all still apply, and they leave with a puff of smoke. But such a mob isn't an `OwnableEntity`, so checks that resolve ownership through `getRootOwner()` won't recognise it, it lacks the two overrides above, and you have to handle its AI targets yourself. New summons should implement `Summoned`.

---

## 6. AI goals

Package `ibx.lib.summon.goal`. Summons **don't look for fights on their own**; they act when their owner does. Add the goals in the entity's `registerGoals`:

```java
// movement: goes into goalSelector
this.goalSelector.addGoal(3, new SummonFollowOwnerGoal(this, 1.2, 6.0F, 2.5F));
// targeting: adds all four, taking 4 priorities starting at 1
SummonGoals.addTargetGoals(this, this.targetSelector, 1);
```

`SummonGoals.addTargetGoals(mob, targetSelector, first)` requires `mob` to be a `PathfinderMob` and adds four goals starting at priority `first`:

| Priority | Goal | Description |
| --- | --- | --- |
| `first` | `SummonMarkedTargetGoal` | Attack the owner's marked target. |
| `first + 1` | `SummonOwnerHurtByTargetGoal` | Attack whoever hurt the owner. |
| `first + 2` | vanilla `HurtByTargetGoal` | Attack whoever hurt the summon itself (never allies). |
| `first + 3` | `SummonCopyOwnerTargetGoal` | When the owner is a mob, copy the owner's attack target. |

If you only want some of them, add them one by one with `addGoal`. The goals:

| Class | Constructor | Behaviour |
| --- | --- | --- |
| `SummonFollowOwnerGoal` | `(mob, speedModifier, startDistance, stopDistance)` | Follows the owner: starts when `startDistance` or more blocks away, stops within `stopDistance` blocks; teleports to 2 to 3 blocks from the owner when 12 or more blocks away. Doesn't follow while it has an attack target, or while the owner is in another dimension or a spectator. Doesn't avoid water while following. Modelled on the vanilla tamed-animal follow goal, but the mob doesn't have to be a tamable animal. |
| `SummonMarkedTargetGoal` | `(mob)` | Sets the attack target to the owner's marked target (`SummonApi.markedTarget`), switching when the mark changes. Put it at the highest priority in the target selector. |
| `SummonOwnerHurtByTargetGoal` | `(mob)` | When the owner is hurt, sets the attack target to the attacker. Modelled on vanilla `OwnerHurtByTargetGoal`. |
| `SummonCopyOwnerTargetGoal` | `(mob)` | When the owner is a `Mob`, sets the attack target to the owner's attack target. Does nothing for player owners. |

The three targeting goals filter with the vanilla default combat conditions: no allies, nothing that can't be attacked (creative-mode players and the like), and the target must be visible at the moment it is picked; once picked, it isn't dropped for going out of sight.

---

## 7. Summoning spells

A spell declares itself a summoning spell through three values on its registration line (`Spell.props()`). The rest of the spell system is in [SPELL_en.md](SPELL_en.md).

| Registration | Default | Description |
| --- | --- | --- |
| `summonCount(int)` | `0` | How many are summoned per cast. **Setting it (greater than 0) makes the spell a summoning spell.** |
| `summonCost(int)` | `1` | Slots each one takes, at least 0. |
| `summonDuration(float)` | `0` | The summon's lifetime in seconds; 0 = permanent. |

The matching getters on the spell: `isSummon()`, `summonCount()`, `summonCost()`, `summonDuration()`.

What the framework does once `summonCount` is set:

- **Slot check before casting**: when the cast starts, if `summonCost` exceeds the caster's total budget, the cast fails with no mana spent and no cooldown, and the player sees "Not enough summon slots" on the action bar (`message.ibx_lib.cast.summon_over_capacity`).
- **Extra tooltip lines**: the spell wheel's tooltip gains a line "Summons N, M slot(s) each"; with `summonDuration` set, another line "Lasts Xs"; and when `summonCost` exceeds the total budget, a "Not enough summon slots" line.

**The summoning itself is yours to write** in `onCast`: loop `summonCount()` times, and for each one `create`, position, `setStat`, then call `SummonApi.summon(caster, mob, this)` (see [2](#2-quick-start)). `summon` records the cost and duration from this spell's `summonCost` and `summonDuration`. `CastContext` has no summon-specific methods.

Notes:

- The pre-cast check looks at a **single** summon's cost, not `summonCount × summonCost`. If one cast summons several and there isn't enough room, the later ones push out the earlier ones.
- `summon` can return `false` (cancelled by `SummonEvent.Pre`, for example); check the return value before playing particles or sounds for that summon.
- To show the summon's strength in the tooltip ("Health 40" and so on), override the spell's `getTooltip(caster)`.

### Dismiss Summons

The mod registers one spell of its own, `ibx_lib:dismiss_summons` (`SpellRegistry.DISMISS_SUMMONS`, class `DismissSummonsSpell`): instant, no mana cost, 1 second cooldown; it makes all of the caster's summons leave (`DISMISSED`).

- It doesn't come from any item: while a player has summons, the framework appends it to the end of their spell list; with no summons it doesn't appear.
- It can't be cast without summons; the message is "You have no summons" (`message.ibx_lib.cast.no_summons`).
- It isn't listed in the spell picker screen and can't be fixed onto an item.

---

## 8. Events

`ibx.lib.api.event.SummonEvent`, server-only, posted on `NeoForge.EVENT_BUS`. All have `getOwner()` and `getSummon()` (a `Mob`).

| Event | Cancellable | When | What you can read |
| --- | --- | --- | --- |
| `SummonEvent.Pre` | Yes | Before summoning: the single-cost check has passed, nothing has been pushed out yet, and the summon isn't in the world yet. Cancelling skips the summon and `SummonApi.summon` returns `false`. | `getSpell()`: the spell that summons it, null when it isn't from a spell. |
| `SummonEvent.Post` | No | The summon is in the world and on the owner's list. | — |
| `SummonEvent.Transfer` | Yes | Before an owner transfer. Cancelling skips the transfer and `transferOwner` returns `false`. | `getOwner()` is the old owner (null if it can't be found), `getNewOwner()` is the one receiving it. |
| `SummonEvent.Unsummon` | No | A summon leaves: the framework sends it away, or it is killed or removed by other code. By now it is off the owner's list; one sent away by the framework isn't removed yet, one that was killed has just started dying, and one removed by other code is already gone. | `getReason()`: the leave reason. `getOwner()` is null when the owner can no longer be found. |

`getOwner()` is never null in `Pre` and `Post`.

Leave reasons, `ibx.lib.summon.UnsummonReason`:

| Value | When |
| --- | --- |
| `EXPIRED` | A timed summon's time is up. |
| `REPLACED` | Pushed out by a new summon when slots ran short. |
| `DISMISSED` | Dismissed on purpose: the "Dismiss Summons" spell, a command, or your own code calling `dismiss`; also used when other code removes it directly. |
| `OWNER_DIED` | The owner died. |
| `OWNER_LEFT` | The player owner logged out. |
| `OWNER_GONE` | The owner can't be found, or the owner's list no longer contains it. |
| `KILLED` | Killed. The framework doesn't handle its removal (the vanilla death sequence runs); it only updates the list and fires the event, and `onUnsummon` isn't called. |

```java
// do something when a summon is pushed out by a newer one
NeoForge.EVENT_BUS.addListener((SummonEvent.Unsummon event) -> {
    if (event.getReason() == UnsummonReason.REPLACED && event.getOwner() instanceof Player player) {
        // ...
    }
});
```

---

## 9. Attachments

The framework keeps its state in two data attachments, registered in `ibx.lib.registry.AttachmentRegistry`. **Always read and write through `SummonApi`**; this section only explains what is stored and who it is synced to.

| Attachment | On | Type | Saved | Synced |
| --- | --- | --- | --- | --- |
| `ibx_lib:summon` (`SUMMON`) | The summon | `SummonData` | Yes | Not synced |
| `ibx_lib:owner_summons` (`OWNER_SUMMONS`) | The owner | `OwnerSummons` | The list is saved, the marked target is not | The list is synced only to the owner's own client; the marked target is not synced |

What follows from this:

- The client can't tell whether an entity is a summon or who owns it. If your client code needs that (to render summons differently, say), sync it yourself.
- A player's client only knows that player's own summons (UUIDs and costs), not anyone else's.
- Summon data is saved with the entity and carried across dimensions, so ownership survives a reload.
- The owner's list is not kept after death (all summons leave when the owner dies, so it is empty anyway).

`ibx.lib.summon.SummonData` (get it with `SummonApi.data(entity)`):

| Method | Description |
| --- | --- |
| `isSummon()` | Whether it has an owner, i.e. whether it is a summon. |
| `owner()` | The owner's UUID. |
| `spell()` | Id of the spell that summoned it; null when it wasn't from a spell. |
| `cost()` | Slots it takes from its owner. |
| `expiresAt()` | Expiry as a world game time (`level.getGameTime()`); -1 = permanent. |
| `isPermanent()` | Whether it is permanent. |

`SummonData.NONE` is the default value meaning "not a summon".

`ibx.lib.summon.OwnerSummons` (the list on the owner, in summoning order, oldest first):

| Method | Description |
| --- | --- |
| `isEmpty()` | Whether the list is empty. |
| `entries()` | A read-only copy of the list; each `Entry` has `id()` (the summon's UUID) and `cost()` (slots taken). |
| `used()` | Sum of the costs. |
| `contains(id)` | Whether this summon is on the list. |
| `marked()` / `markedAt()` | The marked target's UUID / the game time it was marked. |

`add`, `remove` and `setMarked` are for `SummonApi` only; don't call them directly, or the list won't be synced and no events will fire.
