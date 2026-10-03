package ibx.taverntales.lib.spell;

import ibx.taverntales.lib.api.ManaApi;
import ibx.taverntales.lib.api.event.SpellCastEvent;
import ibx.taverntales.lib.registry.AttachmentRegistry;
import ibx.taverntales.lib.registry.AttributeRegistry;
import ibx.taverntales.lib.registry.TagRegistry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

import java.text.DecimalFormat;

/**
 * 施法的唯一入口。施法者是玩家、生物都一样：{@code SpellCasting.cast(caster, spell)}。
 *
 * <ul>
 *   <li>{@link #cast}：检查（正在施法 → 法术条件 → 冷却 → 法力）→ {@link SpellCastEvent.Pre} → 按类型开始。
 *       失败时把原因发到玩家的 action bar。</li>
 *   <li>读条由框架推进（{@link ActiveCast} + 每 tick 的 {@link #onEntityTick}），生物 AI 不用自己计时，
 *       只要 {@link #isCasting} / {@link #stopCasting}。</li>
 *   <li>扣蓝、冷却、卷轴消耗按 {@link CastSource} 结算；生效过至少一次才进冷却、才消耗卷轴。</li>
 * </ul>
 *
 * <table>
 *   <caption>各类型的流程</caption>
 *   <tr><td>瞬发</td><td>扣蓝 → onCastStart → onCast → 结束（完成）</td></tr>
 *   <tr><td>吟唱</td><td>onCastStart → 每 tick onCastTick → 到点扣蓝、onCast → 结束（完成）；松手取消、受击打断都不扣不冷却</td></tr>
 *   <tr><td>引导</td><td>onCastStart → 立刻生效一次 → 之后每 tickInterval 扣蓝、onCast，其余 tick onCastTick；松手结束，蓝尽结束</td></tr>
 *   <tr><td>蓄力</td><td>onCastStart → 每 tick onCastTick → 松手（{@link #stopCasting}）按蓄力比例扣蓝、onCast；不足 minCharge 取消</td></tr>
 * </table>
 *
 * <p>所有结算只在服务端；客户端调用 {@link #cast} 只做判断（预测用），不改任何数据。</p>
 */
public final class SpellCasting {
    private SpellCasting() {}

    private static final DecimalFormat SECONDS = new DecimalFormat("0.0");

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpellCasting::onEntityTick);
        // 最后才看：前面的监听取消了这次伤害（免疫、格挡类模组）就不该打断吟唱。
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpellCasting::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onDeath);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onLeaveLevel);
    }

    // ---------------------------------------------------------------- 数值

    /** 实际吟唱 / 满蓄 tick = 吟唱时间 × 20 ÷ {@code spell_cast_speed}，最少 1；瞬发、引导为 0。 */
    public static int getCastTicks(LivingEntity caster, Spell spell) {
        if (spell.castType() != CastType.CAST && spell.castType() != CastType.CHARGE) {
            return 0;
        }
        // spell_cast_speed 下限 0.01，不会除以零。
        double ticks = spell.castTime() * 20.0D / readAttribute(caster, AttributeRegistry.SPELL_CAST_SPEED);
        return Math.max(1, (int) Math.round(ticks));
    }

    /** 实际冷却 tick = 冷却 × 20 × {@code spell_cooldown_ratio}。 */
    public static int getCooldownTicks(LivingEntity caster, Spell spell) {
        double ticks = spell.cooldown() * 20.0D * readAttribute(caster, AttributeRegistry.SPELL_COOLDOWN_RATIO);
        return Math.max(0, (int) Math.round(ticks));
    }

    // ---------------------------------------------------------------- 施法

    /** 按施法者推断来源施法：玩家 → {@link CastSource#ITEM}，生物 → {@link CastSource#MOB}。 */
    public static CastResult cast(LivingEntity caster, Spell spell) {
        return cast(caster, spell, caster instanceof Player ? CastSource.ITEM : CastSource.MOB);
    }

    public static CastResult cast(LivingEntity caster, Holder<Spell> spell) {
        return cast(caster, spell.value());
    }

    public static CastResult cast(LivingEntity caster, Spell spell, CastSource source) {
        return cast(caster, spell, source, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    /**
     * 施法。
     *
     * @param triggerItem 右键的那件施法物品，没有填 {@link ItemStack#EMPTY}
     * @param sourceItem  提供这个法术的物品（卷轴消耗、法杖耐久靠它），没有填 {@link ItemStack#EMPTY}
     */
    public static CastResult cast(LivingEntity caster, Spell spell, CastSource source,
                                  ItemStack triggerItem, ItemStack sourceItem) {
        if (caster.level().isClientSide()) {
            return check(caster, spell, source);
        }
        ActiveCast active = caster.getData(AttachmentRegistry.ACTIVE_CAST);
        if (active.current != null) {
            if (source != CastSource.COMMAND) {
                return fail(caster, CastResult.ALREADY_CASTING, null);
            }
            end(caster, active, CastEndReason.CANCELLED);
        }

        CastContext ctx = new CastContext(caster, spell, source, triggerItem, sourceItem,
                caster.level().getGameTime(), getCastTicks(caster, spell));
        if (!spell.canCast(ctx)) {
            return fail(caster, CastResult.CONDITION_FAILED, ctx.failure);
        }
        if (source.triggersCooldown() && !SpellCooldowns.isReady(caster, spell)) {
            return fail(caster, CastResult.ON_COOLDOWN, Component.translatable(CastResult.ON_COOLDOWN.messageKey(),
                    SECONDS.format(SpellCooldowns.getRemaining(caster, spell) / 20.0D)));
        }
        if (source.consumesMana() && !ManaApi.canAfford(caster, spell.manaCost())) {
            return fail(caster, CastResult.NOT_ENOUGH_MANA, null);
        }
        SpellCastEvent.Pre pre = NeoForge.EVENT_BUS.post(new SpellCastEvent.Pre(caster, spell, source,
                spell.manaCost(), getCooldownTicks(caster, spell)));
        if (pre.isCanceled()) {
            return CastResult.CONDITION_FAILED;
        }
        ctx.manaCost = pre.getManaCost();
        ctx.cooldownTicks = pre.getCooldownTicks();

        if (spell.castType() == CastType.INSTANT) {
            // 先扣：施法前事件可能把耗蓝改高了。
            if (!pay(ctx)) {
                return fail(caster, CastResult.NOT_ENOUGH_MANA, null);
            }
            // 也走一遍"开始 → 结束"的同步，客户端的开始 / 结束钩子（起手音效、粒子）才会跑。
            start(caster, active, ctx);
            effect(ctx);
            end(caster, active, CastEndReason.COMPLETED);
            return CastResult.SUCCESS;
        }

        start(caster, active, ctx);
        if (spell.castType() == CastType.CHANNEL && active.current == ctx && !fire(ctx)) {
            end(caster, active, CastEndReason.OUT_OF_MANA);
        }
        return CastResult.SUCCESS;
    }

    /** 是否正在施法。两端都能问（客户端靠同步）。 */
    public static boolean isCasting(LivingEntity caster) {
        return getCurrentCast(caster) != null;
    }

    /** 正在施的那一次；没在施法为 null。两端都能问。 */
    public static @Nullable CastContext getCurrentCast(LivingEntity caster) {
        ActiveCast active = caster.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        return active == null ? null : active.current;
    }

    /**
     * 松手 / AI 主动停：吟唱取消、引导结束（生效过就进冷却），蓄力按当前比例结算（不足 minCharge 取消）。
     * 只在服务端生效；没在施法时什么也不做。
     */
    public static void stopCasting(LivingEntity caster) {
        if (caster.level().isClientSide()) {
            return;
        }
        ActiveCast active = caster.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        CastContext ctx = active == null ? null : active.current;
        if (ctx == null) {
            return;
        }
        if (ctx.spell().castType() == CastType.CHARGE) {
            ctx.charge = Math.min(1.0F, (float) ctx.elapsed() / ctx.castTicks());
            if (ctx.charge < ctx.spell().minCharge()) {
                end(caster, active, CastEndReason.CANCELLED);
            } else {
                end(caster, active, fire(ctx) ? CastEndReason.COMPLETED : CastEndReason.OUT_OF_MANA);
            }
            return;
        }
        end(caster, active, CastEndReason.CANCELLED);
    }

    /**
     * 强制结束（打断、死亡、离开世界……）：不结算蓄力，直接按 {@code reason} 结束；引导生效过的照常进冷却。
     * 只在服务端生效；没在施法时什么也不做。
     */
    public static void cancelCasting(LivingEntity caster, CastEndReason reason) {
        if (caster.level().isClientSide()) {
            return;
        }
        ActiveCast active = caster.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        if (active != null && active.current != null) {
            end(caster, active, reason);
        }
    }

    // ---------------------------------------------------------------- 内部流程

    /** 客户端的判断：不跑法术条件（只在服务端），只看正在施法、冷却、法力。 */
    private static CastResult check(LivingEntity caster, Spell spell, CastSource source) {
        if (isCasting(caster)) {
            return CastResult.ALREADY_CASTING;
        }
        if (source.triggersCooldown() && !SpellCooldowns.isReady(caster, spell)) {
            return CastResult.ON_COOLDOWN;
        }
        if (source.consumesMana() && !ManaApi.canAfford(caster, spell.manaCost())) {
            return CastResult.NOT_ENOUGH_MANA;
        }
        return CastResult.SUCCESS;
    }

    private static void start(LivingEntity caster, ActiveCast active, CastContext ctx) {
        active.current = ctx;
        active.lastEnd = null;
        caster.syncData(AttachmentRegistry.ACTIVE_CAST);
        ctx.spell().onCastStart(ctx);
    }

    /** 扣蓝（按来源）+ 生效。付不起返回 false，不生效。 */
    private static boolean fire(CastContext ctx) {
        if (!pay(ctx)) {
            return false;
        }
        effect(ctx);
        return true;
    }

    private static boolean pay(CastContext ctx) {
        return !ctx.source().consumesMana() || ManaApi.tryConsume(ctx.caster(), ctx.manaCost);
    }

    private static void effect(CastContext ctx) {
        ctx.effects++;
        ctx.spell().onCast(ctx);
        NeoForge.EVENT_BUS.post(new SpellCastEvent.Post(ctx));
    }

    /**
     * 结束：先清状态并同步（法术在结束钩子里再施法时不会被当成"正在施法"，客户端也先收到结束），
     * 生效过就进冷却、消耗卷轴，最后调结束钩子。
     */
    private static void end(LivingEntity caster, ActiveCast active, CastEndReason reason) {
        CastContext ctx = active.current;
        if (ctx == null) {
            return;
        }
        active.current = null;
        active.lastEnd = reason;
        if (ctx.effects > 0) {
            if (ctx.source().triggersCooldown() && !isCreative(caster)) {
                SpellCooldowns.start(caster, ctx.spell(), ctx.cooldownTicks);
            }
            if (ctx.source() == CastSource.SCROLL && !ctx.sourceItem().isEmpty()) {
                ctx.sourceItem().consume(1, caster);
            }
        }
        caster.syncData(AttachmentRegistry.ACTIVE_CAST);
        ctx.spell().onCastEnd(ctx, reason);
    }

    /** 失败：玩家在 action bar 看到原因（{@code message} 为空时用结果自带的提示）。 */
    private static CastResult fail(LivingEntity caster, CastResult result, @Nullable Component message) {
        if (caster instanceof Player player) {
            player.sendOverlayMessage(message != null ? message : Component.translatable(result.messageKey()));
        }
        return result;
    }

    // ---------------------------------------------------------------- 事件

    /** 推进读条。客户端只调法术的 onCastTick（表现用）。 */
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity caster)) {
            return;
        }
        ActiveCast active = caster.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        CastContext ctx = active == null ? null : active.current;
        if (ctx == null) {
            return;
        }
        Spell spell = ctx.spell();
        if (caster.level().isClientSide()) {
            spell.onCastTick(ctx);
            return;
        }
        if (caster.isDeadOrDying()) {
            return;
        }
        switch (spell.castType()) {
            case CAST -> {
                if (ctx.elapsed() >= ctx.castTicks()) {
                    end(caster, active, fire(ctx) ? CastEndReason.COMPLETED : CastEndReason.OUT_OF_MANA);
                } else {
                    spell.onCastTick(ctx);
                }
            }
            case CHANNEL -> {
                // 按"到现在应生效几次"补齐，而不是 elapsed % 间隔 == 0：生物所在区块偶尔不跑实体 tick 时，
                // 取模会把那一次永久跳过；这里下一 tick 补上（每 tick 最多补一次）。开始那一 tick 的第一次已在 cast 里生效。
                int due = ctx.elapsed() / spell.tickInterval() + 1;
                if (ctx.effects < due) {
                    if (!fire(ctx)) {
                        end(caster, active, CastEndReason.OUT_OF_MANA);
                    }
                } else {
                    spell.onCastTick(ctx);
                }
            }
            case CHARGE -> spell.onCastTick(ctx);
            case INSTANT -> end(caster, active, CastEndReason.COMPLETED);
        }
    }

    /** 吟唱中受击打断：只对吟唱、可打断的法术；带 {@code #cast_ignore} 的持续掉血不算。 */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        CastContext ctx = getCurrentCast(event.getEntity());
        if (ctx != null
                && ctx.spell().castType() == CastType.CAST
                && ctx.spell().interruptible()
                && event.getAmount() > 0.0F
                && !event.getSource().is(TagRegistry.CAST_IGNORE)) {
            cancelCasting(event.getEntity(), CastEndReason.INTERRUPTED);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        cancelCasting(event.getEntity(), CastEndReason.CANCELLED);
    }

    /** 下线、换维度、卸载、被移除：施法状态不存盘，在这里补上结束钩子让法术收尾。 */
    private static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) {
            cancelCasting(living, CastEndReason.CANCELLED);
        }
    }

    private static boolean isCreative(LivingEntity entity) {
        return entity instanceof Player player && player.isCreative();
    }

    /** 本模组的属性挂在所有生物上，这里只防万一：没挂的实体按属性默认值算。 */
    private static double readAttribute(LivingEntity entity, Holder<Attribute> attribute) {
        return entity.getAttributes().hasAttribute(attribute)
                ? entity.getAttributeValue(attribute)
                : attribute.value().getDefaultValue();
    }
}
