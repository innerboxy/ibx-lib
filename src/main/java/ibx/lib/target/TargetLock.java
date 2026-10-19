package ibx.lib.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 实体身上的「当前锁定」（附加数据 {@code ibx_lib:target_lock}，只在服务端、不存盘、不同步）：一次施法或一次物品使用选中的目标 / 落点。
 * 一个实体一次只有一个，新的顶掉旧的（旧的来源收到失效通知）。建、查、放都走 {@link TargetApi}。
 *
 * <ul>
 *   <li>开始时锁定（{@link TargetMode#START}，以及 AI 直接给的对象）：{@link #target()} / {@link #point()} 就是锁定的那个；
 *       失效（死了、换维度、离开保持距离）时锁定被移除，来源收到通知。</li>
 *   <li>生效时选取（{@link TargetMode#EFFECT}）：开始时选中的只当后备，失效了就丢掉、不通知；每 tick 按准星重选，
 *       {@link #target()} / {@link #point()} 是「现在生效会作用到的」那个（准星指着的，没指着就是后备）。</li>
 * </ul>
 */
public final class TargetLock {
    final Targeting targeting;
    final TargetMode mode;
    final Object owner;
    final @Nullable Runnable onLost;
    final boolean given;
    /** 指向型：锁定的 / 后备；方向型：AI 给的对象。 */
    @Nullable LivingEntity locked;
    /** 地点型：锁定的 / 后备。 */
    @Nullable Vec3 lockedPoint;
    @Nullable LivingEntity current;
    @Nullable Vec3 currentPoint;
    /** 上次发给客户端的提示和发出后过了几 tick（{@link TargetHandler} 用）。 */
    @Nullable TargetIndicator sent;
    int sentAge;

    TargetLock(Targeting targeting, TargetMode mode, Object owner, @Nullable Runnable onLost, boolean given) {
        this.targeting = targeting;
        this.mode = mode;
        this.owner = owner;
        this.onLost = onLost;
        this.given = given;
    }

    public Targeting targeting() {
        return this.targeting;
    }

    /** 这次用的选取时机；AI 直接给对象时恒为 {@link TargetMode#START}。 */
    public TargetMode mode() {
        return this.mode;
    }

    /** 谁锁的（一次施法的 {@code CastContext}、一次物品使用……），放锁时对得上才放。 */
    public Object owner() {
        return this.owner;
    }

    /** 对象是不是 AI 直接给的。 */
    public boolean isGiven() {
        return this.given;
    }

    /** 指向型、方向型（AI 给了对象）现在作用到的生物；没有为 null。 */
    public @Nullable LivingEntity target() {
        return this.current;
    }

    /** 地点型现在的落点；没有为 null。 */
    public @Nullable Vec3 point() {
        return this.currentPoint;
    }

    /** 开始时锁定 / 生效时选取都靠的：锁定的那个（开始时锁定），或者后备（生效时选取）。 */
    public @Nullable LivingEntity locked() {
        return this.locked;
    }

    boolean locksAtStart() {
        return this.given || this.mode == TargetMode.START;
    }
}
