# 目标选择

**简体中文** | [English](TARGETING_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

**目标选择**(包 `ibx.lib.target`)回答两个问题:这个动作**作用到谁**、**落在哪**。法术、按住使用的物品、生物 AI 都用这一套;敌我判断、准星选取、范围找人、读条时地上的提示都在这里。

- **一套敌我规则**:`TargetApi.canHarm` 统一处理队伍、PvP、宠物和召唤物的归属,全模组的友伤保护都按它算(见 [3](#3-阵营与敌我))。
- **只写声明**:法术、物品用一句 `Targeting` 写「怎么瞄」,选目标、锁定、读条中校验、生效前再校验、没有目标时的提示都由 lib 做(见 [4](#4-瞄准方式-targeting))。
- **两种选取时机**:「生效时选取」和「开始时锁定」,由玩家自己在 选项 → 控制 里选,不由法术决定(见 [5](#5-选取时机))。
- **地上的提示**:读条时在锁定的目标脚下画方形,范围画圆 / 扇形 / 长条,按作用的阵营上色,周围的玩家都看得见(见 [10](#10-读条提示与客户端))。
- **服务端说了算**:选谁、锁谁只在服务端算,客户端只照服务端发来的提示画。
- **也能单独用**:不写声明,直接调 `TargetApi` 的准星选取、范围找人(见 [8](#8-targetapi))。

涉及的类:

| 类 | 作用 |
| --- | --- |
| `Faction` | 阵营:敌方 / 友方 / 不分敌我 / 谁也不作用。 |
| `Targeting` | 瞄准方式的声明,法术、物品各写一次。 |
| `TargetMode` / `TargetModes` | 选取时机,以及玩家的两项设置。 |
| `TargetApi` | 通用入口:敌我判断、准星选取、范围找人、当前锁定。 |
| `TargetLock` | 实体身上的「当前锁定」:一次施法或一次物品使用选中的目标 / 落点。 |
| `TargetHandler` | 服务端事件:每 tick 校验锁定、发提示,替 `TargetingItem` 选目标。 |
| `TargetingItem` | 要选目标的物品实现的接口。 |
| `TargetIndicator` | 地上提示的数据,服务端算好发给客户端。 |
| `client.ground.GroundShapes` | 客户端画地上形状的工具。 |

相关文档:

- 法术的注册、施法类型、伤害结算、弹射物 / 射线 / 范围实体:[法术](SPELL.md)
- 召唤物和它的主人:[召唤物](SUMMON.md)
- 服务端发的地面预警(`api.GroundWarnings`)和其他视觉效果:[视觉效果](EFFECT.md)
- 配置项的详细说明:[配置](CONFIG.md)
- 法术强度等属性:[属性](ATTRIBUTE.md)

---

## 2. 快速上手

两个法术:一个打准星指着的单个敌方,一个选一块地面、打范围内的敌方。瞄准方式写在构造方法里,交给 `Spell` 的第二个构造参数。

```java
/** 惩击:吟唱 1 秒,打准星 16 格内的一个敌方。 */
public final class SmiteSpell extends Spell {
    public SmiteSpell(Properties properties) {
        super(properties, Targeting.unit(Faction.ENEMY).range(16.0));
    }

    @Override
    public void onCast(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        LivingEntity target = ctx.target();   // 生效时定下来的那个
        if (target == null) {
            return;
        }
        SpellDamageApi.hurt(target, ctx.damage(c -> 6.0F + c.spellPower() * 1.2F), caster, caster, null);
    }
}

/** 地震:吟唱 1.5 秒,准星 24 格内选落点,打落点半径 3.5 格内的敌方。 */
public final class QuakeSpell extends Spell {
    private static final float RADIUS = 3.5F;

    public QuakeSpell(Properties properties) {
        super(properties, Targeting.point().range(24.0).circle(RADIUS, Faction.ENEMY));
    }

    @Override
    public void onCast(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        Vec3 center = ctx.targetPos();        // 生效时定下来的落点
        if (center == null) {
            return;
        }
        SpellDamageSnapshot damage = ctx.damage(c -> 4.0F + c.spellPower() * 0.8F);
        // 圆只是提示,打谁要自己找:落点上方 3 格高的圆柱里的敌方
        for (LivingEntity target : TargetApi.inCylinder(ctx.level(), center, RADIUS, 3.0,
                TargetApi.affects(caster, Faction.ENEMY, false))) {
            SpellDamageApi.hurt(target, damage, caster, caster, center);
        }
    }
}
```

注册和别的法术一样,瞄准方式不写在注册行里:

```java
public static final DeferredRegister<Spell> SPELLS = DeferredRegister.create(SpellRegistries.SPELL_KEY, "examplemod");

public static final DeferredHolder<Spell, SmiteSpell> SMITE =
        SPELLS.register("smite", () -> new SmiteSpell(Spell.props().manaCost(20).cooldown(8.0F).castTime(1.0F)));
public static final DeferredHolder<Spell, QuakeSpell> QUAKE =
        SPELLS.register("quake", () -> new QuakeSpell(Spell.props().manaCost(30).cooldown(12.0F).castTime(1.5F)));
```

只写了这些,两个法术就有了下面的行为:

- **惩击**
  - 右键时准星 16 格内没有敌方:放不了,动作栏提示「范围内没有目标」,不读条、不扣蓝、不冷却。
  - 挡在中间的友方(自己的宠物、召唤物等)不算,准星穿过去找后面的敌方;墙挡得住。
  - 读条时目标脚下画一块红色方形。
  - 玩家设的是「生效时选取」(默认):读完时准星指着另一个敌方就打那个,没指着就打开始时选中的。
  - 玩家设的是「开始时锁定」:目标死了、或者离开 24 格(射程 × 1.5)就取消,提示「目标丢失」,不扣蓝、不冷却。
  - 生物放它:先用自己的攻击目标,AI 也可以直接把对象传进来(见 [6](#6-在法术里使用))。
- **地震**
  - 读条时落点画半径 3.5 格的红圈;「生效时选取」下圈跟着准星走,「开始时锁定」下圈不动。
  - 指着生物取它脚下,指着墙、天空时往下找地面,找不到地面就放不了。

---

## 3. 阵营与敌我

### Faction

`Faction` 表示一个效果作用于谁。准星选人、范围找人、法术实体的命中筛选、提示的颜色都用它。

| 值 | 作用于 |
| --- | --- |
| `ENEMY` | 能伤害的(`TargetApi.canHarm` 为 true)。 |
| `ALLY` | 不能伤害的:自己、宠物、召唤物、关了友伤的队友、关了 PvP 时的其他玩家……**包括自己**,要不要算上自己由用的地方决定。 |
| `ALL` | 不分敌我。 |
| `NONE` | 谁也不作用。 |

`faction.test(from, target)` 判断 `from` 这一方的效果作用不作用于 `target`。没有来源(`from` 为 null,比如施法者下线了)时:`ENEMY`、`ALL` 照样作用,`ALLY` 一律不作用。

### 敌我规则

`TargetApi.canHarm(from, target)` 是唯一的敌我判断,按顺序看:

1. 没有 `from`:能伤。
2. 目标是自己,或者两者同乘一个载具:不能。
3. 两者各自归到「最终主人」(`OwnableEntity#getRootOwner()`;没有主人的就是它自己)。归属相同就不能:自己的宠物和召唤物、宠物打主人、同一主人的宠物之间。
4. 两边都是玩家:按原版 `Player#canHarmPlayer`,也就是服务器的 `pvp` 规则和队伍友伤。
5. 两边同队:看队伍开没开友伤,开了就能。
6. 其余按原版 `isAlliedTo` 取反。

要点:

- **宠物、召唤物按主人算**:主人能伤谁,它们就能伤谁;谁能伤主人,谁就能伤它们。
- **队伍默认开着友伤**:原版记分板队伍的友伤默认是开的,这时队友算敌方。要队友互相算友方,得把队伍的友伤关掉。
- **玩家之间只在服务端准**:客户端不知道服务器的 `pvp` 规则。
- 法术伤害(`SpellDamageApi.hurt`)和法术的弹射物、射线、范围实体默认只作用于敌方,用的也是这条规则;伤害快照开了友伤就不分敌我(见 [法术](SPELL.md))。

---

## 4. 瞄准方式 Targeting

`Targeting` 是一句声明:怎么瞄、射程多远、读条时地上画什么。用工厂方法建,链式方法补参数。

```java
Targeting.self()                                           // 不选(默认)
Targeting.unit(Faction.ALLY).range(16.0).orSelf()          // 准星锁一个友方,没指着就对自己
Targeting.unit(Faction.ENEMY).range(16.0)                  // 准星锁一个敌方,没指着放不了
Targeting.point().range(24.0).circle(3.5F, Faction.ENEMY)  // 准星选落点,读条时地上画半径 3.5 的红圈
Targeting.direction()                                      // 不锁定,生效时朝准星
Targeting.self().fan(2.5F, 45.0F, Faction.ENEMY)           // 自身前方的扇形,读条时画出来
Targeting.self().rect(4.0F, 12.0F, 0.8F, Faction.ENEMY)    // 面前 4 格处横着一条 12 × 0.8 的长条,读条时画出来
```

每次调工厂方法都是一个新对象,链式方法改的是它自己;声明完就别再改。

### 四种类型

| 工厂方法 | `Kind` | 选什么 | 法术里怎么取 |
| --- | --- | --- | --- |
| `Targeting.self()` | `SELF` | 不选,作用于自己或自身周围。 | 不用取;范围内的人自己找。 |
| `Targeting.unit(faction)` | `UNIT` | 准星锁一个生物。`faction` 只能是 `ENEMY` 或 `ALLY`,其他值抛 `IllegalArgumentException`。 | `ctx.target()` |
| `Targeting.point()` | `POINT` | 准星选一个落点。 | `ctx.targetPos()` |
| `Targeting.direction()` | `DIRECTION` | 不锁定,生效时朝准星;生物朝 AI 给的对象或自己的攻击目标。 | `ctx.aimPoint()`;弹射物、射线基类自己就按这个规则瞄。 |

准星怎么选人、选落点的具体规则见 [8](#8-targetapi)。

### 链式方法

| 方法 | 用在 | 说明 |
| --- | --- | --- |
| `range(double)` | 指向、地点、方向 | 射程(格):指向型找人、地点型找落点的距离。默认 0,所以指向型、地点型一定要写。方向型只用来检查 AI 直接给的对象够不够得着(0 = 不查)。 |
| `keepRange(double)` | 指向,以及 AI 给的对象 | 保持距离(格):锁定后离开它就算失效。不写 = 射程 × 1.5(`Targeting.KEEP_RANGE_RATIO`);0 = 不看距离。 |
| `orSelf()` | 指向 | 开始时没指着就对自己,而不是放不了。只在开始时用。 |
| `blockedByOthers()` | 指向 | 准星和目标之间挡着阵营不对的生物时当没指着。不写 = 穿过去找后面阵营对的。 |
| `circle(float radius, Faction faction)` | 地点、自身 | 读条时地上画圆:地点型以落点为圆心,自身型以自己为圆心。 |
| `fan(float range, float halfAngle, Faction faction)` | 自身 | 读条时地上画扇形:前方 `range` 格、左右各 `halfAngle` 度。 |
| `rect(float ahead, float length, float width, Faction faction)` | 自身 | 读条时地上画长条:中心在面前 `ahead` 格处,横在面朝方向的左右,长 `length`、沿面朝方向宽 `width`,跟着朝向转。 |
| `hidden()` | 全部 | 不显示提示(Boss 想偷袭的招式)。 |

`circle` / `fan` / `rect` 里的 `faction` 是「这个范围作用于谁」,只决定提示的颜色。

> **范围只是提示**。`circle` / `fan` / `rect` 不替法术找人,生效时作用到谁要在 `onCast` 里自己找(`TargetApi.inCylinder` / `TargetApi.inFan`,或者生成范围实体)。声明的范围和实际结算的范围要自己对齐。

不同类型实际画什么见 [10](#10-读条提示与客户端)。指向型画在目标脚下,不用写范围;方向型什么也不画。

### 读取

| 方法 | 返回 |
| --- | --- |
| `kind()` | `Targeting.Kind`:`SELF` / `UNIT` / `POINT` / `DIRECTION`。 |
| `faction()` | 指向型锁的阵营;其他类型为 `Faction.ALL`。 |
| `range()` / `keepRange()` | 射程 / 保持距离(没写时已经换算成射程 × 1.5)。 |
| `fallsBackToSelf()` / `isBlockedByOthers()` / `isHidden()` | 对应 `orSelf()` / `blockedByOthers()` / `hidden()`。 |
| `area()` | `Targeting.Area`:`NONE` / `CIRCLE` / `FAN` / `RECT`。 |
| `areaRadius()` | 圆半径、扇形长度、长条长度。 |
| `areaHalfAngle()` / `areaAhead()` / `areaWidth()` | 扇形左右各几度 / 长条中心在面前几格 / 长条的宽。 |
| `areaFaction()` | 范围作用于谁,默认 `Faction.ALL`。 |
| `picks()` | 开始时要不要选:指向、地点为 true。 |
| `needsLock()` | 需不需要「当前锁定」:要选的,或者要画范围的。 |

---

## 5. 选取时机

**什么时候选**不由法术、物品决定,是玩家的设置:`TargetMode`,指向型、地点型各一个。

| 值 | 名字 | 指向型 | 地点型 |
| --- | --- | --- | --- |
| `EFFECT`(默认) | 生效时选取 | 开始时选中的只当后备。读条中每 tick 按准星重选,生效时再选一次:指着谁就是谁,没指着才用后备。后备失效了只是丢掉,不取消。 | 读条中落点跟着准星走,生效时按当时的准星定;没有落点时用开始时的。 |
| `START` | 开始时锁定 | 开始时选中就锁死,准星可以移开。读条中目标失效就取消。 | 开始时选中的落点就定下,之后准星怎么动都不变。 |

- **开始时都要有目标**:两种模式下,开始时没指着(又没声明 `orSelf()`)、找不到落点都放不了。
- **失效**指:死了、被移除、换了维度、离使用者超过保持距离(`keepRange`)。锁定的目标和后备都只查这几项,不再看视线,也不再查阵营。目标是自己时只要自己活着就有效。
- `orSelf()` 只在开始时起作用:开始时选中了别人,之后它失效、准星又没指着谁,生效时不会改成对自己,而是取消。
- **生物**没有设置,按 `EFFECT`;AI 直接给了对象时(见 [6](#6-在法术里使用))一律当 `START`。
- **在哪设**:选项 → 控制 里的「指向目标」「地点目标」两个按钮,对应客户端配置 `targetUnitLockAtStart`、`targetPointLockAtStart`(默认都是 false,即生效时选取)。客户端进服时、改动时、配置重载时把这两项发给服务端。

服务端存在玩家的附加数据 `ibx_lib:target_modes`(`TargetModes`)里,死亡重生后保留:

| 方法 | 说明 |
| --- | --- |
| `TargetModes.of(player)` | 这个玩家的两项设置;没发过就是 `TargetModes.DEFAULT`(都是 `EFFECT`)。 |
| `modes.unit()` / `modes.point()` | 指向型 / 地点型用哪种。 |
| `TargetMode.of(holder, kind)` | 这个实体这次用哪种:不是玩家返回 `EFFECT`;玩家的地点型取 `point()`,其余取 `unit()`。 |

---

## 6. 在法术里使用

### 声明

`Spell` 有两个构造方法:

| 构造方法 | 瞄准方式 |
| --- | --- |
| `Spell(Properties properties)` | `Targeting.self()`:不选目标。 |
| `Spell(Properties properties, Targeting targeting)` | 在法术类里声明。 |

瞄准方式属于法术的效果,写在法术类里,注册行的 `Spell.props()` 里没有对应的项。`spell.targeting()` 可以读回来。

### 在钩子里取

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `ctx.target()` | `LivingEntity`,可能为 null | 指向型作用到的生物。`canCast` 和读条中是「现在选中的」(开始时锁定的那个;或者生效时选取下准星现在指着的,没指着就是后备);`onCast` 里是生效时定下来的那个,声明了 `orSelf()` 时可能是施法者自己。生物施法时 AI 直接给的对象也在这里。 |
| `ctx.targetPos()` | `Vec3`,可能为 null | 地点型的落点,规则同上。 |
| `ctx.aimPoint()` | `Vec3` | 方向型朝哪:AI 给的对象或生物的攻击目标的身体正中;玩家是准星指着的地方(`TargetApi.aimPoint`)。 |

- 指向型法术走到 `onCast` 时 `ctx.target()` 一定有值,地点型走到 `onCast` 时 `ctx.targetPos()` 一定有值:没有的话框架已经取消了这次生效。
- 读条中(`onCastTick`)「生效时选取」下 `ctx.target()` 可能暂时为 null(后备失效了,准星又没指着谁)。
- **客户端的钩子里两者恒为 null**。`onCastStart` / `onCastTick` / `onCastEnd` 两端都调,和目标有关的表现(目标身上的粒子等)放在服务端那次里发。
- 自身型、方向型的法术两者都是 null;只有 AI 直接给了对象时 `ctx.target()` 是那个对象(见下面的「生物施法」)。

### 框架替你做的

| 时机 | 框架做什么 | 没有目标时 |
| --- | --- | --- |
| 开始施法 | 冷却、法力检查过了之后按声明选目标、锁定,再调 `canCast`(这时 `ctx.target()` / `ctx.targetPos()` 已经有了)。 | 返回 `CastResult.CONDITION_FAILED`,动作栏提示「范围内没有目标」,不读条、不扣蓝、不冷却。 |
| 读条中每 tick | 开始时锁定:查目标还有没有效。生效时选取:按准星重选。提示跟着更新。 | 开始时锁定的目标失效:取消施法(`CastEndReason.CANCELLED`),提示「目标丢失」。生效时选取不取消。 |
| 生效前(吟唱到点、引导每次生效、蓄力松手) | 再定一次目标,再调一次 `canCast`,通过了才扣蓝、调 `onCast`。 | 取消(`CANCELLED`),这一下不扣蓝。提示「目标丢失」(开始时锁定、AI 给的对象)或「范围内没有目标」(生效时选取)。 |
| 施法结束 | 放掉锁定,提示消失。 | — |

- 取消时没生效过就不进冷却;引导法术已经生效过几次的照常进冷却。
- **瞬发**法术只在开始时选一次,当场生效,不画提示。
- `canCast` 会被调多次(目标可能换了),所以只检查、不改东西。依赖目标状态的条件写在这里:

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

两条提示的语言键在 `TargetHandler.NO_TARGET`(`message.ibx_lib.target.no_target`)和 `TargetHandler.TARGET_LOST`(`message.ibx_lib.target.lost`)。

### 生物施法

生物没有准星,规则略有不同:

| 调用 | 指向型 | 地点型 | 方向型 |
| --- | --- | --- | --- |
| `SpellCasting.cast(mob, spell)` | 锁敌方的法术先用自己的攻击目标(`Mob#getTarget`,要在射程内、看得见、能伤害);没有再按视线选。锁友方的按视线选。 | 先用攻击目标的脚下(要在射程内、看得见);没有再按视线选。 | 瞄攻击目标的身体正中;没有就按视线。 |
| `SpellCasting.cast(mob, spell, target)` | 锁 `target`。 | 落在 `target` 开始时的脚下。 | 瞄 `target`。 |

AI 直接给对象(第二行,另有 `Holder<Spell>` 和带来源、物品的重载)时:

- 只查够不够得着:活着、同维度、在法术射程内(射程为 0 时不查距离)。**不查阵营**,给谁就是谁。够不着时施法失败,同「范围内没有目标」。
- 之后一律按开始时锁定处理:对象失效(死了、换维度、离开保持距离)施法就取消。
- 自身型的法术也可以给对象,`ctx.target()` 取得到。
- `target` 传 null 等同于第一行。

---

## 7. 在物品里使用

**按住使用、松手(或用完)生效**的物品实现 `TargetingItem`,lib 替它选、锁、校验、画提示:

```java
public interface TargetingItem {
    Targeting targeting(ItemStack stack, LivingEntity user);
}
```

| 阶段 | lib 做什么 | 物品做什么 |
| --- | --- | --- |
| 开始使用 | 按声明选目标、写进当前锁定。没有目标就不开始使用,动作栏提示「范围内没有目标」(声明了 `orSelf()` 就对自己)。声明不需要锁定(`needsLock()` 为 false)时什么也不做。 | 照常 `startUsingItem`。 |
| 使用中 | 每 tick 校验、重选、发提示。开始时锁定的目标失效就停止使用,提示「目标丢失」。 | — |
| 生效 | — | 在自己的 `releaseUsing` / `finishUsingItem` 里调 `TargetApi.resolve(user)`,拿这次作用到的目标 / 落点;返回 null 就当没放出来。 |
| 停止使用后 | 自动放掉锁定,提示消失。 | — |

选取时机同样按玩家的设置(见 [5](#5-选取时机))。锁定只在服务端有,`TargetApi.resolve` 要在服务端调。

```java
/** 闪现杖:按住瞄准,松手传送到准星 16 格内的落点。 */
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

**瞬间生效**的物品(`use` 里就生效)不用实现它,直接调 `TargetApi.pickEntity` / `TargetApi.pickPoint`。

---

## 8. TargetApi

`ibx.lib.target.TargetApi` 全是静态方法。带「锁定」的方法只在服务端用;其余的只读世界,但涉及玩家之间敌我的结果只在服务端准。

### 敌我与筛选

| 方法 | 说明 |
| --- | --- |
| `canHarm(from, target)` | `from` 能不能伤到 `target`(规则见 [3](#3-阵营与敌我))。`from` 可以为 null,一律能伤。 |
| `affects(from, faction, includeSelf)` | 常用的筛选,返回 `Predicate<LivingEntity>`:`from` 这一方的 `faction`;`includeSelf` 为 false 时排除 `from` 自己。配合下面两个范围方法用。 |

### 范围找人

| 方法 | 说明 |
| --- | --- |
| `inCylinder(level, base, radius, height, filter)` | 竖圆柱里的生物,离 `base` 近的排前面。底面中心 `base`、半径 `radius`、从 `base` 往上高 `height`。脚(实体位置)在圆里、碰撞箱和圆柱有重叠才算。 |
| `inFan(user, range, halfAngle, needSight, filter)` | `user` 前方扇形里的生物,近的排前面。水平距离不超过 `range`(加目标半宽)、在面朝方向左右各 `halfAngle` 度以内(贴身的不看角度),上下比 `user` 的碰撞箱多 1 格。`needSight` 为 true 时要 `user` 看得见(中间没有墙)。不含 `user` 自己。 |

两者都排除死的、旁观者、标记用的盔甲架;普通盔甲架不排除。

```java
// 自己周围 5 格内的友方,含自己
List<LivingEntity> allies = TargetApi.inCylinder(caster.level(), caster.position(), 5.0, 3.0,
        TargetApi.affects(caster, Faction.ALLY, true));

// 面前 3 格、左右各 60 度、看得见的敌方
List<LivingEntity> enemies = TargetApi.inFan(caster, 3.0, 60.0, true,
        TargetApi.affects(caster, Faction.ENEMY, false));
```

### 准星选取

| 方法 | 说明 |
| --- | --- |
| `pickEntity(seeker, range, faction, blockedByOthers)` | 准星 `range` 格内选一个 `faction` 的生物;没有为 null。`blockedByOthers` 为 true 时挡在中间阵营不对的生物也算挡住。 |
| `pickPoint(seeker, range)` | 准星 `range` 格内选一个落点;没有为 null。 |
| `aimPoint(seeker, partialTick, ignore)` | 瞄准点:视线 64 格(`TargetApi.AIM_RANGE`)内先碰到的方块或实体上的那一点;什么也没碰到时是视线上 64 格那一点。`ignore` 是不算的实体(一般是弹射物、射线自己),可以为 null。两端都能调。 |
| `inReach(holder, target, range)` | 还够不够得着:活着、没被移除、同维度、离 `holder` 不超过 `range` 格(0 = 不看距离)。 |

三种选取都**不穿墙**:方块按碰撞箱算,流体不挡。

**选人**(`pickEntity`,指向型用它):

- 瞄准辅助:目标的碰撞箱放大 0.3 格(`TargetApi.AIM_ASSIST`)再判断。
- 排除自己、死的、旁观者、盔甲架、不可选中的实体,以及自己的坐骑和同乘一个载具的。
- 打中多部件实体的部件时算到本体上。
- 视线上由近到远找第一个阵营对的;阵营不对的默认穿过去。

**选落点**(`pickPoint`,地点型用它):

- 指着生物:取它脚下的位置,不分敌我。
- 指着方块:从方块面上退出来一点,再往下找地面。指着墙、天花板也能落到下面的地上。
- 指着天、超出射程:取视线上射程末端那一点,往下找地面。
- 往下最多找 `range` 格,找不到地面就没有落点。

### 当前锁定

声明式的选取(法术、`TargetingItem`)背后是这几个方法,自己做一套「开始 → 持续 → 生效」的动作时也可以直接用(见 [9](#9-当前锁定))。都只在服务端用。

| 方法 | 说明 |
| --- | --- |
| `getLock(holder)` | 实体身上的当前锁定;没有为 null。 |
| `begin(holder, targeting, given, owner, onLost)` | 按声明开始一次锁定,写进当前锁定,顶掉旧的。没有目标时返回 null,不写。 |
| `resolve(holder, lock)` | 生效时定下这次作用到谁,写回 `lock.target()` / `lock.point()`。没有可作用的返回 false。 |
| `resolve(holder)` | 物品生效时用:当前锁定定下来后返回;没有锁定、或者没有可作用的为 null。 |
| `release(holder, owner)` | 放掉 `owner` 的锁定;当前锁定不是它的就不动。 |
| `givenTarget(holder)` | AI 直接给的对象(还在当前锁定里);没有为 null。 |
| `aimEntity(holder)` | 方向型要瞄的生物:AI 直接给的对象,没有时生物是自己的攻击目标;玩家为 null(按准星)。 |

`begin` 的参数:

| 参数 | 说明 |
| --- | --- |
| `given` | AI 直接给的对象,可以为 null。给了就只查够不够得着(活着、同维度、射程内),不查阵营,之后按开始时锁定处理;地点型落在它当时的脚下。 |
| `owner` | 这次锁定的来源,任意对象。放锁时要是同一个对象才放。 |
| `onLost` | 可以为 null。开始时锁定的目标失效、或者锁定被别的来源顶掉时调,来源在这里取消自己的动作。 |

`begin` 返回 null 的情况:指向型没指着且没声明 `orSelf()`、地点型没有落点、AI 给的对象够不着。

---

## 9. 当前锁定

`TargetLock` 是实体身上的附加数据 `ibx_lib:target_lock`:一次施法或一次物品使用选中的目标 / 落点。

- 只在服务端有,不存盘、不同步。
- 一个实体一次只有一个,新的顶掉旧的,旧的来源收到 `onLost`。
- 建、查、放都走 `TargetApi`,不能直接往附加数据里写。

| 方法 | 说明 |
| --- | --- |
| `targeting()` | 这次锁定的声明。 |
| `mode()` | 这次用的选取时机;AI 直接给对象时恒为 `TargetMode.START`。 |
| `owner()` | 谁锁的。法术施法时是那次的 `CastContext`。 |
| `isGiven()` | 对象是不是 AI 直接给的。 |
| `target()` | 指向型(以及给了对象的其他类型)现在作用到的生物;没有为 null。 |
| `point()` | 地点型现在的落点;没有为 null。 |
| `locked()` | 开始时锁定下是锁定的那个;生效时选取下是后备。 |

`target()` / `point()` 的含义跟选取时机走:开始时锁定下就是锁定的那个;生效时选取下是「现在生效会作用到的」那个(准星指着的,没指着就是后备)。

### TargetHandler 自动做的事

有锁定的实体,`TargetHandler` 每 tick(服务端)替它做:

- **校验和重选**:开始时锁定的查目标还有没有效,失效就移除锁定、调 `onLost`;生效时选取的丢掉失效的后备,按准星重选。
- **发提示**:把现在该画的提示发给看得见这个实体的玩家和它自己(是玩家时)。锁定活过一 tick 之后才发;变了就发,没变也每 20 tick 重发一次,中途走进视野的玩家也能收到。锁定没了就通知客户端不画。
- **物品的锁定**:使用者不再使用那件物品时自动放掉。

另外它监听开始使用物品的事件,替 `TargetingItem` 选目标(见 [7](#7-在物品里使用))。`TargetHandler.message(holder, key)` 给玩家发一条动作栏提示(不是玩家时什么也不做)。

### 直接使用

不走法术、物品,自己的动作也可以借这一套校验和提示,比如生物的一招蓄力横扫:

```java
// 开始蓄力:面前 6 格、左右各 45 度的红色扇形,跟着朝向转
TargetApi.begin(mob, Targeting.self().fan(6.0F, 45.0F, Faction.ENEMY), null, this, null);

// 蓄力结束:结算,放掉锁定
for (LivingEntity target : TargetApi.inFan(mob, 6.0, 45.0, false, TargetApi.affects(mob, Faction.ENEMY, false))) {
    // ……
}
TargetApi.release(mob, this);
```

一个实体一次只有一个锁定:这期间它再放要锁定的法术(或者反过来),后来的会顶掉先前的,先前的来源收到 `onLost`。只想在某个位置放一个限时的预警,用 `api.GroundWarnings`(见 [视觉效果](EFFECT.md))。

---

## 10. 读条提示与客户端

### 画什么

提示(`TargetIndicator`)由服务端从当前锁定算出来,客户端照着画。玩家看到的:

| 声明 | 画什么 | 颜色按 |
| --- | --- | --- |
| `unit(faction)` | 现在选中的目标脚下一块方形,每边比碰撞箱多 0.3 格,跟着目标走。 | `faction` |
| `point()` + `circle(...)` | 落点上一个声明半径的圆。 | `circle` 的 `faction` |
| `point()`,没写 `circle` | 落点上一个半径 0.5 格的小圆,只标位置。 | `Faction.ALL` |
| `self()` + `circle` / `fan` / `rect` | 跟着施法者走的圆 / 扇形 / 长条,扇形和长条跟着朝向转。 | 范围的 `faction` |
| `self()`,没写范围;`direction()` | 不画。 | — |
| 任何声明 + `hidden()` | 不画。 | — |

- 形状中间半透明填色、边框稍深,贴着地形起伏,自发光,从上从下都看得见。
- 地点型只认 `circle`,指向型、方向型写了范围也不画。
- 「生效时选取」下提示每 tick 跟着准星变,画的就是现在生效会作用到的目标 / 落点。
- 瞬发的动作不画(锁定活不过一 tick)。
- 提示挂着的实体对本机玩家隐身时不画。

`TargetIndicator` 是一个 record(`shape`、`entityId`、`point`、`radius`、`halfAngle`、`ahead`、`width`、`color`),`Shape` 有 `FOOTPRINT` / `CIRCLE` / `FAN` / `RECT`,工厂方法 `footprint` / `circleAt` / `circleOn` / `fanOn` / `rectOn`。它由 `TargetHandler` 生成和发送,一般不用自己建。

### 颜色和开关

颜色按**效果作用的阵营**,开关按**施法者对看的人来说是谁**,都是客户端配置(`config/ibx_lib-client.toml`),每个玩家自己定。

| 配置项 | 默认 | 作用 |
| --- | --- | --- |
| `targetColorAlly` | `#55D16A` | 作用于友方的提示的颜色。 |
| `targetColorEnemy` | `#E5484D` | 作用于敌方的提示的颜色。 |
| `targetColorAll` | `#F2C230` | 敌我都作用的、只标落点的提示的颜色。 |
| `showTargetSelf` | `true` | 显示自己的提示。 |
| `showTargetPlayerAlly` / `showTargetPlayerEnemy` | `true` | 显示友方 / 敌方玩家的提示。 |
| `showTargetMobAlly` / `showTargetMobEnemy` | `true` | 显示友方 / 敌方生物的提示。 |
| `targetUnitLockAtStart` / `targetPointLockAtStart` | `false` | 指向型 / 地点型的选取时机(见 [5](#5-选取时机))。 |

配置项的详细说明见 [配置](CONFIG.md)。

「友方 / 敌方」是相对看的那个玩家来说的,由服务端按 `TargetApi.canHarm(看的人, 施法者)` 逐个玩家算好发下去。所以同一个提示,不同玩家可能一个看得见、一个看不见,颜色也可以各不相同。

### 服务端与客户端

- **选取只在服务端**。客户端不自己选目标、不预测,地上的提示是服务端选好之后发来的,所以画出来的就是服务端要作用的那个。
- 客户端开始施法时只判断正在施法、冷却、法力,不知道有没有目标。服务端因为没有目标拒绝时,会通知客户端把举起的手放下来。
- 两个选取时机的设置存在客户端,由客户端发给服务端(见 [5](#5-选取时机))。
- 当前锁定不同步,`ctx.target()` / `ctx.targetPos()` 在客户端恒为 null(见 [6](#6-在法术里使用))。

### GroundShapes

`ibx.lib.client.ground.GroundShapes`(只在客户端)是画这些形状的工具,读条提示和地面预警都用它,所以看着是同一套。要自己在客户端画同样风格的形状时可以直接用。

| 成员 | 说明 |
| --- | --- |
| `color(faction)` | 客户端配置里这个阵营的颜色(RGB)。`ALL`、`NONE` 都取 `targetColorAll`。 |
| `fill(rgb)` / `edge(rgb)` | 换成填充色 / 边框色(带不透明度的 ARGB,边框更深、更不透明)。 |
| `sector(out, level, base, yaw, radius, halfAngle, fill, edge, origin)` | 把扇形切成小四边形加进 `out`。`yaw` 是面朝方向(原版的 yRot,换成弧度),`halfAngle` 也是弧度,等于 π 时是整圆。半径不超过边框宽度(0.12 格)时不画。 |
| `rect(out, level, base, ux, uz, halfU, halfV, fill, edge, origin)` | 长方形:中心 `base`,`(ux, uz)` 是长边方向的单位向量,沿它半长 `halfU`、沿垂直方向半长 `halfV`。 |
| `groundY(level, x, z, nearY)` | 这一列在 `nearY` 附近的地面高度:在方块里就往上找,最多 2 格;悬空就往下找,最多 4 格;都找不到按 `nearY`。 |
| `surfaceBlock(level, bx, bz, baseY)` | 这一列在 `baseY` 附近露天的那块方块;两格以上的墙、坑返回 null。 |
| `submit(collector, poseStack, quads)` | 把切好的四边形画出来。 |
| `Quad` / `RENDER_TYPE` | 一个四边形(坐标相对 `origin`)/ 用的渲染类型。 |

`base.y` 是找地面的参考高度,每个顶点都会各自贴到地面上。`origin` 是坐标的原点,要和 `submit` 时 `poseStack` 的原点一致;lib 自己的做法是抽取世界渲染状态时用镜头位置切好,提交自定义几何时画:

```java
private static final ContextKey<List<GroundShapes.Quad>> QUADS =
        new ContextKey<>(Identifier.fromNamespaceAndPath("examplemod", "ground_shapes"));

private static void onExtract(ExtractLevelRenderStateEvent event) {
    Vec3 camera = event.getCamera().position();
    List<GroundShapes.Quad> quads = new ArrayList<>();
    int rgb = GroundShapes.color(Faction.ENEMY);
    // center:要画的位置;半径 4 格的整圆
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

两个监听都注册在 `NeoForge.EVENT_BUS` 上。要让服务端指定位置、所有人都看得见的预警,用 `api.GroundWarnings`(见 [视觉效果](EFFECT.md))。
