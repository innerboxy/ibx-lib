# 法术系统

**简体中文** | [English](SPELL_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

ibx_lib 的法术系统把「放一个法术」拆成几块,各管各的:

- **法术**(`ibx.lib.spell.Spell`):注册行只写数值(耗蓝、冷却、吟唱时间),效果写在法术类的钩子里(见 [3](#3-法术))。
- **施法**(`SpellCasting`):唯一的入口。检查、选目标、读条、扣蓝、冷却、同步都由它做,玩家和生物走同一套(见 [4](#4-施法))。
- **法术容器**:玩家不会「学会」法术,能放的法术就是身上物品提供的那些(见 [5](#5-法术容器))。
- **法术伤害**(`SpellDamageApi`):施法瞬间把伤害算成快照,命中时结算;友伤、暴击、法术抗性、点燃 / 冰冻 / 吸血都由框架做(见 [8](#8-法术伤害))。
- **法术实体基类**:弹射物、范围效果、射线三种,只写自己不一样的部分(见 [9](#9-法术实体基类))。
- **法力**(`ManaApi`):当前值、消耗、回复(见 [10](#10-法力))。
- **事件**:施法、法术伤害、法术列表、法力都能监听和改(见 [11](#11-事件))。

所有结算只在服务端;客户端只做表现(音效、粒子、读条、施法动作)。

本文不讲的内容:

| 内容 | 文档 |
| --- | --- |
| 属性一览(法强、法术抗性、最大法力……) | [ATTRIBUTE.md](ATTRIBUTE.md) |
| 召唤物、召唤法术 | [SUMMON.md](SUMMON.md) |
| 选目标(`Targeting`、`TargetApi`,法术怎么瞄准) | [TARGETING.md](TARGETING.md) |
| 法典、法杖等物品基类 | [EQUIPMENT.md](EQUIPMENT.md) |
| 地面预警、地面起伏、换色的火 | [EFFECT.md](EFFECT.md) |
| 配置项 | [CONFIG.md](CONFIG.md) |
| 指令 | [COMMAND.md](COMMAND.md) |

---

## 2. 快速上手

注册一个吟唱法术「电花」,再注册一本自带它的法典。模组 id 用 `examplemod`。

```java
// 法术注册表:各模组用自己的 DeferredRegister
public final class ExampleSpells {
    public static final DeferredRegister<Spell> SPELLS =
            DeferredRegister.create(SpellRegistries.SPELL_KEY, "examplemod");

    // 注册行只放数值:耗蓝 12、冷却 3 秒、吟唱 0.8 秒(写了吟唱时间就是吟唱法术)
    public static final DeferredHolder<Spell, SparkSpell> SPARK = SPELLS.register("spark",
            () -> new SparkSpell(Spell.props().manaCost(12).cooldown(3.0F).castTime(0.8F)));
}
```

```java
// 法术类:只写效果
public final class SparkSpell extends Spell {
    public SparkSpell(Properties props) {
        // 准星锁一个 16 格内的敌方,没指着就放不了
        super(props, Targeting.unit(Faction.ENEMY).range(16.0));
    }

    @Override
    public void onCast(CastContext ctx) {   // 只在服务端调,蓝已经扣过
        LivingEntity target = ctx.target();
        if (target != null) {
            // 4 + 80% 法强,命中后点燃 3 秒
            SpellDamageApi.hurt(target, ctx.damage(c -> 4.0F + 0.8F * c.spellPower()).withFire(60),
                    ctx.caster(), ctx.caster(), null);
        }
    }
}
```

```java
// 法典:耐久 500、主手法强 +4、自带法术 examplemod:spark
public final class ExampleItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("examplemod");

    public static final DeferredItem<IbxTomeItem> SPARK_TOME = ITEMS.registerItem("spark_tome",
            p -> new IbxTomeItem(p, 500, 4.0F, List.of(Identifier.fromNamespaceAndPath("examplemod", "spark"))));
}

// 模组构造函数里
ExampleSpells.SPELLS.register(modEventBus);
ExampleItems.ITEMS.register(modEventBus);
```

还要准备的资源:

| 资源 | 位置 |
| --- | --- |
| 法术名 / 描述 | 语言文件 `spell.examplemod.spark`、`spell.examplemod.spark.desc` |
| 法术图标 | `assets/examplemod/textures/spell/spark.png`,16×16;没有时用占位图标 |
| 物品名、物品模型 | 同普通物品(法典的手持模型见 [EQUIPMENT.md](EQUIPMENT.md)) |

不用写标签:`IbxTomeItem` 自动进 `#ibx_lib:can_cast_spell`。进游戏后把法典拿在手上,准星对着 16 格内的敌方右键,就是吟唱「电花」。

---

## 3. 法术

### 注册

- **注册表**:`ibx_lib:spell`,键是 `SpellRegistries.SPELL_KEY`,注册表对象是 `SpellRegistries.SPELL`。同步到客户端,不能被数据包覆盖。
- **法术 id**:注册名加命名空间,例如 `examplemod:spark`。数据组件、指令、存档里都用它。
- **语言键**:按 id 推,`spell.<命名空间>.<路径>` 和 `spell.<命名空间>.<路径>.desc`。
- **标签**:注册表自带标签支持,文件放 `data/<命名空间>/tags/ibx_lib/spell/`。

lib 自己只注册了一个法术 `ibx_lib:dismiss_summons`(解散召唤物,见 [SUMMON.md](SUMMON.md)),具体法术都由内容模组注册。

### 数值 `Spell.Properties`

`Spell.props()` 新建一份数值,链式写完传给法术的构造函数。

| 方法 | 默认 | 说明 |
| --- | --- | --- |
| `manaCost(float)` | `0` | 基础耗蓝。实际消耗再乘属性 `ibx_lib:mana_cost_multiplier`。引导法术是每次生效的耗蓝。 |
| `cooldown(float 秒)` | `0` | 基础冷却。实际冷却再乘属性 `ibx_lib:spell_cooldown_ratio`。 |
| `castTime(float 秒)` | `0` | 吟唱 / 满蓄时间。实际时间再除以属性 `ibx_lib:spell_cast_speed`。 |
| `castType(CastType)` | 按吟唱时间推断 | 不写时:吟唱时间为 0 是瞬发,大于 0 是吟唱。引导、蓄力要显式写。 |
| `tickInterval(int tick)` | `10` | 引导每隔多少 tick 生效一次,最少 1。不受施法速率影响。 |
| `minCharge(float)` | `0.1` | 蓄力比例(0~1)低于它松手算取消。 |
| `interruptible(boolean)` | `true` | 吟唱中受击是否打断。 |
| `castAnimation(Identifier)` | 无 | 专属施法动作,盖过法器的默认动作(见 [12](#12-施法动作))。 |
| `summonCount(int)` | `0` | 一次召几只;写了就是召唤法术(见 [SUMMON.md](SUMMON.md))。 |
| `summonCost(int)` | `1` | 召唤法术:每只占几个召唤物名额。 |
| `summonDuration(float 秒)` | `0` | 召唤法术:限时召唤物的寿命,0 为常驻。 |

> 引导法术打多段伤害时注意无敌帧:法术伤害默认吃原版无敌帧,间隔小于等于 10 tick 时会随机丢伤害。在快照上开 `withLeaveNoIFrames(true)`(见 [8](#8-法术伤害)),命中频率交给 `tickInterval` 控制。

`Spell` 上有同名的读取方法(`manaCost()`、`cooldown()`、`castTime()`、`castType()`、`tickInterval()`、`minCharge()`、`interruptible()`、`castAnimation()`、`isSummon()`、`summonCount()`、`summonCost()`、`summonDuration()`),返回注册行写的原值。按施法者算出的实际值用 `SpellCasting.getCastTicks` / `getCooldownTicks` 和 `ManaApi.getActualCost`。

其他读取方法:

| 方法 | 说明 |
| --- | --- |
| `id()` | 注册 id;注册前为 null。 |
| `holder()` | 注册表里的 `Holder<Spell>`。 |
| `descriptionId()` / `getName()` / `getDescription()` | 语言键、名字、描述。 |
| `targeting()` | 构造时声明的瞄准方式,默认 `Targeting.self()`(不选目标)。 |

### 施法类型 `CastType`

| 类型 | 说明 |
| --- | --- |
| `INSTANT` 瞬发 | 施放即生效,没有读条。 |
| `CAST` 吟唱 | 读条到点自动生效;中途松手或被打断就取消,不扣蓝、不进冷却。 |
| `CHANNEL` 引导 | 开始那一 tick 生效第一次,之后每隔 `tickInterval` 扣一次蓝、生效一次;松手或蓝尽结束。 |
| `CHARGE` 蓄力 | 按住蓄力,松手时生效一次,蓄力比例在 `ctx.charge()`;比例低于 `minCharge` 算取消。 |

### 钩子

法术类继承 `Spell`,必须实现 `onCast`,其余按需重写。

| 钩子 | 哪一端 | 时机 |
| --- | --- | --- |
| `canCast(ctx)` | 服务端 | 蓝、冷却之外的额外条件,默认 `true`。开始施法时调一次(目标已经选好);吟唱到点、引导每次生效、蓄力松手时,扣蓝之前再调一次,不满足就取消这次施法,这一下不扣蓝。只检查,不要在里面改东西。用 `ctx.fail(原因)` 告诉玩家为什么放不了。 |
| `onCastStart(ctx)` | 两端 | 开始施法;瞬发也调。放起手音效、粒子,引导法术在这里生成射线实体。 |
| `onCastTick(ctx)` | 两端 | 吟唱 / 引导 / 蓄力期间每 tick。服务端在生效的那一 tick 调的是 `onCast`,不调本方法。 |
| `onCast(ctx)` | 服务端 | 生效,耗蓝已经扣过。瞬发 / 吟唱一次;引导每 `tickInterval` 一次;蓄力松手一次。 |
| `onCastEnd(ctx, reason)` | 两端 | 结束,`reason` 见 [4](#4-施法)。收尾放这里(删掉引导生成的射线实体等)。 |
| `getTooltip(caster)` | 客户端 | 法术轮盘说明框里按施法者算出的数值行(「伤害 16」)。耗蓝、冷却、读条三行由框架显示,不用写。默认空。 |

两端都调的钩子,客户端那次由施法状态同步触发,法术自己用 `ctx.level().isClientSide()` 区分。

冷却或吟唱时间随施法者变化的法术(比如按法强缩短),重写下面两个方法;框架在它们的返回值上再乘 / 除属性。两端都会调:

```java
@Override
public float cooldown(LivingEntity caster) {   // 秒;默认返回 cooldown()
    float power = (float) caster.getAttributeValue(AttributeRegistry.SPELL_POWER);
    return Math.max(2.0F, this.cooldown() - 0.1F * power);
}

@Override
public float castTime(LivingEntity caster) {   // 秒;默认返回 castTime()
    return this.castTime();
}
```

### 施法上下文 `CastContext`

一次施法从开始到结束是同一个 `CastContext`,传给各个钩子。

| 方法 | 说明 |
| --- | --- |
| `caster()` / `level()` / `spell()` / `source()` | 施法者、世界、法术、施法来源。 |
| `triggerItem()` | 右键的那件施法物品;生物、指令施法时为空。施法掉耐久的就是它。 |
| `sourceItem()` | 提供这个法术的物品;没有时为空。 |
| `startTick()` / `elapsed()` | 开始时的世界时刻 / 已施法 tick(开始那一 tick 为 0)。 |
| `castTicks()` | 吟唱 / 满蓄所需 tick(已除施法速率,最少 1);瞬发、引导为 0。 |
| `charge()` | 蓄力比例 0~1,只在蓄力松手生效时有意义,其他类型恒为 1。 |
| `effects()` | 已生效次数(引导第几次)。 |
| `target()` | 指向型法术作用到的生物;生物施法时 AI 直接给的对象也在这里。不选目标的法术为 null。 |
| `targetPos()` | 地点型法术的落点;其他法术为 null。 |
| `aimPoint()` | 方向型法术朝哪:生物是 AI 给的对象或攻击目标的身体正中,玩家是准星指着的地方。弹射物、射线基类自己会瞄,一般用不到。 |
| `damage(formula)` / `damage(damageType, formula)` | 按施法者此刻的属性生成法术伤害快照,带上本法术的 id(见 [8](#8-法术伤害))。 |
| `fail(reason)` | 在 `canCast` 里说明为什么放不了,框架发到玩家的 action bar。 |
| `data()` / `setData(obj)` | 本次施法的临时对象(引导生成的射线实体等):`onCastStart` 放,之后的钩子取,施法结束自动丢弃。 |

服务端和客户端各有一份上下文。客户端那份只有施法者、法术、来源、开始时刻和读条长度:没有物品,`target()` / `targetPos()` 为 null,`data` 槽也是各管各的。

瞄准方式在构造函数里声明(`super(props, Targeting.xxx())`),怎么写见 [TARGETING.md](TARGETING.md)。

---

## 4. 施法

### 入口

`ibx.lib.spell.SpellCasting`,全是静态方法。

| 方法 | 说明 |
| --- | --- |
| `cast(caster, spell)` | 施法,来源按施法者推断:玩家是 `ITEM`,其他生物是 `MOB`。`spell` 可以传 `Spell` 或 `Holder<Spell>`。 |
| `cast(caster, spell, target)` | 生物 AI 直接给对象施法(见 [7](#7-生物怎么施法))。 |
| `cast(caster, spell, source)` | 指定施法来源。 |
| `cast(caster, spell, source, triggerItem, sourceItem)` | 同上,带上施法物品和提供法术的物品;没有就传 `ItemStack.EMPTY`。还有一个末尾多 `target` 参数的版本。 |
| `castSelected(player, hand)` | 放玩家当前选中的法术,来源 `ITEM`。右键施法物品时 lib 自己会调。 |
| `isCasting(caster)` | 是否正在施法。两端都能问。 |
| `getCurrentCast(caster)` | 正在施的那一次的 `CastContext`;没在施法为 null。两端都能问。 |
| `getLastEndReason(caster)` | 上一次施法是怎么结束的;正在施法或还没施过法为 null。 |
| `stopCasting(caster)` | 松手 / AI 主动停:吟唱取消,引导结束,蓄力按当前比例结算。只在服务端生效。 |
| `cancelCasting(caster, reason)` | 强制结束,不结算蓄力,直接按 `reason` 结束。只在服务端生效。 |
| `getCastTicks(caster, spell)` | 实际吟唱 / 满蓄 tick = `castTime(caster)` × 20 ÷ `spell_cast_speed`,最少 1;瞬发、引导为 0。 |
| `getCooldownTicks(caster, spell)` | 实际冷却 tick = `cooldown(caster)` × 20 × `spell_cooldown_ratio`。 |

玩家法术列表和选中相关的方法见 [5](#5-法术容器)。

### 流程与结果

`cast` 在服务端依次检查,哪一步没过就返回对应的 `CastResult`:

| 顺序 | 检查 | 没过时返回 |
| --- | --- | --- |
| 1 | 正在施别的法术(来源是 `COMMAND` 时直接取消原来的再放) | `ALREADY_CASTING` |
| 2 | 冷却(只有会进冷却的来源才查) | `ON_COOLDOWN` |
| 3 | 法力(只有会扣蓝的来源才查) | `NOT_ENOUGH_MANA` |
| 4 | 按法术的瞄准方式选目标,没有目标 | `CONDITION_FAILED` |
| 5 | 法术自己的 `canCast` | `CONDITION_FAILED` |
| 6 | 召唤法术:召唤物名额不够 | `CONDITION_FAILED` |
| 7 | `SpellCastEvent.Pre` 被取消 | `CONDITION_FAILED` |

全部通过返回 `SUCCESS`:瞬发是已经生效,其余类型是已经开始读条。`NO_SPELL` 只有 `castSelected` 会返回(身上没有任何法术)。瞬发在施法前事件把耗蓝改高后付不起,也返回 `NOT_ENOUGH_MANA`。

- 失败时玩家在 action bar 看到原因(语言键 `message.ibx_lib.cast.<结果小写名>`,或者 `ctx.fail` 给的原因),并听到一声失败提示音,调用方不用再提示。被事件取消的那种不提示。
- `CastResult.isSuccess()` 判断是否成功。
- 客户端调 `cast` 只做判断(正在施法、冷却、法力),不改任何数据。

开始之后,各类型的流程:

| 类型 | 流程 |
| --- | --- |
| 瞬发 | 扣蓝 → `onCastStart` → `onCast` → `onCastEnd`(完成) |
| 吟唱 | `onCastStart` → 每 tick `onCastTick` → 到点扣蓝、`onCast` → `onCastEnd`(完成) |
| 引导 | `onCastStart` → 立刻生效一次 → 之后每 `tickInterval` 扣蓝、`onCast`,其余 tick `onCastTick` → 松手或蓝尽时 `onCastEnd` |
| 蓄力 | `onCastStart` → 每 tick `onCastTick` → 松手时扣蓝、`onCast` → `onCastEnd`(完成);不足 `minCharge` 取消 |

- **生效前再校验一次**(吟唱到点、引导每次生效、蓄力松手):重新定目标(开始时锁定的看还有没有效,生效时选取的按准星重选),再调一次 `canCast`。不通过就取消,这一下不扣蓝。
- **冷却**:生效过至少一次,结束时才进冷却;吟唱被取消、打断不进冷却。
- **耗蓝**:每次生效扣一次,扣的是注册行的耗蓝(可被 `SpellCastEvent.Pre` 改)乘法力消耗倍率。蓄力不按比例折算,一律扣全额。
- **耐久**:扣了蓝,`triggerItem` 有耐久就跟着掉,每扣 `SpellCasting.MANA_PER_DURABILITY`(4)点蓝掉 1 点,四舍五入,零头留给同一次施法的下一跳。物品已经不在手上就不扣。
- **创造模式**:不扣蓝、不进冷却,也就不掉耐久。
- 读条由框架每 tick 推进,生物 AI 不用自己计时。

### 施法来源 `CastSource`

| 来源 | 扣蓝 | 进冷却 | 说明 |
| --- | --- | --- | --- |
| `ITEM` | 是 | 是 | 手持施法物品。玩家施法的默认来源。 |
| `SCROLL` | 否 | 否 | 卷轴:生效过就消耗 1 个 `sourceItem`。lib 的右键施法一律用 `ITEM`,要做卷轴得自己调 `cast` 并传这个来源和物品。 |
| `MOB` | 否 | 否 | 生物(含 Boss)。非玩家施法的默认来源。 |
| `COMMAND` | 否 | 否 | 指令。正在施法时直接取消原来的再放。 |

`consumesMana()` / `triggersCooldown()` 读这两列。

### 结束原因 `CastEndReason`

| 原因 | 说明 |
| --- | --- |
| `COMPLETED` | 正常完成:瞬发 / 吟唱生效,蓄力松手生效。 |
| `CANCELLED` | 主动结束或条件没了:松手(吟唱取消、引导停止、蓄力不足)、生效前校验没过、锁定的目标丢了、施法物品离手、提供法术的装备被卸下、死亡、离开世界。 |
| `INTERRUPTED` | 吟唱中受击被打断。 |
| `OUT_OF_MANA` | 引导中途蓝尽,或吟唱 / 蓄力到生效那一刻付不起。 |

引导法术没有「完成」:松手结束是 `CANCELLED`。要知道它有没有生效过,看 `ctx.effects()`。

**受击打断**只对吟唱(`CAST`)且 `interruptible` 的法术生效,以下情况不打断:

- 伤害为 0,或者被别的监听取消了;
- 伤害类型在 `#ibx_lib:cast_ignore` 里(中毒、凋零、着火、冻伤、饥饿、溺水、摔落这类持续掉血);
- 施法者的实体类型在 `#c:bosses` 里。

### 冷却 `SpellCooldowns`

冷却按「法术 id → 结束的世界时刻」记在施法者身上,存盘,只同步给实体自己;玩家死亡重生后清零。

| 方法 | 说明 |
| --- | --- |
| `isReady(entity, spell)` | 冷却好了没有。 |
| `getRemaining(entity, spell)` | 剩余冷却 tick,0 为已好。 |
| `getRemainingFraction(entity, spell)` | 剩余比例 1 → 0,画冷却遮罩用。 |
| `start(entity, spell, ticks)` | 开始一段冷却,覆盖原有的。只在服务端生效。 |
| `clear(entity)` | 清掉所有冷却。只在服务端生效。 |

读的方法两端都能用,客户端只能读本地玩家的。

### 附加数据

施法相关的状态都是实体上的 Data Attachment,读写请走上面的类,不要直接 `getData`。

| id | 内容 | 存盘 | 同步 |
| --- | --- | --- | --- |
| `ibx_lib:active_cast` | 正在施的法术(`ActiveCast`) | 否 | 自己和周围玩家 |
| `ibx_lib:spell_cooldowns` | 法术冷却(`SpellCooldowns`) | 是 | 只给自己 |
| `ibx_lib:selected_spell` | 玩家选中的法术 id(`SelectedSpell`),死亡后保留 | 是 | 只给自己 |
| `ibx_lib:mana` | 当前法力(`ManaStorage`) | 是 | 只给自己 |
| `ibx_lib:spell_snapshot` | 法术实体上的伤害快照 | 是 | 否 |

施法状态不存盘:下线、换维度、死亡都会结束施法,并照常调 `onCastEnd`。

---

## 5. 法术容器

### 哪些物品提供法术

玩家能放的法术,就是身上所有**法术容器**提供的法术的并集。一件物品是容器,有两种方式:

- **数据组件 `ibx_lib:spells`**(`DataComponentRegistry.SPELLS`):一个法术 id 列表,按法术轮盘里的顺序。任何物品带上它就是容器,不用写代码。
- **实现接口 `SpellContainer`**:物品类自己决定现在提供哪些法术(充能、镶嵌之类),返回值优先于组件。

```
/give @s minecraft:book[ibx_lib:spells=["examplemod:spark"]]
```

```java
// 代码里给物品栈写组件
stack.set(DataComponentRegistry.SPELLS.get(), List.of(ExampleSpells.SPARK.get().holder()));
```

| `SpellContainer` 的方法 | 说明 |
| --- | --- |
| `getSpells(stack)` | 这件物品现在提供哪些法术,按显示顺序。 |
| `innateSpells(stack)` | 物品自带的法术(没被组件改过时提供的那几个)。默认没有。 |
| `SpellContainer.spellsOf(stack)`(静态) | 任意物品提供的法术:物品类实现了接口就问它,否则读组件;都没有为空。 |

lib 的法器基类 `IbxMagicWeaponItem`(法典 `IbxTomeItem`、法杖 `IbxStaffItem`)已经实现了 `SpellContainer`:构造时给自带法术的 id,物品栈带 `ibx_lib:spells` 组件时以组件为准(给空列表就是没有法术)。id 查不到的会跳过,所以可以写别的模组的法术而不依赖它。详见 [EQUIPMENT.md](EQUIPMENT.md)。

容器物品的提示里,名字下面会列出它提供的法术;物品用 `tooltip_display` 隐藏了 `ibx_lib:spells` 组件时不显示。

### 玩家的法术列表

`SpellCasting.getSpells(player)` 按固定顺序扫描身上的容器:

**主手 → 副手 → 饰品栏(装了 Curios 才扫)→ 头 → 胸 → 腿 → 脚**

同一法术只列第一次出现;最后发 `SpellListEvent`,监听可以增删(见 [11](#11-事件))。卸下装备,它提供的法术就没了。

| 方法 | 说明 |
| --- | --- |
| `getSpells(player)` | 玩家现在能放的法术,每条是一个 `SpellEntry`。两端都能调。 |
| `getSpellsExcept(player, excluded)` | 同上,但不算 `excluded` 这一个物品栈。 |
| `getSelectedId(player)` | 选中的法术 id,不校验还在不在列表里;没选过为 null。 |
| `getSelectedSpell(player)` | 当前会放的那一条:选中的还在列表里就是它,否则列表第一个;身上没有法术为 null。 |
| `resolveSelected(player, spells)` | 同上,用已经算好的列表。 |
| `select(player, id)` | 改选:法术在该玩家的列表里才写入,返回是否成功。只在服务端生效。 |

- `SpellEntry` 是一条记录:`spell()`(`Holder<Spell>`)、`sourceItem()`(提供它的物品,由事件加进来、不来自物品的为 `ItemStack.EMPTY`)、`value()`(直接取 `Spell`)。
- 选中的是**法术 id**(`SelectedSpell`),不是下标:换一件装备列表顺序会变。存盘,死亡后保留。
- 选中的法术已经不在列表里时,`castSelected` 回退到列表第一个并提示玩家。

### 能施法的物品

右键放法术的资格和「提供法术」是两回事:

- **`#ibx_lib:can_cast_spell`**(`TagRegistry.CAN_CAST_SPELL`):手持这个标签里的物品右键 = 放当前选中的法术。所有 `IbxMagicWeaponItem` 自动在里面;别的物品要施法,在自己模组的 `data/ibx_lib/tags/item/can_cast_spell.json` 里加(`"replace": false`)。
- 进了标签的物品,使用时长统一是 72000,右键和松手不再走物品自己的 `use` / `releaseUsing`。食物、药水这类本来要「用完」的物品不要进这个标签。
- 提供法术的物品不一定能施法:一件带 `ibx_lib:spells` 的护甲只是把法术加进列表,还是要手持施法物品才能放。

### 选法术界面 `SpellPicker`

物品类实现 `SpellPicker`(它继承 `SpellContainer`),玩家主手拿着它蹲下 + 左键就打开选法术界面:左边是物品现在的法术,右边是可选的法术,选好的写进物品栈的 `ibx_lib:spells` 组件(服务端校验后才写)。所以物品读法术时也要读这个组件——继承 `IbxMagicWeaponItem` 的物品已经是这样。

```java
public class ExampleGrimoire extends IbxTomeItem implements SpellPicker {
    public ExampleGrimoire(Item.Properties properties) {
        super(properties, 800, 5.0F);
    }

    @Override
    public SpellPickerSource pickerSource(ItemStack stack) {
        return SpellPickerSource.CARRIED;
    }
}
```

| `SpellPickerSource` | 右边列什么 |
| --- | --- |
| `REGISTERED` | 游戏里注册的全部法术,按注册顺序。 |
| `CARRIED` | 这件物品自带的法术(`innateSpells`),加上玩家身上其他装备现在能放的法术。 |

两种都不列 `ibx_lib:dismiss_summons`。`SpellPickerSource.spells(player, stack)` 取右边的列表,`SpellPickerSource.pickable(holder)` 判断一个法术能不能放进物品。

---

## 6. 玩家怎么施法

- **右键施法**:手持 `#ibx_lib:can_cast_spell` 的物品右键,放当前选中的法术。瞬发挥一下手;其余类型按住读条,松手结束(吟唱取消、引导停止、蓄力结算),切走物品、打开容器也算取消。吟唱和引导可以在 选项 → 控制 里各自改成「切换」(按一下开始、再按一下结束);蓄力始终是按住。
- **法术轮盘**:按住轮盘键(默认 R,可在按键设置里改)弹出,格子就是 `getSpells` 的结果,左键点一格或松开轮盘键选中。旁边的说明框显示法术描述、`Spell#getTooltip` 的数值行,以及耗蓝、冷却、读条。轮盘只选不放。
- **HUD**:手持施法物品时,副手栏外侧显示当前选中的法术(冷却中、法力不足有遮罩);吟唱条在左、蓝条在右,叠在生命和饥饿那几行上方(用不用原版 HUD 都显示)。瞬发没有吟唱条。
- **图标**:法术图标按 id 找 `assets/<命名空间>/textures/spell/<路径>.png`(16×16),轮盘和 HUD 共用。

相关的客户端设置见 [CONFIG.md](CONFIG.md)。

---

## 7. 生物怎么施法

生物(含 Boss)和玩家走同一个入口,来源是 `CastSource.MOB`:**不扣蓝、不进法术冷却**,放多勤只看生物自己招式的冷却。读条由框架推进,AI 只要问 `isCasting`、必要时 `stopCasting`。

```java
// 生物 AI 里(服务端)
if (!SpellCasting.isCasting(mob) && --this.cooldown <= 0) {
    LivingEntity target = mob.getTarget();
    if (target != null && SpellCasting.cast(mob, ExampleSpells.SPARK, target).isSuccess()) {
        this.cooldown = 100;
    }
}
```

- **`cast(caster, spell, target)`**:AI 直接给对象。指向型锁它,地点型落在它脚下,方向型瞄它。只查够不够得着(活着、同维度、在法术射程内),不查阵营;它之后失效了,施法就取消。`target` 为 null 时等同 `cast(caster, spell)`。
- **不给对象时**:敌方指向型、地点型法术优先用生物的攻击目标;弹射物、射线瞄攻击目标的身体正中。
- **打断**:实体类型在 `#c:bosses` 里的施法者,吟唱不会被受击打断。
- **结果**:施法结束后用 `getLastEndReason(caster)` 看是完成了还是被打断。
- **出手位置**:生物实现 `SpellOrigin`,可以决定弹射物、射线从哪里出发(见 [9](#9-法术实体基类))。
- **数值**:生物的 `ibx_lib:spell_power` 默认是 0,伤害公式里只乘法强的话打不出伤害;给公式留固定部分,或者给生物加属性。
- 生物的施法状态同样同步给周围玩家,客户端的 `onCastStart` / `onCastTick` / `onCastEnd` 和 `SpellCastStateEvent` 照常触发。

---

## 8. 法术伤害

### 快照与结算

法术伤害分两步:

1. **施法时**生成快照 `SpellDamageSnapshot`:按施法者此刻的属性跑一遍伤害公式,连同暴击参数、法术穿透一起记下。之后换装备、buff 过期、施法者死亡都不影响它。
2. **命中时**用 `SpellDamageApi.hurt` 结算。弹射物、范围效果、射线把快照带在实体身上,飞出去之后伤害不再变。

```java
// 法术里:最常用的写法,带上本法术的 id
SpellDamageSnapshot snapshot = ctx.damage(c -> 6.0F + 1.2F * c.spellPower());

// 不在施法上下文里(Boss 招式等)
SpellDamageSnapshot snapshot = SpellDamageApi.snapshot(caster, c -> 10.0F);
```

公式是 `SpellDamageApi.Formula`,拿到 `SpellDamageContext`,返回基础值(不含暴击):

| `SpellDamageContext` 的字段 | 说明 |
| --- | --- |
| `caster()` | 施法者。公式要用别的数值(最大生命、护甲……)就从它身上读。 |
| `spellPower()` | 法术强度(`ibx_lib:spell_power` 总值)。 |
| `canCrit()` | 法术能否暴击(`ibx_lib:can_spell_crit` 总值大于等于 1)。 |
| `critChance()` | 暴击率(`ibx_lib:critical_chance`);大于等于 1 必定暴击。 |
| `critMultiplier()` | 暴击时的伤害倍率,即 1 + `ibx_lib:critical_damage`。 |
| `spellPenetration()` | 法术穿透(`ibx_lib:spell_penetration`)。 |

`SpellDamageApi` 的方法:

| 方法 | 说明 |
| --- | --- |
| `snapshot(caster, formula)` | 用默认伤害类型 `ibx_lib:spell_general` 生成快照。两端都能调(客户端可以拿来算预估值)。 |
| `snapshot(caster, damageType, formula)` | 指定伤害类型。 |
| `hurt(target, snapshot, directEntity, caster, sourcePosition)` | 结算一次法术伤害,返回是否造成了伤害。`directEntity` 是直接造成伤害的实体(弹射物、范围实体;瞬发法术填施法者或 null);`caster` 可空;`sourcePosition` 是盾牌判定朝向用的来源位置,null 时取 `directEntity` 的位置。 |
| `setProjectileSnapshot(entity, snapshot)` / `getProjectileSnapshot(entity)` | 把快照写到实体上(任何实体都行,存盘不丢)/ 读出来。 |
| `hurtFromProjectile(projectile, target)` | 弹射物命中结算:取它身上的快照,施法者取弹射物的主人。没有快照返回 false。 |
| `heal(target, snapshot)` | 结算一次法术治疗,返回实际回复的生命值。按快照掷暴击,不做友伤判定。 |
| `knockback(target, from, strength, up)` | 击退工具:法术伤害默认不击退,要击退的法术命中后自己调。`strength` 是水平力度(原版近战 0.4),`up` 是额外的向上速度;都吃击退抗性。 |

都只在服务端结算,客户端调 `hurt` / `heal` 直接返回。

### 快照上的开关

快照是不可变的记录,`withXxx` 返回改过的新快照,可以连着写:`ctx.damage(...).withFire(80).withLifesteal(0.2F)`。

| 方法 | 默认 | 说明 |
| --- | --- | --- |
| `withDamageType(key)` | `ibx_lib:spell_general` | 换伤害类型。应在 `#ibx_lib:is_spell` 里,否则会被护甲减免、不吃法术抗性。 |
| `withSpell(id)` | 无(`ctx.damage` 会填上) | 来自哪个法术,给事件用。 |
| `withFriendlyFire(boolean)` | `false` | 是否允许打友军。默认友伤保护开着:宠物、召唤物、同队、关了 PvP 的玩家打不到。 |
| `withIgnoreIFrames(boolean)` | `false` | 穿透已有无敌帧:命中前把目标的无敌时间清零。 |
| `withLeaveNoIFrames(boolean)` | `false` | 打完不留无敌帧:命中后把目标的无敌时间还原,这一下不会挡掉别的攻击。多段伤害配合 `HitTracker` 用。 |
| `withFire(ticks)` | `0` | 命中后点燃多少 tick。 |
| `withFreeze(ticks)` | `0` | 命中后给目标加大约这么多 tick 的冰冻(细雪那种减速;累积到原版冻伤门槛才会掉血)。目标不能被冻(`canFreeze()` 为 false)时无效。 |
| `withLifesteal(ratio)` | `0` | 吸血比例:按实际造成的伤害给施法者回血。 |
| `scaled(factor)` | — | 基础值乘一个系数,给范围衰减、多段分摊用(爆炸边缘 `scaled(0.5F)`)。 |

快照的其他字段(`baseAmount`、`canCrit`、`critChance`、`critMultiplier`、`spellPenetration`)在生成时定下,只读。

### 一次命中的顺序

`SpellDamageApi.hurt` 对生物目标依次做:

1. **友伤判定**:快照没开 `friendlyFire` 时,施法者伤不到的目标直接返回 false。没有施法者(弹射物的主人已下线)时不做判定。
2. **掷暴击**:每个目标各掷一次,暴击就乘快照里的暴击倍率。
3. **`SpellDamageEvent`**:可取消、可改伤害量(见 [11](#11-事件))。
4. **无敌帧开关**:`ignoreIFrames`、`leaveNoIFrames`。
5. **原版受伤流程**:其中按法术抗性减免(见下),之后还有抗性提升、保护附魔、伤害吸收等原版减免。
6. **命中之后**:暴击的在目标身上播暴击粒子和音效;点燃、冰冻、吸血在伤害结算完之后生效。

非生物目标(末影水晶、船等)只走原版受伤,用快照的基础值,不做上面的友伤、暴击、事件、无敌帧处理。

法术伤害不会触发荆棘、火焰附加这类「攻击后」附魔效果,也不触发属性 `ibx_lib:thorns` 的反伤。

伤害来源是 `SpellDamageSource`(继承原版 `DamageSource`),多带两样:`snapshot()` 和 `isCritical()`。**判断「是不是法术伤害」请用标签 `#ibx_lib:is_spell`,不要 `instanceof SpellDamageSource`**——别的模组的魔法伤害进了标签也算法术。

### 抗性、穿透、暴击、治疗

- **法术抗性**:伤害类型在 `#ibx_lib:is_spell` 里的伤害,按目标的 `ibx_lib:spell_resistance`(记作 R)套原版护甲公式减免,韧性用原版 `armor_toughness`:

  ```
  减免比例 = clamp(R − 伤害 ÷ (2 + 韧性 ÷ 4), min(R × 0.2, 20), 20) ÷ 25 × (1 − 法术穿透)
  ```

  最多减 80%。按标签判断,所以不经过 `SpellDamageApi`、但进了标签的伤害同样吃法术抗性。
- **法术穿透**:`SpellDamageSource` 用快照里施法时读的值;别的进了标签的伤害读攻击者此刻的 `ibx_lib:spell_penetration`。
- **暴击**:施法者的 `ibx_lib:can_spell_crit` 大于等于 1 时法术才会暴击;概率和倍率是施法时记进快照的 `critical_chance` 和 1 + `critical_damage`,和近战、远程同一套属性。治疗同样会暴击,但不播暴击表现。
- **点燃 / 冰冻 / 吸血**:只认 `SpellDamageSource`,在伤害真正造成之后生效。吸血按实际造成的伤害(所有减免之后)算,施法者已死亡时不回。
- **治疗**:所有走原版 `LivingEntity#heal` 的回复(包括 `SpellDamageApi.heal` 和吸血)都乘受治疗者的 `ibx_lib:heal_efficiency`。

属性的默认值和范围见 [ATTRIBUTE.md](ATTRIBUTE.md)。

### 多段命中 `HitTracker`

范围、引导法术每 tick 都在找目标,开了 `leaveNoIFrames` 之后原版无敌帧不再限流,用 `ibx.lib.api.HitTracker` 控制每个目标多久挨一下:

```java
private final HitTracker hits = new HitTracker(10);   // 同一目标两次命中至少隔 10 tick

if (this.hits.tryHit(target)) {                       // 现在能打就记下这一次并返回 true
    SpellDamageApi.hurt(target, snapshot.withLeaveNoIFrames(true), this, owner, null);
}
```

`clear()` 清空记录。不存盘。`SpellAoe` 和 `SpellBeam` 已经内置了它。

### 伤害类型与标签

键在 `DamageTypeRegistry`,定义在 `data/ibx_lib/damage_type/`。

| 伤害类型 | 是法术伤害 | 特性 |
| --- | --- | --- |
| `ibx_lib:spell_general` | 是 | 通用法术伤害,法术不指定类型时用它。 |
| `ibx_lib:digest` | 否 | 无视护甲、盾牌和无敌帧,没有击退。 |
| `ibx_lib:bleeding` | 否 | 无视护甲、抗性提升和保护类附魔,没有击退。 |
| `ibx_lib:soul_link` | 否 | 无视护甲、抗性提升、保护类附魔、盾牌和无敌帧,没有击退。 |

伤害类型标签(键在 `TagRegistry`):

| 标签 | 说明 |
| --- | --- |
| `#ibx_lib:is_spell` | 法术伤害。所有「是不是法术伤害」的判断只看它。原版的 `bypasses_armor`、`no_knockback`、`avoids_guardian_thorns` 都包含本标签,所以进了它的类型自动不吃护甲、不被原版击退、不触发守卫者荆棘。 |
| `#ibx_lib:cast_ignore` | 不打断吟唱的伤害。自带 `neoforge:poison`、`minecraft:wither`、`on_fire`、`freeze`、`starve`、`drown`、`fall`。 |

加自己的法术伤害类型(比如冰霜):

1. 在 `data/examplemod/damage_type/frost.json` 定义伤害类型。
2. 在 `data/ibx_lib/tags/damage_type/is_spell.json` 里加上它(`"replace": false`)。
3. 法术里 `ctx.damage(FROST, formula)`,或者 `snapshot.withDamageType(FROST)`。

```json
{
  "replace": false,
  "values": [ "examplemod:frost" ]
}
```

快照里的伤害类型查不到时(提供它的数据包被删了)退回 `ibx_lib:spell_general`。法术伤害正面举盾能挡;不想被挡,就用同时在原版 `bypasses_shield` 里的伤害类型。

---

## 9. 法术实体基类

`ibx.lib.entity` 里有三个基类:弹射物**飞**出去,范围效果**留在原地**,射线**跟着施法者转**。伤害都来自施法瞬间存在实体上的快照,实体类型由各模组自己注册。

三者共通的地方:

- 不打施法者自己;友伤保护开着时(快照默认)不作用于友方,弹射物和射线直接穿过去。
- `affects()` 返回作用于谁(`Faction`):默认敌方,快照开了 `friendlyFire` 就不分敌我。治疗类重写成友方。
- 构造函数有两个:只带 `(EntityType, Level)` 的给实体类型的工厂用,带施法者的给法术用(只在服务端)。

### 弹射物 `SpellProjectile`

> **`SpellProjectile` 需要 GeckoLib**:它实现了 GeckoLib 的 `GeoEntity`。GeckoLib 对 ibx_lib 是可选依赖,lib 里只有这个类和它的渲染器用到;你的模组要注册法术弹射物,就在自己的 `neoforge.mods.toml` 里把 GeckoLib 写成必需依赖。

```java
public class FireBolt extends SpellProjectile {
    public FireBolt(EntityType<? extends FireBolt> type, Level level) {
        super(type, level);
    }

    public FireBolt(LivingEntity caster, SpellDamageSnapshot snapshot) {
        super(ExampleEntities.FIRE_BOLT.get(), caster, snapshot);
    }

    @Override protected int getLifetime() { return 60; }
    @Override protected boolean isExtinguishedByWater() { return true; }
    @Override protected void trailParticles() {
        this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
    }
}

// 法术里
@Override
public void onCast(CastContext ctx) {
    FireBolt bolt = new FireBolt(ctx.caster(), ctx.damage(c -> 6.0F + 1.2F * c.spellPower()).withFire(60));
    bolt.shootFromCaster(1.5F, 0.0F);   // 速度(格 / tick)、散布(0 为绝对准)
    ctx.level().addFreshEntity(bolt);
}

// 实体类型
public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities("examplemod");
public static final DeferredHolder<EntityType<?>, EntityType<FireBolt>> FIRE_BOLT = ENTITIES.registerEntityType(
        "fire_bolt", FireBolt::new, MobCategory.MISC,
        b -> b.noLootTable().sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(10));
```

`shootFromCaster` 朝主人准星指着的地方发射(出发点在下巴,不在眼睛上,所以是斜着对准准星);生物瞄 AI 给的对象或攻击目标的身体正中,带重力的弹射物会自动抬高瞄准点抵消下坠。

| 参数(重写) | 默认 | 说明 |
| --- | --- | --- |
| `getLifetime()` | `100` | 最多飞多少 tick。 |
| `getDrag()` / `getWaterDrag()` | `1.0` | 空气 / 水里每 tick 速度保留的比例,1 为不减速。 |
| `getDefaultGravity()`(原版) | `0` | 重力;雪球是 0.03。 |
| `isExtinguishedByWater()` | `false` | 一进水就熄灭。 |
| `getPierce()` | `0` | 能穿过几个目标;0 为打中第一个就结束。同一个目标只打一次。 |
| `getHomingStrength()` | `0` | 追踪强度:每 tick 把速度方向往目标转多少(0~1),0 为不追踪。追前方 60° 内最近的可作用生物。 |
| `getHomingRange()` | `16.0` | 追踪找目标的距离(格)。 |
| `flyAnimation()` | `null` | 用 3D 模型渲染时,飞行中循环播的 GeckoLib 动画。 |

| 钩子 | 时机 |
| --- | --- |
| `onHitTarget(target, damaged)` | 打中一个目标之后,伤害已经结算。`damaged` 为 false 表示被挡掉了(无敌帧、盾牌、免疫)。 |
| `onImpact(hit)` | 命中结束:撞到方块,或者打中了最后一个能打的目标。爆炸、留下范围效果放这里。之后弹射物消失。 |
| `onExpire()` | 飞到寿命尽头,或者进水熄灭。之后弹射物消失。 |
| `trailParticles()` | 客户端每 tick:尾迹粒子。 |
| `impactParticles()` | 客户端:命中结束时的粒子。 |

渲染器(`ibx.lib.client.renderer`)在 `EntityRenderersEvent.RegisterRenderers` 里注册:

```java
// 一张始终朝向镜头的图:贴图、边长(格)、是否自发光
event.registerEntityRenderer(ExampleEntities.FIRE_BOLT.get(), ctx -> new SpellProjectileRenderer<>(ctx,
        Identifier.fromNamespaceAndPath("examplemod", "textures/entity/spell/fire_bolt.png"), 0.5F, true));

// GeckoLib 3D 模型,顺着飞行方向;文件走 GeckoLib 的默认路径
event.registerEntityRenderer(ExampleEntities.ICE_SHARD.get(), ctx -> new GeoSpellProjectileRenderer<>(ctx,
        Identifier.fromNamespaceAndPath("examplemod", "spell/ice_shard")));
```

`GeoSpellProjectileRenderer` 的文件位置:`geckolib/models/entity/<路径>.geo.json`、`geckolib/animations/entity/<路径>.animation.json`、`textures/entity/<路径>.png`。建模时尖头朝 Blockbench 的北面(-Z),模型中心放在原点。

### 范围效果 `SpellAoe`

留在原地一段时间、持续作用于范围里的生物:火墙、毒云、治疗圈。形状是竖着的圆柱:底面中心在实体位置,水平半径 `getRadius()`,高 `getHeight()`。

```java
public class FireField extends SpellAoe {
    public FireField(EntityType<? extends FireField> type, Level level) {
        super(type, level);
    }

    public FireField(LivingEntity caster, SpellDamageSnapshot snapshot, Vec3 pos, float radius, int duration) {
        super(ExampleEntities.FIRE_FIELD.get(), caster, snapshot, pos, radius, duration);
    }

    @Override protected ParticleOptions getParticle() { return ParticleTypes.FLAME; }
    @Override protected void onAffect(LivingEntity target, boolean hit) {
        if (hit) target.igniteForSeconds(2.0F);
    }
}

// 法术:准星选落点,读条时地上画半径 3 的圈
public final class FireFieldSpell extends Spell {
    public FireFieldSpell(Properties props) {
        super(props, Targeting.point().range(24.0).circle(3.0F, Faction.ENEMY));
    }

    @Override
    public void onCast(CastContext ctx) {
        Vec3 pos = ctx.targetPos();
        if (pos != null) {
            ctx.level().addFreshEntity(new FireField(ctx.caster(),
                    ctx.damage(c -> 2.0F + 0.3F * c.spellPower()), pos, 3.0F, 100));
        }
    }
}
```

| 参数(重写) | 默认 | 说明 |
| --- | --- | --- |
| `getHitInterval()` | `10` | 同一目标两次作用之间至少隔多少 tick。 |
| `getHeight()` | `1.0` | 圆柱高度(格),从实体位置往上算。 |
| `getRadiusPerTick()` | `0` | 半径每 tick 变化多少(扩散为正、收缩为负);缩到 0.5 格以下就提前结束。 |
| `leavesNoIFrames()` | `true` | 打完不留无敌帧:节奏由 `getHitInterval()` 决定,持续伤害也不会挡掉别的攻击。 |
| `getParticle()` | 女巫的紫色粒子 | 客户端在圆盘上随机冒的粒子。 |

| 钩子 | 说明 |
| --- | --- |
| `affects()` | 作用于谁。只借外壳(预警、时长、半径同步)、自己不结算的返回 `Faction.NONE`,就不再每 tick 找人。 |
| `canAffect(target)` | 这个目标该不该作用:默认不打施法者,其余看 `affects()`。 |
| `affect(target)` | 作用一次:默认造成快照伤害,返回是否打中。治疗圈改成 `SpellDamageApi.heal`。 |
| `onAffect(target, hit)` | 作用一次之后(附带效果)。`hit` 为 false 表示被挡掉了。 |
| `onExpire()` | 持续时间到了,或者半径缩没了。之后实体消失。 |
| `ambientParticles()` | 客户端每 tick 的粒子;要完全自己画就重写它。 |

| 方法 | 说明 |
| --- | --- |
| `getRadius()` / `setRadius(radius)` | 半径,同步给客户端,最大 32。 |
| `setWaitTime(ticks)` | 先「预警」多久再生效,放进世界之前设。预警期间不作用,粒子稀疏。 |
| `isWaiting()` | 是否还在预警。 |
| `getOwner()` / `snapshot()` | 施法者 / 伤害快照,都可能为 null。 |

- 还有一个不带快照的构造函数 `(type, caster, pos, radius, durationTicks)`,配合 `affects()` 返回 `Faction.NONE` 用。
- `durationTicks` 是生效多久,不含预警。
- 没有模型,实体渲染器注册原版的 `NoopRenderer::new` 即可。注册实体类型时的 `sized` 随便填,碰撞箱会跟着半径变。
- 伤害的来源位置是圆心:正面朝圆心举盾能挡。
- 存盘;施法者下线或被卸载后照样生效,只是不做友伤判定。

### 射线 `SpellBeam`

从施法者胸口射出、跟着视线转的一道光束,持续作用于线上的生物。主要给引导法术用:`onCastStart` 建、放进 `ctx.setData`,`onCastEnd` 调 `end()`。

```java
// 注册行:Spell.props().castType(CastType.CHANNEL).tickInterval(10).manaCost(2)
public final class FrostRaySpell extends Spell {
    public FrostRaySpell(Properties props) {
        super(props);
    }

    @Override
    public void onCastStart(CastContext ctx) {
        if (!ctx.level().isClientSide()) {
            FrostBeam beam = new FrostBeam(ctx.caster(), ctx.damage(c -> 1.0F + 0.2F * c.spellPower()));
            ctx.level().addFreshEntity(beam);
            ctx.setData(beam);
        }
    }

    @Override
    public void onCast(CastContext ctx) {
        // 扣蓝由框架做,伤害射线自己打
    }

    @Override
    public void onCastEnd(CastContext ctx, CastEndReason reason) {
        SpellBeam beam = ctx.data();
        if (beam != null) {
            beam.end();
        }
    }
}

public class FrostBeam extends SpellBeam {
    public FrostBeam(EntityType<? extends FrostBeam> type, Level level) {
        super(type, level);
    }

    public FrostBeam(LivingEntity caster, SpellDamageSnapshot snapshot) {
        super(ExampleEntities.FROST_BEAM.get(), caster, snapshot.withFreeze(60), 0);   // 0 = 不限时
    }

    @Override protected double getRange() { return 12.0; }
}

// 实体类型:不存盘、不能 /summon;碰撞箱随便填,判定用的是射线
public static final DeferredHolder<EntityType<?>, EntityType<FrostBeam>> FROST_BEAM = ENTITIES.registerEntityType(
        "frost_beam", FrostBeam::new, MobCategory.MISC,
        b -> b.noSave().noSummon().noLootTable().fireImmune().sized(0.1F, 0.1F).clientTrackingRange(10).updateInterval(1));
```

引导法术把 `tickInterval` 和射线的 `getHitInterval()` 设成一样,就是扣一次蓝打一下。

| 参数(重写) | 默认 | 说明 |
| --- | --- | --- |
| `getRange()` | `16.0` | 最远射程(格)。 |
| `getWidth()` | `0.3` | 粗细(格):判定时目标碰撞箱按它的一半撑开,画出来的方管边长也是它。 |
| `isPiercing()` | `false` | false 停在第一个能作用的目标上,true 打中线上所有能作用的目标。 |
| `stopsAtBlocks()` | `true` | 撞到方块是否停下;false 为穿墙。 |
| `getHitInterval()` | `10` | 同一目标两次作用之间至少隔多少 tick。 |
| `leavesNoIFrames()` | `true` | 打完不留无敌帧。 |

| 钩子 | 说明 |
| --- | --- |
| `origin(owner, partialTick)` | 起点。默认胸口(离脚 0.65 倍身高);施法者实现了 `SpellOrigin` 就用它给的点。 |
| `direction(owner, start, partialTick)` | 方向。默认从起点对准准星指着的地方;生物对准 AI 给的对象或攻击目标的身体正中。 |
| `aimPoint(owner, partialTick)` | 准星指着的地方。 |
| `affects()` / `canAffect(target)` / `affect(target)` / `onAffect(target, hit)` | 同 `SpellAoe`。 |
| `onBlockHit(hit)` | 每 tick 射线停在方块上时。 |
| `onExpire()` | 到时间了、施法者没了,或者法术调了 `end()`。之后实体消失。 |
| `beamParticles(start, end)` | 客户端每 tick:沿射线放粒子。 |

| 方法 | 说明 |
| --- | --- |
| `end()` | 结束:调 `onExpire()` 再消失。直接 `discard()` 会跳过收尾钩子。只在服务端。 |
| `getStart(partialTick)` / `getEnd(partialTick)` / `getLength()` | 这一刻的两端和长度。 |
| `getEndKind()` | 射线停在了什么上:`NONE`(打满射程)、`BLOCK`、`ENTITY`。 |
| `getOwner()` / `snapshot()` | 施法者 / 伤害快照。 |

- 构造函数的 `durationTicks`:最多存在多少 tick,0 为不限时(由法术调 `end()`)。
- 施法者死了、下线、换维度,射线自己消失。不存盘。
- 伤害的来源位置是起点:正面朝施法者举盾能挡。

渲染器 `SpellBeamRenderer`:从起点到终点画一根或几根同轴的贴图方管。

```java
// 一层:贴图、是否自发光、贴图纵向重复一次对应多少格、每 tick 沿射线往外滑多少格
event.registerEntityRenderer(ExampleEntities.FROST_BEAM.get(), ctx -> new SpellBeamRenderer<>(ctx,
        Identifier.fromNamespaceAndPath("examplemod", "textures/entity/spell/frost_beam.png"), true, 1.0F, 0.1F));

// 两层:外层辉光写前面,细芯写最后(后写的盖在上面)
event.registerEntityRenderer(ExampleEntities.FROST_BEAM.get(), ctx -> new SpellBeamRenderer<>(ctx, true,
        new SpellBeamRenderer.Layer(GLOW_TEXTURE, 1.0F, 1.2F, 0.2F),
        new SpellBeamRenderer.Layer(CORE_TEXTURE, 0.33F, 0.8F, 0.3F)));
```

`Layer` 的四个参数:贴图、粗细(相对射线粗细的比例)、贴图纵向重复一次对应多少格、每 tick 往外滑多少格。贴图横向不要画成中间亮两边暗(斜着看会裂成两条),要中间亮就再加一层细芯。

### `SpellOrigin` 与 `PhasedEntity`

两个给施法者实体实现的接口:

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `SpellOrigin` | `getSpellOrigin()` | 法术弹射物、射线从哪里出发(世界坐标):从嘴里、手里喷出来的生物用。没实现的施法者,弹射物从下巴出发、射线从胸口出发。 |
| `SpellOrigin` | `getSpellAimPoint()` | 射线对准的点;默认 null,照常对准攻击目标 / 准星。想让射线跟着身体慢慢转时用。 |
| `PhasedEntity` | `getPhase()` | 分阶段的实体(Boss 半血进二阶段)当前第几阶段,一阶段为 0。法术可以据此变化:`ctx.caster() instanceof PhasedEntity boss && boss.getPhase() >= 1`。 |

---

## 10. 法力

- **上限**:属性 `ibx_lib:max_mana`。
- **回复**:属性 `ibx_lib:mana_regeneration`,每秒点数,负数为流失。由 `ManaHandler` 在服务端每 tick 结算。玩家始终参与;其他生物只有用过法力(身上有法力数据)才参与。
- **消耗**:基础消耗 × 属性 `ibx_lib:mana_cost_multiplier`。
- **当前值**:附加数据 `ibx_lib:mana`(`ManaStorage`),存盘,只同步给实体自己——客户端只能读本地玩家的法力。新玩家和死亡重生后都从 0 开始。
- **创造模式**:消耗恒成功且不扣。

`ibx.lib.api.ManaApi`:

| 方法 | 说明 |
| --- | --- |
| `getMana(entity)` | 当前法力。没有法力数据的实体为 0。 |
| `getMaxMana(entity)` | 法力上限。 |
| `getActualCost(entity, baseCost)` | 实际消耗 = 基础消耗 × 法力消耗倍率。给界面预览用。 |
| `canAfford(entity, baseCost)` | 够不够付。创造模式恒为 true。 |
| `tryConsume(entity, baseCost)` | 够就扣掉并返回 true,不够返回 false 且不扣。扣之前发 `ManaEvent.Consume`。客户端调用只做判断。 |
| `add(entity, amount)` | 增加法力(药水、击中回蓝、退还),结果截断到 [0, 上限];负数为扣减。先发 `ManaEvent.Gain`。返回实际变化量。只在服务端生效。 |
| `set(entity, value)` | 直接设值,截断到 [0, 上限],不发 `ManaEvent.Gain`。只在服务端生效。 |

`tickRegeneration` 是给 `ManaHandler` 用的,别的代码不要调。`ManaStorage` 只有一个读取方法 `get()`,读写一律走 `ManaApi`。

```java
// 击中回蓝
ManaApi.add(player, 5.0F);

// 自己的消耗(不是法术):够才做
if (ManaApi.tryConsume(player, 20.0F)) {
    // ...
}
```

法术的耗蓝不用自己扣,`SpellCasting` 会在每次生效前调 `tryConsume`。

---

## 11. 事件

都在 `ibx.lib.api.event` 包里,发在 `NeoForge.EVENT_BUS` 上。

| 事件 | 可取消 | 哪一端 | 时机 | 能改什么 |
| --- | --- | --- | --- | --- |
| `SpellCastEvent.Pre` | 是 | 服务端 | 施法开始前,冷却、法力、目标、法术条件都已通过。取消后施法失败(`CONDITION_FAILED`,不发提示)。 | `setManaCost`(基础值,实际扣时还会乘法力消耗倍率;引导是每次生效的耗蓝)、`setCooldownTicks`(已乘冷却倍率的 tick 数)。 |
| `SpellCastEvent.Post` | 否 | 服务端 | 每次生效之后(`onCast` 之后;引导每次都发)。`getContext()` 取上下文。 | 只通知。 |
| `SpellCastStateEvent.Start` | 否 | 客户端 | 某个生物开始施法(施法状态同步到达时),自己和周围生物的都会收到。瞬发也有,紧接着就是结束。 | 只通知。 |
| `SpellCastStateEvent.End` | 否 | 客户端 | 某个生物结束施法,`getReason()` 是结束原因。 | 只通知。 |
| `SpellDamageEvent` | 是 | 服务端 | 法术伤害命中前(`SpellDamageApi.hurt`,目标是生物时):友伤判定已通过、暴击已掷完(伤害量已乘暴击倍率),还没进原版受伤流程。 | `setAmount`;`getSource()` 取 `SpellDamageSource`(快照、是否暴击)。 |
| `SpellListEvent` | 否 | 两端 | 玩家的法术列表聚合完之后(`SpellCasting.getSpells`)。 | `add(spell, sourceItem)`、`remove(spell)`、`contains(spell)`,或直接改 `getSpells()`。 |
| `ManaEvent.Consume` | 是 | 服务端 | 消耗法力前(`ManaApi.tryConsume`)。取消后这次消耗失败、不扣。创造模式不触发。 | `setCost`(收到的值已经乘过法力消耗倍率)。 |
| `ManaEvent.Gain` | 否 | 服务端 | 获得法力前(`ManaApi.add`)。自然回复不触发。 | `setAmount`,负数为扣减。 |
| `ManaEvent.Changed` | 否 | 服务端 | 法力变化后,包括自然回复——回复中每 tick 都可能来一次。`getOldValue()` / `getNewValue()`。 | 只通知。 |

- `SpellCastEvent` 都带 `getCaster()`、`getSpell()`、`getSource()`;`SpellCastStateEvent` 带 `getCaster()`、`getContext()`;`ManaEvent` 带 `getEntity()`;`SpellDamageEvent` 的 `getEntity()` 是受到伤害的目标。
- 客户端的 `CastContext` 没有物品;要知道用哪只手施法,看施法者手上的物品。
- **`SpellListEvent` 两端算出的结果必须一致**,否则轮盘里能选的法术会被服务端拒掉。监听里只看两端都有的数据:物品、药水效果、同步过的附加数据。

```java
// 有某个药水效果时临时多一个法术
NeoForge.EVENT_BUS.addListener((SpellListEvent event) -> {
    if (event.getPlayer().hasEffect(ExampleEffects.ARCANE_INSIGHT)) {
        event.add(ExampleSpells.SPARK, ItemStack.EMPTY);
    }
});

// 水里施法耗蓝减半
NeoForge.EVENT_BUS.addListener((SpellCastEvent.Pre event) -> {
    if (event.getCaster().isInWater()) {
        event.setManaCost(event.getManaCost() * 0.5F);
    }
});
```

---

## 12. 施法动作

玩家施法时的手臂动作由 Player Animation Library 播放。它对 ibx_lib 是**可选依赖**:没装时法术照常放,只是手臂不动。自己和周围玩家都能看到,第一人称也显示。

放哪个动画,按这个顺序找:

1. 法术自己指定的:`Spell.props().castAnimation(id)`。
2. 施法那只手上法器的:`IbxMagicWeaponItem#castAnimation(CastType)`,法典、法杖各有一套,按施法类型分(瞬发、吟唱各一段,引导和蓄力共用一段)。
3. 不是 Ibx 法器的施法物品:`IbxMagicWeaponItem.defaultCastAnimation(CastType)`(法典那一套)。

- 动画文件放 `assets/<命名空间>/player_animations/`,`castAnimation` 填的是动画 id。
- 动画都按右手施法做;施法的是左手时整段镜像。主手拿着施法物品就用主手,否则用副手。
- 动画自己定循环:只放一遍的放完就停;循环的放到施法结束再淡出,至少放 0.6 秒(瞬发也看得到)。
- 物品模型条件 `ibx_lib:casting`:这件物品正拿在手上施法时为真(施法动作还在放的那段也算)。法典的物品模型用它切换合上 / 翻开。

自己做别的客户端表现(粒子、镜头、自定义动画),监听 `SpellCastStateEvent`(见 [11](#11-事件)),或者直接写在法术的 `onCastStart` / `onCastTick` / `onCastEnd` 里。
