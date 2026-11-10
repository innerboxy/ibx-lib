# 装备基类

**简体中文** | [English](EQUIPMENT_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

本模组提供一组物品基类,别的模组直接用它们或继承它们来注册武器、盾牌、法器、盔甲和饰品。数值由构造参数给定,属性修饰符、伤害结算、标签、动作这些共用的部分由基类处理。

| 想做什么 | 用哪个类 | 见 |
| --- | --- | --- |
| 近战武器(剑、匕首、砍刀等) | `ibx.lib.item.general.IbxMeleeWeaponItem` | [2](#2-近战武器) |
| 弓 | `ibx.lib.item.IbxBowItem` | [3](#3-远程武器) |
| 弩 | `ibx.lib.item.IbxCrossbowItem` | [3](#3-远程武器) |
| 法典 | `ibx.lib.item.IbxTomeItem` | [5](#5-法器) |
| 法杖 | `ibx.lib.item.IbxStaffItem` | [5](#5-法器) |
| 别的种类的法器 | 继承 `ibx.lib.item.general.IbxMagicWeaponItem` | [5](#5-法器) |
| 单手盾(只挡) | `ibx.lib.item.IbxOneHandedShieldItem` | [6](#6-盾牌) |
| 双手盾(能普通攻击和盾击) | `ibx.lib.item.IbxTwoHandedShieldItem` | [6](#6-盾牌) |
| 3D 盔甲(穿上的样子由 GeckoLib 画) | `ibx.lib.item.IbxGeoArmorItem` | [7](#7-3d-盔甲) |
| 给盔甲加护甲以外的属性 | `ibx.lib.item.general.IbxArmorProperties` | [7](#盔甲的物品属性) |
| 同一种只能戴一个的饰品 | 普通物品 + `ibx.lib.item.curio.UniqueCurioItem` | [8](#8-饰品) |
| 攻击、挨打、击杀、施法时触发的饰品 | 物品类实现 `ibx.lib.item.curio.CurioTriggers` | [8](#触发钩子) |

- **注册方式都一样**:`Item.Properties` 必须先带上物品 id 才能构造物品,所以用 `DeferredRegister.Items#registerItem` 注册,在工厂里使用传进来的 `Properties`;稀有度、防火等也设在它上面。
- **数值挂在主手**:基类把数值烤成主手的属性修饰符,用「基础 id」,tooltip 显示成原版剑那样的深绿合并行(比如「8 远程伤害」)。
- **标签自动加**:物品按基类自动进通用标签,data 里不用写(见 [9](#9-自动物品标签))。3D 盔甲除外。
- **能直接用**:普通装备直接 `new` 基类;有自己机制的再继承,重写对应的方法。

不在本文里的:

- 属性的完整列表、伤害结算的先后顺序见 [属性](ATTRIBUTE.md)。
- 法术、施法流程、法力见 [法术系统](SPELL.md)。
- 选目标(盾击判定用到的阵营、扇形)见 [选目标](TARGETING.md)。
- 配置项的细节见 [配置](CONFIG.md)。

---

## 2. 近战武器

`ibx.lib.item.general.IbxMeleeWeaponItem`:近战武器的基类,按原版剑注册(`Properties#sword`)。普通武器直接用它;有自己机制的(命中附加伤害、特殊操作)继承它,写在子类里。

**自带的**:

- 材料给耐久、修复材料、附魔能力。
- 同原版剑:砍蜘蛛网快,每次攻击掉 1 点耐久。
- 主手挂攻击伤害、攻速,用原版的基础 id,tooltip 是深绿合并行。
- 自动进 `#c:tools`、`#c:tools/melee_weapon`。

**构造参数** `IbxMeleeWeaponItem(properties, material, attackDamage, attackSpeed)`:

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `properties` | `Item.Properties` | 注册工厂传进来的那个。耐久由材料给,不用自己设。 |
| `material` | `ToolMaterial` | 耐久、修复材料、附魔能力;攻击伤害再加上它的加成。 |
| `attackDamage` | `float` | 伤害基数。显示的攻击伤害 = 本值 + 材料加成 + 空手的 1。 |
| `attackSpeed` | `float` | 攻速基数。显示的攻速 = 本值 + 玩家的 4。 |

这个类没有自己的钩子,要加机制就重写 `Item` 的方法。

```java
public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("examplemod");

// 显示 4 攻击伤害(1 + 铁的 2 + 空手 1)、2 攻速
public static final DeferredItem<IbxMeleeWeaponItem> IRON_DIRK = ITEMS.registerItem("iron_dirk",
        p -> new IbxMeleeWeaponItem(p, ToolMaterial.IRON, 1.0F, -2.0F));
```

> 不会自动进 `#minecraft:swords` 和各个 `#minecraft:enchantable/...` 标签。要能附锋利之类的魔咒,自己在 data 里把物品加进对应标签。

### 手持模型与左键动作

手持显示和左键动作按武器种类在资源里给,不在物品类里。本模组带两个通用手持模型,都以 `minecraft:item/handheld` 为父模型,只改了第一、第三人称左右手的显示,贴图填 `layer0`:

| 模型 | 用于 |
| --- | --- |
| `ibx_lib:item/general_dagger_hand` | 匕首类 |
| `ibx_lib:item/general_cutlass_hand` | 砍刀类 |

`assets/examplemod/models/item/iron_dirk.json`:

```json
{
  "parent": "ibx_lib:item/general_dagger_hand",
  "textures": {
    "layer0": "examplemod:item/iron_dirk"
  }
}
```

左键动作交给 Better Combat(本模组不依赖它,没装就是原版挥臂):在 `data/<命名空间>/weapon_attributes/<物品名>.json` 里指定一个预设,比如匕首:

```json
{
  "parent": "bettercombat:dagger"
}
```

---

## 3. 远程武器

弓和弩各自继承原版的 `BowItem` / `CrossbowItem`,共用的部分走接口 `ibx.lib.item.general.IbxRangedWeaponItem` 和 `ibx.lib.api.RangedWeaponApi`。

**两者都有的**:

- **伤害和箭速分开**:发射的瞬间把伤害算好写在箭上(伤害快照),命中时覆盖原版伤害。弓 = 射手的远程伤害 × 蓄力百分比,弩 = 射手的远程伤害。箭速只管弹道手感,不影响伤害。
- **拉弦时间跟着属性走**:满蓄 / 装填所需 tick = 拉弦时间(`ibx_lib:draw_time`)÷ 拉弦速率(`ibx_lib:draw_speed`)× 20,最少 1 tick。
- **暴击**:发射时在服务端按射手的暴击率掷一次,结果记在箭的原版暴击标记上(所以有原版的暴击粒子),伤害倍率在命中时才乘。
- **主手挂远程伤害、拉弦时间**:修饰符 id 为 `ibx_lib:base_ranged_damage`、`ibx_lib:base_draw_time`,tooltip 是深绿合并行。
- **只能主手使用**:修饰符只挂主手,副手右键直接失败。
- **自动射击**:物品栈带 `ibx_lib:auto_fire` 组件时拉满自动放(见下面的 [自动射击](#自动射击))。
- **标签**:自动进 `#c:tools`、`#c:tools/ranged_weapon`,弓再进 `#c:tools/bow`,弩再进 `#c:tools/crossbow`。

属性的定义和命中后的结算步骤见 [属性](ATTRIBUTE.md)。

### 弓 IbxBowItem

| 构造方法 | 说明 |
| --- | --- |
| `IbxBowItem(properties, rangedDamage, drawTime)` | 箭速用基准值,弹药为任意箭(`#minecraft:arrows`)。 |
| `IbxBowItem(properties, rangedDamage, drawTime, arrowVelocity)` | 弹药为任意箭。 |
| `IbxBowItem(properties, rangedDamage, drawTime, arrowVelocity, ammo)` | 全部参数。 |

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `properties` | `Item.Properties` | 注册工厂传进来的那个。**耐久要自己设**(原版弓 384),不设就不会损坏。 |
| `rangedDamage` | `float` | 远程伤害(满蓄时)。 |
| `drawTime` | `float` | 满蓄秒数;不大于 0 用 `BASELINE_DRAW_TIME`(1.0)。 |
| `arrowVelocity` | `float` | 满蓄时的初速度;不大于 0 用 `BASELINE_ARROW_VELOCITY`(3.0)。 |
| `ammo` | `Predicate<ItemStack>` | 弹药谓词,默认 `ARROW_ONLY`。自定义弹药物品必须是 `ArrowItem` 的子类,否则会被当成普通箭射出去。 |

行为:

- 蓄力百分比是线性的:已蓄力 tick ÷ 满蓄 tick,上限 1。
- 实际箭速 = `getArrowVelocity` × 蓄力百分比。
- 蓄力百分比低于 `getMinCharge`(默认 `MIN_CHARGE` = 0.1)时不发射、不消耗。
- 发射前照常触发 NeoForge 的 `ArrowLooseEvent`。

可重写的方法:

| 方法 | 默认 | 说明 |
| --- | --- | --- |
| `protected float getArrowVelocity(ItemStack stack)` | 构造时给的箭速 | 满蓄时的箭速。要按物品栈动态改就重写。 |
| `protected float getMinCharge(ItemStack stack)` | `0.1` | 发射门槛(蓄力百分比)。 |
| `protected @Nullable Consumer<Projectile> prepareShot(ServerLevel level, Player player, ItemStack stack, float chargePercent)` | `null` | 发射前调一次(服务端,已过门槛、马上要射):在这里结算这一发的额外代价。返回的处理会在每支弹射物写好伤害快照之后、生成进世界之前调用(多重射击每支都调),可以改伤害快照、给弹射物挂数据;返回 `null` 表示不额外处理。 |
| `public float getChargePercent(int chargeTicks, LivingEntity shooter, ItemStack stack)` | 线性 | 蓄力百分比。玩家射击和持弓生物的 AI 都读它。 |
| `protected void shootWithDamage(...)` | — | 发射流程本体:散射、多重射击、耐久同原版,每支箭多写一份伤害快照。一般不用动。 |

```java
public static final DeferredItem<IbxBowItem> LONGBOW = ITEMS.registerItem("longbow",
        p -> new IbxBowItem(p.durability(500), 8.0F, 1.2F));
```

带机制的弓:满弓时伤害 +20%。

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

### 弩 IbxCrossbowItem

| 构造方法 | 说明 |
| --- | --- |
| `IbxCrossbowItem(properties, rangedDamage, drawTime)` | 箭速用基准值,弹药为任意箭,手持的烟花火箭也能装。 |
| `IbxCrossbowItem(properties, rangedDamage, drawTime, arrowVelocity)` | 弹药同上。 |
| `IbxCrossbowItem(properties, rangedDamage, drawTime, arrowVelocity, ammo, allowFireworks)` | 全部参数。 |

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `properties` | `Item.Properties` | 注册工厂传进来的那个。**耐久要自己设**(原版弩 465),不设就不会损坏。 |
| `rangedDamage` | `float` | 远程伤害。 |
| `drawTime` | `float` | 装填秒数;不大于 0 用 `BASELINE_DRAW_TIME`(1.25)。 |
| `arrowVelocity` | `float` | 发射初速度;不大于 0 用 `BASELINE_ARROW_VELOCITY`(3.15)。 |
| `ammo` | `Predicate<ItemStack>` | 能从背包取用的弹药,默认 `ARROW_ONLY`。自定义弹药物品必须是 `ArrowItem` 的子类。 |
| `allowFireworks` | `boolean` | 手持的烟花火箭能不能当弹药(同原版弩),默认 `true`。 |

行为:

- 拉满的那一刻就在服务端装填,不必松手。
- 装填音效在拉到 20%、50% 和装好时各响一次。
- 装的是烟花火箭时速度照原版,也不写伤害快照(快照只给箭)。
- 发射流程(多重射击等)是原版的,没有弓的 `prepareShot`;要改每支箭,重写 `createProjectile`。

可重写的方法:

| 方法 | 默认 | 说明 |
| --- | --- | --- |
| `protected float getArrowVelocity(ItemStack stack)` | 构造时给的箭速 | 发射初速度。玩家和生物用它射箭都走这个值。 |
| `protected ChargingSounds getChargingSoundsFor(ItemStack stack)` | 附魔给的装填音效,没有就用原版弩的三段 | 装填音效。 |
| `protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit)` | 掷暴击、给箭写伤害快照 | 造一支弹射物。重写时先调 `super`。 |

```java
public static final DeferredItem<IbxCrossbowItem> ARBALEST = ITEMS.registerItem("arbalest",
        p -> new IbxCrossbowItem(p.durability(600), 10.0F, 1.5F));
```

### IbxRangedWeaponItem 与 IbxRangedWeaponStats

- `IbxRangedWeaponItem`:弓弩共同实现的接口。`stats()` 返回构造时给的静态数值;`IbxRangedWeaponItem.appendAutoFireTooltip(stack, tooltip)` 给开了自动射击的物品栈加一行金色的「自动发射」(语言键 `tooltip.ibx_lib.auto_fire`),弓弩的 `appendHoverText` 已经调过。
- `IbxRangedWeaponStats`:`rangedDamage()`、`drawTime()`、`arrowVelocity()`、`modifiers()`(烤好的主手属性修饰符)。

> 运行时不要直接读 `stats()` 里的远程伤害和拉弦时间,一律走 `RangedWeaponApi` 读射手的属性,护甲、饰品、药水效果和按物品栈覆盖的组件才都能生效。箭速没有对应的属性,直接读 `arrowVelocity()`。

### RangedWeaponApi

`ibx.lib.api.RangedWeaponApi`:远程武器系统的读数入口,物品类、命中结算、拉弓动画都从这里取值。

| 方法 | 说明 |
| --- | --- |
| `isManagedBow(weapon)` | 这把弓走不走本模组的远程系统:`IbxBowItem`(含子类),或开了接管的原版弓(见 [4](#4-原版物品的改动))。其他模组继承原版类的弓不算。 |
| `isManagedCrossbow(weapon)` | 同上,判断弩。 |
| `getFullDrawTicks(shooter, weapon)` | 满蓄(弓)/ 装填(弩)所需 tick = 拉弦时间 ÷ 拉弦速率 × 20,最少 `MIN_FULL_DRAW_TICKS`(1)。两端都能调。 |
| `getChargePercent(chargeTicks, shooter, weapon)` | 弓的线性蓄力百分比:已蓄力 tick ÷ 满蓄 tick,上限 1。 |
| `getRangedDamage(shooter, weapon)` | 射手当前的远程伤害(属性总值)。 |
| `setAmmoRangedDamage(projectile, damage)` | 把伤害快照写到弹射物上。箭命中时用它覆盖原版伤害。 |
| `getAmmoRangedDamage(projectile)` | 弹射物上的伤害快照,`Optional<Float>`;没有(没走本模组系统的箭)则为空。 |
| `isAutoFire(weapon)` | 物品栈的 `ibx_lib:auto_fire` 组件是不是 `true`,没有组件算 `false`。 |
| `tickAutoFire(shooter, weapon, remainingTicks)` | 自动射击的每 tick 判定,在物品的 `onUseTick` 里调,返回是否已释放。只对玩家生效。 |

不继承这两个类的远程武器也能用这套结算:发射时给箭(`AbstractArrow`)写上伤害快照,命中时就按它算。带快照的箭如果有原版暴击标记,命中时会乘射手的暴击倍率,所以暴击标记也要自己按 `CriticalStrikeApi.roll` 掷。

### 自动射击

物品栈带 `ibx_lib:auto_fire=true` 时,拉满的那一刻自动释放:弓直接射出,弩装填完毕;右键一直按着就是连发。

- 只对玩家生效,生物的拉弓和射击由 AI 自己掌握。
- 组件不在物品的默认组件里,只有显式打开的物品栈才带,比如 `/give @s minecraft:bow[ibx_lib:auto_fire=true]`,或代码里 `stack.set(DataComponentRegistry.AUTO_FIRE.get(), true)`。
- 开了的物品在名称正下方多一行金色的「自动发射」。

### 物品模型:拉弓动画

物品模型定义在 `assets/<命名空间>/items/<物品名>.json`。原版弓的拉弓阶段用 `minecraft:use_duration` 配 `scale: 0.05`,等于写死 20 tick 拉满,所以本模组注册了一个数值属性:

- **`ibx_lib:pull`**(`ibx.lib.client.RangedWeaponModelProperties.PULL`):已拉弓 tick ÷ 满蓄 tick,拉满为 1。没在拉的弓、装填完毕的 Ibx 弩为 0。

**弓**:照抄原版 `items/bow.json`,把 `"property": "minecraft:use_duration"` 和 `"scale": 0.05` 换成 `"property": "ibx_lib:pull"`,阈值不变。

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

**弩**:直接照抄原版 `items/crossbow.json`,只换模型 id。原版的 `minecraft:crossbow/pull` 对 Ibx 弩已经按属性算装填时长;想统一写法也可以换成 `ibx_lib:pull`,结果一样。

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

**端弩姿势**(`ibx.lib.client.CrossbowArmPose`):第三人称下,原版只给 `minecraft:crossbow` 摆「端着已装填的弩」的姿势。本模组给所有 `IbxCrossbowItem` 自动补上,不用做任何事。

- 例外:如果你给自己的弩注册了客户端扩展(`IClientItemExtensions`,比如自定义渲染),本模组就不再给它注册(同一物品只能有一份)。这时在自己的 `getArmPose` 里调 `CrossbowArmPose.crossbowHold(entity, stack)`:已装填且没在挥手时返回端弩姿势,否则返回 `null`。

### 生物使用弓弩

- **认得模组弓弩**:骷髅系(`AbstractSkeleton`)认任何 `BowItem` 子类,掠夺者、猪灵认任何 `CrossbowItem` 子类,不用再往 NeoForge 的 `skeleton_usable_bows` 等标签里加。
- **持弓生物**(用 `RangedBowAttackGoal` 的,如骷髅系、幻术师)拿着 Ibx 弓时:拉满武器自己的拉弦时间才松手,蓄力百分比用线性值(`IbxBowItem#getChargePercent`);射出的箭带伤害快照 = 生物的远程伤害 × 蓄力百分比。生物射箭不走物品的发射流程,`prepareShot`、`getArrowVelocity`、`getMinCharge` 只对玩家生效。
- **持弩生物**拿着 Ibx 弩时:装填时长按属性算;发射走物品自己的流程,所以箭速是武器的箭速(原版生物固定 1.6),箭带伤害快照 = 生物的远程伤害,暴击按生物的暴击率掷。
- 生物射中玩家时,伤害照原版按难度缩放。
- 本模组的属性挂在全部生物上,武器在主手时修饰符对生物同样生效。

### 改过的原版附魔

本模组在 `data/minecraft/enchantment/` 下覆盖了三个原版附魔的定义。这三个文件一直生效,不受启动配置的开关控制。

| 附魔 | 文件 | 改了什么 |
| --- | --- | --- |
| 力量 | `power.json` | 多了一条属性效果:主手 `ibx_lib:ranged_damage` +3,每多一级再 +1.5(I~V:3 / 4.5 / 6 / 7.5 / 9)。原版的箭伤害加成留着,没走本模组远程系统的弓照旧;走本模组系统的箭命中时伤害被快照覆盖,只吃属性这一份。 |
| 快速装填 | `quick_charge.json` | 多了一条属性效果:主手 `ibx_lib:draw_time` 每级 -0.25 秒。原版的装填时间效果和装填音效留着,没走本模组系统的弩照旧。生效槽位从主手 + 副手改成只有主手。 |
| 荆棘 | `thorns.json` | 原版的效果(按概率反伤 1~5 点,并多掉 2 点耐久)换成属性效果:每级 `ibx_lib:thorns` +1。生效槽位从 `any` 改成 `armor`。反甲的结算见 [属性](ATTRIBUTE.md)。 |

> 附魔能不能附到你的物品上还是看原版的 `#minecraft:enchantable/bow`、`#minecraft:enchantable/crossbow` 等标签,这些标签不会自动加。

---

## 4. 原版物品的改动

`ibx.lib.item.vanilla` 包里的四个类改了原版物品。前三个由启动配置 `config/ibx_lib-startup.toml` 控制;启动配置不同步,服务端和客户端要改成一致,改完重启才生效(见 [配置](CONFIG.md))。

| 类 | 改了什么 | 开关 |
| --- | --- | --- |
| `VanillaRangedWeapons` | 原版弓、弩走本模组的远程系统 | `takeOverVanillaRangedWeapons`,默认 `true` |
| `VanillaArmors` | 下界合金、金、铜盔甲的属性 | `adjustVanillaArmors`,默认 `true` |
| `VanillaStackSizes` | 堆叠上限 16 的物品改成 64 | `raiseVanillaStackSizes`,默认 `true` |
| `VanillaSpawnEggs` | 凋灵、末影龙刷怪蛋放进创造栏 | 无 |

### 原版弓弩

只认 `minecraft:bow` 和 `minecraft:crossbow` 这两个物品本身,其他模组继承原版类的弓弩不碰。

| | 远程伤害 | 拉弦时间 | 箭速 |
| --- | --- | --- | --- |
| 弓(`VanillaRangedWeapons.BOW`) | 6 | 1.0 秒 | 3.0 |
| 弩(`VanillaRangedWeapons.CROSSBOW`) | 7 | 1.25 秒 | 3.15 |

- **数值**:远程伤害、拉弦时间的修饰符烤进两个物品的默认组件(`attribute_modifiers`),tooltip 自动出现;按物品栈覆盖的组件(指令、数据包给的)仍然优先。
- **行为**同 Ibx 弓弩(见 [3](#3-远程武器)):线性蓄力、伤害快照、按暴击率掷暴击(原版是弓满蓄才暴击、玩家的弩箭必定暴击)、副手不能用、支持自动射击;拿着它们的生物也按属性结算,持弩生物按 3.15 的箭速射。
- **拉弓动画**:本模组的 `assets/minecraft/items/bow.json` 把原版弓的定义换成了 `ibx_lib:pull`。关掉开关时这个属性对原版弓按 20 tick 算,和原版一样。
- 判断用 `RangedWeaponApi.isManagedBow` / `isManagedCrossbow`;只想问开关用 `VanillaRangedWeapons.isEnabled()`。

### 原版盔甲

| 套装 | 护甲 | 法术抗性(头 / 胸 / 腿 / 脚) |
| --- | --- | --- |
| 下界合金 | 不变(韧性、击退抗性也不变) | 3 / 8 / 6 / 3,等于各件的护甲值 |
| 金 | 去掉 | 3 / 8 / 6 / 3,钻石套装的护甲值 |
| 铜 | 去掉 | 2 / 4 / 3 / 1,它原来的护甲值 |

做法同弓弩:修饰符烤进默认组件,按物品栈覆盖的组件仍然优先。

### 堆叠上限

原版里一组只能叠 16 个的物品改成 64 个(`VanillaStackSizes.STACK_SIZE`)。只认下面这些原版物品本身,别的模组的物品不碰:

| 类别 | 物品 |
| --- | --- |
| 告示牌 | 12 种木头的告示牌(橡木、云杉木、白桦木、丛林木、金合欢木、樱花木、深色橡木、苍白橡木、红树木、竹、绯红木、诡异木) |
| 悬挂式告示牌 | 同上 12 种 |
| 旗帜 | 16 种颜色 |
| 投掷物 | 雪球、鸡蛋、蓝色鸡蛋、棕色鸡蛋、末影珍珠 |
| 其他 | 铁桶(空桶)、盔甲架、蜂蜜瓶 |

- **成书不改**,仍是 16。
- 做法同弓弩:改的是物品的默认组件(`max_stack_size`),按物品栈覆盖的组件仍然优先。
- 装了东西的桶(水桶、熔岩桶等)本来就是 1,不受影响。

### 刷怪蛋

凋灵刷怪蛋、末影龙刷怪蛋在原版里注册了却没进创造栏。本模组把它们按这个顺序接在「刷怪蛋」栏的潜影贝刷怪蛋后面;别的模组已经放进去的就不再放。

---

## 5. 法器

`ibx.lib.item.general.IbxMagicWeaponItem` 是所有施法物品的基类,按种类分子类:`ibx.lib.item.IbxTomeItem`(法典)、`ibx.lib.item.IbxStaffItem`(法杖)。本节只讲物品这一侧,施法流程见 [法术系统](SPELL.md)。

**自带的**:

- **耐久**(必填):施法时按实际扣掉的法力掉耐久,每 4 点法力掉 1 点(`SpellCasting.MANA_PER_DURABILITY`),用光就碎。不可堆叠。
- **法术强度**:主手挂 `ibx_lib:spell_power`,修饰符 id `ibx_lib:base_spell_power`,tooltip 是深绿合并行。
- **施法时的移速**:写进原版的 `use_effects` 组件。
- **自带法术**:按法术 id 给,不填为空。
- **右键施法**:自动进 `#ibx_lib:can_cast_spell`,手持右键就是放当前选中的法术。
- **施法动作**:按施法类型放一段玩家动作(要装 Player Animation Library,没装只是手臂不动)。
- **标签**:自动进 `#c:tools`、`#c:tools/magic_weapon`,法典再进 `#c:tools/tome`,法杖再进 `#c:tools/staff`。

### 构造参数

`IbxMagicWeaponItem` 和 `IbxTomeItem` 的构造方法相同:

| 构造方法 | 说明 |
| --- | --- |
| `(properties, durability, spellPower)` | 施法移速用基准值,不带法术。 |
| `(properties, durability, spellPower, castMoveSpeed)` | 不带法术。 |
| `(properties, durability, spellPower, spells)` | 施法移速用基准值。 |
| `(properties, durability, spellPower, castMoveSpeed, spells)` | 全部参数。 |

`IbxStaffItem` 有同样的四个(攻速用默认值),另加一个带攻速的:`(properties, durability, spellPower, castMoveSpeed, attackSpeed, spells)`。只想改攻速时 `castMoveSpeed` 给 0。

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `properties` | `Item.Properties` | 注册工厂传进来的那个。耐久由下一个参数设,不用自己设。 |
| `durability` | `int` | 耐久。 |
| `spellPower` | `float` | 法术强度(主手)。 |
| `castMoveSpeed` | `float` | 施法时的移速倍率,截到 0~1;不大于 0 用 `BASELINE_CAST_MOVE_SPEED`(0.5)。 |
| `spells` | `List<Identifier>` | 自带法术的 id,按法术轮盘里的顺序。id 查不到的跳过,所以可以写别的模组的法术而不依赖它。 |
| `attackSpeed`(只有法杖) | `float` | 主手攻速基数,显示的攻速 = 本值 + 玩家的 4。默认 `DEFAULT_ATTACK_SPEED`(-2.8,显示 1.2)。 |

继承 `IbxMagicWeaponItem` 做新种类的法器时,还有一个 `protected` 构造方法,末尾多一个 `Consumer<ItemAttributeModifiers.Builder> extraModifiers`,往同一份属性修饰符里追加自己的(法杖的攻速就是这样加的)。

```java
public static final DeferredItem<IbxTomeItem> EMBER_TOME = ITEMS.registerItem("ember_tome",
        p -> new IbxTomeItem(p, 1000, 6.0F, List.of(Identifier.fromNamespaceAndPath("examplemod", "fireball"))));

public static final DeferredItem<IbxStaffItem> OAK_STAFF = ITEMS.registerItem("oak_staff",
        p -> new IbxStaffItem(p, 800, 4.0F, 0.7F));
```

### 自带法术

- 构造时给的法术是物品的默认值。物品栈带 `ibx_lib:spells` 组件时以组件为准,指令、战利品可以覆盖;组件给空列表就是没有法术。
- `getSpells(stack)` 返回这件物品现在提供的法术,`innateSpells(stack)` 始终返回构造时给的那几个。
- 法器实现了 `ibx.lib.spell.SpellContainer`。别的物品要提供法术,给物品栈加 `ibx_lib:spells` 组件就行,不用继承法器。
- **tooltip**:只要物品提供法术,名字下面就多一行「法术:」和每个法术的名字;物品用 `tooltip_display` 隐藏了 `ibx_lib:spells` 组件时不显示。

### 可重写的方法

| 方法 | 默认 | 说明 |
| --- | --- | --- |
| `public Identifier castAnimation(CastType type)` | 法典的三段 `ibx_lib:tome.cast_instant` / `tome.cast_hold` / `tome.cast_channel`;法杖换成 `ibx_lib:staff.cast_*` | 这件法器施法时放的玩家动作,按施法类型(瞬发、吟唱、引导和蓄力共用一段)。法术自己指定了动作时以法术为准。 |
| `public List<Holder<Spell>> getSpells(ItemStack stack)` | 组件优先,否则构造时给的 | 要按物品状态(充能、镶嵌等)变化就重写。 |

`IbxMagicWeaponItem.defaultCastAnimation(type)` 是法典那三段,不是 Ibx 法器的施法物品也用它。`spellPower()` 返回构造时给的法术强度。

### 法典的手持模型

本模组带一对法典的 3D 手持模型:合上的 `ibx_lib:item/general_tome_hand` 和翻开的 `ibx_lib:item/general_tome_hand_open`。书页用本模组自带的贴图,你只要给封面贴图 `cover`:

| `cover` 贴图上的区域(按 16×16 算) | 用在哪 |
| --- | --- |
| (0, 0)~(8, 6) | 封面 |
| (8, 0)~(16, 6) | 封底 |
| (0, 6) 一格 | 封面、封底的侧边 |
| (0, 9) 一格 | 书脊 |

`assets/examplemod/models/item/ember_tome_hand.json`(翻开的那个同理,父模型换成 `general_tome_hand_open`):

```json
{
  "parent": "ibx_lib:item/general_tome_hand",
  "textures": {
    "cover": "examplemod:item/ember_tome_cover"
  }
}
```

物品模型定义里用条件 **`ibx_lib:casting`** 切换合上 / 翻开:这件物品正拿在手上施法时为真。和原版 `minecraft:using_item` 的区别是瞬发法术也算(瞬发不挂「使用物品」,但施法动作放着的这段时间里也为真;没装 Player Animation Library 时瞬发不算)。

`assets/examplemod/items/ember_tome.json`:手上用 3D 模型,其余地方(物品栏、掉落物等)用平面图标。

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

### 法杖的手持模型

本模组不带法杖模型。法杖的施法动作(右手抬起、法杖向前倾)按这样的握法做:手持模型沿 +Y 方向、握点在模型中心、第三人称显示不旋转,也就是法杖和手臂垂直。新法杖的手持模型照这个握法摆,就能直接用自带的动作。

法杖主手带攻速修饰符,拿去近战(比如给它写 Better Combat 的 `weapon_attributes`)时挥一下的快慢看它。

---

## 6. 盾牌

基类 `ibx.lib.item.general.IbxShieldItem`(抽象)管格挡,子类按单双手分:

| 类 | 说明 |
| --- | --- |
| `ibx.lib.item.IbxOneHandedShieldItem` | 单手盾:只挡,主手副手都同原版盾,能和单手武器一起拿。 |
| `ibx.lib.item.IbxTwoHandedShieldItem` | 双手盾:主手拿着还能普通攻击和盾击。 |

### 格挡

格挡全走原版的 `blocks_attacks` 组件,由构造参数烤出来:

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `properties` | `Item.Properties` | 注册时只给耐久和修复材料;`blocks_attacks`、副手装备位、破碎音由基类挂。 |
| `blockDelay` | `float` | 举盾几秒后生效;不大于 0 用 `BASELINE_BLOCK_DELAY`(0.25)。 |
| `blockFactor` | `float` | 挡掉的比例 0~1;不大于 0 用 `BASELINE_BLOCK_FACTOR`(1.0,全挡)。 |
| `blockAngle` | `float` | 水平格挡角度:正前方左右各多少度;不大于 0 用 `BASELINE_BLOCK_ANGLE`(90)。 |

- 其余同原版盾:`#minecraft:bypasses_shield` 的伤害挡不住,格挡时掉耐久的算法、格挡音、破碎音都一样。
- 继承原版的 `ShieldItem`,第一人称举盾的显示和原版盾一致。
- 标签:自动进 `#c:tools`、`#c:tools/shield`,再按子类进 `#c:tools/one_handed_shield` 或 `#c:tools/two_handed_shield`。

### 单手盾

| 构造方法 | 说明 |
| --- | --- |
| `IbxOneHandedShieldItem(properties)` | 格挡数值全用原版盾的。 |
| `IbxOneHandedShieldItem(properties, blockDelay, blockFactor, blockAngle)` | 自定格挡数值。 |

```java
// 举得快,但只挡 60%、正面左右各 60°
public static final DeferredItem<IbxOneHandedShieldItem> BUCKLER = ITEMS.registerItem("buckler",
        p -> new IbxOneHandedShieldItem(p.durability(250).repairable(ItemTags.IRON_TOOL_MATERIALS), 0.1F, 0.6F, 60.0F));
```

### 双手盾

| 构造方法 | 说明 |
| --- | --- |
| `(properties, attackDamage, attackSpeed)` | 格挡数值全用原版盾的,没有额外属性。 |
| `(properties, attackDamage, attackSpeed, extraModifiers)` | 格挡数值全用原版盾的。 |
| `(properties, blockDelay, blockFactor, blockAngle, attackDamage, attackSpeed)` | 没有额外属性。 |
| `(properties, blockDelay, blockFactor, blockAngle, attackDamage, attackSpeed, extraModifiers)` | 全部参数。 |

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `attackDamage` | `float` | 主手普通攻击的伤害基数(显示时加空手的 1)。盾击的伤害也看它。 |
| `attackSpeed` | `float` | 主手攻速基数(显示时加玩家的 4)。 |
| `extraModifiers` | `Consumer<ItemAttributeModifiers.Builder>` | 往同一份属性修饰符里追加的(盾的被动属性,比如法术抗性)。注册时自己调 `Properties#attributes` 会被基类覆盖,所以要走这个参数。 |

- **主手普通攻击**:左键就是原版普通攻击,伤害和攻速来自上面两个参数。
- **双手和攻击动作**交给 Better Combat(见下面的 [资源文件](#双手盾的资源文件)):主手拿着它时副手被当成空手。没装 Better Combat 时不限双手,副手照常拿东西,普通攻击是原版挥臂。
- **盾击**:主手举盾时按左键。举着它时左键只能是盾击,这期间的普通攻击会被取消。
- 盾击只认主手。放在副手时没有盾击:装了 Better Combat 被当成空手,没装就是一面原版盾。

### 盾击流程

1. **客户端输入**(`ibx.lib.client.ShieldInput`):主手拿着双手盾并按着右键时,左键不再交给原版和 Better Combat。盾举着、盾击不在冷却时按下左键,发一个 `ShieldBashPayload`(`ibx_lib:shield_bash`,没有数据)。
2. **服务端校验** `ShieldApi.tryBash(player)`:主手是双手盾、主手正举着、冷却已过,才调 `shield.bash(player, stack)`。
3. **`IbxTwoHandedShieldItem#bash`** 依次:
   - `ShieldApi.markBash` 记下这次盾击并进冷却(`bashCooldownTicks()`),状态同步给自己和周围玩家;
   - 挥臂、播放音效;
   - 趁盾还举着调 `onBash` 结算,打中的生物再交给饰品的触发钩子(见 [8](#触发钩子));
   - 盾失效 `bashDisableTicks()`(物品冷却 + 停止使用,不响破碎音)。右键还按着的话,失效一过会自动重新举起。
4. **`onBash` 默认**:掷一次暴击,`bashTargets` 里的每个目标挨一下 `bashHit`,打中的再走 `onBashHit`。
5. **客户端**收到同步过来的盾击状态时触发 `ShieldBashEvent`,盾击动作靠它放。

盾击冷却是独立计时的,不是物品冷却(物品冷却会让盾举不起来)。

### 盾击的可重写方法

数值(具体某面盾重写来调):

| 方法 | 默认 | 说明 |
| --- | --- | --- |
| `protected int bashCooldownTicks()` | `BASH_COOLDOWN_TICKS` = 60 | 盾击冷却 tick。 |
| `protected int bashDisableTicks()` | `BASH_DISABLE_TICKS` = 10 | 盾击后盾失效 tick。 |
| `protected double bashRange()` | `BASH_RANGE` = 2.5 | 打击范围:前方多少格。 |
| `protected float bashHalfAngle()` | `BASH_HALF_ANGLE` = 45 | 打击范围:正前方左右各多少度。 |
| `protected float bashKnockback()` | `BASH_KNOCKBACK` = 0.6 | 受伤本身的击退之外再推多少;击退属性和击退附魔另加。 |

结算:

| 方法 | 默认 | 说明 |
| --- | --- | --- |
| `protected void onBashHit(ServerPlayer player, ItemStack stack, LivingEntity target, boolean critical)` | 什么都不做 | 打中一个目标之后(伤害、击退已结算)的附加效果,比如定身、点燃、减速。最常重写的就是它。 |
| `protected List<LivingEntity> bashTargets(ServerPlayer player)` | 扇形里看得见的敌方,近的先挨;无敌的、弹射物打不中的不算 | 盾击打到谁。 |
| `protected boolean bashHit(ServerPlayer player, ItemStack stack, LivingEntity target, boolean critical)` | 伤害 = 玩家的攻击伤害,暴击时 ×(1 + 暴击伤害);附魔、耐久、统计同普通攻击,不带横扫。目标真受了伤才算打中(被盾挡住、无敌帧里不算),打中再给击退 | 打一个目标,返回打中没有。 |
| `protected List<LivingEntity> onBash(ServerPlayer player, ItemStack stack)` | 上面那一套,返回打中的生物 | 要把盾击的效果整个换掉就重写它;返回值交给饰品的 `onShieldBash`。 |
| `public void bash(ServerPlayer player, ItemStack stack)` | 冷却、挥臂、结算、盾失效 | 盾击的框架,一般不重写。 |
| `public Identifier bashAnimation()` | `ibx_lib:shield.bash` | 盾击放一遍的玩家动作。 |
| `public Identifier guardAnimation()` | `ibx_lib:shield.guard` | 主手举盾时循环放的玩家动作。 |

```java
public class TowerShieldItem extends IbxTwoHandedShieldItem {
    private static final Identifier RESISTANCE_ID = Identifier.fromNamespaceAndPath("examplemod", "tower_shield_resistance");

    public TowerShieldItem(Item.Properties properties) {
        super(properties, 5.0F, -3.0F, modifiers -> modifiers.add(AttributeRegistry.SPELL_RESISTANCE,
                new AttributeModifier(RESISTANCE_ID, 4.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND));
    }

    @Override
    protected int bashCooldownTicks() {
        return 100;
    }

    /** 盾击打中的目标减速 2 秒。 */
    @Override
    protected void onBashHit(ServerPlayer player, ItemStack stack, LivingEntity target, boolean critical) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
    }
}

public static final DeferredItem<TowerShieldItem> TOWER_SHIELD = ITEMS.registerItem("tower_shield",
        p -> new TowerShieldItem(p.durability(600).repairable(ItemTags.IRON_TOOL_MATERIALS)));
```

### ShieldApi、ShieldBash 与 ShieldBashEvent

`ibx.lib.api.ShieldApi`:

| 方法 | 说明 |
| --- | --- |
| `isRaisingTwoHandedShield(entity)` | 主手正举着一面双手盾(盾击的前提)。两端都能读。 |
| `isBashReady(entity)` | 盾击冷却过了没。两端都能读。 |
| `getState(entity)` | 盾击状态 `ShieldBash`,没出过盾击为 `null`。 |
| `tryBash(player)` | 校验后出盾击。只在服务端调。 |
| `markBash(entity, cooldownTicks)` | 记下这次盾击的时刻和冷却并同步。只在服务端调,`bash` 里已经调过。 |

- `ibx.lib.api.ShieldBash`:记录 `lastBashTick`(上一次盾击的世界时刻,没出过为 -1)和 `cooldownUntil`(冷却到点的世界时刻)。不存盘,同步给自己和周围玩家。读写走 `ShieldApi`。
- `ibx.lib.api.event.ShieldBashEvent`:某个生物出了一次盾击,发在 `NeoForge.EVENT_BUS` 上,**只在客户端触发**,自己和周围玩家的都会收到。`getEntity()`、`getState()`。

### 格挡与盾击的动作

`ibx.lib.client.ShieldAnimations` 用 Player Animation Library 放动作,自己和周围玩家都能看到。Player Animation Library 是可选的,没装时不放动作,其余照常。

- **格挡**:主手举着双手盾时循环放 `guardAnimation()`,放下时淡回原来的姿势。只管第三人称,第一人称照原版画举盾。
- **盾击**:收到 `ShieldBashEvent` 时放一遍 `bashAnimation()`,从格挡姿势起手,第一人称也显示。
- 动作按右手做,主手是左手时整段镜像。动画文件不存在就什么都不放。
- 要换动作:重写 `guardAnimation()` / `bashAnimation()`,返回自己的动画 id。动画文件放在自己模组的 `assets/<命名空间>/player_animations/` 下,id 是「命名空间:文件里 `animations` 下的动画名」,和文件名无关。

本模组的 `assets/ibx_lib/player_animations/shield_attack.json` 里有五段:`shield.guard`、`shield.bash`,以及给 Better Combat 用的 `shield.pose`、`shield.attack_swipe`、`shield.attack_slam`。

### 双手盾的资源文件

**Better Combat**:本模组带一份预设 `data/ibx_lib/weapon_attributes/two_handed_shield.json`:双手持握、站姿 `ibx_lib:shield.pose`、攻击距离 -0.5,普通攻击两段——横扫(120°)和下砸(90°,伤害 ×1.2)。每面双手盾在 `data/<命名空间>/weapon_attributes/<物品名>.json` 里引用它:

```json
{
  "parent": "ibx_lib:two_handed_shield"
}
```

**手持显示**:本模组带两个只有显示变换(`display`)的父模型,几何和贴图由你的模型自己给:

| 模型 | 用于 |
| --- | --- |
| `ibx_lib:item/general_two_handed_shield_hand` | 没举盾时 |
| `ibx_lib:item/general_two_handed_shield_block` | 举盾时。和上一个只差第一人称的显示(换成原版举盾的位置)。 |

物品模型定义用 `minecraft:using_item` 分两支,两支的模型分别以这两个为父模型:

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

两支除了第一人称,其他显示必须一样:

- 第三人称不一样的话,盾击那一下会停止使用、换回没举盾的显示,盾会在手里转一下。
- 物品栏里的这面盾举着时也走举盾那一支,缺了 `gui` 显示图标就会变。

---

## 7. 3D 盔甲

`ibx.lib.item.IbxGeoArmorItem`:穿上的样子由 GeckoLib 画的盔甲件。它只管「穿上长什么样」,护甲、耐久、穿戴部位、修复材料照常由注册时的物品属性给,见下面的 [盔甲的物品属性](#盔甲的物品属性)。

> 用它必须装 GeckoLib:本模组对 GeckoLib 是可选依赖,你的模组要自己把它设成必需。

| 构造方法 | 说明 |
| --- | --- |
| `IbxGeoArmorItem(properties, model)` | 没有自发光贴图。 |
| `IbxGeoArmorItem(properties, model, glow)` | `glow` 为 `true` 时再叠一层自发光贴图。 |

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `properties` | `Item.Properties` | 注册时给好材质和部位:`humanoidArmor`,或者用 `IbxArmorProperties`。 |
| `model` | `Identifier` | 模型 id。一套盔甲的几件传同一个,共用一个模型、一张贴图。 |
| `glow` | `boolean` | 有没有自发光贴图,不填就是 `false`。没有这张图却传 `true` 会画出缺失贴图。 |

```java
private static final Identifier PLATE_MODEL = Identifier.fromNamespaceAndPath("examplemod", "bone_plate");

public static final DeferredItem<IbxGeoArmorItem> BONE_HELMET = ITEMS.registerItem("bone_helmet",
        p -> new IbxGeoArmorItem(p.humanoidArmor(BONE_MATERIAL, ArmorType.HELMET), PLATE_MODEL, true));
public static final DeferredItem<IbxGeoArmorItem> BONE_CHESTPLATE = ITEMS.registerItem("bone_chestplate",
        p -> new IbxGeoArmorItem(p.humanoidArmor(BONE_MATERIAL, ArmorType.CHESTPLATE), PLATE_MODEL, true));
```

### 盔甲的物品属性

`ibx.lib.item.general.IbxArmorProperties`:给一件人形盔甲配物品属性。材质自带护甲、韧性、击退抗性,在这之上逐条加这件自己的属性。它和 3D 盔甲无关,普通盔甲(原版盔甲层画的)也能用。

```java
public static final DeferredItem<IbxGeoArmorItem> BONE_HELMET = ITEMS.registerItem("bone_helmet",
        p -> new IbxGeoArmorItem(IbxArmorProperties.of(p, BONE_MATERIAL, ArmorType.HELMET)
                .add(AttributeRegistry.SPELL_RESISTANCE, 3)
                .add(Attributes.MAX_HEALTH, 2)
                .fireResistant()
                .build(), PLATE_MODEL, true));
```

| 方法 | 说明 |
| --- | --- |
| `IbxArmorProperties.of(properties, material, type)` | 起头:相当于 `properties.humanoidArmor(material, type)`,属性从材质自带的那几条开始。 |
| `add(attribute, amount)` | 加一条属性,直接加数值。 |
| `add(attribute, amount, operation)` | 加一条属性,自己指定运算方式(按百分比加的用 `ADD_MULTIPLIED_BASE`)。 |
| `fireResistant()` | 防火:掉进岩浆、火里不烧。 |
| `build()` | 得到 `Item.Properties`,后面还可以接着设稀有度等。之后不要再调 `Properties#attributes`,会把上面加的全盖掉。 |

- 加的修饰符 id 都同原版护甲的 `minecraft:armor.<部位>`(一个部位只穿得下一件,不会撞)。所以**一件上同一种属性只能有一条**:材质已经给的护甲、韧性、击退抗性不能再 `add`,重复了会在注册时抛异常。要调护甲值就改材质。
- 只要属性修饰符、不要 `Item.Properties` 时(比如在 `ModifyDefaultComponentsEvent` 里改已有物品),用 `IbxArmorProperties.modifiers(material, type)`:同样的 `add`,另有 `remove(attribute)` 去掉材质给的某一种属性,`build()` 得到 `ItemAttributeModifiers`。本模组调整原版盔甲(见 [4](#4-原版物品的改动))走的就是它。

### 资源文件

模型 id `examplemod:bone_plate` 对应:

| 文件 | 说明 |
| --- | --- |
| `assets/examplemod/geckolib/models/armor/bone_plate.geo.json` | 模型,几件都在这一个文件里。 |
| `assets/examplemod/textures/armor/bone_plate.png` | 贴图。 |
| `assets/examplemod/textures/armor/bone_plate_glowmask.png` | 自发光贴图,`glow` 为 `true` 时才用。发光的像素照抄贴图上的颜色,其余透明。 |

骨骼名同 GeckoLib 的盔甲模板,每件只画自己部位的骨骼(连同它下面的子骨骼):

| 物品的部位 | 画的骨骼 |
| --- | --- |
| 头盔 | `armorHead` |
| 胸甲 | `armorBody`、`armorRightArm`、`armorLeftArm` |
| 护腿 | `armorRightLeg`、`armorLeftLeg` |
| 靴子 | `armorRightBoot`、`armorLeftBoot` |

- **物品栏图标**是普通的物品模型,和 3D 模型无关,照常写 `items/<物品名>.json` 和物品贴图。
- **装备资源 id**:`ArmorMaterial` 里的 `assetId` 仍然要填,但原版盔甲层不画这件盔甲,不用写对应的 `equipment/*.json`。盔甲纹饰走的是原版盔甲层,所以纹饰不会显示。
- **标签**:盔甲不在 [9](#9-自动物品标签) 的自动标签里。要能附魔,在自己的 data 里把物品加进 `#minecraft:head_armor`、`#minecraft:chest_armor`、`#minecraft:leg_armor`、`#minecraft:foot_armor`。
- **原版第一人称的手臂**上不画盔甲,同原版和 GeckoLib 的默认行为。

### 第一人称动作

Player Animation Library 播第一人称动作(比如 Better Combat 的攻击)时,会把玩家自己的身体临时画出来。它只会藏原版盔甲层画的盔甲,GeckoLib 盔甲不走那条路,不处理的话头盔会糊在镜头上。

渲染器 `ibx.lib.client.renderer.IbxGeoArmorRenderer` 照它对原版盔甲的规矩处理这一遍:

- 头盔、护腿、靴子不画。
- 胸甲不画身体;手臂只有动作打开了「第一人称显示盔甲」、并且显示那只手时才画。
- 第三人称、别的玩家、物品栏里的人物预览、睡觉时照常画。没装 Player Animation Library 时没有这一遍,也照常画。

要自己判断这一遍(比如给别的不走原版盔甲层的东西用),用 `ibx.lib.compat.playeranim.PlayerAnimFirstPerson`,只在客户端用:

| 方法 | 说明 |
| --- | --- |
| `isFirstPersonPass(state)` | 这份实体渲染状态是不是正被当作第一人称那一遍来画。 |
| `showsArmArmor(state, arm)` | 那一遍里这只手臂上的盔甲画不画;不是那一遍时返回 `false`。 |

没装 Player Animation Library 时两个方法都返回 `false`,不会碰到它的类,不用先判断装没装。只想问装没装,用 `ibx.lib.compat.PlayerAnimCompat.isLoaded()`。

### 可重写的部分

| 想做什么 | 做法 |
| --- | --- |
| 加动画 | 继承 `IbxGeoArmorItem`,重写 `registerControllers` 加动画控制器;动画文件是 `geckolib/animations/armor/<名字>.animation.json`。 |
| 换画法(加渲染层、改骨骼映射等) | 继承 `IbxGeoArmorRenderer`(构造参数同物品:模型 id、`glow`,`glow` 同样可以不填),再在物品子类里重写 `createGeoRenderer` 用它。 |
| 改第一人称那一遍画哪些部位 | 重写 `IbxGeoArmorRenderer#firstPersonSegments(wearer, slot, segments)`,返回要画的部位;`segments` 是平时这个部位画的。 |

---

## 8. 饰品

本模组对 Curios(模组 id `curios`)是可选依赖。饰品本身是普通物品,槽位、属性加成按 Curios 自己的办法配;本模组提供下面几样东西。

**`ibx.lib.item.curio.UniqueCurioItem`**:同一种饰品只能戴一个。饰品栏别的格子里已经有同一物品就不让放;放回自己原来那格不算。其余行为(属性加成等)走 Curios 默认。不挂它就是能戴多个,每个都算加成。

```java
modEventBus.addListener(FMLCommonSetupEvent.class, event ->
        CuriosApi.registerCurio(SIGNET_RING.get(), UniqueCurioItem.INSTANCE));
```

**`ibx.lib.compat.CuriosCompat`**:

| 方法 | 说明 |
| --- | --- |
| `isLoaded()` | 装没装 Curios。 |
| `forEachCurio(entity, action)` | 按 Curios 的槽位顺序逐个给出饰品栏里的物品(跳过空格)。两端都能用。 |
| `anyCurio(entity, condition)` | 饰品栏里有没有满足条件的物品。两端都能用。 |
| `onCurioChange(listener)` | 饰品换了时通知,`listener` 拿到穿戴者。只在服务端触发。 |

> `UniqueCurioItem` 和 `CuriosCompat` 里除 `isLoaded()` 以外的方法都会碰到 Curios 的类。你的模组如果也把 Curios 当可选依赖,调用前先过 `CuriosCompat.isLoaded()`,否则没装 Curios 时会 `NoClassDefFoundError`。

饰品也能提供法术:饰品栏里带 `ibx_lib:spells` 组件的物品会算进玩家能放的法术(见 [法术系统](SPELL.md))。

### 触发钩子

**`ibx.lib.item.curio.CurioTriggers`**:饰品在战斗里的触发时机。饰品物品类实现这个接口,重写自己需要的方法,其余默认什么都不做。

```java
public class VampireFangItem extends Item implements CurioTriggers {
    public VampireFangItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onDamageDealt(SlotContext slot, ItemStack stack, LivingEntity target, DamageSource source, float amount) {
        // 打中后按伤害的 10% 回血
        slot.entity().heal(amount * 0.1F);
    }
}
```

| 方法 | 什么时候 | 能做什么 |
| --- | --- | --- |
| `onDealDamage(slot, stack, target, source, amount)` | 穿戴者打中别人:本模组的基础伤害(远程伤害、远程暴击)已定,还没算护甲、法术抗性 | 返回新的伤害量 |
| `onTakeDamage(slot, stack, source, amount)` | 穿戴者挨打,同一时间点,在攻击方的饰品之后 | 返回新的伤害量 |
| `onDamageDealt(slot, stack, target, source, amount)` | 打中之后,伤害已结算、已扣血;`amount` 是最终伤害,大于 0 才调 | 只通知 |
| `onDamageTaken(slot, stack, source, amount)` | 挨打之后,同上 | 只通知 |
| `onKill(slot, stack, victim, source)` | 穿戴者杀死一个生物;召唤物杀的算主人的 | 只通知 |
| `onSpellCastStart(slot, stack, event)` | 施法开始前,条件都已通过(`SpellCastEvent.Pre`) | `setManaCost`、`setCooldownTicks`、`setCanceled(true)` |
| `onSpellCast(slot, stack, event)` | 每次施法生效之后,引导每次都调(`SpellCastEvent.Post`) | 只通知 |
| `onSummon(slot, stack, summon)` | 召唤出一只召唤物之后 | 只通知,可以给召唤物加效果 |
| `onBlock(slot, stack, source, blocked)` | 举盾挡下一次攻击,原版盾和本模组的盾都算;`blocked` 是挡下的量 | 只通知 |
| `onShieldBash(slot, stack, shield, hits)` | 打出一次盾击之后;`hits` 是打中的生物,可能为空 | 只通知 |

- **只在服务端调用**,只认戴在饰品栏里的(化妆栏不算)。`slot` 是这件饰品所在的格子,`slot.entity()` 是穿戴者;`stack` 是这件饰品本身。
- **按槽位顺序逐件调用**:同一种戴了几个就调几次,改伤害的会一件接一件叠上去。不想叠的照上面挂 `UniqueCurioItem`。
- **冷却、触发几率由饰品自己处理**(比如 `Player#getCooldowns`、`slot.entity().getRandom()`),框架不管。
- **伤害**:近战、弹射物(穿戴者是射手)、法术都走 `onDealDamage` / `onDamageDealt`,用 `source` 区分。改的伤害会再被护甲、法术抗性减免,和原版附魔的伤害加成一样。致命的那一下,`onKill` 在 `onDamageDealt` 之前调。
- **被别的模组取消的不触发**:取消了的伤害、死亡、格挡都不会调到。
- **盾击**:由 `IbxTwoHandedShieldItem` 的服务端流程调用。重写了 `onBash` 的盾,返回值就是交给这里的 `hits`。
- 分发在 `ibx.lib.compat.curios.CurioTriggerHandler`。

> `CurioTriggers` 引用了 Curios 的类(`SlotContext`):实现它的饰品所在的模组要把 Curios 设成必需。

---

## 9. 自动物品标签

用本模组的基类、接口注册的物品(含子类、含别的模组注册的)会自动进下面的标签,各模组的 data 里不用再写。规则在 `ibx.lib.registry.AutoItemTags`,子类同时满足父类的规则。

| 基类 / 接口 | 自动进的标签 |
| --- | --- |
| `IbxMeleeWeaponItem` | `#c:tools`、`#c:tools/melee_weapon` |
| `IbxRangedWeaponItem`(接口) | `#c:tools`、`#c:tools/ranged_weapon` |
| `IbxBowItem` | `IbxRangedWeaponItem` 的,加 `#c:tools/bow` |
| `IbxCrossbowItem` | `IbxRangedWeaponItem` 的,加 `#c:tools/crossbow` |
| `IbxShieldItem` | `#c:tools`、`#c:tools/shield` |
| `IbxOneHandedShieldItem` | `IbxShieldItem` 的,加 `#c:tools/one_handed_shield` |
| `IbxTwoHandedShieldItem` | `IbxShieldItem` 的,加 `#c:tools/two_handed_shield` |
| `IbxMagicWeaponItem` | `#ibx_lib:can_cast_spell`、`#c:tools`、`#c:tools/magic_weapon` |
| `IbxTomeItem` | `IbxMagicWeaponItem` 的,加 `#c:tools/tome` |
| `IbxStaffItem` | `IbxMagicWeaponItem` 的,加 `#c:tools/staff` |

- 是真的标签成员:同步到客户端,指令、JEI、别的模组都认得。
- 物品插在各标签的最前面,数据包可以用 `"remove"` 把某件物品去掉;`"replace": true` 去不掉。
- `#c:tools/magic_weapon`、`#c:tools/tome`、`#c:tools/staff`、`#c:tools/one_handed_shield`、`#c:tools/two_handed_shield` 是本模组自建的通用标签,没有数据文件,全靠自动填。
- 标签的 key 在 `ibx.lib.registry.TagRegistry`:`CAN_CAST_SPELL`、`TOOLS_MAGIC_WEAPON`、`TOOLS_TOME`、`TOOLS_STAFF`、`TOOLS_ONE_HANDED_SHIELD`、`TOOLS_TWO_HANDED_SHIELD`。

**`#ibx_lib:can_cast_spell`**(能施法的物品):手持右键放当前选中的法术,使用时长统一为 72000 tick(同弓,能一直按着)。不是 Ibx 法器的物品要施法,由物品所在模组在 `data/ibx_lib/tags/item/can_cast_spell.json` 里加进去。食物、药水这类本来有使用时长的物品不要进这个标签,否则吃不完、喝不完。

---

## 10. 数据组件

`ibx.lib.registry.DataComponentRegistry` 注册了两个物品数据组件:

| id | 字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| `ibx_lib:auto_fire` | `AUTO_FIRE` | 布尔 | 自动射击(见 [3](#3-远程武器))。对 Ibx 弓弩和被接管的原版弓弩有效。缺省视为 `false`,不在任何物品的默认组件里。读取走 `RangedWeaponApi.isAutoFire`。 |
| `ibx_lib:spells` | `SPELLS` | 法术 id 的列表 | 物品提供的法术,按法术轮盘里的顺序。带它的物品就是法术容器;法器上有它时代替构造时给的自带法术(见 [5](#5-法器))。 |

```
/give @s minecraft:bow[ibx_lib:auto_fire=true]
/give @s minecraft:book[ibx_lib:spells=["examplemod:fireball"]]
```

基类用到的原版组件:

| 组件 | 谁设的 |
| --- | --- |
| `attribute_modifiers` | 所有基类(主手的数值);被接管的原版弓弩、调整过的原版盔甲是改默认组件。 |
| `tool`、`weapon` | `IbxMeleeWeaponItem`(同原版剑)。 |
| `use_effects` | `IbxMagicWeaponItem`(施法时的移速)。 |
| `blocks_attacks`、`break_sound`、副手装备位 | `IbxShieldItem`。 |
