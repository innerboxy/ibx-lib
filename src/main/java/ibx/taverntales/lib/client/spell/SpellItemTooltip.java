package ibx.taverntales.lib.client.spell;

import ibx.taverntales.lib.TavernTalesLib;
import ibx.taverntales.lib.registry.DataComponentRegistry;
import ibx.taverntales.lib.spell.Spell;
import ibx.taverntales.lib.spell.SpellContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 法术容器的物品提示：名字下面列出这件物品提供的法术（{@link SpellContainer#spellsOf}，组件和自己实现接口的物品都算）。
 * 物品用 {@code tooltip_display} 隐藏了 {@code taverntales_l1b:spells} 组件时不显示。
 */
public final class SpellItemTooltip {
    private SpellItemTooltip() {}

    private static final String HEADER = "tooltip." + TavernTalesLib.MODID + ".spells";

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Holder<Spell>> spells = SpellContainer.spellsOf(stack);
        if (spells.isEmpty()
                || !stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).shows(DataComponentRegistry.SPELLS.get())) {
            return;
        }
        List<Component> lines = new ArrayList<>(spells.size() + 1);
        lines.add(Component.translatable(HEADER).withStyle(ChatFormatting.GRAY));
        for (Holder<Spell> spell : spells) {
            lines.add(Component.literal(" ").append(spell.value().getName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        List<Component> tooltip = event.getToolTip();
        tooltip.addAll(Math.min(1, tooltip.size()), lines);
    }
}
