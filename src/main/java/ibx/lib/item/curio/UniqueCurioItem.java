package ibx.lib.item.curio;

import ibx.lib.compat.CuriosCompat;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 同一种饰品只能戴一个：饰品栏别的格子里已经有同一物品就不让放。用 {@code CuriosApi.registerCurio(item, UniqueCurioItem.INSTANCE)} 挂到物品上，
 * 其余行为（属性加成等）走 Curios 默认。不挂就是能戴多个，每个都算加成。
 *
 * <p>会碰到 Curios 的类，用之前先过 {@link CuriosCompat#isLoaded()}。</p>
 */
public final class UniqueCurioItem implements ICurioItem {
    public static final UniqueCurioItem INSTANCE = new UniqueCurioItem();

    private UniqueCurioItem() {}

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() == null) {
            return true;
        }
        // 用不带缓存的 findCurios(Predicate)：findCurios(Item) 一刻内只算一次，同一刻连放两个会漏判。
        // 放回自己原来那格（重新校验）不算第二个
        return CuriosApi.getCuriosInventory(slotContext.entity())
                .map(inventory -> inventory.findCurios(worn -> worn.is(stack.getItem())).stream()
                        .allMatch(worn -> isSameSlot(worn.slotContext(), slotContext)))
                .orElse(true);
    }

    private static boolean isSameSlot(SlotContext a, SlotContext b) {
        return a.identifier().equals(b.identifier()) && a.index() == b.index() && a.cosmetic() == b.cosmetic();
    }
}
