package ibx.taverntales.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ibx.taverntales.lib.client.spell.SpellCastInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 右键施法的切换模式：原版在 {@code handleKeybinds} 里每 tick 看右键没按着就停用物品，
 * 切换模式下的施法（{@link SpellCastInput#keepUsing}）跳过这一步，等玩家再按一下右键时由 {@link SpellCastInput} 松手。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
    private void taverntales$keepToggledCast(MultiPlayerGameMode gameMode, Player player, Operation<Void> original) {
        if (!SpellCastInput.keepUsing()) {
            original.call(gameMode, player);
        }
    }
}
