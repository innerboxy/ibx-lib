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
| 按住 Ctrl 才显示的另一段(公式、背景故事……) | `ItemTooltips.Registrar#add(item, lines, details, extra)` | [3](#3-按住-shift-展开) |

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

            // 主动:「主动 · [右键]使用」+「冷却8秒」+「朝前方放出3颗造成自身40%⚔攻击伤害的火球」(属性名带图标)
            registrar.add(ModItems.RUBY_STAFF, (stack, tooltip) -> {
                tooltip.accept(TooltipLines.active(TooltipLines.RIGHT_CLICK));
                tooltip.accept(TooltipLines.cooldown(160));
                tooltip.accept(TooltipLines.body(KEY + "ruby_staff.active",
                        TooltipLines.value(3),
                        TooltipLines.percent(0.4F),
                        TooltipLines.attribute(Attributes.ATTACK_DAMAGE)));
            });
        });
    }
}
```

`zh_cn.json` 和 `en_us.json`:

```json
{
  "tooltip.examplemod.ruby_sword.passive": "攻击有%s概率给目标加持续%s秒的%s",
  "tooltip.examplemod.ruby_staff.active": "朝前方放出%s颗造成自身%s%s的火球"
}
```

```json
{
  "tooltip.examplemod.ruby_sword.passive": "Attacks have a %s chance to give the target %s seconds of %s",
  "tooltip.examplemod.ruby_staff.active": "Shoots %s fireballs forward, each dealing %s of your %s"
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
    tooltip.accept(TooltipLines.body(KEY + "ruby_staff.active", TooltipLines.value(3), TooltipLines.percent(0.4F),
            TooltipLines.attribute(Attributes.ATTACK_DAMAGE)));
}, (stack, tooltip) -> {
    // 按住 Shift 才显示
    tooltip.accept(TooltipLines.muted(KEY + "ruby_staff.detail", TooltipLines.key("key.sneak")));
    tooltip.accept(TooltipLines.flavor(KEY + "ruby_staff.flavor"));
});
```

- 没按住 Shift 时,说明最后多一行「按住 [Shift] 查看详情」(`TooltipLines.foldHint()`,语言键 `tooltip.ibx_lib.fold_hint`);按住时这一行没有了,`details` 的内容接在 `lines` 后面。
- `details` 一行都没加时(比如按物品栈的状态判断后什么都没写)不显示提示行。
- 说明里用了带图标的片段(`attribute`、`effect`、`spell`、`item`、`block`,见 [4](#4-tooltiplines-一览))时,它们也跟着折叠:没按住 Shift 只显示图标(效果多留一个等级,「🛡II」)、同样加提示行,按住才显示图标 + 名字。这时就算没有 `details` 也会出提示行。只看显示出来的段:图标只在没显示的段里(`details`,或者没按住 Ctrl 时的 `extra`)不算。找不到图标、只写了名字的片段不折叠。
- 看的是键盘上的 Shift 键,不跟潜行键的改键走。

还可以有第三段,按住 Ctrl 才显示:`registrar.add(item, lines, details, extra)`,不要的那段传 `ItemTooltips.NONE`(比如只要 Ctrl 段:`add(item, lines, ItemTooltips.NONE, extra)`)。

- `extra` 排在前两段后面;没按住 Ctrl 时说明最后多一行「按住 [Ctrl] 查看更多」(`TooltipLines.ctrlHint()`,语言键 `tooltip.ibx_lib.ctrl_hint`),`extra` 一行都没加时不显示。
- Shift 和 Ctrl 各管各的:同时按住两段都显示。图标片段的折叠只跟 Shift 走。两行提示都排在说明的最后,Shift 的在前。
- 看的是键盘上的 Ctrl 键(左右都算)。

---

## 4. TooltipLines 一览

`ibx.lib.tooltip.TooltipLines` 的方法分两类:**整行**直接交给 `tooltip.accept`;**片段**当整行的参数用,填进语言文字里的 `%s`。颜色都来自 `ibx.lib.client.UiPalette`。

| 方法 | 产出 | 颜色 | 类型 |
| --- | --- | --- | --- |
| `passive()` | 「被动」,被动特性的标题行 | 强调色(琥珀,`ACCENT`) | 整行 |
| `active(trigger)` | 「主动 · [右键]使用」,主动技能的标题行 | 强调色 | 整行 |
| `cooldown(ticks)` | 「冷却8秒」,tick 换算成秒 | 次要色(旧木,`MUTED`),秒数原版蓝 | 整行 |
| `foldHint()` | 「按住 [Shift] 查看详情」,`ItemTooltips` 自动加 | 禁用色(`DISABLED`),按键用按键色 | 整行 |
| `ctrlHint()` | 「按住 [Ctrl] 查看更多」,`ItemTooltips` 自动加 | 禁用色,按键用按键色 | 整行 |
| `altHint()` | 「按住 [Alt] 查看套装详情」,`GearSetTooltips` 自动加 | 禁用色,按键用按键色 | 整行 |
| `blank()` | 空行,把说明分成几块 | — | 整行 |
| `body(key, args...)` | 一行正文 | 正文色(奶油,`BODY`) | 整行 |
| `muted(key, args...)` | 一行次要说明:计数、条件这类补充 | 次要色 | 整行 |
| `flavor(key, args...)` | 一行风味文字:背景故事这类不讲机制的话 | 次要色、斜体 | 整行 |
| `value(number)` | 数值,最多一位小数,整数不带小数点 | 原版蓝 | 片段 |
| `percent(ratio)` | 比例写成百分数:`0.06` → `6%` | 原版蓝 | 片段 |
| `seconds(ticks)` | tick 换算成秒数:`100` → `5`,`30` → `1.5`;「秒」字写在语言文件里 | 原版蓝 | 片段 |
| `effect(effect, amplifier)` | 效果图标 + 效果名 + 等级:「🛡伤害吸收 II」 | 增益正面色、减益负面色、中性正文色 | 片段 |
| `spell(spell)` | 法术图标 + 法术名 | 法术色(`SPELL`) | 片段 |
| `item(item)` / `item(stack)` / `item(id)` | 物品图标 + 物品名:传物品、物品栈或注册 id(`Identifier` 或字符串) | 物品的稀有度颜色,同说明框第一行 | 片段 |
| `block(block)` / `block(id)` | 方块图标 + 方块名:传方块或注册 id | 同物品(按方块物品的稀有度);没有物品的方块(火、传送门)用原版白 | 片段 |
| `entity(type)` / `entity(id)` | 生物名:传 `EntityType` 或注册 id | 怪物负面色,动物 / 水生 / 环境生物正面色,其余正文色 | 片段 |
| `attribute(attribute)` / `attribute(id)` | 属性图标 + 属性名:「❤最大生命值」 | 图标原色,名字正面色(`POSITIVE`) | 片段 |
| `biome(key)` / `biome(id)` | 生物群系名:「平原」 | 正文色 | 片段 |
| `dimension(key)` / `dimension(id)` | 维度名:「下界」 | 正文色 | 片段 |
| `colored(text, color)` / `colored(key, color, args...)` | 任意一段字上指定的颜色 | 传入的 `UiPalette` 语义色 | 片段 |
| `key(keybind)` | 按键,带方括号:「[Shift]」 | 按键色(`KEY`) | 片段 |
| `keyName(name)` | 固定名字的按键,带方括号 | 按键色 | 片段 |

- **语言键**:`body`、`muted`、`flavor` 传完整的语言键(`tooltip.examplemod.ruby_sword.passive`)。语言文字里不是参数的字都用这一行的颜色,参数保留自己的颜色。
- **数字**:用原版属性行那种蓝(`ChatFormatting.BLUE`),和原版的「+8 护甲」一个颜色。
- **效果**:`amplifier` 是放大等级(从 0 起),等级总是写出来(I 级也写);10 级以内用原版罗马数字(`enchantment.level.N`),再高写阿拉伯数字。
- **物品**:`item(Items.ROTTEN_FLESH)` 和说明框第一行的物品名同色(按稀有度);传物品栈时附魔过的升一档、改过名的用改后的名字(斜体)。引用别的模组的物品又不想依赖它时传 id:`item("othermod:ruby")`,没装那个模组、id 查不到时写 id 本身(禁用色)。
- **生物 / 属性**:`entity(EntityType.ZOMBIE)`、`attribute(Attributes.ATTACK_DAMAGE)`,也都能传 id(`entity("othermod:wraith")`、`attribute("othermod:mana")`),查不到时同物品,写 id 本身(禁用色)。生物的颜色看它的分类(`MobCategory`):`MONSTER` 负面色,`MISC`(村民、铁傀儡、投射物这类)正文色,其余正面色。
- **小图标**:`attribute`、`effect`、`spell`、`item`、`block` 的名字前面带一个小图标,只在客户端加,固定白色、不带阴影(不跟着那一行染色),底边和文字对齐,后面空 1 像素。写在 `ItemTooltips` 登记的说明里时,没按住 Shift 只显示图标(见 [3](#3-按住-shift-展开))。取纯文本时(JEI 按说明文字搜索、复述功能)图标本身不留字;折叠着的图标换成它顶替的名字,所以没按住 Shift 也能按「最大生命值」这类名字搜到。

  | 片段 | 图标 | 画多大 | 找不到时 |
  | --- | --- | --- | --- |
  | `attribute` | 属性面板的图标(找图规则同[属性面板](ATTRIBUTE.md)) | 原尺寸 9×9 | 画占位图标 |
  | `effect` | 原版药水效果图标 `textures/mob_effect/<路径>.png`(18×18) | 缩一半,9×9 | 只写名字 |
  | `spell` | 法术图标 `textures/spell/<路径>.png`(16×16,同法术轮盘) | 缩一半,8×8 | 画法术的占位图标 |
  | `item` / `block` | 物品贴图 `textures/item/<路径>.png`,没有再找 `textures/block/<路径>.png` | 缩一半,8×8 | 只写名字(3D 模型、贴图名和 id 对不上的物品没有图标) |

  图标走原版的「文字里嵌图集贴图」(`Component.object`),原版一律画 8×8;lib 按贴图路径(`hud/icon/`、`mob_effect/`、`spell/`、`item/`、`block/`)改成按整数倍缩小、后面空 1 像素,GUI 缩放下像素齐整。别处(别的模组、`/tellraw`)用原版写法嵌这几类贴图时也会这样画。法术图标本来不在图集里,lib 的 `assets/minecraft/atlases/gui.json` 把各模组的 `textures/spell` 收进 gui 图集(贴图 id `<命名空间>:spell/<路径>`)。
- **方块**:`block(Blocks.OBSIDIAN)`、`block("othermod:ruby_ore")`,id 查不到时同物品,写 id 本身(禁用色)。
- **群系 / 维度**:`biome(Biomes.PLAINS)`、`dimension(Level.NETHER)`,也能传 id。群系的语言键同原版 `biome.<命名空间>.<路径>`;原版没有维度的翻译,语言键用 `dimension.<命名空间>.<路径>`,lib 给原版三个维度写了,自己的维度在自己的语言文件里加。没有翻译时按路径拼一个英文名(`crimson_forest` → `Crimson Forest`)。群系和维度是数据包注册的,不检查在不在。
- **自定颜色**:上面没有专门方法的关键词用 `colored`,比如「吸血」`colored(KEY + "lifesteal", UiPalette.HEAL)`;颜色只传 `UiPalette` 里的语义名,不直接写色值。
- **按键**:`key("key.sneak")` 填按键绑定的名字(原版的、模组自己注册的都行),显示玩家此刻实际绑定的键,改键后跟着变;`keyName("Shift")` 用于不走按键绑定的固定按键。
- **触发方式**:`TooltipLines.RIGHT_CLICK`([右键]使用)、`TooltipLines.LEFT_CLICK`([左键]使用)、`TooltipLines.HOLD_RIGHT_CLICK`(按住[右键]使用,按着期间持续生效)、`TooltipLines.HOLD_LEFT_CLICK`(按住[左键]使用,按着期间持续生效)、`TooltipLines.SNEAK`([Shift]使用,按一下就触发)、`TooltipLines.HOLD_SNEAK`(按住[Shift]使用,按着期间持续生效)、`TooltipLines.SNEAK_LEFT_CLICK`([Shift]+[左键]使用)、`TooltipLines.SNEAK_RIGHT_CLICK`([Shift]+[右键]使用),语言键 `tooltip.ibx_lib.trigger.<trigger>`,里面的按键都是按键色、带方括号:`%1$s` 是「[Shift]」,`%2$s` 是「[左键]」,`%3$s` 是「[右键]」(键名语言键 `tooltip.ibx_lib.key.*`)。都不合适时用 `active(Component)` 自己拼触发方式那一段,里面按键的颜色保留。

---

## 5. 注意

- 说明行超过 180 GUI 像素(大约 20 个汉字)自动换行,按原版的切法:优先在空格处断,汉字之间直接断。写语言文件时不用自己拆行。
- 登记的说明插在物品名正下方、原版属性行之前;原版的行和物品自己 `appendHoverText` 加的行都不动,排在登记的说明后面。
- 语言键建议按 `tooltip.<modid>.<物品id>.passive` 命名,第二条被动用 `passive_2`,主动用 `active`。
- 中文语言文字里 `%s` 和前后的字之间不加空格(「攻击有%s概率」);英文照常加空格。
- 属性的数值写在属性名前面:「8最大生命值」「自身40%攻击伤害」,不写「最大生命值8」;没按住 Shift 只剩图标时读起来是「8❤」。名字带图标的片段写进句子时,前后的字照常连着写,折叠后图标正好顶替名字的位置。

---

## 6. 套装

属于套装的物品,说明框的最后(原版属性行之后)自动多一段「套装名 [在位件数/总件数]」,按住 Alt 展开件位和各档效果,不用在 `ItemTooltips` 里登记。触发钩子这类写不出来的效果用 `GearSetTooltips.describe` 登记说明。见 [装备基类 · 套装的说明框](EQUIPMENT.md#套装的说明框)。
