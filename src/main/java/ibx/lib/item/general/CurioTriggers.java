package ibx.lib.item.general;

import ibx.lib.api.event.SpellCastEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;

/**
 * 饰品的触发钩子：饰品物品类实现这个接口，重写自己需要的时机，其余默认什么都不做。
 *
 * <ul>
 *   <li>只在服务端调用，只认戴在 Curios 饰品栏里的（化妆栏不算）；按饰品栏的槽位顺序逐件调用，同一种戴了几个就调几次。</li>
 *   <li>冷却、触发几率由饰品自己处理（比如用 {@code Player#getCooldowns}、{@code RandomSource}）。</li>
 *   <li>{@code slot} 是这件饰品所在的格子（{@code slot.entity()} 是穿戴者），{@code stack} 是这件饰品本身。</li>
 * </ul>
 *
 * 要用它必须装 Curios：这个接口引用了 Curios 的类，本模组对 Curios 是可选依赖，你的模组要自己把它设成必需。
 * 分发见 {@link ibx.lib.compat.curios.CurioTriggerHandler}。
 */
public interface CurioTriggers {
    // ---------------------------------------------------------------- 伤害

    /**
     * 穿戴者打中别人：本模组的基础伤害（远程伤害、远程暴击）已经定下，还没算护甲、法术抗性等减免。
     * 近战、弹射物（穿戴者是射手）、法术都算，看 {@code source} 区分。
     *
     * @return 这次的伤害量，不改就原样返回 {@code amount}
     */
    default float onDealDamage(SlotContext slot, ItemStack stack, LivingEntity target, DamageSource source, float amount) {
        return amount;
    }

    /**
     * 穿戴者挨打，时间点同 {@link #onDealDamage}（在攻击方的饰品之后调）。
     *
     * @return 这次的伤害量，不改就原样返回 {@code amount}
     */
    default float onTakeDamage(SlotContext slot, ItemStack stack, DamageSource source, float amount) {
        return amount;
    }

    /** 穿戴者打中别人之后：伤害已经结算完、扣了血，{@code amount} 是最终伤害（大于 0 才调）。 */
    default void onDamageDealt(SlotContext slot, ItemStack stack, LivingEntity target, DamageSource source, float amount) {}

    /** 穿戴者挨打之后，同 {@link #onDamageDealt}。 */
    default void onDamageTaken(SlotContext slot, ItemStack stack, DamageSource source, float amount) {}

    /** 穿戴者杀死一个生物（没被别的模组取消的死亡）；召唤物杀的算主人的。 */
    default void onKill(SlotContext slot, ItemStack stack, LivingEntity victim, DamageSource source) {}

    // ---------------------------------------------------------------- 施法和召唤

    /**
     * 穿戴者施法开始前，冷却、法力、目标、法术条件都已通过。
     * 可以 {@code event.setManaCost}、{@code event.setCooldownTicks}，或 {@code event.setCanceled(true)} 取消这次施法。
     */
    default void onSpellCastStart(SlotContext slot, ItemStack stack, SpellCastEvent.Pre event) {}

    /** 穿戴者每次施法生效之后（引导法术每次生效都调）。 */
    default void onSpellCast(SlotContext slot, ItemStack stack, SpellCastEvent.Post event) {}

    /** 穿戴者召唤出一只召唤物之后。 */
    default void onSummon(SlotContext slot, ItemStack stack, Mob summon) {}

    // ---------------------------------------------------------------- 格挡和盾击

    /** 穿戴者举盾挡下一次攻击（原版盾和本模组的盾都算）；{@code blocked} 是挡下的伤害量。 */
    default void onBlock(SlotContext slot, ItemStack stack, DamageSource source, float blocked) {}

    /** 穿戴者打出一次盾击之后；{@code hits} 是打中的生物，可能为空。 */
    default void onShieldBash(SlotContext slot, ItemStack stack, ItemStack shield, List<LivingEntity> hits) {}
}
