package ibx.lib.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Curios 饰品栏联动（modId {@code curios}）。本模组对它是<b>非强制依赖</b>。
 *
 * <p><b>凡是会碰到 {@code top.theillusivec4.curios} 的方法，调用前必须先过 {@link #isLoaded()}</b>，
 * 否则玩家没装 Curios 时会 NoClassDefFoundError。</p>
 */
public final class CuriosCompat {
    public static final String MODID = "curios";

    private CuriosCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

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

    /** 饰品栏里有没有满足条件的物品（跳过空格）。两端都能用，同 {@link #forEachCurio}。 */
    public static boolean anyCurio(LivingEntity entity, Predicate<ItemStack> condition) {
        boolean[] found = {false};
        forEachCurio(entity, stack -> found[0] |= condition.test(stack));
        return found[0];
    }

    /** 饰品换了（只在服务端触发）：{@code listener} 拿到穿戴者。 */
    public static void onCurioChange(Consumer<LivingEntity> listener) {
        NeoForge.EVENT_BUS.addListener(CurioChangeEvent.Item.class, event -> listener.accept(event.getEntity()));
    }
}
