# Mob Effects

[简体中文](MOB_EFFECT.md) | **English**

[← Back to home](../README/README_en.md)

---

## 1. Overview

Vanilla adds mob effects with `LivingEntity#addEffect`: when the entity already has the same effect, it is only replaced if the new one has a higher level, or the same level and a longer duration. This mod adds versions of `addEffect` with two extra parameters, "stack" and "cap", for effects like "every hit adds one level of Poison".

- **Stack**: adds on top of the level the target already has; how many levels depends on the level given (Poison I adds 1 level, Poison II adds 2).
- **Cap**: an amplifier (the same as `MobEffectInstance`'s amplifier, starting from 0, so 4 = level V); leave it out for no cap.
- Vanilla `addEffect(effect)` and `addEffect(effect, source)` behave exactly as before.

---

## 2. Usage

lib adds these methods to `LivingEntity` with a mixin (interface `ibx.lib.effect.IbxEffectTarget`):

| Method | Description |
| --- | --- |
| `addEffect(effect, source, stack, maxAmplifier)` | Adds the effect. `stack` is whether to stack, `maxAmplifier` is the cap (an amplifier). Returns the same as vanilla: whether the effect was added or changed. |
| `addEffect(effect, source, stack)` | Same as above, no cap. |
| `addEffect(effect, stack, maxAmplifier)` | Same as above, no source. |
| `addEffect(effect, stack)` | Same as above, no source and no cap. |
| `IbxEffectTarget.NO_LIMIT` | No cap (`-1`), same as leaving it out. |

```java
// Every hit stacks one level of Poison for 5 seconds, up to level V
target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), attacker, true, 4);

// No stacking, cap only: at most level III no matter what is given, otherwise vanilla
target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 5), attacker, false, 2);
```

### Stacking rules

With `stack = true`, when the target already has the same effect:

- **Level**: existing amplifier + (given amplifier + 1), no higher than the cap.
- **Duration**: the longer of the remaining duration and the given one; infinite if either is infinite.
- **Ambient, particles, icon**: as in vanilla, taken from the given effect.

If the target does not have the effect yet, it is added at the given level. With `stack = false` it behaves like vanilla. In both cases a given level above the cap is clamped to the cap first.

The resulting level and duration are passed to vanilla `addEffect(effect, source)`, so the applicability checks (undead immune to Poison, NeoForge's `MobEffectEvent.Applicable`), the `MobEffectEvent.Added` event, attribute refreshes and client sync all work as in vanilla.

---

## 3. Calling it from your own mod

`entity.addEffect(effect, source, true, 4)` can be written directly thanks to ModDevGradle interface injection, which makes `LivingEntity` implement `IbxEffectTarget` at compile time. lib configures this in its own `build.gradle`; mods depending on lib need the same configuration in their own `build.gradle` (same content as lib's `interfaces.json`):

```json
{
  "net/minecraft/world/entity/LivingEntity": [
    "ibx/lib/effect/IbxEffectTarget"
  ]
}
```

```groovy
neoForge {
    interfaceInjectionData {
        from(file('interfaces.json'))
    }
}
```

If you would rather not, cast first; it behaves the same in game:

```java
((IbxEffectTarget) target).addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), attacker, true, 4);
```

---

## 4. Modifying vanilla poison

When the server config option `modifyVanillaPoison` is on (off by default, see [Config](CONFIG_en.md)), vanilla poison changes to:

- **Damage per tick = poison level**: 1 at level I, +1 per level, no cap.
- **Fixed interval**: every 10 ticks regardless of level (vanilla is every `25 >> amplifier` ticks, faster at higher levels).
- **Ignores the hurt invulnerability window**: the damage type becomes `ibx_lib:poison`, which is in `neoforge:is_poison` (so it bypasses armor and has no knockback like vanilla poison) and also in `minecraft:bypasses_cooldown`. Vanilla `neoforge:poison` is not changed.
- **Never kills, as in vanilla**: it stops at 1 health, and one tick never takes the entity below 1.

When the option is off, poison is fully vanilla. The damage is dealt by `ibx.lib.effect.VanillaPoison`; a mixin takes over `PoisonMobEffect#applyEffectTick`.

---

## 5. Notes

- "Stack" and "cap" only apply at the moment the effect is given; they are not stored in the effect. Potions, tipped arrows, lingering clouds and other things carrying effects still store vanilla effects and do not stack.
- Vanilla `forceAddEffect` replaces the effect directly and ignores stacking.
