# 物品说明

**简体中文** | [English](TOOLTIP_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

给物品说明框(鼠标指着物品时弹出的框)加自己的说明行:按物品登记,插在物品名正下方;长句自动换行,换行后每段保持原来的颜色;还可以分出一段按住 Shift 才显示的详情。每行怎么拼、用什么颜色有一套统一的写法:正文用奶油色,只给数字、效果名、按键这类关键词单独上色。

| 想做什么 | 用哪个类 | 见 |
| --- | --- | --- |
| 拼说明行和带颜色的片段(被动、主动、冷却、数值、效果名……) | `ibx.lib.tooltip.TooltipLines`(两端通用) | [4](#4-tooltiplines-一览) |
| 按物品登记说明 | `ibx.lib.client.tooltip.ItemTooltips`(只在客户端) | [2](#2-登记说明) |
| 按住 Shift 才显示的详情 | `ItemTooltips.Registrar#add(item, lines, details)` | [3](#3-按住-shift-展开) |

- `TooltipLines` 只是拼 `Component`,两端都能调;物品自己的 `appendHoverText` 里也可以用它,配色就和登记的说明一致。
- `ItemTooltips` 用到客户端的字体和按键状态,只能在客户端入口里调。

---

## 2. 登记说明

在客户端模组入口(`@Mod(value = ..., dist = Dist.CLIENT)`)的构造函数里调 `ItemTooltips.register`,传一个登记回调;回调里每件物品调一次 `registrar.add(item, lines)`。`lines` 拿到物品栈和一个 `Consumer<Component>`,按顺序 `accept` 每一行:

```java
@Mod(value = "examplemod", dist = Dist.CLIENT)
public class ExampleModClient {
    public ExampleModClient(ModContainer container) {
        ExampleTooltips.register();
    }
}
```

```java
public final class ExampleTooltips {
    private static final String KEY = "tooltip.examplemod.";

    public static void register() {
        ItemTooltips.register(registrar -> {
            // 被动:「被动」+「攻击有15%概率给目标加持续5秒的缓慢 II」
            registrar.add(ModItems.RUBY_SWORD, (stack, tooltip) -> {
                tooltip.accept(TooltipLines.passive());
                tooltip.accept(TooltipLines.body(KEY + "ruby_sword.passive",
                        TooltipLines.percent(0.15F),
                        TooltipLines.seconds(100),
                        TooltipLines.effect(MobEffects.SLOWNESS, 1)));
            });

            // 主动:「主动 · [右键]使用」+「冷却8秒」+「朝前方放出3颗造成自身攻击伤害40%的火球」
            registrar.add(ModItems.RUBY_STAFF, (stack, tooltip) -> {
                tooltip.accept(TooltipLines.active(TooltipLines.RIGHT_CLICK));
                tooltip.accept(TooltipLines.cooldown(160));
                tooltip.accept(TooltipLines.body(KEY + "ruby_staff.active",
                        TooltipLines.value(3),
                        TooltipLines.percent(0.4F)));
            });
        });
    }
}
```

`zh_cn.json` 和 `en_us.json`:

```json
{
  "tooltip.examplemod.ruby_sword.passive": "攻击有%s概率给目标加持续%s秒的%s",
  "tooltip.examplemod.ruby_staff.active": "朝前方放出%s颗造成自身攻击伤害%s的火球"
}
```

```json
{
  "tooltip.examplemod.ruby_sword.passive": "Attacks have a %s chance to give the target %s seconds of %s",
  "tooltip.examplemod.ruby_staff.active": "Shoots %s fireballs forward, each dealing %s of your attack damage"
}
```

- **回调晚执行**:`register` 只是把回调存起来,等第一次显示说明框(物品都注册完了)才调,所以回调里可以直接取物品。
- **一件物品只登记一次**:所有模组合起来算,重复登记会在建表时抛异常。`register` 本身可以调多次,比如每类物品一个类各调一次。
- `add` 收 `ItemLike`,`DeferredItem`、`Item`、方块都行。数值作为参数传进去,改数值不用改语言文件。

---

## 3. 按住 Shift 展开

`registrar.add(item, lines, details)`:`lines` 一直显示,`details` 按住 Shift 才显示。把上一节法杖的登记换成:

```java
registrar.add(ModItems.RUBY_STAFF, (stack, tooltip) -> {
    tooltip.accept(TooltipLines.active(TooltipLines.RIGHT_CLICK));
    tooltip.accept(TooltipLines.cooldown(160));
    tooltip.accept(TooltipLines.body(KEY + "ruby_staff.active", TooltipLines.value(3), TooltipLines.percent(0.4F)));
}, (stack, tooltip) -> {
    // 按住 Shift 才显示
    tooltip.accept(TooltipLines.muted(KEY + "ruby_staff.detail", TooltipLines.key("key.sneak")));
    tooltip.accept(TooltipLines.flavor(KEY + "ruby_staff.flavor"));
});
```

- 没按住 Shift 时,`lines` 后面多一行「按住 [Shift] 查看详情」(`TooltipLines.foldHint()`,语言键 `tooltip.ibx_lib.fold_hint`);按住时这一行换成 `details` 的内容。
- `details` 一行都没加时(比如按物品栈的状态判断后什么都没写)不显示提示行。
- 看的是键盘上的 Shift 键,不跟潜行键的改键走。

---

## 4. TooltipLines 一览

`ibx.lib.tooltip.TooltipLines` 的方法分两类:**整行**直接交给 `tooltip.accept`;**片段**当整行的参数用,填进语言文字里的 `%s`。颜色都来自 `ibx.lib.client.UiPalette`。

| 方法 | 产出 | 颜色 | 类型 |
| --- | --- | --- | --- |
| `passive()` | 「被动」,被动特性的标题行 | 强调色(琥珀,`ACCENT`) | 整行 |
| `active(trigger)` | 「主动 · [右键]使用」,主动技能的标题行 | 强调色 | 整行 |
| `cooldown(ticks)` | 「冷却8秒」,tick 换算成秒 | 次要色(旧木,`MUTED`),秒数原版蓝 | 整行 |
| `foldHint()` | 「按住 [Shift] 查看详情」,`ItemTooltips` 自动加 | 禁用色(`DISABLED`),按键用按键色 | 整行 |
| `body(key, args...)` | 一行正文 | 正文色(奶油,`BODY`) | 整行 |
| `muted(key, args...)` | 一行次要说明:计数、条件这类补充 | 次要色 | 整行 |
| `flavor(key, args...)` | 一行风味文字:背景故事这类不讲机制的话 | 次要色、斜体 | 整行 |
| `value(number)` | 数值,最多一位小数,整数不带小数点 | 原版蓝 | 片段 |
| `percent(ratio)` | 比例写成百分数:`0.06` → `6%` | 原版蓝 | 片段 |
| `seconds(ticks)` | tick 换算成秒数:`100` → `5`,`30` → `1.5`;「秒」字写在语言文件里 | 原版蓝 | 片段 |
| `effect(effect, amplifier)` | 效果名 + 等级:「缓慢 II」 | 增益正面色、减益负面色、中性正文色 | 片段 |
| `spell(spell)` | 法术名 | 法术色(`SPELL`) | 片段 |
| `item(item)` / `item(stack)` / `item(id)` | 物品名:传物品、物品栈或注册 id(`Identifier` 或字符串) | 物品的稀有度颜色,同说明框第一行 | 片段 |
| `key(keybind)` | 按键,带方括号:「[Shift]」 | 按键色(`KEY`) | 片段 |
| `keyName(name)` | 固定名字的按键,带方括号 | 按键色 | 片段 |

- **语言键**:`body`、`muted`、`flavor` 传完整的语言键(`tooltip.examplemod.ruby_sword.passive`)。语言文字里不是参数的字都用这一行的颜色,参数保留自己的颜色。
- **数字**:用原版属性行那种蓝(`ChatFormatting.BLUE`),和原版的「+8 护甲」一个颜色。
- **效果**:`amplifier` 是放大等级(从 0 起),等级总是写出来(I 级也写);10 级以内用原版罗马数字(`enchantment.level.N`),再高写阿拉伯数字。
- **物品**:`item(Items.ROTTEN_FLESH)` 和说明框第一行的物品名同色(按稀有度);传物品栈时附魔过的升一档、改过名的用改后的名字(斜体)。引用别的模组的物品又不想依赖它时传 id:`item("othermod:ruby")`,没装那个模组、id 查不到时写 id 本身(禁用色)。
- **按键**:`key("key.sneak")` 填按键绑定的名字(原版的、模组自己注册的都行),显示玩家此刻实际绑定的键,改键后跟着变;`keyName("Shift")` 用于不走按键绑定的固定按键。
- **触发方式**:`TooltipLines.RIGHT_CLICK`([右键]使用)、`TooltipLines.LEFT_CLICK`([左键]使用)、`TooltipLines.SNEAK`([Shift]使用,按一下就触发)、`TooltipLines.HOLD_SNEAK`(按住[Shift]使用,按着期间持续生效)、`TooltipLines.SNEAK_LEFT_CLICK`([Shift]+[左键]使用)、`TooltipLines.SNEAK_RIGHT_CLICK`([Shift]+[右键]使用),语言键 `tooltip.ibx_lib.trigger.<trigger>`,里面的按键都是按键色、带方括号:`%1$s` 是「[Shift]」,`%2$s` 是「[左键]」,`%3$s` 是「[右键]」(键名语言键 `tooltip.ibx_lib.key.*`)。都不合适时用 `active(Component)` 自己拼触发方式那一段,里面按键的颜色保留。

---

## 5. 注意

- 说明行超过 180 GUI 像素(大约 20 个汉字)自动换行,按原版的切法:优先在空格处断,汉字之间直接断。写语言文件时不用自己拆行。
- 登记的说明插在物品名正下方、原版属性行之前;原版的行和物品自己 `appendHoverText` 加的行都不动,排在登记的说明后面。
- 语言键建议按 `tooltip.<modid>.<物品id>.passive` 命名,第二条被动用 `passive_2`,主动用 `active`。
- 中文语言文字里 `%s` 和前后的字之间不加空格(「攻击有%s概率」);英文照常加空格。
