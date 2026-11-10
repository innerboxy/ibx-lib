# Commands

[简体中文](COMMAND.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

All of the mod's commands live under `/taverntales` in three groups, `mana`, `spell` and `summon`, each requiring **permission level 2** (command blocks, ops).

- The root `/taverntales` may be shared with other mods of this series, so the permission check sits on each group.
- Non-living entities among the targets are ignored; it's an error if no living entity is left.
- `spell` and `summon` act on the command's executor when no target is given; use `/execute as` to have another entity run them.

---

## 2. Mana

```
/taverntales mana get <target>
/taverntales mana set <targets> <amount>
/taverntales mana add <targets> <amount>
/taverntales mana fill <targets>
```

| Subcommand | Description |
| --- | --- |
| `get <target>` | Shows one living entity's current and maximum mana. Returns current mana (rounded). |
| `set <targets> <amount>` | Sets mana to this amount (at least 0, capped at max mana). |
| `add <targets> <amount>` | Adds mana; may be negative. Goes through `ManaApi.add`, so the mana gain event fires (see [Spell system](SPELL_en.md)). |
| `fill <targets>` | Refills mana to max. |

---

## 3. Spells

```
/taverntales spell cast <spell> [source]
/taverntales spell stop
/taverntales spell cooldown clear [targets]
/taverntales spell list
```

| Subcommand | Description |
| --- | --- |
| `cast <spell> [source]` | The executor casts this spell (spell ids tab-complete). Chat shows the result code; returns 1 on success. |
| `stop` | Release: a chanted cast is cancelled, a channel ends, a charge resolves at its current ratio. Errors if the executor isn't casting. |
| `cooldown clear [targets]` | Clears spell cooldowns; defaults to the executor. |
| `list` | Lists every registered spell: id, cast type, mana cost, cooldown seconds, cast seconds. |

**Cast sources** (`CastSource`, deciding whether mana is spent and cooldown triggered):

| Source | Spends mana | Triggers cooldown | Notes |
| --- | --- | --- | --- |
| `item` | Yes | Yes | A held casting item. The default when a player executes without a source. |
| `scroll` | No | No | A scroll. |
| `mob` | No | No | A mob (bosses included). The default when a non-player executes without a source. |
| `command` | No | No | A command: if already casting, the current cast is cancelled first. |

Players in creative mode additionally skip mana and cooldowns. See [Spell system](SPELL_en.md) for what sources and result codes mean.

```
# Make the nearest zombie cast a spell (source inferred from the executor: mob)
/execute as @e[type=minecraft:zombie,limit=1,sort=nearest] at @s run taverntales spell cast examplemod:frost_bolt
```

---

## 4. Summons

```
/taverntales summon list [owner]
/taverntales summon dismiss [owners]
```

| Subcommand | Description |
| --- | --- |
| `list [owner]` | Lists the owner's active summons: how many slots each takes and whether it's permanent or how many seconds remain; plus used / total slots. Defaults to the executor. |
| `dismiss [owners]` | Dismisses all summons of these owners; returns how many were dismissed. Defaults to the executor. |

See [Summons](SUMMON_en.md) for the slot and lifetime rules.
