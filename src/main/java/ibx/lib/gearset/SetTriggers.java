package ibx.lib.gearset;

import ibx.lib.api.event.SpellCastEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 套装档位的触发钩子：实现这个接口，重写自己需要的时机，其余默认什么都不做；用 {@code bonus(件数, b -> b.triggers(...))} 挂到某一档上。
 *
 * <ul>
 *   <li>只在服务端调用，只在所挂的那一档生效期间调用（手持件离手保留的那 1 秒也算生效）。</li>
 *   <li>同一个时机先调饰品的钩子（{@code CurioTriggers}），再调套装的；套装之间按注册顺序，同一个套装里档位从低到高。</li>
 *   <li>冷却、触发几率由钩子自己处理（比如 {@code Player#getCooldowns}、{@code wearer.getRandom()}）。
 *       一个钩子对象是所有穿戴者共用的，每个穿戴者各自的状态要记在穿戴者身上（附加数据之类），不要记在钩子的字段里。</li>
 * </ul>
 *
 * 分发见 {@link SetTriggerHandler}。
 */
public interface SetTriggers {
    // ---------------------------------------------------------------- 生效和失效

    /**
     * 这一档刚生效。实体读档、重生后进世界，身上的套装会重新生效一次，这里也会再调一次。
     */
    default void onActivate(LivingEntity wearer) {}

    /** 这一档刚掉。实体下线、卸载时不调（状态不存盘）；只适合收拾不存盘的东西。 */
    default void onDeactivate(LivingEntity wearer) {}

    /** 这一档生效期间每刻调一次（穿戴者活着时）。 */
    default void tick(LivingEntity wearer) {}

    // ---------------------------------------------------------------- 伤害

    /**
     * 穿戴者打中别人：本模组的基础伤害（远程伤害、远程暴击）已经定下，还没算护甲、法术抗性等减免。
     * 近战、弹射物（穿戴者是射手）、法术都算，看 {@code source} 区分。
     *
     * @return 这次的伤害量，不改就原样返回 {@code amount}
     */
    default float onDealDamage(LivingEntity wearer, LivingEntity target, DamageSource source, float amount) {
        return amount;
    }

    /**
     * 穿戴者挨打，时间点同 {@link #onDealDamage}（在攻击方的钩子之后调）。
     *
     * @return 这次的伤害量，不改就原样返回 {@code amount}
     */
    default float onTakeDamage(LivingEntity wearer, DamageSource source, float amount) {
        return amount;
    }

    /** 穿戴者打中别人之后：伤害已经结算完、扣了血，{@code amount} 是最终伤害（大于 0 才调）。 */
    default void onDamageDealt(LivingEntity wearer, LivingEntity target, DamageSource source, float amount) {}

    /** 穿戴者挨打之后，同 {@link #onDamageDealt}。 */
    default void onDamageTaken(LivingEntity wearer, DamageSource source, float amount) {}

    /** 穿戴者杀死一个生物（没被别的模组取消的死亡）；召唤物杀的算主人的。 */
    default void onKill(LivingEntity wearer, LivingEntity victim, DamageSource source) {}

    // ---------------------------------------------------------------- 施法和召唤

    /**
     * 穿戴者施法开始前，冷却、法力、目标、法术条件都已通过。
     * 可以 {@code event.setManaCost}、{@code event.setCooldownTicks}，或 {@code event.setCanceled(true)} 取消这次施法。
     */
    default void onSpellCastStart(LivingEntity wearer, SpellCastEvent.Pre event) {}

    /** 穿戴者每次施法生效之后（引导法术每次生效都调）。 */
    default void onSpellCast(LivingEntity wearer, SpellCastEvent.Post event) {}

    /** 穿戴者召唤出一只召唤物之后。 */
    default void onSummon(LivingEntity wearer, Mob summon) {}

    // ---------------------------------------------------------------- 格挡和盾击

    /** 穿戴者举盾挡下一次攻击（原版盾和本模组的盾都算）；{@code blocked} 是挡下的伤害量。 */
    default void onBlock(LivingEntity wearer, DamageSource source, float blocked) {}

    /** 穿戴者打出一次盾击之后；{@code hits} 是打中的生物，可能为空。 */
    default void onShieldBash(LivingEntity wearer, ItemStack shield, List<LivingEntity> hits) {}
}
