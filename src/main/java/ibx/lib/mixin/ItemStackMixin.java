package ibx.lib.mixin;

import ibx.lib.registry.TagRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 施法物品（{@code #ibx_lib:can_cast_spell}）的使用时长统一给 72000（同弓）：右键施法时挂上的"使用物品"
 * 要能一直按着，什么时候停由施法框架决定。挂在 {@code ItemStack} 上，任何物品进了标签都生效，物品类不用自己写。
 *
 * <p>代价：标签里的食物、药水这类本来有使用时长的物品吃 / 喝不完了——这种物品不该进标签。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void ibx_lib$spellUseDuration(LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (((ItemStack) (Object) this).is(TagRegistry.CAN_CAST_SPELL)) {
            cir.setReturnValue(72000);
        }
    }
}
