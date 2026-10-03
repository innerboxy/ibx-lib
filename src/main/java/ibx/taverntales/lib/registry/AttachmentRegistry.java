package ibx.taverntales.lib.registry;

import com.mojang.serialization.Codec;
import ibx.taverntales.lib.TavernTalesLib;
import ibx.taverntales.lib.api.ManaStorage;
import ibx.taverntales.lib.api.SpellDamageSnapshot;
import ibx.taverntales.lib.spell.ActiveCast;
import ibx.taverntales.lib.spell.SpellCooldowns;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 本模组注册的 Data Attachment（实体附加数据）。
 */
public final class AttachmentRegistry {
    private AttachmentRegistry() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TavernTalesLib.MODID);

    /**
     * 弹药上的远程伤害：Tt 弓弩发射的瞬间把算好的最终伤害写在箭上，命中时由
     * {@link ibx.taverntales.lib.event.DamageHandler} 直接覆盖原版伤害。
     * 原版弓弩射出的箭没有这个数据，完全不受影响。带序列化：箭跨区块加载、插在地上都不丢。
     * 读写请走 {@link ibx.taverntales.lib.api.RangedWeaponApi}。
     *
     * <p>26.1 起附加数据只接受 MapCodec（存成一个带字段的对象），所以数值包在 {@code value} 字段里。</p>
     */
    public static final Supplier<AttachmentType<Float>> AMMO_RANGED_DAMAGE =
            ATTACHMENT_TYPES.register("ammo_ranged_damage",
                    () -> AttachmentType.builder(() -> 0.0F)
                            .serialize(Codec.FLOAT.fieldOf("value"))
                            .build());

    /**
     * 法术弹射物上的伤害快照：施法瞬间算好写上去，命中时由
     * {@link ibx.taverntales.lib.api.SpellDamageApi#hurtFromProjectile} 结算。任何实体都能带。
     * 带序列化：跨区块加载、存盘都不丢。不同步：伤害只在服务端结算。
     * 读写请走 {@link ibx.taverntales.lib.api.SpellDamageApi}（只读已存在的，不要 {@code getData} 顺手创建默认值）。
     */
    public static final Supplier<AttachmentType<SpellDamageSnapshot>> SPELL_SNAPSHOT =
            ATTACHMENT_TYPES.register("spell_snapshot",
                    () -> AttachmentType.builder(() -> SpellDamageSnapshot.NONE)
                            .serialize(SpellDamageSnapshot.MAP_CODEC)
                            .build());

    /**
     * 当前法力值，上限是属性 {@code max_mana}。读写请走 {@link ibx.taverntales.lib.api.ManaApi}，
     * 每 tick 的回复见 {@link ibx.taverntales.lib.event.ManaHandler}。
     *
     * <ul>
     *   <li>默认 0：新玩家与重生后都从空蓝开始。</li>
     *   <li>不标 copyOnDeath：死亡重生时不复制（从 0 开始）；从末地返回这类非死亡的重建会原样保留。</li>
     *   <li>只同步给实体自己：别人看不到你的法力。何时同步由 ManaApi 控制（值是原地修改的）。</li>
     * </ul>
     */
    public static final Supplier<AttachmentType<ManaStorage>> MANA =
            ATTACHMENT_TYPES.register("mana",
                    () -> AttachmentType.builder(() -> new ManaStorage())
                            .serialize(ManaStorage.CODEC)
                            .sync((holder, player) -> holder == player, ManaStorage.STREAM_CODEC)
                            .build());

    /**
     * 正在施的法术。读写请走 {@link ibx.taverntales.lib.spell.SpellCasting}。
     * 不存盘（下线、换维度、死亡都结束施法）；同步给自己和周围玩家，客户端据此跑法术的客户端钩子、画吟唱条。
     */
    public static final Supplier<AttachmentType<ActiveCast>> ACTIVE_CAST =
            ATTACHMENT_TYPES.register("active_cast",
                    () -> AttachmentType.builder(() -> new ActiveCast())
                            .sync(ActiveCast.SYNC_HANDLER)
                            .build());

    /**
     * 法术冷却：法术 id → 冷却结束的世界时刻。读写请走 {@link SpellCooldowns} 的静态方法。
     * 存盘；只同步给实体自己；不标 copyOnDeath（死亡后冷却清零，和法力一致）。
     */
    public static final Supplier<AttachmentType<SpellCooldowns>> SPELL_COOLDOWNS =
            ATTACHMENT_TYPES.register("spell_cooldowns",
                    () -> AttachmentType.builder(() -> new SpellCooldowns())
                            .serialize(SpellCooldowns.CODEC)
                            .sync((holder, player) -> holder == player, SpellCooldowns.STREAM_CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
