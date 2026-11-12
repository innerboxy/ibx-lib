# 属性

**简体中文** | [English](ATTRIBUTE_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

本模组注册了 18 个属性(`ibx.lib.registry.AttributeRegistry`),远程、暴击、法术、法力、召唤物的数值都从它们读。

- **全部生物都有**:玩家、原版生物、其他模组的生物都自动挂上这些属性,装备和药水效果对它们的加减对谁都生效。
- **全部同步到客户端**:tooltip、HUD、属性面板和客户端预测都能直接读。
- **结算集中在一处**:伤害的各个步骤按固定顺序走(见 [3](#3-伤害与治疗的结算顺序)),别的模组的乘区和减免叠在它之后。
- **属性面板**:背包左边可以打开一个面板,列出玩家身上的全部属性(见 [7](#7-属性面板))。

引用属性用 `AttributeRegistry.XXX`(`DeferredHolder<Attribute, Attribute>`,可以直接当 `Holder<Attribute>` 用);数据包、指令里用 id,比如 `/attribute @s ibx_lib:spell_power base set 10`。

---

## 2. 属性一览

| id | 字段 | 名字 | 默认 | 范围 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `ibx_lib:ranged_damage` | `RANGED_DAMAGE` | 远程伤害 | 0 | 0~2048 | 远程武器命中时的伤害点数。弩 = 本值,弓 = 本值 × 蓄力百分比。由武器提供(见 [装备基类](EQUIPMENT.md))。 |
| `ibx_lib:draw_time` | `DRAW_TIME` | 拉弦时间 | 0 | -1024~1024 | 武器的基础拉弦秒数,实际秒数 = 本值 ÷ 拉弦速率。一般只由武器提供;射手自己拉得快慢请加在拉弦速率上。越大越坏(加值显红)。 |
| `ibx_lib:draw_speed` | `DRAW_SPEED` | 拉弦速率 | 1 | 0.01~1024 | 射手拉弦的快慢。用除法,加速收益递减、永远减不到 0。加成建议用 `add_multiplied_base`。 |
| `ibx_lib:critical_chance` | `CRITICAL_CHANCE` | 暴击率 | 0.05 | 0~1024 | 近战、远程、法术共用。0~1 按概率,**大于等于 1 必定暴击**。 |
| `ibx_lib:critical_damage` | `CRITICAL_DAMAGE` | 暴击伤害 | 0.5 | 0~2048 | 暴击时的**额外**伤害比例:最终伤害 = 原伤害 × (1 + 本值)。 |
| `ibx_lib:armor_penetration` | `ARMOR_PENETRATION` | 护甲穿透 | 0 | 0~1 | 实际护甲减免 = 护甲减免 × (1 − 本值)。读攻击者,弹射物读射手。只打折护甲这一步,保护附魔和抗性效果照常。 |
| `ibx_lib:heal_efficiency` | `HEAL_EFFICIENCY` | 回复效率 | 1 | 0~1024 | 实际回复量 = 回复量 × 本值。0 为完全无法回复。 |
| `ibx_lib:thorns` | `THORNS` | 反甲 | 0 | 0~2048 | 被攻击后对攻击者造成本值点 `minecraft:thorns` 伤害。原版荆棘附魔改成了每级 +1 本属性。 |
| `ibx_lib:max_mana` | `MAX_MANA` | 最大法力值 | 100 | 0~1000000 | 法力上限。上限降低时当前法力被截断,提高时不自动补满。 |
| `ibx_lib:mana_regeneration` | `MANA_REGENERATION` | 法力回复效率 | 2 | -1024~1024 | 每秒回复的法力点数(固定值,不按上限比例),负数为持续流失。 |
| `ibx_lib:mana_cost_multiplier` | `MANA_COST_MULTIPLIER` | 法力消耗倍率 | 1 | 0~1024 | 实际消耗 = 基础消耗 × 本值。越大越坏。减耗请用 `add_multiplied_total`(连乘,叠不到 0)。 |
| `ibx_lib:spell_power` | `SPELL_POWER` | 法术强度 | 0 | 0~2048 | 法术伤害、治疗公式的输入,法术按系数取用。由法器提供(见 [装备基类](EQUIPMENT.md))。 |
| `ibx_lib:spell_cooldown_ratio` | `SPELL_COOLDOWN_RATIO` | 法术冷却倍率 | 1 | 0~1024 | 实际冷却 = 法术冷却 × 本值。越大越坏。减冷却同样用 `add_multiplied_total`。 |
| `ibx_lib:spell_cast_speed` | `SPELL_CAST_SPEED` | 施法速率 | 1 | 0.01~1024 | 实际吟唱时间 = 法术吟唱时间 ÷ 本值。加成建议用 `add_multiplied_base`。 |
| `ibx_lib:spell_resistance` | `SPELL_RESISTANCE` | 法术抗性 | 0 | 0~4096 | 法术伤害的「护甲点」,套原版护甲公式,最多减 80%(见 [3](#3-伤害与治疗的结算顺序))。 |
| `ibx_lib:spell_penetration` | `SPELL_PENETRATION` | 法术穿透 | 0 | 0~1 | 实际减免 = 法术抗性减免 × (1 − 本值)。施法时读进伤害快照,之后换装备不影响已经放出去的法术。 |
| `ibx_lib:can_spell_crit` | `CAN_SPELL_CRIT` | 法术可暴击 | 0 | 0~1 | 开关:总值大于等于 1 时法术(含治疗)按暴击率 / 暴击伤害暴击,每个目标各掷一次。装备挂 `add_value` +1 打开。 |
| `ibx_lib:max_summons` | `MAX_SUMMONS` | 召唤物上限 | 1 | 0~1024 | 同时在场的召唤物最多占几个名额(见 [召唤物](SUMMON.md))。 |

- 法力的读写见 [法术系统](SPELL.md),这里只列属性。
- 本模组还把原版的 `attack_damage`、`attack_knockback`、`knockback_resistance` 标成了同步:原版不同步这三个,客户端只能读到基础值,属性面板拿剑、穿甲也不变。

---

## 3. 伤害与治疗的结算顺序

伤害相关的结算都在 `ibx.lib.event.DamageHandler` 里,监听优先级 `HIGHEST`:本模组先把基础伤害定下来,其他模组的乘区和减免叠在它之上。约定是**先定基础伤害,再叠倍率,最后做减免**。

受到伤害时(`LivingIncomingDamageEvent`),按顺序:

| 步骤 | 条件 | 做什么 |
| --- | --- | --- |
| 1. 远程伤害 | 直接来源是带着本模组远程伤害的箭 | 用箭上发射瞬间记下的远程伤害**覆盖**原版伤害(原版按箭速算的那份丢弃)。 |
| 2. 远程暴击 | 同上,且箭带暴击标记 | 伤害 × (1 + 射手的暴击伤害),在目标身上放暴击粒子和音效。掷骰在发射时已经做过。 |
| 3. 难度缩放 | 同 1,且被打的是玩家 | 步骤 1 把原版的难度缩放一起盖掉了,这里按伤害类型补缩一次。 |
| 4. 法术抗性 | 伤害类型在 `#ibx_lib:is_spell` 里 | 按下面的公式减免,减免比例再乘 (1 − 法术穿透)。 |
| 5. 护甲穿透 | 攻击者是生物且护甲穿透大于 0 | 护甲这一步的减免乘 (1 − 护甲穿透)。 |

法术抗性的公式同原版护甲(`CombatRules#getDamageAfterAbsorb`),R 为法术抗性,韧性用原版的 `armor_toughness`:

```
减免比例 = clamp(R − 伤害 ÷ (2 + 韧性 ÷ 4), min(R × 0.2, 20), 20) ÷ 25
```

最多减 80%(20 点);超过 20 点不再提高上限,但让大伤害更难穿透。按伤害类型标签判断,所以别的模组的魔法伤害加进 `#ibx_lib:is_spell` 也吃法术抗性——这种伤害没有快照,法术穿透读攻击者当时的属性。

伤害结算完之后(`LivingDamageEvent.Post`,不再改伤害量):

- **法术的附带效果**:点燃、冰冻、吸血,参数来自法术伤害快照(见 [法术系统](SPELL.md))。
- **反甲**:只认「攻击」——近战和弹射物(伤害落在射手身上);法术、爆炸和荆棘伤害本身不触发。被盾全挡、被无敌帧吃掉的那一下也不触发。

其余两处:

- **近战暴击**(`CriticalHitEvent`):概率走暴击率,倍率走暴击伤害,不再是原版写死的 1.5 倍。原版的跳劈必暴默认关掉,由服务端配置 `enableVanillaCritical` 决定保不保留(见 [配置](CONFIG.md))。
- **治疗**(`ibx.lib.event.HealHandler`,`LivingHealEvent`):回复量 × 回复效率。走 `LivingEntity#heal` 的回复都经过(自然回复、生命恢复、瞬间治疗……);直接改血量的(如不死图腾)不经过。

---

## 4. 暴击

**`ibx.lib.api.CriticalStrikeApi`**:近战、远程、法术都从这里取值。

| 方法 | 说明 |
| --- | --- |
| `getChance(attacker)` | 暴击率。 |
| `getDamageBonus(attacker)` | 暴击伤害加成,即公式里的 `critical_damage`。 |
| `getMultiplier(attacker)` | 暴击时的伤害倍率,等于 1 + `getDamageBonus`。 |
| `roll(attacker)` | 掷一次暴击判定。**只在服务端调**:客户端也掷一次会和服务端对不上。暴击率大于等于 1 时必定返回 `true`。 |
| `spawnEffects(target)` | 在目标身上放原版近战暴击的那套粒子和音效,给远程、法术这类不经过原版暴击流程的命中用。只在服务端调。 |

自己写的伤害想跟着这套暴击走:

```java
float damage = 8.0F;
if (CriticalStrikeApi.roll(attacker)) {
    damage *= CriticalStrikeApi.getMultiplier(attacker);
    CriticalStrikeApi.spawnEffects(target);
}
target.hurtServer(level, source, damage);
```

近战、本模组的弓弩和法术已经自动处理,不用再写。

---

## 5. 属性类型

三个 `RangedAttribute` 的子类(`ibx.lib.attribute`),区别只在 tooltip 怎么显示,注册自己的属性时也可以用:

| 类 | 构造 | tooltip |
| --- | --- | --- |
| `WeaponAttribute` | `(descriptionId, 默认, 最小, 最大, baseId, valueKey)` | 武器上 id 等于 `baseId` 的 `ADD_VALUE` 修饰符算「武器基础值」,显示成原版剑那样的深绿合并行(「8 远程伤害」),别的来源的同属性修饰符自动合并进去。`valueKey` 不为 `null` 时数值套这个语言键(含一个 `%s`)。 |
| `UnitAttribute` | `(descriptionId, 默认, 最小, 最大, valueKey)` | 加法修饰符的数值套语言键显示(如 `"%s/秒"`),百分比修饰符照旧。 |
| `FlagAttribute` | `(descriptionId)` | 开关:默认 0、范围 0~1。正的加法修饰符只显示属性名(「法术可暴击」),不带数字。 |

暴击率、暴击伤害、护甲穿透、法术穿透用的是 NeoForge 的 `PercentageAttribute`,加法修饰符显示成百分数(+0.15 → +15%)。

`AttributeRegistry` 里和它们配套的常量:

| 常量 | 值 | 用途 |
| --- | --- | --- |
| `BASE_RANGED_DAMAGE_ID` | `ibx_lib:base_ranged_damage` | 远程伤害的武器基础修饰符 id。 |
| `BASE_DRAW_TIME_ID` | `ibx_lib:base_draw_time` | 拉弦时间的武器基础修饰符 id。 |
| `BASE_SPELL_POWER_ID` | `ibx_lib:base_spell_power` | 法术强度的法器基础修饰符 id。 |
| `SECONDS_VALUE_KEY` | `attribute.value.ibx_lib.seconds` | 「秒」数值的语言键。 |
| `PER_SECOND_VALUE_KEY` | `attribute.value.ibx_lib.per_second` | 「/秒」数值的语言键。 |

**`ibx.lib.util.AttributeUtil.read(entity, attribute)`**:读属性总值;实体没挂这个属性时返回属性的默认值,不抛异常。读本模组的属性都建议走它。

---

## 6. 给装备加属性

和原版属性一样,用物品的属性修饰符组件。弓弩、法器的基础值由基类自己挂(见 [装备基类](EQUIPMENT.md)),这里是额外加成的写法:

```java
public static final DeferredItem<Item> EMBER_TALISMAN = ITEMS.registerItem("ember_talisman",
        properties -> new Item(properties.stacksTo(1).attributes(ItemAttributeModifiers.builder()
                // 暴击率 +15%
                .add(AttributeRegistry.CRITICAL_CHANCE,
                        new AttributeModifier(Identifier.fromNamespaceAndPath("examplemod", "ember_talisman_crit"),
                                0.15, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.OFFHAND)
                // 法术可以暴击
                .add(AttributeRegistry.CAN_SPELL_CRIT,
                        new AttributeModifier(Identifier.fromNamespaceAndPath("examplemod", "ember_talisman_spell_crit"),
                                1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.OFFHAND)
                // 法力消耗 -20%(连乘)
                .add(AttributeRegistry.MANA_COST_MULTIPLIER,
                        new AttributeModifier(Identifier.fromNamespaceAndPath("examplemod", "ember_talisman_mana_cost"),
                                -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                        EquipmentSlotGroup.OFFHAND)
                .build())));
```

各属性建议用哪种运算:

| 属性 | 建议 | 原因 |
| --- | --- | --- |
| 暴击率、暴击伤害、护甲穿透、法术穿透 | `ADD_VALUE` | 值本身就是比例,tooltip 显示成百分数。 |
| 拉弦速率、施法速率 | `ADD_MULTIPLIED_BASE` | 基础值是 1,tooltip 显示成百分比;结算用除法,多个加成不会叠成瞬发。 |
| 法力消耗倍率、法术冷却倍率 | `ADD_MULTIPLIED_TOTAL` | 连乘,永远叠不到 0;`ADD_MULTIPLIED_BASE` 的两个 -50% 相加就成了免费。 |
| 法术可暴击 | `ADD_VALUE` +1 | 开关,总值大于等于 1 即开启。 |
| 其余 | `ADD_VALUE` | 直接给点数。 |

---

## 7. 属性面板

背包人物框左下角的卷轴按钮打开,贴在背包左边,列出玩家身上的全部属性(原版、本模组、其他模组的都列),每行图标 + 名字 + 数值。面板左下角的齿轮打开设置界面:显示哪些、怎么排序、是否按模组分组,都存在客户端配置里(见 [配置](CONFIG.md))。创造模式背包的「物品栏」页同样有;和原版配方书占同一块地方,配方书开着时不画。

别的模组的属性会自动出现在面板里,还可以补几样东西:

- **图标**:9×9 的贴图,放在属性自己命名空间下的 `assets/<命名空间>/textures/gui/sprites/hud/icon/<属性 id 的路径>.png`:属性 `examplemod:frost_power` 对应 `assets/examplemod/textures/gui/sprites/hud/icon/frost_power.png`,不用登记。找不到时再找 `assets/ibx_lib/` 下同路径的(原版属性的图标都在那里),都没有画通用的占位图标(`unknown.png`)。
- **默认顺序、默认隐藏**:用 `AttributePanelLayout` 登记(见下面)。
- **分组名**:按模组分组时,组名先找语言键 `gui.ibx_lib.attribute_panel.mod.<命名空间>`,没有就用模组的显示名。

面板显示的是客户端读到的值:没标同步的属性(`Attribute#isClientSyncable` 为 `false`)只能显示基础值。自己注册的属性想在面板里跟着装备变,注册时调 `setSyncable(true)`。

### 登记默认排法 `AttributePanelLayout`

`ibx.lib.client.attribute.AttributePanelLayout`,只在客户端。在客户端初始化时调(比如客户端模组入口 `@Mod(value = ..., dist = Dist.CLIENT)` 的构造函数里):

```java
AttributePanelLayout.order(ExampleAttributes.FROST_POWER, ExampleAttributes.FROST_RESISTANCE);
AttributePanelLayout.hideByDefault(ExampleAttributes.FROST_STACKS);
```

| 方法 | 说明 |
| --- | --- |
| `order(attributes...)` | 这几个属性的默认先后,按写的顺序排;已经登记过的不动,可以分几次调。没登记的属性按注册顺序接在登记过的后面。只管同一个命名空间里的先后,命名空间之间固定是原版最前,然后本模组、其他模组,NeoForge 的最后。排序方式选「默认」时用它。 |
| `hideByDefault(attributes...)` | 这几个属性默认不在面板里列出来。玩家的设置说了算:每个属性只替玩家隐藏一次(记在客户端配置 `attributePanelDefaultsApplied`),玩家在设置界面里勾回来就一直显示;点「恢复默认」会再隐藏。 |
