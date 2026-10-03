package ibx.taverntales.lib.mixin;

import ibx.taverntales.lib.client.spell.SpellCastInput;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 选项 → 控制：在原版「使用：切换 / 按住」后面加上吟唱法术、引导法术的「切换 / 按住」，见 {@link SpellCastInput}。 */
@Mixin(ControlsScreen.class)
public abstract class ControlsScreenMixin {
    @Inject(method = "options", at = @At("RETURN"), cancellable = true)
    private static void taverntales$addSpellCastOptions(Options options, CallbackInfoReturnable<OptionInstance<?>[]> cir) {
        cir.setReturnValue(SpellCastInput.addControlOptions(options, cir.getReturnValue()));
    }
}
