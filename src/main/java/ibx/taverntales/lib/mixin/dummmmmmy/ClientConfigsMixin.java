package ibx.taverntales.lib.mixin.dummmmmmy;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ibx.taverntales.lib.compat.DummmmmmyCompat;
import ibx.taverntales.lib.registry.TagRegistry;
import net.mehvahdjukaar.dummmmmmy.configs.ClientConfigs;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 试验假人联动：伤害数字的颜色。
 *
 * <p>假人按客户端配置 {@code damage_type_colors} 逐条匹配伤害类型（id 或 #标签），一条都没匹配上时返回 -1（不透明白）。
 * 这里做两件事：</p>
 * <ol>
 *   <li><b>法术专属色</b>：没匹配上、且在 {@code #taverntales_l1b:is_spell} 里时，改用 {@link DummmmmmyCompat#SPELL_DAMAGE_COLOR}。
 *       玩家在配置里写了的仍以配置为准。伤害类型的标签会同步到客户端，所以客户端判标签可行。</li>
 *   <li><b>修假人自己的透明 bug</b>：假人 26.1 版（3.0.1）的数字粒子用颜色的最高字节当不透明度，但它的默认配色都是
 *       {@code 0x00RRGGBB}（配置里写成 {@code "#00FF0000"}），Moonlight 按 ARGB 原样解析，于是暴击、火焰、爆炸……
 *       所有配了颜色的数字都画成全透明——包括近战和远程的暴击。这里把最高字节为 0 的颜色补成不透明。
 *       故意写了半透明（1~254）的颜色不动。</li>
 * </ol>
 *
 * <p>本 mixin 在单独的配置 {@code taverntales_l1b.dummmmmmy.mixins.json} 里，只在装了假人时加载。</p>
 */
@Mixin(ClientConfigs.class)
public abstract class ClientConfigsMixin {
    @ModifyReturnValue(method = "getDamageColor", at = @At("RETURN"))
    private static int taverntales_l1b$fixDamageColor(int original, Holder<DamageType> damageType) {
        if (original == -1) {
            return damageType.is(TagRegistry.IS_SPELL) ? DummmmmmyCompat.SPELL_DAMAGE_COLOR : original;
        }
        return (original >>> 24) == 0 ? original | 0xFF000000 : original;
    }
}
