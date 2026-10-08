package ibx.lib.spell;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家法术列表里的一条（{@link SpellCasting#getSpells}）：法术 + 提供它的物品。
 * 来源物品会进 {@link CastContext#sourceItem()}（卷轴用完消耗、法杖扣耐久靠它）；
 * 由 {@code SpellListEvent} 临时加进来、不来自物品的条目为 {@link ItemStack#EMPTY}。
 */
public record SpellEntry(Holder<Spell> spell, ItemStack sourceItem) {
    public Spell value() {
        return this.spell.value();
    }
}
