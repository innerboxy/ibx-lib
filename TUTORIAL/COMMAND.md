# 指令

**简体中文** | [English](COMMAND_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

本模组的指令都挂在 `/taverntales` 下,分 `mana`、`spell`、`summon` 三组,每组都要 **2 级权限**(命令方块、OP)。

- 根节点 `/taverntales` 可能和别的模组共用,权限挂在三组各自那一层。
- 目标里不是生物的实体会被忽略;一个生物都没有时报错。
- `spell` 和 `summon` 不写目标时作用于指令的执行者,用 `/execute as` 让别的生物来执行。

---

## 2. 法力

```
/taverntales mana get <目标>
/taverntales mana set <目标们> <数值>
/taverntales mana add <目标们> <数值>
/taverntales mana fill <目标们>
```

| 子指令 | 说明 |
| --- | --- |
| `get <目标>` | 显示一个生物的当前法力和上限。返回值是当前法力(四舍五入)。 |
| `set <目标们> <数值>` | 把法力设为这个数(不小于 0,超过上限按上限)。 |
| `add <目标们> <数值>` | 加法力,可以是负数。走 `ManaApi.add`,会触发法力获得事件(见 [法术系统](SPELL.md))。 |
| `fill <目标们>` | 补满法力。 |

---

## 3. 法术

```
/taverntales spell cast <法术> [来源]
/taverntales spell stop
/taverntales spell cooldown clear [目标们]
/taverntales spell list
```

| 子指令 | 说明 |
| --- | --- |
| `cast <法术> [来源]` | 执行者施放这个法术(法术 id 可以 Tab 补全)。聊天里显示结果码,成功时返回 1。 |
| `stop` | 松手:吟唱取消、引导结束、蓄力按当前比例结算。执行者没在施法时报错。 |
| `cooldown clear [目标们]` | 清掉法术冷却,不写目标即执行者。 |
| `list` | 列出所有已注册的法术:id、施法方式、耗蓝、冷却秒数、读条秒数。 |

**施法来源**(`CastSource`,决定扣不扣蓝、进不进冷却):

| 来源 | 扣蓝 | 进冷却 | 说明 |
| --- | --- | --- | --- |
| `item` | 是 | 是 | 手持施法物品。玩家施法时不写来源就是它。 |
| `scroll` | 否 | 否 | 卷轴。 |
| `mob` | 否 | 否 | 生物(含 Boss)。非玩家执行时不写来源就是它。 |
| `command` | 否 | 否 | 指令:正在施法时直接取消原来的再放。 |

创造模式的玩家另外免蓝、免冷却。来源和结果码的含义见 [法术系统](SPELL.md)。

```
# 让最近的僵尸放一次法术(来源按执行者推断为 mob)
/execute as @e[type=minecraft:zombie,limit=1,sort=nearest] at @s run taverntales spell cast examplemod:frost_bolt
```

---

## 4. 召唤物

```
/taverntales summon list [主人]
/taverntales summon dismiss [主人们]
```

| 子指令 | 说明 |
| --- | --- |
| `list [主人]` | 列出主人在场的召唤物:每只占几个名额、常驻还是剩几秒;以及已用 / 总名额。不写即执行者。 |
| `dismiss [主人们]` | 解散这些主人的全部召唤物,返回解散了几只。不写即执行者。 |

名额和寿命的规则见 [召唤物](SUMMON.md)。
