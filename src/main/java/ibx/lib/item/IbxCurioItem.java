package ibx.lib.item;

import ibx.lib.compat.curios.CuriosAccess;
import ibx.lib.item.general.CurioTriggers;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.common.DropRule;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 饰品的基类：普通饰品直接 {@code new}，链式设置常用的几样；有自己机制的继承它，重写 {@link ICurioItem}
 * 或 {@link CurioTriggers} 的方法。Curios 会自动认出实现了 {@code ICurioItem} 的物品，不用另外挂。
 *
 * <pre>{@code
 * // 普通饰品
 * ITEMS.registerItem("snorkel", p -> new IbxCurioItem(p).effect(MobEffects.WATER_BREATHING).keepOnDeath(), p -> p.stacksTo(1));
 *
 * // 有自己机制的：在构造函数里设置，重写时记得调 super
 * public class WhoopieCushionItem extends IbxCurioItem {
 *     public WhoopieCushionItem(Properties properties) {
 *         super(properties);
 *         keepOnDeath();
 *     }
 *
 *     @Override
 *     public void curioTick(SlotContext slotContext, ItemStack stack) {
 *         super.curioTick(slotContext, stack);
 *         // ……
 *     }
 * }
 * }</pre>
 *
 * <ul>
 *   <li>设置要在构造物品时做完（{@code registerItem} 的工厂里、或子类构造函数里）。</li>
 *   <li><b>重写这几个方法时要调 {@code super}</b>，不然对应的设置就失效：{@link #canEquip}（{@link #unique()}）、
 *       {@link #curioTick}、{@link #onUnequip}（{@link #effect}）。{@link #isEnderMask}、{@link #makesPiglinsNeutral}、
 *       {@link #getDropRule} 重写了就以子类为准。</li>
 *   <li>槽位、属性加成照旧按 Curios 的办法配（物品标签、{@code curios:attribute_modifiers} 组件）。</li>
 *   <li><b>用它的模组必须装 Curios</b>：这个类实现了 Curios 的接口，没装 Curios 时一加载就 {@code NoClassDefFoundError}。</li>
 * </ul>
 */
public class IbxCurioItem extends Item implements ICurioItem, CurioTriggers {
    /** 一个常驻效果：效果和等级（{@code amplifier} 同 {@code MobEffectInstance}，0 是一级）。 */
    public record Effect(Holder<MobEffect> effect, int amplifier) {}

    private boolean unique;
    private final List<Effect> effects = new ArrayList<>();
    private boolean enderMask;
    private boolean keepOnDeath;
    private boolean piglinNeutral;

    public IbxCurioItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- 设置

    /** 同一种只能戴一个：饰品栏别的格子里已经有同一物品就不让放，放回自己原来那格不算。不设就是能戴多个，每个都算加成。 */
    public IbxCurioItem unique() {
        unique = true;
        return this;
    }

    /** 戴着一直有这个效果（一级）。可以调多次给多个效果。 */
    public IbxCurioItem effect(Holder<MobEffect> effect) {
        return effect(effect, 0);
    }

    /**
     * 戴着一直有这个效果：无限时长，不闪、不冒粒子、HUD 不显示图标；摘下来就去掉。
     * 同一效果的药水：等级更高的生效期间不动它，到期后再补上；等级不比它高的会被盖住。
     * 摘下时只去掉饰品给的那个（无限时长、不显示粒子、等级相同），别处来的留着。
     *
     * @param amplifier 效果等级减 1，0 是一级
     */
    public IbxCurioItem effect(Holder<MobEffect> effect, int amplifier) {
        effects.add(new Effect(effect, amplifier));
        return this;
    }

    /** 看末影人不会惹它，同戴南瓜头。 */
    public IbxCurioItem enderMask() {
        enderMask = true;
        return this;
    }

    /** 死了不掉，留在饰品栏（复活后还戴着）。 */
    public IbxCurioItem keepOnDeath() {
        keepOnDeath = true;
        return this;
    }

    /** 猪灵不主动攻击穿戴者，同穿金盔甲。 */
    public IbxCurioItem piglinNeutral() {
        piglinNeutral = true;
        return this;
    }

    public boolean isUnique() {
        return unique;
    }

    public List<Effect> effects() {
        return Collections.unmodifiableList(effects);
    }

    // ---------------------------------------------------------------- Curios

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return !unique || CuriosAccess.notWornElsewhere(slotContext, stack);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (effects.isEmpty()) return;
        LivingEntity entity = slotContext.entity();
        if (entity.level().isClientSide()) return;
        for (Effect effect : effects) {
            MobEffectInstance current = entity.getEffect(effect.effect());
            if (current == null || current.getAmplifier() < effect.amplifier()) {
                entity.addEffect(new MobEffectInstance(effect.effect(), MobEffectInstance.INFINITE_DURATION,
                        effect.amplifier(), false, false, false));
            }
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (effects.isEmpty()) return;
        LivingEntity entity = slotContext.entity();
        if (entity.level().isClientSide()) return;
        for (Effect effect : effects) {
            MobEffectInstance current = entity.getEffect(effect.effect());
            // 只去掉饰品给的那个；别的饰品还给着同一效果的话，它下一刻会再补上
            if (current != null && current.isInfiniteDuration() && !current.isVisible()
                    && current.getAmplifier() == effect.amplifier()) {
                entity.removeEffect(effect.effect());
            }
        }
    }

    @Override
    public boolean isEnderMask(SlotContext slotContext, EnderMan enderMan, ItemStack stack) {
        return enderMask || ICurioItem.super.isEnderMask(slotContext, enderMan, stack);
    }

    @Override
    public boolean makesPiglinsNeutral(SlotContext slotContext, ItemStack stack) {
        return piglinNeutral || ICurioItem.super.makesPiglinsNeutral(slotContext, stack);
    }

    @Override
    public DropRule getDropRule(SlotContext slotContext, DamageSource source, boolean recentlyHit, ItemStack stack) {
        return keepOnDeath ? DropRule.ALWAYS_KEEP : ICurioItem.super.getDropRule(slotContext, source, recentlyHit, stack);
    }
}
