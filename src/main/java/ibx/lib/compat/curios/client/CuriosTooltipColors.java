package ibx.lib.compat.curios.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Curios 在物品说明里加的两种标题行改成原版的灰色（同原版「在主手时：」），Curios 自己写死的是金色：
 * <ul>
 *   <li>「栏位：」（{@code curios.tooltip.slot}）：只改标题，后面的栏位名保持 Curios 的黄色；</li>
 *   <li>「佩戴饰品时：」「佩戴戒指时：」……（{@code curios.modifiers.<栏位>}）：饰品属性加成上面那行。
 *       「+1 戒指栏位」这类加栏位的行（{@code curios.modifiers.slots.*}）不动。</li>
 * </ul>
 * 按语言键认行，不碰 Curios 的类；在 {@link ItemTooltipEvent} 最后跑（属性行这时已经加进来了）。
 */
public final class CuriosTooltipColors {
    private CuriosTooltipColors() {}

    private static final String SLOT_HEADER = "curios.tooltip.slot";
    private static final String MODIFIERS_HEADER = "curios.modifiers.";
    private static final String SLOT_MODIFIERS = "curios.modifiers.slots.";

    public static void onItemTooltip(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            if (line.getContents() instanceof TranslatableContents contents && isHeader(contents.getKey())) {
                tooltip.set(i, line.copy().withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static boolean isHeader(String key) {
        return key.equals(SLOT_HEADER) || key.startsWith(MODIFIERS_HEADER) && !key.startsWith(SLOT_MODIFIERS);
    }
}
