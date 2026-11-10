# Visual effects

[简体中文](EFFECT.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

Three purely visual effects with no gameplay resolution of their own, all in the `ibx.lib.api` package and each one line to call on the server:

| Effect | Class | What it draws |
| --- | --- | --- |
| Ground warning | `GroundWarnings` | Marks a circle or strip on the ground to warn that a hit is coming (see [2](#2-ground-warnings)). |
| Block wave | `BlockWave` | Surface blocks pop up and fall back in a wave spreading out from a point (see [3](#3-block-wave)). |
| Colored fire | `CustomFire` | Replaces the fire texture on an entity and on the first-person screen (see [4](#4-colored-fire)). |

- **Visual only**: no damage, no terrain changes, no entities spawned. Who gets hit and for how much is up to the caller; just keep your timing in step with the effect.
- **Sent to nearby players**: ground warnings and block waves send a single packet to players within 64 blocks; the client computes and draws the rest at the same pace. Players who walk into range after the packet was sent don't see that instance.
- **No registration needed**: all three ship with the lib; just call them.

---

## 2. Ground warnings

**`GroundWarnings`**: marks an area on the ground for a while. It's drawn the same way as the ground indicators of target selection while casting (filled center, slightly darker edge, follows the terrain, emissive); see [Target selection](TARGETING_en.md).

| Method | Description |
| --- | --- |
| `circle(level, center, radius, duration)` | A circle of radius `radius` around `center`, shown for `duration` ticks in the enemy color. Returns an id. |
| `circle(level, center, radius, duration, color)` | Same; `color` is a `Faction` that picks whose color to use. |
| `line(level, from, dir, length, width, duration)` | A strip from `from` along `dir` (a horizontal unit vector), `length` long and `width` wide, in the enemy color. Returns an id. |
| `line(level, from, dir, length, width, duration, color)` | Same, with a color. |
| `cancel(level, id)` | Removes area `id` early (fine if it has already expired), e.g. when the boss dies. |

- `level` is a `ServerLevel`; `center` / `from` are points on the ground.
- The color comes from each viewer's client config for that faction: `ENEMY` uses `targetColorEnemy` (red by default), `ALLY` uses `targetColorAlly` (green), `ALL` / `NONE` use `targetColorAll` (yellow); see [Configuration](CONFIG_en.md).
- It ignores the client's "show enemy mob indicators" switch and is shown even when that is off.
- The client removes it on time based on the world time when it was received; `duration` is at least 1 tick.

```java
// Boss winds up: a 4-block circle at its feet lights up for 1 second, then the hit resolves
ServerLevel level = (ServerLevel) boss.level();
GroundWarnings.circle(level, boss.position(), 4.0F, 20);
// 20 ticks later the boss's own move logic damages targets inside the circle

// A 12-block long, 3-block wide charge path ahead
Vec3 dir = boss.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
int id = GroundWarnings.line(level, boss.position(), dir, 12.0F, 3.0F, 30);
// if the move is interrupted
GroundWarnings.cancel(level, id);
```

---

## 3. Block wave

**`BlockWave`**: pushes a fan-shaped wave out from a point; surface blocks the wave passes over pop up and fall back, as if something were moving underground. The real blocks never move and collision is unchanged.

| Method | Description |
| --- | --- |
| `fan(level, from, dir, halfAngle, startRadius, range, speed, height)` | A fan-shaped wave: pushes from `from` towards `dir` (a horizontal unit vector), `halfAngle` degrees to each side (0–180), from `startRadius` blocks out to `range` blocks, `speed` blocks per tick; the nearest blocks pop `height` blocks high, farther ones less. |
| `fan(..., block)` | Same; when `block` isn't `null`, the popping blocks are drawn as that block (e.g. a magma block) and the grass and flowers on top stay put. |
| `ring(level, center, startRadius, range, speed, height)` | A full ring spreading outwards, equivalent to `fan` with `halfAngle` 180. |
| `ring(..., block)` | Same, with the block to draw. |

- It pops the blocks actually on the ground, along with the grass, flowers and snow layers on top.
- The farthest blocks pop about 60% as high as the nearest ones, and each column varies a little at random.
- **Lining up damage**: at tick `t` the wave is `startRadius + speed × t` blocks from the origin, and each column reaches its peak exactly when the wave reaches it. To "hurt where the wave is", check for targets at that distance each tick.

```java
// Ground slam: starts at 1 block, spreads to 8, 0.5 blocks per tick, pops at most 0.6 blocks
BlockWave.ring(level, boss.position(), 1.0F, 8.0F, 0.5F, 0.6F);

// A wave of magma pushed in a 90° fan ahead
BlockWave.fan(level, boss.position(), dir, 45.0F, 1.0F, 10.0F, 0.6F, 0.8F,
        Blocks.MAGMA_BLOCK.defaultBlockState());
```

---

## 4. Colored fire

**`CustomFire`**: replaces the texture of the fire burning on an entity (the flames on the body in third person and the fire on the first-person screen). The burning itself is still vanilla: burn time, damage, water putting it out and fire-immune entities are unchanged; this only records on the entity "which fire it's burning with", saved and synced to players who can see it, and cleared automatically when the fire goes out.

| Method | Description |
| --- | --- |
| `ignite(entity, look, seconds)` | Sets the entity on fire for `seconds` seconds (same as vanilla `igniteForSeconds`, never shortening existing fire) and records that it burns with `look`. Fire-immune entities don't ignite and nothing is recorded. Server-only. |
| `look(entity)` | Which fire the entity is burning with; `null` for vanilla fire or when not burning. Works on both sides. |

An entity burning with colored fire that walks into vanilla fire keeps showing the colored fire until it's fully out.

**Textures**: `look` is the base name of the textures, e.g. `examplemod:frost_fire`:

| Texture | Location | Notes |
| --- | --- | --- |
| Flames on the body | `assets/examplemod/textures/block/frost_fire_0.png`, `frost_fire_1.png` | Like vanilla `fire_0` / `fire_1`, in the block atlas; may have a `.mcmeta` animation. |
| First-person screen border | `assets/examplemod/textures/misc/frost_fire_outline.png` | Same format as the lib's `ibx_lib:textures/misc/fire_outline.png`: 16 frames stacked vertically, 320×180 each, 2 ticks per frame, looping. Falls back to the vanilla-colored one if missing. |

```java
// Frost bolt hits: burn for 4 seconds with blue fire
CustomFire.ignite(target, Identifier.fromNamespaceAndPath("examplemod", "frost_fire"), 4.0F);
```

**First-person fire**: the lib replaces vanilla's two large sheets of fire in front of the camera with a fire border around the screen corners that fades in and out when you catch fire and when it goes out, for both vanilla and colored fire. Players can switch back to the vanilla sheets with the client config option `replaceFireOverlay` (see [Configuration](CONFIG_en.md)); colored fire is then drawn in the vanilla position with its own texture.
