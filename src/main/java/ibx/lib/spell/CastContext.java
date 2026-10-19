package ibx.lib.spell;

import ibx.lib.api.SpellDamageApi;
import ibx.lib.api.SpellDamageSnapshot;
import ibx.lib.target.TargetApi;
import ibx.lib.target.TargetLock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 一次施法的上下文，从开始到结束是同一个对象，交给 {@link Spell} 的各个钩子。由 {@link SpellCasting} 创建。
 *
 * <p>服务端和客户端各有一份：客户端那份由施法状态同步时建出来，只有施法者、法术、来源、开始时刻和读条长度，
 * 没有物品，{@link #data} 槽也是各管各的。</p>
 */
public final class CastContext {
    private final LivingEntity caster;
    private final Spell spell;
    private final CastSource source;
    private final ItemStack triggerItem;
    private final ItemStack sourceItem;
    private final long startTick;
    private final int castTicks;

    /** 本次的基础耗蓝、冷却 tick；施法前事件可以改，之后由框架按它们结算。 */
    float manaCost;
    int cooldownTicks;
    /** 已生效次数：引导每次生效加一；大于 0 才进冷却、才消耗卷轴。 */
    int effects;
    float charge = 1.0F;
    /** 还没扣的耐久零头：施法物品按蓝量掉耐久时四舍五入剩下的，留给同一次施法的下一跳（引导）。 */
    float pendingWear;
    @Nullable Component failure;
    /** 由"放当前选中"发起（{@link SpellCasting#castSelected}）：提供它的装备被卸下时要取消。只在服务端有意义。 */
    boolean fromSelection;
    /** 由右键施法物品发起并挂着原版"使用物品"：松手结算、物品离手取消、结束时停用。只在服务端有意义。 */
    boolean heldByUse;
    /** 按法术的瞄准方式开始的锁定（{@link TargetApi#begin}）；不选目标的法术、客户端为 null。 */
    @Nullable TargetLock lock;
    private @Nullable Object data;

    CastContext(LivingEntity caster, Spell spell, CastSource source, ItemStack triggerItem, ItemStack sourceItem,
                long startTick, int castTicks) {
        this.caster = caster;
        this.spell = spell;
        this.source = source;
        this.triggerItem = triggerItem;
        this.sourceItem = sourceItem;
        this.startTick = startTick;
        this.castTicks = castTicks;
    }

    public LivingEntity caster() {
        return this.caster;
    }

    public Level level() {
        return this.caster.level();
    }

    public Spell spell() {
        return this.spell;
    }

    public CastSource source() {
        return this.source;
    }

    /** 右键的那件施法物品，施法按蓝量掉耐久的就是它；生物、指令施法时为空。 */
    public ItemStack triggerItem() {
        return this.triggerItem;
    }

    /** 提供这个法术的物品（卷轴消耗靠它）；没有时为空。 */
    public ItemStack sourceItem() {
        return this.sourceItem;
    }

    /** 开始施法时的世界时刻（{@code level.getGameTime()}）。 */
    public long startTick() {
        return this.startTick;
    }

    /** 已施法 tick：开始那一 tick 为 0。 */
    public int elapsed() {
        return (int) (this.level().getGameTime() - this.startTick);
    }

    /** 吟唱 / 满蓄所需 tick（已除施法速率，最少 1）；瞬发、引导为 0。 */
    public int castTicks() {
        return this.castTicks;
    }

    /** 蓄力比例 0~1，只在蓄力松手生效时有意义；其他类型恒为 1。 */
    public float charge() {
        return this.charge;
    }

    /** 已生效次数（引导第几次）。 */
    public int effects() {
        return this.effects;
    }

    /**
     * 指向型法术作用到的生物：{@code canCast} 和读条中是现在选中的（开始时锁定的那个，或者生效时选取模式下准星现在指着的 / 后备），
     * {@code onCast} 里是生效时定下来的那个（可能是施法者自己，声明了对自己时）。生物施法时 AI 直接给的对象也在这里（方向型同样）。
     * 不选目标的法术、客户端为 null。
     */
    public @Nullable LivingEntity target() {
        return this.lock != null ? this.lock.target() : null;
    }

    /** 地点型法术的落点，规则同 {@link #target()}；其他法术、客户端为 null。 */
    public @Nullable Vec3 targetPos() {
        return this.lock != null ? this.lock.point() : null;
    }

    /**
     * 方向型法术朝哪：AI 直接给的对象、生物的攻击目标的身体正中，玩家是准星指着的地方（{@link TargetApi#aimPoint}）。
     * 弹射物、射线基类自己就按这个规则瞄，一般用不到。
     */
    public Vec3 aimPoint() {
        LivingEntity aim = TargetApi.aimEntity(this.caster);
        return aim != null ? aim.getBoundingBox().getCenter() : TargetApi.aimPoint(this.caster, 1.0F, null);
    }

    /** 用默认伤害类型生成法术伤害快照，带上本法术的 id。公式拿施法者此刻的属性。 */
    public SpellDamageSnapshot damage(SpellDamageApi.Formula formula) {
        return SpellDamageApi.snapshot(this.caster, formula).withSpell(this.spell.id());
    }

    /** 同上，指定伤害类型（应在 {@code #ibx_lib:is_spell} 里）。 */
    public SpellDamageSnapshot damage(ResourceKey<DamageType> damageType, SpellDamageApi.Formula formula) {
        return SpellDamageApi.snapshot(this.caster, damageType, formula).withSpell(this.spell.id());
    }

    /** 在 {@link Spell#canCast} 里说明为什么放不了，框架发到玩家的 action bar。 */
    public void fail(Component reason) {
        this.failure = reason;
    }

    /**
     * 本次施法的临时对象（引导生成的射线实体……）：{@code onCastStart} 放，之后的钩子取，
     * 施法结束自动丢弃。不同步、不存盘，服务端和客户端各是各的。
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T data() {
        return (T) this.data;
    }

    public void setData(@Nullable Object data) {
        this.data = data;
    }
}
