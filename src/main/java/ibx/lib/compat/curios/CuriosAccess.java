package ibx.lib.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.function.Consumer;

/**
 * 读饰品栏、听饰品变化，{@code CuriosCompat} 转过来的实现。
 * 引用了 Curios 的类，只在装了 Curios 时调用（先过 {@code CuriosCompat.isLoaded()}）。
 */
public final class CuriosAccess {
    private CuriosAccess() {}

    /**
     * 按 Curios 的槽位顺序逐个给出饰品栏里的物品（跳过空格）。顺序稳定：Curios 按槽位类型排好序后放进 LinkedHashMap，
     * 同一类型内按格子下标。客户端也能用（Curios 会把自己的饰品栏同步给本人）。
     */
    public static void forEachCurio(LivingEntity entity, Consumer<ItemStack> action) {
        CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> {
            for (ICurioStacksHandler handler : inventory.getCurios().values()) {
                IDynamicStackHandler stacks = handler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        action.accept(stack);
                    }
                }
            }
        });
    }

    /** 饰品换了（只在服务端触发）：{@code listener} 拿到穿戴者。 */
    public static void onCurioChange(Consumer<LivingEntity> listener) {
        NeoForge.EVENT_BUS.addListener(CurioChangeEvent.Item.class, event -> listener.accept(event.getEntity()));
    }
}
