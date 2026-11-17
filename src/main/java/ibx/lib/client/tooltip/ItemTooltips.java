package ibx.lib.client.tooltip;

import ibx.lib.tooltip.TooltipLines;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 物品说明框的集中处理器：各模组把说明按物品登记进来（{@link #register}），
 * 这里在 {@link ItemTooltipEvent} 里按物品查表，把说明插在物品名正下方（原版属性行之前）。
 * 每行怎么拼、用什么颜色见 {@link TooltipLines}。
 *
 * <p>说明里的长句按 {@link #MAX_WIDTH} 自动换行（每段保留原来的颜色），所以写说明时不用自己拆行；原版的行不动。</p>
 *
 * <p>一件物品的说明可以分两段：一直显示的，和按住 Shift 才显示的详情。
 * 有详情、没按住时，在说明最后加一行 {@link TooltipLines#foldHint()}。</p>
 *
 * <p>说明里带图标的片段（{@link TooltipLines#attribute}、{@link TooltipLines#effect}、{@link TooltipLines#spell}、
 * {@link TooltipLines#item(ItemStack)}、{@link TooltipLines#block(net.minecraft.world.level.block.Block)}）也跟着折叠：
 * 没按住 Shift 只显示图标（效果多留一个等级），并同样加折叠提示；按住显示图标 + 名字。只看显示出来的段里有没有图标。</p>
 *
 * <p>还可以有第三段，按住 Ctrl 才显示（公式、背景故事这类）：排在前两段后面，没按住时在说明最后加一行 {@link TooltipLines#ctrlHint()}。
 * Shift 和 Ctrl 各管各的，同时按住两段都显示；两行提示都排在说明的最后，Shift 的在前。</p>
 *
 * <p>用到客户端的字体和按键状态，只能在客户端入口里调。</p>
 */
public final class ItemTooltips {
    private ItemTooltips() {}

    /** 说明行的最大宽度（GUI 像素）：大约 20 个汉字。 */
    private static final int MAX_WIDTH = 180;

    /** 一件物品的一段说明：往 tooltip 里按顺序加行。 */
    @FunctionalInterface
    public interface Lines {
        void append(ItemStack stack, Consumer<Component> tooltip);
    }

    /** 登记用：一件物品只能登记一次（所有模组合起来算），重复登记会抛异常。 */
    public interface Registrar {
        /** 只有一直显示的说明。 */
        default void add(ItemLike item, Lines lines) {
            add(item, lines, NONE, NONE);
        }

        /** lines 一直显示，details 按住 Shift 才显示。 */
        default void add(ItemLike item, Lines lines, Lines details) {
            add(item, lines, details, NONE);
        }

        /** lines 一直显示，details 按住 Shift 才显示，extra 按住 Ctrl 才显示；不要哪段就传 {@link #NONE}。 */
        void add(ItemLike item, Lines lines, Lines details, Lines extra);
    }

    /** 空的一段：{@link Registrar#add(ItemLike, Lines, Lines, Lines)} 里不要的那段传它。 */
    public static final Lines NONE = (stack, tooltip) -> {};

    private record Entry(Lines lines, Lines details, Lines extra) {}

    private static final List<Consumer<Registrar>> SOURCES = new ArrayList<>();
    /** 物品注册完才能取到物品，所以第一次显示说明框时再建表。 */
    private static Map<Item, Entry> byItem;

    /**
     * 登记一批物品说明，在客户端入口的构造函数里调。
     * entries 不会马上执行：等第一次显示说明框（物品都注册完了）才调，所以里面可以直接取物品。
     */
    public static synchronized void register(Consumer<Registrar> entries) {
        SOURCES.add(entries);
        byItem = null;
    }

    private static synchronized Map<Item, Entry> table() {
        if (byItem == null) {
            Map<Item, Entry> map = new IdentityHashMap<>();
            Registrar registrar = (item, lines, details, extra) -> {
                if (map.put(item.asItem(), new Entry(lines, details, extra)) != null) {
                    throw new IllegalStateException("Duplicate tooltip for " + item.asItem());
                }
            };
            for (Consumer<Registrar> source : SOURCES) {
                source.accept(registrar);
            }
            byItem = map;
        }
        return byItem;
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Entry entry = table().get(stack.getItem());
        if (entry == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean expanded = mc.hasShiftDown();
        boolean extraShown = mc.hasControlDown();
        List<Component> added = new ArrayList<>();
        List<Component> details = new ArrayList<>();
        List<Component> extra = new ArrayList<>();
        // 折叠掉的图标只算显示出来的段：没显示的段里有图标，按 Shift 也看不出变化，不该出提示
        boolean iconFolded = collect(entry.lines(), stack, !expanded, added);
        collect(entry.details(), stack, !expanded, details);
        boolean extraIconFolded = collect(entry.extra(), stack, !expanded, extra);
        if (expanded) {
            added.addAll(details);
        }
        if (extraShown) {
            added.addAll(extra);
            iconFolded |= extraIconFolded;
        }
        if (!expanded && (!details.isEmpty() || iconFolded)) {
            added.add(TooltipLines.foldHint());
        }
        if (!extraShown && !extra.isEmpty()) {
            added.add(TooltipLines.ctrlHint());
        }
        List<Component> tooltip = event.getToolTip();
        tooltip.addAll(Math.min(1, tooltip.size()), added);
    }

    /** 拼一段说明（长句换行）放进 out；iconsOnly 时带图标的片段只写图标。返回这一段有没有带图标的片段。 */
    private static boolean collect(Lines lines, ItemStack stack, boolean iconsOnly, List<Component> out) {
        boolean iconUsed;
        TooltipLines.beginIconFold(iconsOnly);
        try {
            lines.append(stack, line -> wrap(line, out));
        } finally {
            iconUsed = TooltipLines.endIconFold();
        }
        return iconUsed;
    }

    /**
     * 按 {@link #MAX_WIDTH} 切开一行：原版的切法（优先在空格处断，汉字之间直接断），每段照原样保留颜色；
     * 行里的小图标还原成图标片段（{@link InlineIcons#restore}），取纯文本时和没换行的行一样。
     */
    private static void wrap(Component line, List<Component> out) {
        List<FormattedText> parts = Minecraft.getInstance().font.getSplitter().splitLines(line, MAX_WIDTH, Style.EMPTY);
        if (parts.size() <= 1) {
            out.add(line);
            return;
        }
        for (FormattedText part : parts) {
            MutableComponent piece = Component.empty();
            part.visit((style, text) -> {
                Component icon = InlineIcons.restore(style, text);
                piece.append(icon != null ? icon : Component.literal(text).setStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            out.add(piece);
        }
    }
}
