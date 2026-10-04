package ibx.taverntales.lib.api;

import ibx.taverntales.lib.api.event.ManaEvent;
import ibx.taverntales.lib.registry.AttachmentRegistry;
import ibx.taverntales.lib.registry.AttributeRegistry;
import ibx.taverntales.lib.util.AttributeUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 法力系统的读写入口。
 *
 * <ul>
 *   <li>上限：属性 {@code max_mana}；回复：属性 {@code mana_regeneration}（每秒点数，负数为流失），
 *       由 {@link ibx.taverntales.lib.event.ManaHandler} 每 tick 结算；消耗：基础消耗 × 属性 {@code mana_cost_multiplier}。</li>
 *   <li>当前值存在附加数据里，只同步给实体自己，所以<b>客户端只能读本地玩家</b>的法力。</li>
 *   <li>写操作只在服务端生效；客户端调用 {@link #tryConsume} 只做判断，用于本地预测。</li>
 *   <li>创造模式的玩家消耗法力恒成功且不扣。</li>
 * </ul>
 */
public final class ManaApi {
    private ManaApi() {}

    /** 当前法力。没有法力数据的实体（从没用过法力的生物）视为 0。 */
    public static float getMana(LivingEntity entity) {
        ManaStorage storage = entity.getExistingDataOrNull(AttachmentRegistry.MANA);
        return storage == null ? 0.0F : storage.mana;
    }

    /** 法力上限（{@code max_mana} 属性总值）。 */
    public static float getMaxMana(LivingEntity entity) {
        return (float) AttributeUtil.read(entity, AttributeRegistry.MAX_MANA);
    }

    /** 实际消耗 = 基础消耗 × 法力消耗倍率，不小于 0。给 tooltip、界面预览用。 */
    public static float getActualCost(LivingEntity entity, float baseCost) {
        return Math.max(0.0F, baseCost * (float) AttributeUtil.read(entity, AttributeRegistry.MANA_COST_MULTIPLIER));
    }

    /** 够不够付这次消耗。创造模式恒为 true。 */
    public static boolean canAfford(LivingEntity entity, float baseCost) {
        return isCreative(entity) || getMana(entity) >= getActualCost(entity, baseCost);
    }

    /**
     * 尝试消耗：够就扣掉并返回 true，不够返回 false 且不扣。
     *
     * <p>创造模式直接成功、不扣。扣之前触发 {@link ManaEvent.Consume}，可被取消或改量。
     * 客户端调用只做判断（等同 {@link #canAfford}），不改数据。</p>
     *
     * @param baseCost 基础消耗，会先乘法力消耗倍率
     */
    public static boolean tryConsume(LivingEntity entity, float baseCost) {
        if (isCreative(entity)) {
            return true;
        }
        if (entity.level().isClientSide()) {
            return canAfford(entity, baseCost);
        }
        ManaEvent.Consume event = NeoForge.EVENT_BUS.post(new ManaEvent.Consume(entity, getActualCost(entity, baseCost)));
        if (event.isCanceled()) {
            return false;
        }
        float cost = Math.max(0.0F, event.getCost());
        ManaStorage storage = storage(entity);
        if (storage.mana < cost) {
            return false;
        }
        change(entity, storage, storage.mana - cost, true);
        return true;
    }

    /**
     * 增加法力（药水、击中回蓝、退还等），结果截断到 [0, 上限]；负数表示扣减。
     * 先触发 {@link ManaEvent.Gain}，可被改量。只在服务端生效。
     *
     * @return 实际变化量
     */
    public static float add(LivingEntity entity, float amount) {
        if (entity.level().isClientSide()) {
            return 0.0F;
        }
        ManaEvent.Gain event = NeoForge.EVENT_BUS.post(new ManaEvent.Gain(entity, amount));
        ManaStorage storage = storage(entity);
        float before = storage.mana;
        change(entity, storage, Mth.clamp(before + event.getAmount(), 0.0F, getMaxMana(entity)), true);
        return storage.mana - before;
    }

    /** 直接设值，截断到 [0, 上限]，不触发 {@link ManaEvent.Gain}。给指令用。只在服务端生效。 */
    public static void set(LivingEntity entity, float value) {
        if (entity.level().isClientSide()) {
            return;
        }
        change(entity, storage(entity), Mth.clamp(value, 0.0F, getMaxMana(entity)), true);
    }

    /**
     * 每 tick 的回复 / 流失与上限截断，由 {@link ibx.taverntales.lib.event.ManaHandler} 在服务端调用。
     * 其他代码不要调用。没有法力数据的实体会在这里被创建（从 0 开始）。
     */
    public static void tickRegeneration(LivingEntity entity) {
        ManaStorage storage = storage(entity);
        float perTick = (float) AttributeUtil.read(entity, AttributeRegistry.MANA_REGENERATION) / 20.0F;
        float next = Mth.clamp(storage.mana + perTick, 0.0F, getMaxMana(entity));
        if (next != storage.mana) {
            change(entity, storage, next, false);
        }
    }

    /**
     * 改值、按需同步、发变化事件。
     *
     * <p>同步节流：跳变（消耗、增加、设值）立即同步；逐 tick 的回复只在整数部分变了、
     * 或刚好到达 0 / 上限时才同步，界面显示整数就不会有误差，回复中每秒只发两三个包。</p>
     */
    private static void change(LivingEntity entity, ManaStorage storage, float newValue, boolean forceSync) {
        float oldValue = storage.mana;
        storage.mana = newValue;
        boolean reachedBound = newValue != storage.lastSynced && (newValue == 0.0F || newValue == getMaxMana(entity));
        if (forceSync || reachedBound || Mth.floor(newValue) != Mth.floor(storage.lastSynced)) {
            storage.lastSynced = newValue;
            entity.syncData(AttachmentRegistry.MANA);
        }
        NeoForge.EVENT_BUS.post(new ManaEvent.Changed(entity, oldValue, newValue));
    }

    /** 取法力数据，没有就创建（只在服务端调用）。 */
    private static ManaStorage storage(LivingEntity entity) {
        return entity.getData(AttachmentRegistry.MANA);
    }

    private static boolean isCreative(LivingEntity entity) {
        return entity instanceof Player player && player.isCreative();
    }
}
