package ibx.lib.spell;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 法术。原则：<b>注册行只放数值，效果只写在法术类里</b>。框架（{@link SpellCasting}）替法术做掉耗蓝、冷却、读条、同步，
 * 法术类只写钩子。
 *
 * <pre>{@code
 * SPELLS.register("fireball", () -> new FireballSpell(Spell.props().manaCost(25).cooldown(6.0F).castTime(1.0F)));
 *
 * public final class FireballSpell extends Spell {
 *     public FireballSpell(Properties props) { super(props); }
 *     @Override public void onCast(CastContext ctx) { ... ctx.damage(c -> c.spellPower() * 1.6F) ... }
 * }
 * }</pre>
 *
 * <p>注册在 {@link SpellRegistries#SPELL}（{@code ibx_lib:spell}），各模组用自己的 {@code DeferredRegister} 注册。
 * 名字和描述的语言键按 id 推：{@code spell.<命名空间>.<路径>}、{@code spell.<命名空间>.<路径>.desc}。</p>
 *
 * <p>钩子分两端：{@link #onCast} 只在服务端；{@link #onCastStart}、{@link #onCastTick}、{@link #onCastEnd} 两端都调
 * （客户端那次由施法状态同步触发，用来放音效、粒子），法术自己看 {@code ctx.level().isClientSide()} 区分。
 * 扣蓝、伤害只在服务端，由框架保证。</p>
 */
public abstract class Spell {
    private final Properties properties;
    private final CastType castType;

    protected Spell(Properties properties) {
        this.properties = properties;
        this.castType = properties.castType != null
                ? properties.castType
                : properties.castTime > 0.0F ? CastType.CAST : CastType.INSTANT;
    }

    /** 新建一份数值，注册行里用：{@code Spell.props().manaCost(25).cooldown(6.0F).castTime(1.0F)}。 */
    public static Properties props() {
        return new Properties();
    }

    // ---------- 数值（注册行写的原值，未乘属性；实际值请走 SpellCasting） ----------

    /** 基础耗蓝。实际消耗再乘 {@code mana_cost_multiplier}（由 ManaApi 处理）。 */
    public float manaCost() {
        return this.properties.manaCost;
    }

    /** 基础冷却（秒）。实际 = 本值 × {@code spell_cooldown_ratio}，见 {@link SpellCasting#getCooldownTicks}。 */
    public float cooldown() {
        return this.properties.cooldown;
    }

    /** 基础吟唱 / 满蓄时间（秒）。实际 = 本值 ÷ {@code spell_cast_speed}，见 {@link SpellCasting#getCastTicks}。 */
    public float castTime() {
        return this.properties.castTime;
    }

    public CastType castType() {
        return this.castType;
    }

    /** 引导每隔多少 tick 生效一次。不受施法速率影响。 */
    public int tickInterval() {
        return this.properties.tickInterval;
    }

    /** 蓄力比例低于它松手视为取消。 */
    public float minCharge() {
        return this.properties.minCharge;
    }

    /** 吟唱中受击是否打断。 */
    public boolean interruptible() {
        return this.properties.interruptible;
    }

    /**
     * 法术专属的施法动作（玩家动画 id，动画文件放 {@code assets/<命名空间>/player_animations/}）；没写为 null，
     * 放手上法器的默认动作，见 {@link ibx.lib.item.general.IbxMagicWeaponItem#castAnimation}。
     */
    public @Nullable Identifier castAnimation() {
        return this.properties.castAnimation;
    }

    // ---------- 身份 ----------

    /** 注册 id；注册前为 null。 */
    public @Nullable Identifier id() {
        return SpellRegistries.SPELL.getKey(this);
    }

    public Holder<Spell> holder() {
        return SpellRegistries.SPELL.wrapAsHolder(this);
    }

    /** 名字的语言键 {@code spell.<命名空间>.<路径>}。 */
    public String descriptionId() {
        return Util.makeDescriptionId("spell", this.id());
    }

    public Component getName() {
        return Component.translatable(this.descriptionId());
    }

    public Component getDescription() {
        return Component.translatable(this.descriptionId() + ".desc");
    }

    // ---------- 钩子 ----------

    /**
     * 蓝、冷却之外的额外条件（要有目标、脚下有地……），只在服务端调。不满足时可以 {@code ctx.fail(原因)}，
     * 框架把原因发到玩家的 action bar。默认 true。
     */
    public boolean canCast(CastContext ctx) {
        return true;
    }

    /** 轮盘、物品 tooltip 里按施法者算出的数值行（"伤害 16"），客户端调。耗蓝、冷却、读条三行由框架统一显示。默认空。 */
    public List<Component> getTooltip(LivingEntity caster) {
        return List.of();
    }

    /** 开始施法，两端都调；瞬发也调（起手音效、粒子）。 */
    public void onCastStart(CastContext ctx) {
    }

    /**
     * 吟唱 / 引导 / 蓄力期间每 tick 调（读条粒子），两端都调；已施法 tick 见 {@code ctx.elapsed()}。
     * 服务端在引导生效的那一 tick 调的是 {@link #onCast}，不调本方法。
     */
    public void onCastTick(CastContext ctx) {
    }

    /**
     * 生效，<b>只在服务端</b>调，耗蓝已经扣过。瞬发 / 吟唱一次；引导每 {@code tickInterval} 一次；
     * 蓄力松手一次，蓄力比例见 {@code ctx.charge()}。
     */
    public abstract void onCast(CastContext ctx);

    /** 结束，两端都调。收尾放这里（删掉引导生成的射线实体……）。 */
    public void onCastEnd(CastContext ctx, CastEndReason reason) {
    }

    /**
     * 法术的数值，只在注册行里用。默认：不耗蓝、无冷却、瞬发、引导 10 tick 一次、最低蓄力 10%、可被打断、按法器放默认施法动作。
     */
    public static final class Properties {
        float manaCost;
        float cooldown;
        float castTime;
        @Nullable CastType castType;
        int tickInterval = 10;
        float minCharge = 0.1F;
        boolean interruptible = true;
        @Nullable Identifier castAnimation;

        private Properties() {}

        /** 基础耗蓝（点）。 */
        public Properties manaCost(float manaCost) {
            this.manaCost = Math.max(0.0F, manaCost);
            return this;
        }

        /** 基础冷却（秒）。 */
        public Properties cooldown(float seconds) {
            this.cooldown = Math.max(0.0F, seconds);
            return this;
        }

        /** 吟唱 / 满蓄时间（秒）。不写施法类型时，大于 0 即为吟唱。 */
        public Properties castTime(float seconds) {
            this.castTime = Math.max(0.0F, seconds);
            return this;
        }

        /** 施法类型；引导、蓄力要显式写。 */
        public Properties castType(CastType castType) {
            this.castType = castType;
            return this;
        }

        /**
         * 引导每隔多少 tick 生效一次，最少 1。
         *
         * <p><b>注意无敌帧</b>：法术伤害默认吃原版无敌帧（目标的无敌时间大于 10 时，不比上一下高的伤害被挡掉）。
         * 间隔小于等于 10 时，是否被挡取决于施法者和目标谁先 tick，会随机丢伤害——多段伤害的引导法术请在快照上开
         * {@code withLeaveNoIFrames(true)}，命中频率由本间隔控制。</p>
         */
        public Properties tickInterval(int ticks) {
            this.tickInterval = Math.max(1, ticks);
            return this;
        }

        /** 蓄力比例低于它松手视为取消，0~1。 */
        public Properties minCharge(float minCharge) {
            this.minCharge = Math.clamp(minCharge, 0.0F, 1.0F);
            return this;
        }

        /** 吟唱中受击是否打断；不可打断的大招关掉。 */
        public Properties interruptible(boolean interruptible) {
            this.interruptible = interruptible;
            return this;
        }

        /**
         * 专属施法动作，盖过法器的默认动作。动画自己定循环：只放一遍的放完就停；循环的放到施法结束再淡出（至少放 0.6 秒）。
         * 只写一个动画，所有法器都用它；左手施法时自动镜像。
         */
        public Properties castAnimation(Identifier animation) {
            this.castAnimation = animation;
            return this;
        }
    }
}
