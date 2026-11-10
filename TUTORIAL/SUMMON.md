# 召唤物

**简体中文** | [English](SUMMON_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

召唤物框架让一个生物(玩家或 Boss)成为另一批生物的**主人**。把一只 `Mob` 交给 `SummonApi.summon`,它就成了召唤物,下面这些事由框架自动处理:

- **名额**:主人的属性 `ibx_lib:max_summons` 是总名额,每只召唤物占几个在召唤时给定;名额不够时最早召唤的先消散。
- **寿命**:默认常驻。主人死亡、下线、找不到时全部消散;主人换维度时跟着搬过去。
- **友伤**:召唤物和主人、同一主人的召唤物之间互相打不到;玩家的召唤物按「玩家打玩家」的规则判断。
- **标记目标**:主人打中谁,召唤物就优先打谁。
- **击杀归属**:主人是玩家时,召唤物打死的生物算主人杀的。
- **换主人**:可以把召唤物转给别的主人。
- **解散**:玩家身上有召唤物时,法术列表里自动多一个「解散召唤物」。

各部分在哪:

| 位置 | 内容 |
| --- | --- |
| `ibx.lib.api.SummonApi` | 入口:召唤、离场、查询、换主人、标记目标、身板快照、找落点(见 [4](#4-summonapi)) |
| `ibx.lib.summon.Summoned` | 召唤物实体实现的接口(见 [5](#5-召唤物实体)) |
| `ibx.lib.summon.goal` | 召唤物的 AI 目标(见 [6](#6-ai-目标)) |
| `Spell.props().summonCount(...)` 等 | 把法术声明成召唤法术(见 [7](#7-召唤法术)) |
| `ibx.lib.api.event.SummonEvent` | 召唤、换主人、离场的事件(见 [8](#8-事件)) |
| `ibx.lib.summon.SummonData` / `OwnerSummons` | 存在召唤物和主人身上的数据(见 [9](#9-附加数据)) |

规则由 `ibx.lib.summon.SummonHandler` 监听事件来执行,本模组加载时已经注册,不需要自己调。整个框架只在服务端生效。

> 不在本篇里的:法术系统本身见 [SPELL.md](SPELL.md),属性列表见 [ATTRIBUTE.md](ATTRIBUTE.md),选目标和敌我判断(`TargetApi`)见 [TARGETING.md](TARGETING.md),召唤物相关的指令见 [COMMAND.md](COMMAND.md)。召唤物框架没有自己的配置项,模组的配置见 [CONFIG.md](CONFIG.md)。

---

## 2. 快速上手

做一个召唤法术要三样东西:一个召唤物实体、一个法术类、一行法术注册。下面的例子召出一只「灵犬」,生命和攻击按施法者的法强算。

**召唤物实体**:实现 `Summoned`,重写 `considersEntityAsAlly` 和 `getTeam`(都转给 `SummonApi`),在 `registerGoals` 里加上框架的目标。

```java
public class SpiritHoundEntity extends PathfinderMob implements Summoned {
    public SpiritHoundEntity(EntityType<? extends SpiritHoundEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    // 基础值;生命和攻击在召唤时被法术算的值盖掉
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
        // 离主人 6 格以上开始跟,走到 2.5 格以内停
        this.goalSelector.addGoal(3, new SummonFollowOwnerGoal(this, 1.2, 6.0F, 2.5F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // 主人标记的 → 打主人的 → 打自己的 → 主人是生物时抄主人的目标
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

实体类型和属性照常注册(渲染器也要自己注册,这里省略):

```java
public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities("examplemod");
public static final DeferredHolder<EntityType<?>, EntityType<SpiritHoundEntity>> SPIRIT_HOUND = ENTITIES.registerEntityType(
        "spirit_hound", SpiritHoundEntity::new, MobCategory.MISC,
        builder -> builder.sized(0.6F, 0.85F).noLootTable().clientTrackingRange(8));

// EntityAttributeCreationEvent(模组事件总线)
event.put(SPIRIT_HOUND.get(), SpiritHoundEntity.createAttributes().build());
```

**法术**:在 `onCast` 里 `create` 实体、摆好位置、用 `setStat` 写好身板,最后交给 `SummonApi.summon`。名额和时长由注册行的数值决定。

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
            // 要在主人所在的世界里 create
            SpiritHoundEntity hound = ExampleEntities.SPIRIT_HOUND.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (hound == null) return;
            // 主人面前左右各 60 度、1.5 ~ 3 格的地方
            Vec3 pos = SummonApi.findSpawnPosInFront(caster, hound, 1.5, 3.0, 60.0);
            hound.snapTo(pos.x, pos.y, pos.z, caster.getYRot(), 0.0F);
            // 按施法者此刻的法强算好,之后主人换装备不变
            SummonApi.setStat(hound, Attributes.MAX_HEALTH, 20.0 + 1.0 * spellPower);
            SummonApi.setStat(hound, Attributes.ATTACK_DAMAGE, 4.0 + 0.3 * spellPower);
            SummonApi.summon(caster, hound, this);
        }
    }
}
```

**注册行**:写了 `summonCount` 就是召唤法术。

```java
public static final DeferredRegister<Spell> SPELLS = DeferredRegister.create(SpellRegistries.SPELL_KEY, "examplemod");
public static final DeferredHolder<Spell, SummonSpiritHoundSpell> SUMMON_SPIRIT_HOUND = SPELLS.register("summon_spirit_hound",
        () -> new SummonSpiritHoundSpell(Spell.props().manaCost(30).cooldown(10.0F).castTime(1.5F).summonCount(1).summonCost(1)));
```

**不通过法术召唤**(比如 Boss 的招式):直接调五个参数的 `summon`,法术填 `null`。生物当主人时默认名额也是 1,要多召就在它的属性里把 `ibx_lib:max_summons` 调高。

```java
// Boss 的属性
.add(AttributeRegistry.MAX_SUMMONS, 4.0)

// 招式里:不是法术召的、常驻、占 1 个名额
SummonApi.summon(boss, minion, null, 0, 1);
```

---

## 3. 规则

### 名额

- 总名额是主人的属性 `ibx_lib:max_summons`(`AttributeRegistry.MAX_SUMMONS`,译名「召唤物上限」)向下取整。默认 1,范围 0 ~ 1024,所有生物都有这个属性。
- 每只召唤物占几个名额在召唤时给定:法术召的是注册行的 `summonCost`(默认 1),直接调 `summon` 的是参数 `cost`。可以是 0,不占名额。
- 已用名额是主人名单里所有召唤物的名额之和,暂时没加载的也算。
- 召唤时名额不够,就从**最早召唤的**开始挤掉(离场原因 `REPLACED`),直到放得下。
- 单只的名额就超过总名额时召不出来,`summon` 返回 `false`,不会挤掉任何召唤物。
- 上限降低(比如脱下加上限的装备)不会让已有的召唤物马上消散,下次召唤时才按新的上限挤。

### 寿命与离场

- **常驻**:默认没有时限。召唤时会给实体设上「不自然消失」。
- **限时**:`summon` 的 `durationTicks` 大于 0,或法术注册行写了 `summonDuration`,就是限时召唤物,到点以 `EXPIRED` 离场。到期时刻按世界时刻算,没加载的时间也算。
- **主人死亡**:全部离场(`OWNER_DIED`)。
- **玩家主人下线**:全部离场(`OWNER_LEFT`)。
- **找不到主人**:召唤物每秒查一次主人。连续 3 次找不到(主人被移除、所在区块没加载),或者主人的名单里已经没有它,就离场(`OWNER_GONE`)。
- **主人换维度**:召唤物被搬到主人身边 1.5 ~ 3 格的地方。跨维度后是一个新的实体对象(UUID 和附加数据不变),所以不要长期拿着 `Mob` 引用,用的时候再 `getSummons`。
- **召唤物自己不走传送门**:踩进下界传送门、末地传送门不会过去。
- **被打死**:死亡流程照原版走,框架只在死亡那一刻把它从主人名单里划掉(名额马上让出来)并发事件(`KILLED`)。
- **被别的代码移除**(`discard()` 等):从主人名单里划掉并发事件,离场原因记 `DISMISSED`(实体的移除原因是 `KILLED` 时记 `KILLED`)。
- **没加载的召唤物**:名单里连续 5 秒找不到的条目,在下一次 `getSummons` 时清掉。它以后加载时发现自己不在主人名单里,就自己离场(`OWNER_GONE`)。

框架让召唤物离场时(被打死除外),实现了 `Summoned` 的会调它的 `onUnsummon`,没实现的冒一股烟后移除。离场原因见 [8](#8-事件)。

### 友伤

以下伤害直接取消(看伤害来源的实体,所以召唤物射出的弹射物也算):

- 召唤物打自己的主人;
- 召唤物打同一主人的其他召唤物;
- 主人打自己的召唤物。

主人是玩家时,召唤物还**算作主人本人**,按玩家打玩家判断,两个方向都管:服务器关了 PvP、或者两名玩家同队且队伍关了友伤时,

- 别的玩家(和他的召唤物)打不到你的召唤物;
- 你的召唤物也打不到那名玩家和他的召唤物。

主人是生物时没有这一条。

`Summoned` 继承原版的 `OwnableEntity`,主人取自框架数据,所以按 `getRootOwner()` 认主人的判断(包括本模组的 `TargetApi.canHarm`,见 [TARGETING.md](TARGETING.md))会自动把召唤物归到主人那边。

召唤物的 AI 不会把自己人选成目标,靠的是实体类里重写的 `considersEntityAsAlly` 和 `getTeam`(见 [5](#5-召唤物实体))。

### 标记目标

- 主人对别的生物造成了伤害(真的扣到血、不是自己、不是自己的召唤物),并且身上有召唤物时,那个生物成为主人的**标记目标**。
- 每打中一次重新计时,最近一次打中后 10 秒(`SummonApi.MARK_TICKS` = 200 tick)内有效。
- 目标死了、不见了、和主人不在同一个维度时标记失效。
- 加了 `SummonMarkedTargetGoal` 的召唤物优先打标记目标,标记换了就跟着换。
- 也可以自己调 `SummonApi.mark(owner, target)`。
- 标记目标不存盘,也不同步到客户端。

### 击杀归属

主人是玩家时,召唤物每次打中目标,目标都会记成「最近被这名玩家打过」(100 tick,同原版)。所以召唤物打死的生物掉经验、掉「被玩家击杀」才有的战利品,也计入玩家的统计。主人是生物时没有这一条。

### 换主人

`SummonApi.transferOwner(summon, newOwner)` 把召唤物转给新主人,转完它就是新主人的召唤物:跟着新主人、帮新主人打,友伤、队伍、标记目标、击杀归属都按新主人算。

- 从原主人名单里划掉,加到新主人名单的末尾(算新主人最新召的)。占的名额、召它的法术、到期时刻、`setStat` 写的身板都不变。
- 要占新主人的名额:新主人剩下的名额放不下它就转不了,不会挤掉新主人已有的召唤物。
- 新主人不能是召唤物,也不能是它自己;新主人和召唤物都要活着。
- 转之前发可取消的 `SummonEvent.Transfer`;不发 `Pre` / `Post` / `Unsummon`。
- 转完清掉召唤物手上的攻击目标和「最近被谁打」的记录,下一刻按新主人重新找目标。

### 身板

召唤物的强度在召唤那一刻定下来:法术按施法者当时的属性算出数值,用 `SummonApi.setStat` 写成召唤物自己的属性,之后主人换装备不影响已经召出来的。

`setStat(summon, attribute, value)` 在这个属性上挂一个固定 id(`ibx_lib:summon_snapshot`)的加值修饰符,补上 `value` 和基础值的差额:

- 重复调用会覆盖上一次的;
- 这个属性上没有别的修饰符时,最终值就是 `value`;有别的加成(装备、药水)时叠在 `value` 上面;
- 改的是最大生命时,顺手把血回满;
- 修饰符随实体存盘。

---

## 4. SummonApi

`ibx.lib.api.SummonApi`,全是静态方法。会改东西的方法只在服务端生效;在客户端,召唤物数据不同步,`data` / `isSummon` / `ownerId` 查不到东西(见 [9](#9-附加数据))。

**查询**

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `data(entity)` | `SummonData` | 实体身上的召唤物数据;不是召唤物返回 `SummonData.NONE`。 |
| `isSummon(entity)` | `boolean` | 是不是召唤物。 |
| `ownerId(entity)` | `UUID`,可为 null | 主人的 UUID;不是召唤物为 null。 |
| `getOwner(summon)` | `LivingEntity`,可为 null | 主人,在服务端所有维度里找;不是召唤物、主人没加载时为 null。 |
| `isOwnedBy(summon, owner)` | `boolean` | `summon` 是不是 `owner` 的召唤物。 |
| `capacity(owner)` | `int` | 总名额(`ibx_lib:max_summons` 向下取整)。两端都能用。 |
| `used(owner)` | `int` | 已用名额,没加载的召唤物也算。客户端只有玩家自己的数据。 |
| `fits(owner, cost)` | `boolean` | 占 `cost` 个名额的召唤物能不能召。只看总名额,不看已用(满了会挤掉最早的)。两端都能用。 |
| `getSummons(owner)` | `List<Mob>` | 主人在场(已加载)的召唤物,最早召唤的在前。顺手清掉名单里连续 5 秒找不到、或者已经换了主人的条目。客户端返回空列表。 |

**召唤**

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `summon(owner, summon, spell)` | `boolean` | 按法术注册行的 `summonCost` 和 `summonDuration` 召唤(没写时长 = 常驻)。召唤法术的 `onCast` 里用这个。 |
| `summon(owner, summon, spell, durationTicks, cost)` | `boolean` | 把 `summon` 作为 `owner` 的召唤物放进主人所在的世界。`spell` 不是法术召的填 null;`durationTicks` 小于等于 0 = 常驻;`cost` 最少 0。 |

调 `summon` 之前,召唤物要已经 `create` 好(**在主人所在的世界里**)、摆好位置、写好身板,但还没放进世界——放进世界由 `summon` 来做。它依次:检查单只的名额不超过总名额 → 发 `SummonEvent.Pre` → 名额不够就挤掉最早的 → 写上召唤物数据、设成不自然消失、放进世界 → 记进主人名单 → 发 `SummonEvent.Post`。

返回 `false`(没召出来)的情况:在客户端调、召唤物和主人不在同一个世界、单只的名额超过总名额、`Pre` 被取消、实体放不进世界。

**离场**

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `dismiss(summon, reason)` | — | 让一只召唤物离场:从主人名单里划掉 → 发 `SummonEvent.Unsummon` → 调它的 `Summoned.onUnsummon`(没实现 `Summoned` 的冒烟移除)。 |
| `dismissAll(owner, reason)` | `int` | 让主人的召唤物全部离场,并清空名单和标记目标。返回送走了几只;没加载的直接从名单里划掉,不算数、不发事件。 |
| `forget(summon, reason)` | — | 只从主人名单里划掉,不移除实体;真的划掉了才发 `SummonEvent.Unsummon`。框架在召唤物被打死、被别的代码移除时自己调,一般用不到。 |

**换主人**

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `transferOwner(summon, newOwner)` | `boolean` | 把召唤物转给 `newOwner`,规则见 [3](#3-规则)。不是召唤物、已经是新主人的、新主人死了或自己也是召唤物、新主人名额不够、`SummonEvent.Transfer` 被取消时返回 `false`。 |

**自己人、队伍**(给召唤物实体类重写用,见 [5](#5-召唤物实体))

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `isAlly(summon, other)` | `boolean` | 召唤物把 `other` 当不当自己人:主人、同一主人的其他召唤物、主人的自己人(主人的宠物、队友等)。别人的召唤物一律不算。 |
| `ownerTeam(summon)` | `PlayerTeam`,可为 null | 主人的队伍;找不到主人为 null。 |

**标记目标**

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `mark(owner, target)` | — | 把 `target` 设成主人的标记目标,重新计时。 |
| `markedTarget(owner)` | `LivingEntity`,可为 null | 主人当前的标记目标;过期、死了、不见了、不在同一维度时清掉并返回 null。 |

**身板、落点**

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `setStat(summon, attribute, value)` | — | 把召唤物的某个属性定成 `value`,规则见 [3](#3-规则)。召唤物没有这个属性时什么也不做。 |
| `findSpawnPos(owner, summon, minRadius, maxRadius)` | `Vec3` | 在主人身边 `minRadius` ~ `maxRadius` 格的一圈里找个能站的地方:脚下的方块顶面是实的、身子不卡进方块、不泡在水或岩浆里。随机试 16 次,找不到就是主人脚下。只算位置,不摆实体。 |
| `findSpawnPos(level, center, summon, minRadius, maxRadius)` | `Vec3` | 同上,以任意一点为中心;找不到返回 `center`。 |
| `findSpawnPosInFront(owner, summon, minRadius, maxRadius, halfAngle)` | `Vec3` | 只在主人面前的扇形里找(面朝方向左右各 `halfAngle` 度),而且主人的眼睛到那里不隔方块,召唤物不会冒在墙后、地板下。面前找不到再在身边一圈找,还找不到就是主人脚下。 |

**常量**

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `SNAPSHOT_MODIFIER_ID` | `ibx_lib:summon_snapshot` | `setStat` 挂的属性修饰符的 id。 |
| `MARK_TICKS` | `200` | 标记目标的有效时间(tick)。 |

---

## 5. 召唤物实体

`ibx.lib.summon.Summoned` 是召唤物实体实现的接口,继承原版的 `OwnableEntity`。

| 方法 | 说明 |
| --- | --- |
| `getOwnerReference()` | 默认实现:从框架数据里取主人。有了它,`OwnableEntity` 的 `getOwner()`、`getRootOwner()` 就能用,不用自己存主人。 |
| `onUnsummon(reason)` | 框架让它离场时调(到期、被挤掉、解散、主人死亡 / 下线 / 找不到),只在服务端调,被打死不走这里。默认冒一小股烟(`POOF` 粒子)再 `discard()`。 |

要自己的离场效果就重写 `onUnsummon`,在里面放粒子、声音,最后 `discard()`。方法返回时实体如果还没移除,框架会替你移除,所以这里不能留着实体播一段动画。

```java
@Override
public void onUnsummon(UnsummonReason reason) {
    if (this.level() instanceof ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
    }
    this.discard();
}
```

接口盖不了实体类自己的方法,实现类**还要重写两处**,都转给 `SummonApi`:

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

- `considersEntityAsAlly`:让原版的 `isAlliedTo` 把主人、同伴、主人的自己人(主人的宠物、队友等)认成自己人。原版的目标选择靠它跳过自己人。
- `getTeam`:召唤物自己不在队伍里时算在主人的队伍里,队伍颜色、队伍友伤规则跟主人走。

**不实现 `Summoned` 的生物**也能交给 `SummonApi.summon`(参数类型是 `Mob`):名额、寿命、跟随换维度、[3](#3-规则) 里的友伤、击杀归属照样有,离场时冒烟移除。但它不是 `OwnableEntity`,按 `getRootOwner()` 认主人的判断认不出它,也没有上面两处重写,AI 目标要自己处理。新写的召唤物都应该实现 `Summoned`。

---

## 6. AI 目标

包 `ibx.lib.summon.goal`。召唤物**不主动找怪打**,跟着主人动手。在实体的 `registerGoals` 里加:

```java
// 移动:加进 goalSelector
this.goalSelector.addGoal(3, new SummonFollowOwnerGoal(this, 1.2, 6.0F, 2.5F));
// 选目标:一次加齐,从优先级 1 开始占 4 个
SummonGoals.addTargetGoals(this, this.targetSelector, 1);
```

`SummonGoals.addTargetGoals(mob, targetSelector, first)` 要求 `mob` 是 `PathfinderMob`,从优先级 `first` 起依次加四个目标:

| 优先级 | 目标 | 说明 |
| --- | --- | --- |
| `first` | `SummonMarkedTargetGoal` | 打主人的标记目标。 |
| `first + 1` | `SummonOwnerHurtByTargetGoal` | 打打了主人的。 |
| `first + 2` | 原版 `HurtByTargetGoal` | 打打了自己的(不打自己人)。 |
| `first + 3` | `SummonCopyOwnerTargetGoal` | 主人是生物时,抄主人的攻击目标。 |

只想要其中几个,就自己一个个 `addGoal`。各个目标:

| 类 | 构造 | 行为 |
| --- | --- | --- |
| `SummonFollowOwnerGoal` | `(mob, speedModifier, startDistance, stopDistance)` | 跟着主人走:离主人 `startDistance` 格以上开始跟,走到 `stopDistance` 格以内停;离主人 12 格以上时瞬移到主人身边 2 ~ 3 格。有攻击目标时不跟;主人在别的维度、是旁观者时不跟。跟随期间不避水。照原版驯服动物的跟随,但不要求是驯服动物。 |
| `SummonMarkedTargetGoal` | `(mob)` | 把攻击目标设成主人的标记目标(`SummonApi.markedTarget`),标记换了就换过去。放在目标选择器里优先级最高的位置。 |
| `SummonOwnerHurtByTargetGoal` | `(mob)` | 主人被打时,把攻击目标设成打主人的那个生物。照原版 `OwnerHurtByTargetGoal`。 |
| `SummonCopyOwnerTargetGoal` | `(mob)` | 主人是 `Mob` 时,把攻击目标设成主人的攻击目标。主人是玩家时不起作用。 |

三个选目标的目标都用原版默认的战斗条件过滤:不选自己人、不选打不了的(创造模式玩家等),选的那一刻要看得见目标;选中之后看不见了也不放弃。

---

## 7. 召唤法术

法术通过注册行(`Spell.props()`)的三个数值声明自己是召唤法术。法术系统的其他内容见 [SPELL.md](SPELL.md)。

| 注册行 | 默认 | 说明 |
| --- | --- | --- |
| `summonCount(int)` | `0` | 一次召几只。**写了(大于 0)就是召唤法术。** |
| `summonCost(int)` | `1` | 每只占几个名额,最少 0。 |
| `summonDuration(float)` | `0` | 召唤物的寿命(秒),0 = 常驻。 |

法术类里对应的读取方法:`isSummon()`、`summonCount()`、`summonCost()`、`summonDuration()`。

写了 `summonCount` 之后框架替你做的:

- **施法前查名额**:开始施法时,如果 `summonCost` 超过施法者的总名额,这次施法失败,不扣蓝、不进冷却,玩家在 action bar 看到「召唤物上限不够」(`message.ibx_lib.cast.summon_over_capacity`)。
- **说明框多几行**:法术轮盘的说明里多一行「召唤 N 只,每只占 M 个名额」;写了 `summonDuration` 再多一行「持续 X 秒」;`summonCost` 超过总名额时多一行「召唤物上限不够」的提示。

**召唤本身要自己写**在 `onCast` 里:循环 `summonCount()` 次,每只 `create`、摆位置、`setStat`,再调 `SummonApi.summon(caster, mob, this)`(见 [2](#2-快速上手))。`summon` 按这个法术的 `summonCost` 和 `summonDuration` 记名额和时长。`CastContext` 没有召唤专用的方法。

注意:

- 施法前查的是**单只**的名额,不是 `summonCount × summonCost`。一次召多只而名额不够时,后召的会把先召的挤掉。
- `summon` 可能返回 `false`(比如被 `SummonEvent.Pre` 取消),之后要给这只召唤物放粒子、音效的话先看返回值。
- 强度要在说明框里显示(「生命 40」之类),重写法术的 `getTooltip(caster)`。

### 解散召唤物

本模组自己注册了一个法术 `ibx_lib:dismiss_summons`(`SpellRegistry.DISMISS_SUMMONS`,类 `DismissSummonsSpell`):瞬发、不耗蓝、冷却 1 秒,让施法者的召唤物全部离场(`DISMISSED`)。

- 它不来自任何物品:玩家身上有召唤物时,框架自动把它加到法术列表末尾;没有召唤物就不出现。
- 没有召唤物时放不出来,提示「没有召唤物」(`message.ibx_lib.cast.no_summons`)。
- 选法术界面里不列它,不能固定进物品。

---

## 8. 事件

`ibx.lib.api.event.SummonEvent`,只在服务端、发在 `NeoForge.EVENT_BUS` 上。都带 `getOwner()` 和 `getSummon()`(`Mob`)。

| 事件 | 可取消 | 时机 | 能读到的 |
| --- | --- | --- | --- |
| `SummonEvent.Pre` | 是 | 召唤前:单只名额检查已过,还没挤掉旧的,召唤物还没放进世界。取消 = 这次不召,`SummonApi.summon` 返回 `false`。 | `getSpell()`:召它的法术,不是法术召的为 null。 |
| `SummonEvent.Post` | 否 | 召唤物已经放进世界、记进主人名单。 | — |
| `SummonEvent.Transfer` | 是 | 换主人前。取消 = 这次不转,`transferOwner` 返回 `false`。 | `getOwner()` 是原主人(找不到时为 null),`getNewOwner()` 是要转给的。 |
| `SummonEvent.Unsummon` | 否 | 召唤物离场:框架让它走,或者被打死、被别的代码移除。这时它已经从主人名单里划掉;框架送走的还没移除,被打死的刚进死亡流程,被别的代码移除的已经移除了。 | `getReason()`:离场原因。`getOwner()` 在主人已经找不到时为 null。 |

`Pre` 和 `Post` 里的 `getOwner()` 不会是 null。

离场原因 `ibx.lib.summon.UnsummonReason`:

| 值 | 什么时候 |
| --- | --- |
| `EXPIRED` | 限时召唤物到点了。 |
| `REPLACED` | 名额不够,被新召的挤掉。 |
| `DISMISSED` | 主动解散:「解散召唤物」法术、指令、自己的代码调 `dismiss`;也包括被别的代码直接移除。 |
| `OWNER_DIED` | 主人死了。 |
| `OWNER_LEFT` | 玩家主人下线。 |
| `OWNER_GONE` | 找不到主人,或者主人的名单里已经没有它。 |
| `KILLED` | 被打死。框架不管它的消散(原版死亡流程照走),只划名单、发事件,不调 `onUnsummon`。 |

```java
// 召唤物被新召的挤掉时做点什么
NeoForge.EVENT_BUS.addListener((SummonEvent.Unsummon event) -> {
    if (event.getReason() == UnsummonReason.REPLACED && event.getOwner() instanceof Player player) {
        // ...
    }
});
```

---

## 9. 附加数据

框架用两份附加数据(Data Attachment)记状态,注册在 `ibx.lib.registry.AttachmentRegistry`。**读写都走 `SummonApi`**,这里只说明存了什么、同步给谁。

| 附加数据 | 挂在 | 类型 | 存盘 | 同步 |
| --- | --- | --- | --- | --- |
| `ibx_lib:summon`(`SUMMON`) | 召唤物 | `SummonData` | 是 | 不同步 |
| `ibx_lib:owner_summons`(`OWNER_SUMMONS`) | 主人 | `OwnerSummons` | 名单存盘,标记目标不存 | 名单只同步给主人自己的客户端,标记目标不同步 |

由此可知:

- 客户端认不出一个实体是不是召唤物、主人是谁。客户端要用到这些(比如渲染时区分)得自己同步。
- 玩家的客户端只知道自己有哪些召唤物(UUID 和名额),不知道别人的。
- 召唤物数据随实体存盘、跨维度时一起带过去,读档后照常认主人。
- 主人的名单不在死亡后保留(主人死了召唤物全部离场,名单本来就空了)。

`ibx.lib.summon.SummonData`(用 `SummonApi.data(entity)` 取):

| 方法 | 说明 |
| --- | --- |
| `isSummon()` | 有没有主人,即是不是召唤物。 |
| `owner()` | 主人的 UUID。 |
| `spell()` | 召它的法术 id;不是法术召的为 null。 |
| `cost()` | 占主人几个名额。 |
| `expiresAt()` | 到期的世界时刻(`level.getGameTime()`),-1 = 常驻。 |
| `isPermanent()` | 是不是常驻。 |

`SummonData.NONE` 是「不是召唤物」的默认值。

`ibx.lib.summon.OwnerSummons`(主人身上的名单,按召唤顺序,最早的在前):

| 方法 | 说明 |
| --- | --- |
| `isEmpty()` | 名单是不是空的。 |
| `entries()` | 名单的只读副本;每条 `Entry` 有 `id()`(召唤物 UUID)和 `cost()`(占几个名额)。 |
| `used()` | 名额之和。 |
| `contains(id)` | 名单里有没有这只召唤物。 |
| `marked()` / `markedAt()` | 标记目标的 UUID / 标记的世界时刻。 |

`add`、`remove`、`setMarked` 只给 `SummonApi` 用,不要直接调:绕过去的话名单不会同步,事件也不会发。
