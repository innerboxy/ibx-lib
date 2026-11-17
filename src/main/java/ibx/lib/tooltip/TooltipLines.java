package ibx.lib.tooltip;

import ibx.lib.IbxLib;
import ibx.lib.client.UiPalette;
import ibx.lib.client.tooltip.InlineIcons;
import ibx.lib.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLEnvironment;

import org.jspecify.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.function.Function;

/**
 * 物品说明框的通用写法：正文用 {@link UiPalette#BODY}，只给数字、效果名、按键这类关键词单独上色；没标颜色的字都是正文色。
 * 数字用原版属性加成那种蓝（{@link ChatFormatting#BLUE}），和原版的「+8 护甲」一个颜色。
 *
 * <p>分两类方法：整行的（{@link #body}、{@link #muted}、{@link #flavor}、{@link #passive} 等）直接交给说明框；
 * 片段的（{@link #value}、{@link #percent}、{@link #seconds}、{@link #effect}、{@link #spell}、{@link #item}、{@link #block}、{@link #entity}、{@link #attribute}、{@link #biome}、{@link #dimension}、{@link #colored}、{@link #key}）当整行的参数用。
 * 整行的语言键由调用的模组自己定，传完整的键（{@code tooltip.<modid>.<物品id>.passive}）。</p>
 *
 * <p>按物品登记说明、自动换行、按住 Shift 展开见客户端的 {@code ibx.lib.client.tooltip.ItemTooltips}。</p>
 */
public final class TooltipLines {
    private TooltipLines() {}

    private static final String KEY = "tooltip." + IbxLib.MODID + ".";
    private static final DecimalFormat NUMBER = new DecimalFormat("0.#");

    // ---------------------------------------------------------------- 固定文字的行

    /** "被动"：被动特性的标题行（强调色）。 */
    public static Component passive() {
        return Component.translatable(KEY + "passive").withColor(UiPalette.ACCENT);
    }

    /**
     * "主动 · [右键]使用"：主动技能的标题行（强调色）；trigger 是触发方式（{@link #RIGHT_CLICK}、{@link #LEFT_CLICK}、
     * {@link #HOLD_RIGHT_CLICK}、{@link #HOLD_LEFT_CLICK}、{@link #SNEAK}、{@link #HOLD_SNEAK}、{@link #SNEAK_LEFT_CLICK}、
     * {@link #SNEAK_RIGHT_CLICK}），
     * 语言键 {@code tooltip.ibx_lib.trigger.<trigger>}，里面的按键都是按键色、带方括号：
     * {@code %1$s} 是 "[Shift]"，{@code %2$s} 是 "[左键]"，{@code %3$s} 是 "[右键]"（键名语言键 {@code tooltip.ibx_lib.key.*}）。
     */
    public static Component active(String trigger) {
        return active(Component.translatable(KEY + "trigger." + trigger, keyName("Shift"),
                bracketed(Component.translatable(KEY + "key.left_click")),
                bracketed(Component.translatable(KEY + "key.right_click"))));
    }

    /**
     * "主动 · ……"：触发方式不在上面那几种里时用，trigger 由调用方自己拼（强调色）。
     * 里面自带颜色的片段（{@link #key}、{@link #keyName} 的按键色）保持原色。
     */
    public static Component active(Component trigger) {
        return Component.translatable(KEY + "active").append(" · ").append(trigger).withColor(UiPalette.ACCENT);
    }

    /** 触发方式：[右键]使用。 */
    public static final String RIGHT_CLICK = "right_click";
    /** 触发方式：[左键]使用。 */
    public static final String LEFT_CLICK = "left_click";
    /** 触发方式：按住[右键]使用（按着期间持续生效）。 */
    public static final String HOLD_RIGHT_CLICK = "hold_right_click";
    /** 触发方式：按住[左键]使用（按着期间持续生效）。 */
    public static final String HOLD_LEFT_CLICK = "hold_left_click";
    /** 触发方式：[Shift]使用（按一下就触发）。 */
    public static final String SNEAK = "sneak";
    /** 触发方式：按住[Shift]使用（按着期间持续生效）。 */
    public static final String HOLD_SNEAK = "hold_sneak";
    /** 触发方式：[Shift]+[左键]使用。 */
    public static final String SNEAK_LEFT_CLICK = "sneak_left_click";
    /** 触发方式：[Shift]+[右键]使用。 */
    public static final String SNEAK_RIGHT_CLICK = "sneak_right_click";

    /** "冷却 X 秒"（次要色，秒数原版蓝）：ticks 换算成秒。 */
    public static Component cooldown(int ticks) {
        return Component.translatable(KEY + "cooldown", seconds(ticks)).withColor(UiPalette.MUTED);
    }

    /** "按住 [Shift] 查看详情"（禁用色，按键用按键色）：有折叠内容、没按住 Shift 时显示的提示行。 */
    public static Component foldHint() {
        return Component.translatable(KEY + "fold_hint", keyName("Shift")).withColor(UiPalette.DISABLED);
    }

    /** "按住 [Ctrl] 查看更多"（禁用色，按键用按键色）：有按住 Ctrl 才显示的那段、没按住时显示的提示行。 */
    public static Component ctrlHint() {
        return Component.translatable(KEY + "ctrl_hint", keyName("Ctrl")).withColor(UiPalette.DISABLED);
    }

    /** 空行：把说明分成几块。 */
    public static Component blank() {
        return Component.empty();
    }

    // ---------------------------------------------------------------- 整行

    /** 一行正文：key 是完整的语言键，参数自己带颜色（{@link #value} 等），其余的字用正文色。 */
    public static Component body(String key, Object... args) {
        return Component.translatable(key, args).withColor(UiPalette.BODY);
    }

    /** 一行次要字（旧木色）：计数、条件这类补充说明，参数照样自己带颜色。 */
    public static Component muted(String key, Object... args) {
        return Component.translatable(key, args).withColor(UiPalette.MUTED);
    }

    /** 一行风味文字（旧木色、斜体）：背景故事这类不讲机制的话。 */
    public static Component flavor(String key, Object... args) {
        return Component.translatable(key, args).withColor(UiPalette.MUTED).withStyle(ChatFormatting.ITALIC);
    }

    // ---------------------------------------------------------------- 片段

    /** 数值（原版蓝），整数不带小数点。 */
    public static Component value(float number) {
        return Component.literal(NUMBER.format(number)).withStyle(ChatFormatting.BLUE);
    }

    /** 比例写成百分数（原版蓝）：0.06 → "6%"。 */
    public static Component percent(float ratio) {
        return Component.literal(NUMBER.format(ratio * 100.0F) + "%").withStyle(ChatFormatting.BLUE);
    }

    /** tick 换算成秒数（原版蓝）：100 → "5"，30 → "1.5"；"秒"字写在语言文件里。 */
    public static Component seconds(int ticks) {
        return value(ticks / 20.0F);
    }

    /**
     * 效果图标 + 效果名 + 等级（"🛡伤害吸收 II"）：增益用正面色、减益用负面色、中性用正文色。
     * 等级总是写出来（I 级也写）；10 级以内用原版罗马数字（{@code enchantment.level.N}），再高写阿拉伯数字。
     * 图标是原版的药水效果图标（见 {@link #withIcon}），没按住 Shift 折叠时只剩图标 + 等级（"🛡II"）。
     */
    public static Component effect(Holder<MobEffect> effect, int amplifier) {
        int color = switch (effect.value().getCategory()) {
            case BENEFICIAL -> UiPalette.POSITIVE;
            case HARMFUL -> UiPalette.NEGATIVE;
            case NEUTRAL -> UiPalette.BODY;
        };
        int level = amplifier + 1;
        Component levelText = level <= 10 ? Component.translatable("enchantment.level." + level) : Component.literal(Integer.toString(level));
        MutableComponent name = effect.value().getDisplayName().copy().append(" ").append(levelText).withColor(color);
        return withIcon(fallback -> InlineIcons.effect(effect, fallback), name,
                effect.value().getDisplayName().copy().append(" "), levelText.copy().withColor(color));
    }

    /** 法术图标 + 法术名（法术色）；图标同法术轮盘（见 {@link #withIcon}）。 */
    public static Component spell(Spell spell) {
        Component name = spell.getName().copy().withColor(UiPalette.SPELL);
        return withIcon(fallback -> InlineIcons.spell(spell, fallback), name, name, null);
    }

    /** 物品图标 + 物品名，颜色按物品默认的稀有度（和说明框第一行的物品名同色）："腐肉"、"下界之星"。 */
    public static Component item(ItemLike item) {
        return item(new ItemStack(item));
    }

    /**
     * 物品图标 + 物品名，颜色按这一组物品的稀有度（附魔过的会升一档），改过名的用改后的名字、斜体，同原版说明框的第一行。
     * 图标是物品贴图（见 {@link #withIcon}），3D 模型这类没有平面贴图的不带图标。
     */
    public static Component item(ItemStack stack) {
        Component name = stack.getStyledHoverName();
        return withIcon(fallback -> InlineIcons.item(stack, fallback), name, name, null);
    }

    /**
     * 名字前面加小图标（{@code ibx.lib.client.tooltip.InlineIcons}），只在客户端加；icon 给 null（找不到图标）就只有名字。
     * 在 {@code ItemTooltips} 登记的说明里、没按住 Shift 时折叠：只剩图标（foldedTail 不为 null 时再接上它），并记下用到了图标（出折叠提示）。
     *
     * <p>icon 的参数是图标的 fallback（取纯文本时顶替图标的字，JEI 按说明搜索、复述用）：没折叠时名字就在旁边，给空的；
     * 折叠时给 foldedName（被图标顶替掉的那部分名字），所以折叠着纯文本里也有名字。</p>
     */
    private static Component withIcon(Function<Component, @Nullable Component> icon, Component name, Component foldedName,
                                      @Nullable Component foldedTail) {
        if (!FMLEnvironment.getDist().isClient()) {
            return name;
        }
        IconFold fold = FOLD.get();
        Component image = icon.apply(fold.iconsOnly ? foldedName : Component.empty());
        if (image == null) {
            return name;
        }
        fold.iconUsed = true;
        if (fold.iconsOnly) {
            return foldedTail == null ? image : Component.empty().append(image).append(foldedTail);
        }
        return Component.empty().append(image).append(name);
    }

    /**
     * 按注册 id 写物品名（颜色同 {@link #item(ItemLike)}）：引用别的模组的物品又不想依赖它时用。
     * 没装那个模组、id 查不到时写 id 本身（禁用色）。
     */
    public static Component item(Identifier id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return missing(id.toString());
        }
        return item(BuiltInRegistries.ITEM.getValue(id));
    }

    /** 同 {@link #item(Identifier)}，id 写成字符串："minecraft:rotten_flesh"；格式不对时也写原字符串（禁用色）。 */
    public static Component item(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null ? item(parsed) : missing(id);
    }

    /**
     * 方块图标 + 方块名（"黑曜石"），图标和颜色同 {@link #item(ItemLike)}（方块物品的贴图、稀有度）；
     * 没有对应物品的方块（火、传送门这类）不带图标、用原版白。
     */
    public static Component block(Block block) {
        if (block.asItem() != Items.AIR) {
            return item(block);
        }
        return block.getName().copy().withStyle(ChatFormatting.WHITE);
    }

    /** 按注册 id 写方块名（颜色同 {@link #block(Block)}）；id 查不到时写 id 本身（禁用色）。 */
    public static Component block(Identifier id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            return missing(id.toString());
        }
        return block(BuiltInRegistries.BLOCK.getValue(id));
    }

    /** 同 {@link #block(Identifier)}，id 写成字符串："minecraft:obsidian"；格式不对时也写原字符串（禁用色）。 */
    public static Component block(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null ? block(parsed) : missing(id);
    }

    /**
     * 生物群系名（"平原"、"深暗之域"，正文色）：语言键同原版 {@code biome.<命名空间>.<路径>}，
     * 没有翻译（别的模组的群系没装、或者它没写语言文件）时按路径拼一个英文名（"crimson_forest" → "Crimson Forest"）。
     * 群系是数据包注册的，这里不查在不在。
     */
    public static Component biome(ResourceKey<Biome> biome) {
        return biome(biome.identifier());
    }

    /** 同 {@link #biome(ResourceKey)}，传群系 id。 */
    public static Component biome(Identifier id) {
        return Component.translatableWithFallback(Util.makeDescriptionId("biome", id), prettify(id.getPath())).withColor(UiPalette.BODY);
    }

    /** 同 {@link #biome(Identifier)}，id 写成字符串："minecraft:plains"；格式不对时写原字符串（禁用色）。 */
    public static Component biome(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null ? biome(parsed) : missing(id);
    }

    /**
     * 维度名（"下界"，正文色）：原版没有维度的翻译，语言键用 {@code dimension.<命名空间>.<路径>}，
     * lib 给原版三个维度（主世界、下界、末地）写了；别的维度没有翻译时按路径拼英文名。
     */
    public static Component dimension(ResourceKey<Level> dimension) {
        return dimension(dimension.identifier());
    }

    /** 同 {@link #dimension(ResourceKey)}，传维度 id。 */
    public static Component dimension(Identifier id) {
        return Component.translatableWithFallback(Util.makeDescriptionId("dimension", id), prettify(id.getPath())).withColor(UiPalette.BODY);
    }

    /** 同 {@link #dimension(Identifier)}，id 写成字符串："minecraft:the_nether"；格式不对时写原字符串（禁用色）。 */
    public static Component dimension(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null ? dimension(parsed) : missing(id);
    }

    /** 没有翻译时的名字：路径最后一段按下划线拆开、每个词首字母大写。 */
    private static String prettify(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : last.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    /**
     * 生物名（"僵尸"、"别西卜"），颜色按生物的分类：怪物（{@link MobCategory#MONSTER}）用负面色，
     * 动物、水生、环境生物用正面色，其余（村民、铁傀儡、盔甲架、投射物这类 {@link MobCategory#MISC}）用正文色。
     */
    public static Component entity(EntityType<?> type) {
        int color = switch (type.getCategory()) {
            case MONSTER -> UiPalette.NEGATIVE;
            case MISC -> UiPalette.BODY;
            default -> UiPalette.POSITIVE;
        };
        return type.getDescription().copy().withColor(color);
    }

    /** 按注册 id 写生物名（颜色同 {@link #entity(EntityType)}）；id 查不到时写 id 本身（禁用色）。 */
    public static Component entity(Identifier id) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return missing(id.toString());
        }
        return entity(BuiltInRegistries.ENTITY_TYPE.getValue(id));
    }

    /** 同 {@link #entity(Identifier)}，id 写成字符串："minecraft:zombie"；格式不对时也写原字符串（禁用色）。 */
    public static Component entity(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null ? entity(parsed) : missing(id);
    }

    /**
     * 属性图标 + 属性名（"❤最大生命值"，名字正面色）：说"提高 X"、"按 X 计算"这类句子里点到的属性。
     * 图标同属性面板（9×9、按属性 id 找、找不到画 unknown，见 {@code ibx.lib.client.attribute.AttributeIcons}），只在客户端加。
     * 写在 {@code ItemTooltips} 登记的说明里时，没按住 Shift 只显示图标（并出折叠提示），按住才显示图标 + 名字。
     */
    public static Component attribute(Holder<Attribute> attribute) {
        Component name = Component.translatable(attribute.value().getDescriptionId()).withColor(UiPalette.POSITIVE);
        return withIcon(fallback -> InlineIcons.attribute(attribute, fallback), name, name, null);
    }

    /** 按注册 id 写属性名（颜色同 {@link #attribute(Holder)}）；id 查不到时写 id 本身（禁用色）。 */
    public static Component attribute(Identifier id) {
        return BuiltInRegistries.ATTRIBUTE.get(id)
                .<Component>map(TooltipLines::attribute)
                .orElseGet(() -> missing(id.toString()));
    }

    /** 同 {@link #attribute(Identifier)}，id 写成字符串："minecraft:attack_damage"；格式不对时也写原字符串（禁用色）。 */
    public static Component attribute(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null ? attribute(parsed) : missing(id);
    }

    /**
     * 给一段字上色：color 用 {@link UiPalette} 里的语义名（{@code UiPalette.HEAL}、{@code UiPalette.CRIT}……），
     * 用于上面没有专门方法的关键词。text 里自带颜色的部分保持原色。
     */
    public static Component colored(Component text, int color) {
        return text.copy().withColor(color);
    }

    /** 同 {@link #colored(Component, int)}，字从语言文件来：key 是完整的语言键，参数照样自己带颜色。 */
    public static Component colored(String key, int color, Object... args) {
        return Component.translatable(key, args).withColor(color);
    }

    // ---------------------------------------------------------------- 图标片段折叠（ItemTooltips 用）

    /** 折叠状态，每个线程一份：物品说明可能被别的模组在后台线程里取（建搜索索引），不能和画面那边共用。 */
    private static final class IconFold {
        /** 拼说明时带图标的片段只写图标、不写名字（没按住 Shift 时）；效果多留一个等级。 */
        boolean iconsOnly;
        /** 这次拼的那段说明有没有用到带图标的片段。 */
        boolean iconUsed;
    }

    private static final ThreadLocal<IconFold> FOLD = ThreadLocal.withInitial(IconFold::new);

    /**
     * lib 内部用，由 {@code ItemTooltips} 在拼一段说明前调：iconsOnly 为 true 时，接下来带图标的片段
     * （{@link #attribute}、{@link #effect}、{@link #spell}、{@link #item(ItemStack)}、{@link #block(Block)}）只写图标、不写名字。
     * 不在这一对调用之间时总是图标 + 名字。
     */
    public static void beginIconFold(boolean iconsOnly) {
        IconFold fold = FOLD.get();
        fold.iconsOnly = iconsOnly;
        fold.iconUsed = false;
    }

    /** lib 内部用，和 {@link #beginIconFold} 成对：结束折叠，返回这一段有没有用到带图标的片段。 */
    public static boolean endIconFold() {
        IconFold fold = FOLD.get();
        boolean used = fold.iconUsed;
        fold.iconsOnly = false;
        fold.iconUsed = false;
        return used;
    }

    /** id 查不到（没装那个模组）或格式不对时，原样写出来（禁用色）。 */
    private static Component missing(String id) {
        return Component.literal(id).withColor(UiPalette.DISABLED);
    }

    /**
     * 按键（按键色，带方括号）：keybind 是按键绑定的名字（{@code key.sneak}、{@code key.use}、模组自己注册的也行），
     * 显示玩家此刻实际绑定的键，改键后跟着变。
     */
    public static Component key(String keybind) {
        return bracketed(Component.keybind(keybind));
    }

    /** 固定名字的按键（按键色，带方括号）：不走按键绑定的键，比如 "Shift"。 */
    public static Component keyName(String name) {
        return bracketed(Component.literal(name));
    }

    /** 给键名加方括号、上按键色。 */
    private static Component bracketed(Component name) {
        return Component.literal("[").append(name).append("]").withColor(UiPalette.KEY);
    }
}
