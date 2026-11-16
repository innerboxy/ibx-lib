package ibx.lib.tooltip;

import ibx.lib.IbxLib;
import ibx.lib.client.UiPalette;
import ibx.lib.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.text.DecimalFormat;

/**
 * 物品说明框的通用写法：正文用 {@link UiPalette#BODY}，只给数字、效果名、按键这类关键词单独上色；没标颜色的字都是正文色。
 * 数字用原版属性加成那种蓝（{@link ChatFormatting#BLUE}），和原版的「+8 护甲」一个颜色。
 *
 * <p>分两类方法：整行的（{@link #body}、{@link #muted}、{@link #flavor}、{@link #passive} 等）直接交给说明框；
 * 片段的（{@link #value}、{@link #percent}、{@link #seconds}、{@link #effect}、{@link #spell}、{@link #item}、{@link #key}）当整行的参数用。
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
     * {@link #SNEAK}、{@link #HOLD_SNEAK}、{@link #SNEAK_LEFT_CLICK}、{@link #SNEAK_RIGHT_CLICK}），
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
     * 效果名 + 等级（"伤害吸收 II"）：增益用正面色、减益用负面色、中性用正文色。
     * 等级总是写出来（I 级也写）；10 级以内用原版罗马数字（{@code enchantment.level.N}），再高写阿拉伯数字。
     */
    public static Component effect(Holder<MobEffect> effect, int amplifier) {
        int color = switch (effect.value().getCategory()) {
            case BENEFICIAL -> UiPalette.POSITIVE;
            case HARMFUL -> UiPalette.NEGATIVE;
            case NEUTRAL -> UiPalette.BODY;
        };
        MutableComponent name = effect.value().getDisplayName().copy();
        int level = amplifier + 1;
        name.append(" ").append(level <= 10 ? Component.translatable("enchantment.level." + level) : Component.literal(Integer.toString(level)));
        return name.withColor(color);
    }

    /** 法术名（法术色）。 */
    public static Component spell(Spell spell) {
        return spell.getName().copy().withColor(UiPalette.SPELL);
    }

    /** 物品名，颜色按物品默认的稀有度（和说明框第一行的物品名同色）："腐肉"、"下界之星"。 */
    public static Component item(ItemLike item) {
        return item(new ItemStack(item));
    }

    /** 物品名，颜色按这一组物品的稀有度（附魔过的会升一档），改过名的用改后的名字、斜体，同原版说明框的第一行。 */
    public static Component item(ItemStack stack) {
        return stack.getStyledHoverName();
    }

    /**
     * 按注册 id 写物品名（颜色同 {@link #item(ItemLike)}）：引用别的模组的物品又不想依赖它时用。
     * 没装那个模组、id 查不到时写 id 本身（禁用色）。
     */
    public static Component item(Identifier id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return Component.literal(id.toString()).withColor(UiPalette.DISABLED);
        }
        return item(BuiltInRegistries.ITEM.getValue(id));
    }

    /** 同 {@link #item(Identifier)}，id 写成字符串："minecraft:rotten_flesh"；格式不对时也写原字符串（禁用色）。 */
    public static Component item(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null ? item(parsed) : Component.literal(id).withColor(UiPalette.DISABLED);
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
