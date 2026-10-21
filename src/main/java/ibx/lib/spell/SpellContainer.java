package ibx.lib.spell;

import ibx.lib.registry.DataComponentRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 提供法术的物品（法典、卷轴、法杖、护甲、饰品……）。玩家能放的法术就是身上所有容器的并集，
 * 见 {@link SpellCasting#getSpells}；玩家本身不会"学会"法术。
 *
 * <p>带数据组件 {@code ibx_lib:spells} 的物品默认就是容器，不用实现本接口。
 * 想按状态变化（充能、镶嵌……）的物品类自己实现本接口，返回值优先于组件。</p>
 */
public interface SpellContainer {
    /** 这件物品现在提供哪些法术，按显示顺序。 */
    List<Holder<Spell>> getSpells(ItemStack stack);

    /**
     * 物品自带的法术：没被 {@code spells} 组件改过时提供的那几个（Ibx 法器就是构造时给的）。默认没有。
     * 选法术界面按 {@link SpellPickerSource#CARRIED} 列法术时一直算它们可选，移出了还能加回来。
     */
    default List<Holder<Spell>> innateSpells(ItemStack stack) {
        return List.of();
    }

    /** 任意物品提供的法术：物品类实现了本接口就问它，否则读 {@code spells} 组件；都没有为空。 */
    static List<Holder<Spell>> spellsOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }
        if (stack.getItem() instanceof SpellContainer container) {
            return container.getSpells(stack);
        }
        return stack.getOrDefault(DataComponentRegistry.SPELLS.get(), List.of());
    }
}
