package ibx.lib.compat.curios;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 饰品栏的鞘翅槽位（槽位 id {@code elytra}，定义在 {@code data/ibx_lib/curios/slots/elytra.json}）：
 * 放在这里的鞘翅和穿在胸甲栏里一样能滑翔、滑翔时一样掉耐久。
 *
 * <ul>
 *   <li><b>本模组只提供功能，玩家默认 0 格</b>：要用的模组自己加格数（槽位文件写 {@code size}，或 Curios 的槽位修饰符）。</li>
 *   <li>能放什么同 Curios 别的槽位，按标签：{@code #curios:elytra} 和 {@code #curios:curio} 里的物品。</li>
 *   <li>放进来的物品里，带 {@code minecraft:glider} 组件的（原版鞘翅，以及别的模组照原版做的鞘翅）才能滑翔。</li>
 *   <li>能不能用同原版：再掉一点耐久就坏的不算。</li>
 *   <li>胸甲栏和鞘翅槽都有鞘翅时，滑翔掉的耐久同原版一样在所有能用的鞘翅里随机挑一件扣。</li>
 * </ul>
 *
 * 滑翔判定由 {@code ibx.lib.mixin.curios.LivingEntityElytraSlotMixin} 接进原版。
 * 引用了 Curios 的类，只在装了 Curios 时由本模组调用。
 */
public final class ElytraCurioSlot {
    /** 槽位 id。 */
    public static final String SLOT = "elytra";

    private ElytraCurioSlot() {}

    /** 放在鞘翅槽里能不能用来滑翔：带滑翔组件。 */
    public static boolean isGlider(ItemStack stack) {
        return stack.has(DataComponents.GLIDER);
    }

    /** 鞘翅槽里现在能用来滑翔的鞘翅，按格子顺序；化妆栏不算。两端都能用。 */
    public static List<SlotResult> usableElytras(LivingEntity entity) {
        List<SlotResult> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity)
                .flatMap(inventory -> inventory.getStacksHandler(SLOT))
                .ifPresent(handler -> collectUsable(entity, handler, out));
        return out;
    }

    private static void collectUsable(LivingEntity entity, ICurioStacksHandler handler, List<SlotResult> out) {
        IDynamicStackHandler stacks = handler.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (isGlider(stack) && !stack.nextDamageWillBreak()) {
                boolean visible = i < handler.getRenders().size() && handler.getRenders().get(i);
                out.add(new SlotResult(new SlotContext(SLOT, entity, i, false, visible), stack));
            }
        }
    }

    /** 靠鞘翅槽能不能滑翔。不能滑翔的情形同原版：在地上、骑着东西、飘浮。 */
    public static boolean canGlide(LivingEntity entity) {
        return !entity.onGround()
                && !entity.isPassenger()
                && !entity.hasEffect(MobEffects.LEVITATION)
                && !usableElytras(entity).isEmpty();
    }

    /**
     * 滑翔到了该掉耐久的时候（只在服务端）：鞘翅槽里能用的鞘翅和身上 {@code equipmentGliders} 件能用的滑翔装备一起随机挑一件。
     *
     * @return 挑中的是鞘翅槽里的、已经扣过耐久时返回 {@code true}；返回 {@code false} 就照原版扣身上的
     */
    public static boolean damageOnGlide(LivingEntity entity, int equipmentGliders) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        List<SlotResult> elytras = usableElytras(entity);
        if (elytras.isEmpty()) {
            return false;
        }
        int pick = entity.getRandom().nextInt(equipmentGliders + elytras.size());
        if (pick < equipmentGliders) {
            return false;
        }
        SlotResult chosen = elytras.get(pick - equipmentGliders);
        chosen.stack().hurtAndBreak(1, level, entity, item -> CuriosApi.broadcastCurioBreakEvent(chosen.slotContext()));
        return true;
    }
}
