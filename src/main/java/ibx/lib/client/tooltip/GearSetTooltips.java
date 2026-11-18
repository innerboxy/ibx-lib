package ibx.lib.client.tooltip;

import ibx.lib.client.UiPalette;
import ibx.lib.gearset.GearSet;
import ibx.lib.gearset.GearSets;
import ibx.lib.tooltip.TooltipLines;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 套装在物品说明框里的那一段：属于套装的物品自动带上，排在说明框的最后（原版属性行之后）。
 *
 * <pre>
 * 猎人套装 [2/4]              ← 套装名 + 在位件数 / 总件数（强调色），属于几个套装就列几行
 * 按住[Alt]查看套装详情       ← 没按住 Alt 时
 * </pre>
 *
 * 按住 Alt 时每个套装下面展开：
 *
 * <pre>
 * 猎人套装 [2/4]
 *  猎人头盔                  ← 件位：在位的正面色，不在位的禁用色；任选一的件位把几样用 " / " 连起来
 *  猎人弓 / 猎人弩
 * [2] +4 最大生命值          ← 各档效果：生效的照常上色、件数用正面色；没生效的整行禁用色
 * [4] 夜视 I
 * </pre>
 *
 * <p>各档里的属性加成、常驻效果自动写出来（属性行同原版的写法）；触发钩子这类写不出来的效果，
 * 用 {@link #describe} 给这一档登记说明，排在自动写的行后面。件数按看说明的玩家眼下的装备现数，不算离手保留。</p>
 *
 * <p>用到客户端的字体和按键状态，只能在客户端入口里调。</p>
 */
public final class GearSetTooltips {
    private GearSetTooltips() {}

    private static final String KEY = "tooltip.ibx_lib.gearset.";

    /** 一档效果的说明：往 tooltip 里按顺序加行，写法同物品说明（{@link TooltipLines}），长句自动换行。 */
    @FunctionalInterface
    public interface Lines {
        void append(Consumer<Component> tooltip);
    }

    private static final Map<GearSet.Bonus, List<Lines>> DESCRIPTIONS = new ConcurrentHashMap<>();

    /**
     * 给套装的某一档登记说明，在客户端入口的构造函数里调；同一档可以登记多次，按登记顺序排。
     * lines 每次显示说明框时才执行。
     *
     * @param pieces 哪一档（件数），套装里没有这一档会抛异常
     */
    public static void describe(GearSet set, int pieces, Lines lines) {
        for (GearSet.Bonus bonus : set.bonuses()) {
            if (bonus.pieces() == pieces) {
                DESCRIPTIONS.computeIfAbsent(bonus, key -> new CopyOnWriteArrayList<>()).add(lines);
                return;
            }
        }
        throw new IllegalArgumentException("GearSet " + set.id() + " has no bonus for " + pieces + " pieces");
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<GearSet> sets = GearSets.setsOf(stack.getItem());
        if (sets.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean detailed = mc.hasAltDown();
        LivingEntity wearer = event.getEntity() != null ? event.getEntity() : mc.player;

        List<Component> added = new ArrayList<>();
        added.add(TooltipLines.blank());
        for (GearSet set : sets) {
            // 展开时几个套装之间空一行
            if (detailed && added.size() > 1) {
                added.add(TooltipLines.blank());
            }
            int count = wearer == null ? 0 : GearSets.countWorn(wearer, set);
            added.add(Component.translatable(KEY + "name", set.name(), count, set.pieces().size()).withColor(UiPalette.ACCENT));
            if (detailed) {
                for (GearSet.Piece piece : set.pieces()) {
                    ItemTooltips.wrap(pieceLine(piece, wearer != null && GearSets.isWorn(wearer, piece)), added);
                }
                for (GearSet.Bonus bonus : set.bonuses()) {
                    bonusLines(bonus, count >= bonus.pieces(), event.getFlags(), added);
                }
            }
        }
        if (!detailed) {
            added.add(TooltipLines.altHint());
        }
        List<Component> tooltip = event.getToolTip();
        tooltip.addAll(insertIndex(tooltip, stack, event.getFlags()), added);
    }

    /** 一个件位那一行：物品图标 + 物品名，任选一的几样用 " / " 连起来。 */
    private static Component pieceLine(GearSet.Piece piece, boolean worn) {
        MutableComponent line = Component.literal(" ");
        boolean first = true;
        for (Item item : piece.items()) {
            if (!first) {
                line.append(" / ");
            }
            first = false;
            ItemStack stack = new ItemStack(item);
            Component icon = InlineIcons.item(stack, Component.empty());
            if (icon != null) {
                line.append(icon);
            }
            line.append(stack.getHoverName().copy());
        }
        return line.withColor(worn ? UiPalette.POSITIVE : UiPalette.DISABLED);
    }

    /** 一档效果的各行，每行前面带 "[件数] "。 */
    private static void bonusLines(GearSet.Bonus bonus, boolean active, TooltipFlag flags, List<Component> out) {
        List<Component> lines = new ArrayList<>();
        for (GearSet.AttributeBonus attribute : bonus.attributes()) {
            lines.add(attribute.attribute().value().toComponent(attribute.modifier(), flags));
        }
        for (GearSet.EffectBonus effect : bonus.effects()) {
            lines.add(TooltipLines.effect(effect.effect(), effect.amplifier()));
        }
        for (Lines described : DESCRIPTIONS.getOrDefault(bonus, List.of())) {
            described.append(lines::add);
        }
        for (Component line : lines) {
            Component full = Component.empty()
                    .append(Component.literal("[" + bonus.pieces() + "] ").withColor(UiPalette.POSITIVE))
                    .append(line)
                    .withColor(UiPalette.BODY);
            ItemTooltips.wrap(active ? full : dimmed(full), out);
        }
    }

    /** 整行换成禁用色（没生效的档位）；行里的小图标原样留着。 */
    private static Component dimmed(Component line) {
        MutableComponent out = Component.empty();
        line.visit((style, text) -> {
            Component icon = InlineIcons.restore(style, text);
            out.append(icon != null ? icon : Component.literal(text).setStyle(style.withColor(UiPalette.DISABLED)));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /**
     * 套装这一段插在哪：说明框最后；开了高级提示（F3+H）时排在耐久、物品 id 那几行调试信息前面。
     */
    private static int insertIndex(List<Component> tooltip, ItemStack stack, TooltipFlag flags) {
        if (!flags.isAdvanced()) {
            return tooltip.size();
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (int i = tooltip.size() - 1; i >= 1; i--) {
            if (tooltip.get(i).getString().equals(id)) {
                return isDurabilityLine(tooltip.get(i - 1)) ? i - 1 : i;
            }
        }
        return tooltip.size();
    }

    private static boolean isDurabilityLine(@Nullable Component line) {
        return line != null && line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("item.durability");
    }
}
