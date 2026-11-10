# Target selection

[简体中文](TARGETING.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

**Target selection** (package `ibx.lib.target`) answers two questions: **who** does this action affect, and **where** does it land. Spells, hold-to-use items and mob AI all share it; friend-or-foe checks, crosshair picking, area queries and the on-ground indicators shown while casting all live here.

- **One friend-or-foe rule**: `TargetApi.canHarm` handles teams, PvP and the ownership of pets and summons in one place; all friendly-fire protection in the mod goes through it (see [3](#3-factions-and-friend-or-foe)).
- **Declare, don't code**: a spell or item states "how it aims" with one `Targeting`; picking, locking, validating during the cast, re-validating before the effect and the "no target" messages are all done by the lib (see [4](#4-targeting-declarations)).
- **Two pick timings**: "Pick on Effect" and "Lock at Start", chosen by each player in Options → Controls, not by the spell (see [5](#5-pick-timing)).
- **On-ground indicators**: while casting, a square is drawn under the locked target and areas are drawn as a circle / fan / bar, coloured by the faction they affect and visible to nearby players (see [10](#10-indicators-and-the-client)).
- **Server-authoritative**: who gets picked and locked is decided on the server only; the client just draws the indicators the server sends.
- **Usable on its own**: skip the declaration and call the crosshair picks and area queries in `TargetApi` directly (see [8](#8-targetapi)).

Classes involved:

| Class | Purpose |
| --- | --- |
| `Faction` | Faction: enemy / ally / everyone / no one. |
| `Targeting` | The aiming declaration, written once per spell or item. |
| `TargetMode` / `TargetModes` | Pick timing, and the player's two settings. |
| `TargetApi` | The general entry point: friend-or-foe, crosshair picking, area queries, the current lock. |
| `TargetLock` | An entity's "current lock": the target / point picked by one cast or one item use. |
| `TargetHandler` | Server events: validates locks every tick, sends indicators, picks targets for `TargetingItem`. |
| `TargetingItem` | Interface for items that need a target. |
| `TargetIndicator` | The indicator data, computed on the server and sent to clients. |
| `client.ground.GroundShapes` | Client utility that draws shapes on the ground. |

Related docs:

- Registering spells, cast types, damage, projectiles / beams / area entities: [Spells](SPELL_en.md)
- Summons and their owners: [Summons](SUMMON_en.md)
- Server-sent ground warnings (`api.GroundWarnings`) and other visual effects: [Visual effects](EFFECT_en.md)
- Config options in detail: [Configuration](CONFIG_en.md)
- Spell power and other attributes: [Attributes](ATTRIBUTE_en.md)

---

## 2. Quick start

Two spells: one hits the single enemy under the crosshair, the other picks a spot on the ground and hits enemies around it. The aiming declaration is written in the constructor and passed as the second argument of `Spell`'s constructor.

```java
/** Smite: 1 second cast, hits one enemy within 16 blocks of the crosshair. */
public final class SmiteSpell extends Spell {
    public SmiteSpell(Properties properties) {
        super(properties, Targeting.unit(Faction.ENEMY).range(16.0));
    }

    @Override
    public void onCast(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        LivingEntity target = ctx.target();   // the one settled on at effect time
        if (target == null) {
            return;
        }
        SpellDamageApi.hurt(target, ctx.damage(c -> 6.0F + c.spellPower() * 1.2F), caster, caster, null);
    }
}

/** Quake: 1.5 second cast, picks a point within 24 blocks and hits enemies within 3.5 blocks of it. */
public final class QuakeSpell extends Spell {
    private static final float RADIUS = 3.5F;

    public QuakeSpell(Properties properties) {
        super(properties, Targeting.point().range(24.0).circle(RADIUS, Faction.ENEMY));
    }

    @Override
    public void onCast(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        Vec3 center = ctx.targetPos();        // the point settled on at effect time
        if (center == null) {
            return;
        }
        SpellDamageSnapshot damage = ctx.damage(c -> 4.0F + c.spellPower() * 0.8F);
        // the circle is only an indicator; find who gets hit yourself: enemies in a 3-block-tall cylinder above the point
        for (LivingEntity target : TargetApi.inCylinder(ctx.level(), center, RADIUS, 3.0,
                TargetApi.affects(caster, Faction.ENEMY, false))) {
            SpellDamageApi.hurt(target, damage, caster, caster, center);
        }
    }
}
```

Registration is the same as for any spell; the aiming declaration is not part of the registration line:

```java
public static final DeferredRegister<Spell> SPELLS = DeferredRegister.create(SpellRegistries.SPELL_KEY, "examplemod");

public static final DeferredHolder<Spell, SmiteSpell> SMITE =
        SPELLS.register("smite", () -> new SmiteSpell(Spell.props().manaCost(20).cooldown(8.0F).castTime(1.0F)));
public static final DeferredHolder<Spell, QuakeSpell> QUAKE =
        SPELLS.register("quake", () -> new QuakeSpell(Spell.props().manaCost(30).cooldown(12.0F).castTime(1.5F)));
```

With just that, the two spells behave as follows:

- **Smite**
  - No enemy within 16 blocks of the crosshair on right-click: the spell can't be cast, the action bar says "No target in range", and there is no cast bar, no mana cost and no cooldown.
  - Allies standing in the way (your own pets, summons, etc.) don't count; the crosshair passes through them to the enemy behind. Walls do block.
  - While casting, a red square is drawn under the target.
  - With the player's setting on "Pick on Effect" (default): if the crosshair is on another enemy when the cast completes, that one is hit; if it's on no one, the one picked at the start is hit.
  - With the player's setting on "Lock at Start": if the target dies or gets more than 24 blocks away (range × 1.5), the cast is cancelled with "Target lost", with no mana cost and no cooldown.
  - Cast by a mob: its own attack target is used first, and the AI can also pass a target in directly (see [6](#6-using-it-in-spells)).
- **Quake**
  - While casting, a red circle of radius 3.5 is drawn at the point; under "Pick on Effect" the circle follows the crosshair, under "Lock at Start" it stays put.
  - Pointing at a mob takes the spot under its feet; pointing at a wall or the sky searches downward for ground; with no ground found the spell can't be cast.

---

## 3. Factions and friend or foe

### Faction

`Faction` says who an effect applies to. Crosshair picking, area queries, the hit filter of spell entities and indicator colours all use it.

| Value | Applies to |
| --- | --- |
| `ENEMY` | Those that can be harmed (`TargetApi.canHarm` is true). |
| `ALLY` | Those that can't be harmed: yourself, pets, summons, teammates with friendly fire off, other players while PvP is off… **Includes yourself**; whether to count yourself is up to the caller. |
| `ALL` | Everyone. |
| `NONE` | No one. |

`faction.test(from, target)` tells whether an effect from `from`'s side applies to `target`. With no source (`from` is null, e.g. the caster logged out): `ENEMY` and `ALL` still apply, `ALLY` never does.

### The friend-or-foe rule

`TargetApi.canHarm(from, target)` is the only friend-or-foe check. In order:

1. No `from`: can harm.
2. The target is `from` itself, or both ride the same vehicle: cannot.
3. Both are mapped to their "root owner" (`OwnableEntity#getRootOwner()`; an entity without an owner is itself). Same owner means cannot: your own pets and summons, a pet against its owner, pets of the same owner against each other.
4. Both sides are players: vanilla `Player#canHarmPlayer`, i.e. the server's `pvp` rule and team friendly fire.
5. Both sides are on the same team: depends on the team's friendly fire setting; allowed if it's on.
6. Otherwise the negation of vanilla `isAlliedTo`.

Key points:

- **Pets and summons count as their owner**: whoever the owner can harm, they can harm; whoever can harm the owner can harm them.
- **Teams have friendly fire on by default**: a vanilla scoreboard team allows friendly fire by default, in which case teammates count as enemies. For teammates to count as allies, turn the team's friendly fire off.
- **Between players it is only accurate on the server**: the client doesn't know the server's `pvp` rule.
- Spell damage (`SpellDamageApi.hurt`) and spell projectiles, beams and area entities only affect enemies by default, using this same rule; with friendly fire enabled on the damage snapshot they affect everyone (see [Spells](SPELL_en.md)).

---

## 4. Targeting declarations

A `Targeting` is one declaration: how to aim, how far, and what to draw on the ground while casting. Create it with a factory method and add parameters with the chained methods.

```java
Targeting.self()                                           // no pick (default)
Targeting.unit(Faction.ALLY).range(16.0).orSelf()          // lock an ally under the crosshair, or yourself if there is none
Targeting.unit(Faction.ENEMY).range(16.0)                  // lock an enemy under the crosshair; can't cast without one
Targeting.point().range(24.0).circle(3.5F, Faction.ENEMY)  // pick a point; draws a red circle of radius 3.5 while casting
Targeting.direction()                                      // no lock, aims at the crosshair at effect time
Targeting.self().fan(2.5F, 45.0F, Faction.ENEMY)           // a fan in front of yourself, drawn while casting
Targeting.self().rect(4.0F, 12.0F, 0.8F, Faction.ENEMY)    // a 12 × 0.8 bar lying across, 4 blocks ahead, drawn while casting
```

Every factory call returns a new object and the chained methods modify that object; don't change it after declaring.

### The four kinds

| Factory | `Kind` | What is picked | How the spell reads it |
| --- | --- | --- | --- |
| `Targeting.self()` | `SELF` | Nothing; affects the user or the area around them. | Nothing to read; find the entities in range yourself. |
| `Targeting.unit(faction)` | `UNIT` | Locks one living entity under the crosshair. `faction` must be `ENEMY` or `ALLY`; anything else throws `IllegalArgumentException`. | `ctx.target()` |
| `Targeting.point()` | `POINT` | Picks a point under the crosshair. | `ctx.targetPos()` |
| `Targeting.direction()` | `DIRECTION` | No lock; aims at the crosshair at effect time. Mobs aim at the AI-given target or their own attack target. | `ctx.aimPoint()`; the projectile and beam base classes already aim by this rule. |

The exact rules for picking an entity or a point are in [8](#8-targetapi).

### Chained methods

| Method | Used with | Description |
| --- | --- | --- |
| `range(double)` | unit, point, direction | Range in blocks: how far unit targeting looks for an entity and point targeting for a point. Defaults to 0, so unit and point declarations must set it. For direction it is only used to check that an AI-given target is within reach (0 = no check). |
| `keepRange(double)` | unit, and AI-given targets | Keep range in blocks: a locked target further away than this is lost. Unset = range × 1.5 (`Targeting.KEEP_RANGE_RATIO`); 0 = distance is not checked. |
| `orSelf()` | unit | With nothing under the crosshair at the start, target yourself instead of failing. Only applies at the start. |
| `blockedByOthers()` | unit | An entity of the wrong faction between the crosshair and the target counts as "nothing targeted". Unset = pass through and take the right-faction one behind. |
| `circle(float radius, Faction faction)` | point, self | Draws a circle while casting: centred on the point for point targeting, on the user for self. |
| `fan(float range, float halfAngle, Faction faction)` | self | Draws a fan while casting: `range` blocks ahead, `halfAngle` degrees to each side. |
| `rect(float ahead, float length, float width, Faction faction)` | self | Draws a bar while casting: centred `ahead` blocks in front, lying across the facing direction, `length` long and `width` wide along the facing direction; turns with the user. |
| `hidden()` | all | No indicator (for a boss move meant to surprise). |

The `faction` in `circle` / `fan` / `rect` is "who this area affects" and only decides the indicator's colour.

> **Areas are indicators only.** `circle` / `fan` / `rect` don't find anyone for the spell; who is affected at effect time must be found in `onCast` (`TargetApi.inCylinder` / `TargetApi.inFan`, or by spawning an area entity). Keeping the declared area and the actual area in sync is up to you.

What each kind actually draws is listed in [10](#10-indicators-and-the-client). Unit targeting draws under the target and needs no area; direction draws nothing.

### Reading

| Method | Returns |
| --- | --- |
| `kind()` | `Targeting.Kind`: `SELF` / `UNIT` / `POINT` / `DIRECTION`. |
| `faction()` | The faction a unit declaration locks; `Faction.ALL` for the other kinds. |
| `range()` / `keepRange()` | Range / keep range (already resolved to range × 1.5 when unset). |
| `fallsBackToSelf()` / `isBlockedByOthers()` / `isHidden()` | Match `orSelf()` / `blockedByOthers()` / `hidden()`. |
| `area()` | `Targeting.Area`: `NONE` / `CIRCLE` / `FAN` / `RECT`. |
| `areaRadius()` | Circle radius, fan length or bar length. |
| `areaHalfAngle()` / `areaAhead()` / `areaWidth()` | Fan degrees to each side / how far ahead the bar's centre is / the bar's width. |
| `areaFaction()` | Who the area affects; `Faction.ALL` by default. |
| `picks()` | Whether something is picked at the start: true for unit and point. |
| `needsLock()` | Whether a "current lock" is needed: anything that picks, or that draws an area. |

---

## 5. Pick timing

**When** to pick is not decided by the spell or item; it is a player setting: `TargetMode`, one for unit targeting and one for point targeting.

| Value | Name | Unit targeting | Point targeting |
| --- | --- | --- | --- |
| `EFFECT` (default) | Pick on Effect | The target picked at the start is only a fallback. It is re-picked under the crosshair every tick while casting and once more at effect time: whoever is under the crosshair is used, and the fallback only when no one is. A fallback that becomes invalid is simply dropped; the cast is not cancelled. | The point follows the crosshair while casting and is settled by the crosshair at effect time; with no point found, the starting one is used. |
| `START` | Lock at Start | The target picked at the start is locked and the crosshair may move away. The cast is cancelled if the target becomes invalid. | The point picked at the start is fixed, wherever the crosshair moves afterwards. |

- **A target is required at the start either way**: in both modes, having nothing under the crosshair (without `orSelf()`) or finding no point means the action can't start.
- **Invalid** means: dead, removed, in another dimension, or further from the user than the keep range (`keepRange`). A locked target and a fallback are only checked for these; line of sight and faction are not checked again. When the target is the user itself it stays valid as long as the user is alive.
- `orSelf()` only applies at the start: if someone else was picked at the start, later becomes invalid and no one is under the crosshair, the effect does not switch to yourself; it is cancelled.
- **Mobs** have no setting and use `EFFECT`; when the AI passes a target in directly (see [6](#6-using-it-in-spells)) it is always treated as `START`.
- **Where to set it**: the "Unit Targeting" and "Point Targeting" buttons in Options → Controls, backed by the client config options `targetUnitLockAtStart` and `targetPointLockAtStart` (both false by default, i.e. Pick on Effect). The client sends the two values to the server on login, when they are changed and when the config is reloaded.

The server keeps them in the player attachment `ibx_lib:target_modes` (`TargetModes`), which is kept across death:

| Method | Description |
| --- | --- |
| `TargetModes.of(player)` | This player's two settings; `TargetModes.DEFAULT` (both `EFFECT`) if none were sent. |
| `modes.unit()` / `modes.point()` | The mode for unit / point targeting. |
| `TargetMode.of(holder, kind)` | The mode this entity uses this time: `EFFECT` for non-players; for players, `point()` for point targeting and `unit()` otherwise. |

---

## 6. Using it in spells

### Declaring

`Spell` has two constructors:

| Constructor | Aiming |
| --- | --- |
| `Spell(Properties properties)` | `Targeting.self()`: no pick. |
| `Spell(Properties properties, Targeting targeting)` | Declared in the spell class. |

Aiming is part of the spell's effect and is written in the spell class; `Spell.props()` on the registration line has no option for it. `spell.targeting()` reads it back.

### Reading it in the hooks

| Method | Returns | Description |
| --- | --- | --- |
| `ctx.target()` | `LivingEntity`, may be null | The entity a unit spell affects. In `canCast` and while casting it is "the one currently selected" (the locked one; or under Pick on Effect whoever is under the crosshair now, falling back to the fallback); in `onCast` it is the one settled on at effect time, which may be the caster with `orSelf()`. A target passed in directly by mob AI is also found here. |
| `ctx.targetPos()` | `Vec3`, may be null | The point of a point spell; same rules. |
| `ctx.aimPoint()` | `Vec3` | Where a direction spell aims: the body centre of the AI-given target or the mob's attack target; for players, what the crosshair points at (`TargetApi.aimPoint`). |

- When a unit spell reaches `onCast`, `ctx.target()` always has a value, and when a point spell reaches `onCast`, `ctx.targetPos()` always has a value: otherwise the framework has already cancelled that effect.
- While casting (`onCastTick`) under Pick on Effect, `ctx.target()` can be null for a while (the fallback became invalid and no one is under the crosshair).
- **In client-side hooks both are always null.** `onCastStart` / `onCastTick` / `onCastEnd` run on both sides; send target-related visuals (particles on the target, etc.) from the server-side call.
- For self and direction spells both are null; only when the AI passed a target in directly is `ctx.target()` that target (see "Mob casting" below).

### What the framework does for you

| When | What the framework does | With no target |
| --- | --- | --- |
| Starting the cast | After the cooldown and mana checks, picks and locks by the declaration, then calls `canCast` (`ctx.target()` / `ctx.targetPos()` are already set). | Returns `CastResult.CONDITION_FAILED`, the action bar says "No target in range"; no cast bar, no mana cost, no cooldown. |
| Every tick while casting | Lock at Start: checks that the target is still valid. Pick on Effect: re-picks under the crosshair. The indicator is updated. | A locked target became invalid: the cast is cancelled (`CastEndReason.CANCELLED`) with "Target lost". Pick on Effect does not cancel. |
| Before each effect (cast time reached, every channel tick, releasing a charge) | Settles the target once more and calls `canCast` again; only then is mana spent and `onCast` called. | Cancelled (`CANCELLED`); this effect costs no mana. The message is "Target lost" (Lock at Start, AI-given target) or "No target in range" (Pick on Effect). |
| Cast ends | Releases the lock; the indicator disappears. | — |

- A cancelled cast that never took effect starts no cooldown; a channelled spell that already took effect a few times goes on cooldown as usual.
- **Instant** spells pick once at the start, take effect on the spot and draw no indicator.
- `canCast` is called several times (the target may have changed), so it must only check and not change anything. Conditions that depend on the target's state go here:

```java
@Override
public boolean canCast(CastContext ctx) {
    LivingEntity target = ctx.target();
    if (target != null && target.getHealth() >= target.getMaxHealth()) {
        ctx.fail(Component.translatable("message.examplemod.mend.full_health"));
        return false;
    }
    return true;
}
```

The language keys of the two messages are in `TargetHandler.NO_TARGET` (`message.ibx_lib.target.no_target`) and `TargetHandler.TARGET_LOST` (`message.ibx_lib.target.lost`).

### Mob casting

Mobs have no crosshair, so the rules differ slightly:

| Call | Unit | Point | Direction |
| --- | --- | --- | --- |
| `SpellCasting.cast(mob, spell)` | A spell that locks an enemy first uses the mob's attack target (`Mob#getTarget`; must be in range, visible and harmable), then falls back to picking along its line of sight. A spell that locks an ally picks along the line of sight. | First the spot under the attack target's feet (must be in range and visible), then picking along the line of sight. | Aims at the body centre of the attack target; along the line of sight if there is none. |
| `SpellCasting.cast(mob, spell, target)` | Locks `target`. | Lands under `target`'s feet as of the start. | Aims at `target`. |

When the AI passes a target in directly (second row; there are also overloads taking a `Holder<Spell>` and ones with a source and items):

- Only reach is checked: alive, same dimension, within the spell's range (distance is not checked when the range is 0). **Faction is not checked**; whoever is given is used. If it is out of reach the cast fails the same way as "No target in range".
- From then on it is always handled as Lock at Start: the cast is cancelled when the target becomes invalid (dead, other dimension, beyond the keep range).
- A self spell can be given a target too, and `ctx.target()` returns it.
- Passing null for `target` is the same as the first row.

---

## 7. Using it in items

Items that are **held to use and take effect on release (or on finishing)** implement `TargetingItem`; the lib picks, locks, validates and draws the indicator for them:

```java
public interface TargetingItem {
    Targeting targeting(ItemStack stack, LivingEntity user);
}
```

| Phase | What the lib does | What the item does |
| --- | --- | --- |
| Start using | Picks by the declaration and writes the current lock. With no target, the use doesn't start and the action bar says "No target in range" (or the user is targeted with `orSelf()`). Does nothing when the declaration needs no lock (`needsLock()` is false). | Calls `startUsingItem` as usual. |
| While using | Validates, re-picks and sends the indicator every tick. If a locked target becomes invalid, stops the use with "Target lost". | — |
| Effect | — | In its own `releaseUsing` / `finishUsingItem`, calls `TargetApi.resolve(user)` to get the target / point for this effect; null means nothing happens. |
| After the use stops | Releases the lock automatically; the indicator disappears. | — |

Pick timing follows the player's setting here too (see [5](#5-pick-timing)). The lock only exists on the server, so `TargetApi.resolve` must be called on the server.

```java
/** Blink wand: hold to aim, release to teleport to a point within 16 blocks of the crosshair. */
public final class BlinkWandItem extends Item implements TargetingItem {
    public BlinkWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public Targeting targeting(ItemStack stack, LivingEntity user) {
        return Targeting.point().range(16.0);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide()) {
            return false;
        }
        TargetLock lock = TargetApi.resolve(entity);
        Vec3 point = lock != null ? lock.point() : null;
        if (point == null) {
            return false;
        }
        entity.teleportTo(point.x, point.y, point.z);
        return true;
    }
}
```

Items that **take effect instantly** (inside `use`) don't need to implement it; call `TargetApi.pickEntity` / `TargetApi.pickPoint` directly.

---

## 8. TargetApi

`ibx.lib.target.TargetApi` is all static methods. The "lock" methods are server-only; the rest only read the world, but results that involve friend-or-foe between players are only accurate on the server.

### Friend-or-foe and filters

| Method | Description |
| --- | --- |
| `canHarm(from, target)` | Whether `from` can harm `target` (rules in [3](#3-factions-and-friend-or-foe)). `from` may be null, which always can. |
| `affects(from, faction, includeSelf)` | The usual filter, returned as a `Predicate<LivingEntity>`: `faction` from `from`'s side; with `includeSelf` false, `from` itself is excluded. Use it with the two area methods below. |

### Area queries

| Method | Description |
| --- | --- |
| `inCylinder(level, base, radius, height, filter)` | Living entities in an upright cylinder, nearest to `base` first. Bottom centre `base`, radius `radius`, height `height` upward from `base`. An entity counts when its feet (its position) are inside the circle and its hitbox overlaps the cylinder. |
| `inFan(user, range, halfAngle, needSight, filter)` | Living entities in a fan in front of `user`, nearest first. Horizontal distance at most `range` (plus half the target's width), within `halfAngle` degrees to each side of the facing direction (entities right on top of the user skip the angle check), and vertically 1 block beyond `user`'s hitbox. With `needSight` true, `user` must see them (no wall in between). `user` itself is never included. |

Both exclude dead entities, spectators and marker armor stands; ordinary armor stands are not excluded.

```java
// allies within 5 blocks around yourself, including yourself
List<LivingEntity> allies = TargetApi.inCylinder(caster.level(), caster.position(), 5.0, 3.0,
        TargetApi.affects(caster, Faction.ALLY, true));

// visible enemies within 3 blocks ahead, 60 degrees to each side
List<LivingEntity> enemies = TargetApi.inFan(caster, 3.0, 60.0, true,
        TargetApi.affects(caster, Faction.ENEMY, false));
```

### Crosshair picking

| Method | Description |
| --- | --- |
| `pickEntity(seeker, range, faction, blockedByOthers)` | Picks one living entity of `faction` within `range` blocks of the crosshair; null if none. With `blockedByOthers` true, a wrong-faction entity in the way also blocks. |
| `pickPoint(seeker, range)` | Picks a point within `range` blocks of the crosshair; null if none. |
| `aimPoint(seeker, partialTick, ignore)` | The aim point: the point on the first block or entity hit within 64 blocks (`TargetApi.AIM_RANGE`) along the line of sight; if nothing is hit, the point 64 blocks along it. `ignore` is an entity to skip (usually the projectile or beam itself) and may be null. Callable on both sides. |
| `inReach(holder, target, range)` | Whether it is still within reach: alive, not removed, same dimension, and at most `range` blocks from `holder` (0 = distance is not checked). |

All three picks **never go through walls**: blocks count by their collision shape, and fluids don't block.

**Picking an entity** (`pickEntity`, used by unit targeting):

- Aim assist: the target's hitbox is inflated by 0.3 blocks (`TargetApi.AIM_ASSIST`) before testing.
- Excluded: the seeker, dead entities, spectators, armor stands, non-pickable entities, and the seeker's mount and fellow passengers.
- Hitting a part of a multipart entity counts as its parent.
- The first entity of the right faction along the line of sight, nearest first, is taken; wrong-faction ones are passed through by default.

**Picking a point** (`pickPoint`, used by point targeting):

- Pointing at a living entity: the position under its feet, whatever its faction.
- Pointing at a block: steps slightly out from the block face, then searches downward for ground. Pointing at a wall or ceiling lands on the floor below.
- Pointing at the sky or beyond the range: takes the point at the end of the range along the line of sight and searches downward for ground.
- The downward search goes at most `range` blocks; with no ground found there is no point.

### The current lock

Declarative targeting (spells, `TargetingItem`) is built on these methods; you can also use them directly for your own "start → hold → effect" action (see [9](#9-the-current-lock)). All are server-only.

| Method | Description |
| --- | --- |
| `getLock(holder)` | The entity's current lock; null if none. |
| `begin(holder, targeting, given, owner, onLost)` | Starts a lock by the declaration and writes it as the current lock, replacing the old one. Returns null and writes nothing when there is no target. |
| `resolve(holder, lock)` | At effect time, settles who this effect applies to and writes it back to `lock.target()` / `lock.point()`. Returns false when there is nothing to affect. |
| `resolve(holder)` | For items at effect time: resolves the current lock and returns it; null with no lock or nothing to affect. |
| `release(holder, owner)` | Releases `owner`'s lock; does nothing if the current lock isn't its own. |
| `givenTarget(holder)` | The AI-given target (still in the current lock); null if none. |
| `aimEntity(holder)` | The entity a direction action should aim at: the AI-given target, or else a mob's own attack target; null for players (who use the crosshair). |

Parameters of `begin`:

| Parameter | Description |
| --- | --- |
| `given` | A target passed in directly by AI; may be null. When given, only reach is checked (alive, same dimension, in range), not faction, and it is handled as Lock at Start from then on; point targeting lands under its feet as of that moment. |
| `owner` | The source of this lock, any object. Releasing only works with the same object. |
| `onLost` | May be null. Called when a locked target becomes invalid, or when the lock is replaced by another source; the source cancels its own action here. |

`begin` returns null when: a unit declaration has nothing under the crosshair and no `orSelf()`, a point declaration finds no point, or the AI-given target is out of reach.

---

## 9. The current lock

`TargetLock` is the entity attachment `ibx_lib:target_lock`: the target / point picked by one cast or one item use.

- Server-only; not saved, not synced.
- One per entity at a time; a new one replaces the old one, whose source receives `onLost`.
- Create, read and release it through `TargetApi`; it can't be written to the attachment directly.

| Method | Description |
| --- | --- |
| `targeting()` | The declaration of this lock. |
| `mode()` | The pick timing used this time; always `TargetMode.START` for an AI-given target. |
| `owner()` | Who locked it. For a spell cast it is that cast's `CastContext`. |
| `isGiven()` | Whether the target was given directly by AI. |
| `target()` | The entity currently affected for unit targeting (and for other kinds with a given target); null if none. |
| `point()` | The current point for point targeting; null if none. |
| `locked()` | Under Lock at Start, the locked one; under Pick on Effect, the fallback. |

What `target()` / `point()` mean follows the pick timing: under Lock at Start they are the locked one; under Pick on Effect they are "what an effect right now would apply to" (whatever is under the crosshair, or the fallback).

### What TargetHandler does automatically

For an entity with a lock, `TargetHandler` does the following every tick (on the server):

- **Validating and re-picking**: under Lock at Start it checks that the target is still valid, and removes the lock and calls `onLost` if not; under Pick on Effect it drops an invalid fallback and re-picks under the crosshair.
- **Sending the indicator**: sends what should be drawn now to the players who can see the entity, and to the entity itself if it is a player. Nothing is sent until the lock has lived for one tick; it is sent on every change and re-sent every 20 ticks even without one, so players who walk into view also get it. When the lock is gone, clients are told to stop drawing.
- **Item locks**: released automatically once the user is no longer using that item.

It also listens for the start of item use and picks targets for `TargetingItem` (see [7](#7-using-it-in-items)). `TargetHandler.message(holder, key)` shows an action bar message to a player (and does nothing for non-players).

### Using it directly

Outside spells and items, your own actions can borrow the same validation and indicators, e.g. a mob's charged sweep:

```java
// start charging: a red fan 6 blocks ahead, 45 degrees to each side, turning with the mob
TargetApi.begin(mob, Targeting.self().fan(6.0F, 45.0F, Faction.ENEMY), null, this, null);

// charge finished: resolve the hit and release the lock
for (LivingEntity target : TargetApi.inFan(mob, 6.0, 45.0, false, TargetApi.affects(mob, Faction.ENEMY, false))) {
    // ...
}
TargetApi.release(mob, this);
```

An entity has only one lock at a time: if it casts a spell that needs a lock in the meantime (or the other way round), the later one replaces the earlier one, whose source receives `onLost`. To just place a timed warning at some position, use `api.GroundWarnings` (see [Visual effects](EFFECT_en.md)).

---

## 10. Indicators and the client

### What is drawn

The indicator (`TargetIndicator`) is computed on the server from the current lock and drawn as-is by the client. What players see:

| Declaration | What is drawn | Coloured by |
| --- | --- | --- |
| `unit(faction)` | A square under the currently selected target, 0.3 blocks larger than its hitbox on each side, following the target. | `faction` |
| `point()` + `circle(...)` | A circle of the declared radius at the point. | the `faction` of `circle` |
| `point()` without `circle` | A small circle of radius 0.5 at the point, just marking the spot. | `Faction.ALL` |
| `self()` + `circle` / `fan` / `rect` | A circle / fan / bar that follows the caster; fans and bars turn with the caster's facing. | the area's `faction` |
| `self()` without an area; `direction()` | Nothing. | — |
| Any declaration + `hidden()` | Nothing. | — |

- Shapes have a translucent fill and a slightly darker border, follow the terrain, are emissive and are visible from above and below.
- Point targeting only honours `circle`; an area written on a unit or direction declaration is not drawn.
- Under Pick on Effect the indicator follows the crosshair every tick; what is drawn is the target / point an effect right now would apply to.
- Instant actions draw nothing (the lock doesn't live for a tick).
- Nothing is drawn when the entity the indicator is attached to is invisible to the local player.

`TargetIndicator` is a record (`shape`, `entityId`, `point`, `radius`, `halfAngle`, `ahead`, `width`, `color`); `Shape` is one of `FOOTPRINT` / `CIRCLE` / `FAN` / `RECT`, with the factories `footprint` / `circleAt` / `circleOn` / `fanOn` / `rectOn`. It is created and sent by `TargetHandler`; you normally don't build one yourself.

### Colours and toggles

The colour depends on **the faction the effect applies to**, and the toggles on **who the caster is to the viewer**. Both are client config (`config/ibx_lib-client.toml`), set by each player.

| Option | Default | Effect |
| --- | --- | --- |
| `targetColorAlly` | `#55D16A` | Colour of indicators that affect allies. |
| `targetColorEnemy` | `#E5484D` | Colour of indicators that affect enemies. |
| `targetColorAll` | `#F2C230` | Colour of indicators that affect everyone, and of ones that only mark a point. |
| `showTargetSelf` | `true` | Show your own indicators. |
| `showTargetPlayerAlly` / `showTargetPlayerEnemy` | `true` | Show indicators of allied / enemy players. |
| `showTargetMobAlly` / `showTargetMobEnemy` | `true` | Show indicators of allied / enemy mobs. |
| `targetUnitLockAtStart` / `targetPointLockAtStart` | `false` | Pick timing for unit / point targeting (see [5](#5-pick-timing)). |

The options are described in detail in [Configuration](CONFIG_en.md).

"Allied / enemy" is relative to the viewing player and is computed per player on the server with `TargetApi.canHarm(viewer, caster)` before sending. So the same indicator may be visible to one player and hidden for another, and its colour can differ between them too.

### Server and client

- **Picking happens on the server only.** The client doesn't pick or predict targets; the on-ground indicator is sent after the server has picked, so what is drawn is exactly what the server will affect.
- When starting a cast the client only checks "already casting", cooldown and mana; it doesn't know whether there is a target. When the server refuses for lack of a target, it tells the client to lower the raised hand.
- The two pick-timing settings are stored on the client and sent to the server by the client (see [5](#5-pick-timing)).
- The current lock is not synced; `ctx.target()` / `ctx.targetPos()` are always null on the client (see [6](#6-using-it-in-spells)).

### GroundShapes

`ibx.lib.client.ground.GroundShapes` (client-only) is the utility that draws these shapes. Casting indicators and ground warnings both use it, which is why they look alike. Use it directly to draw shapes in the same style on the client.

| Member | Description |
| --- | --- |
| `color(faction)` | The colour (RGB) of this faction from the client config. `ALL` and `NONE` both use `targetColorAll`. |
| `fill(rgb)` / `edge(rgb)` | Converts to the fill / border colour (ARGB with opacity; the border is darker and more opaque). |
| `sector(out, level, base, yaw, radius, halfAngle, fill, edge, origin)` | Cuts a fan into small quads and adds them to `out`. `yaw` is the facing direction (vanilla yRot, in radians) and `halfAngle` is in radians too; π makes a full circle. Nothing is drawn when the radius is not larger than the border width (0.12 blocks). |
| `rect(out, level, base, ux, uz, halfU, halfV, fill, edge, origin)` | A rectangle: centre `base`, `(ux, uz)` is the unit vector of the long side, half-length `halfU` along it and `halfV` across it. |
| `groundY(level, x, z, nearY)` | The ground height of this column near `nearY`: searches upward when inside a block, at most 2 blocks; downward when in the air, at most 4 blocks; `nearY` if neither finds anything. |
| `surfaceBlock(level, bx, bz, baseY)` | The exposed block of this column near `baseY`; null for walls or pits of two blocks or more. |
| `submit(collector, poseStack, quads)` | Draws the quads. |
| `Quad` / `RENDER_TYPE` | One quad (coordinates relative to `origin`) / the render type used. |

`base.y` is the reference height for the ground search; every vertex is snapped to the ground on its own. `origin` is the origin of the coordinates and must match the origin of the `poseStack` passed to `submit`; the lib itself cuts the shapes relative to the camera position when the level render state is extracted and draws them when custom geometry is submitted:

```java
private static final ContextKey<List<GroundShapes.Quad>> QUADS =
        new ContextKey<>(Identifier.fromNamespaceAndPath("examplemod", "ground_shapes"));

private static void onExtract(ExtractLevelRenderStateEvent event) {
    Vec3 camera = event.getCamera().position();
    List<GroundShapes.Quad> quads = new ArrayList<>();
    int rgb = GroundShapes.color(Faction.ENEMY);
    // center: where to draw; a full circle of radius 4
    GroundShapes.sector(quads, event.getLevel(), center, 0.0F, 4.0, Math.PI,
            GroundShapes.fill(rgb), GroundShapes.edge(rgb), camera);
    event.getRenderState().setRenderData(QUADS, quads);
}

private static void onSubmit(SubmitCustomGeometryEvent event) {
    List<GroundShapes.Quad> quads = event.getLevelRenderState().getRenderData(QUADS);
    if (quads != null) {
        GroundShapes.submit(event.getSubmitNodeCollector(), event.getPoseStack(), quads);
    }
}
```

Both listeners are registered on `NeoForge.EVENT_BUS`. For a warning at a position chosen by the server and visible to everyone, use `api.GroundWarnings` (see [Visual effects](EFFECT_en.md)).
