package ibx.lib.registry;

import com.mojang.serialization.Codec;
import ibx.lib.IbxLib;
import ibx.lib.api.ManaStorage;
import ibx.lib.api.ShieldBash;
import ibx.lib.api.SpellDamageSnapshot;
import ibx.lib.spell.ActiveCast;
import ibx.lib.spell.SelectedSpell;
import ibx.lib.spell.SpellCooldowns;
import ibx.lib.summon.OwnerSummons;
import ibx.lib.summon.SummonData;
import ibx.lib.target.TargetLock;
import ibx.lib.target.TargetModes;
import net.minecraft.resources.Identifier;
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
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IbxLib.MODID);

    /**
     * 弹药上的远程伤害：Ibx 弓弩发射的瞬间把算好的最终伤害写在箭上，命中时由
     * {@link ibx.lib.event.DamageHandler} 直接覆盖原版伤害。
     * 原版弓弩射出的箭没有这个数据，完全不受影响。带序列化：箭跨区块加载、插在地上都不丢。
     * 读写请走 {@link ibx.lib.api.RangedWeaponApi}。
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
     * {@link ibx.lib.api.SpellDamageApi#hurtFromProjectile} 结算。任何实体都能带。
     * 带序列化：跨区块加载、存盘都不丢。不同步：伤害只在服务端结算。
     * 读写请走 {@link ibx.lib.api.SpellDamageApi}（只读已存在的，不要 {@code getData} 顺手创建默认值）。
     */
    public static final Supplier<AttachmentType<SpellDamageSnapshot>> SPELL_SNAPSHOT =
            ATTACHMENT_TYPES.register("spell_snapshot",
                    () -> AttachmentType.builder(() -> SpellDamageSnapshot.NONE)
                            .serialize(SpellDamageSnapshot.MAP_CODEC)
                            .build());

    /**
     * 当前法力值，上限是属性 {@code max_mana}。读写请走 {@link ibx.lib.api.ManaApi}，
     * 每 tick 的回复见 {@link ibx.lib.event.ManaHandler}。
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
     * 正在施的法术。读写请走 {@link ibx.lib.spell.SpellCasting}。
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

    /**
     * 玩家当前选中的法术 id。读写请走 {@link ibx.lib.spell.SpellCasting}（改选只有轮盘的选中包一条路）。
     * 存盘；只同步给自己；死亡后保留（选择是玩家的偏好，不是状态）。
     */
    public static final Supplier<AttachmentType<SelectedSpell>> SELECTED_SPELL =
            ATTACHMENT_TYPES.register("selected_spell",
                    () -> AttachmentType.builder(() -> SelectedSpell.NONE)
                            .serialize(SelectedSpell.CODEC)
                            .sync((holder, player) -> holder == player, SelectedSpell.STREAM_CODEC)
                            .copyOnDeath()
                            .build());

    /**
     * 实体身上烧的是哪种火（贴图基础名，见 {@link ibx.lib.api.CustomFire}）；没有 = 原版火。
     * 存盘（着火时间本来就存盘）；同步给所有看得见它的玩家，客户端据此换身上和屏幕上的火。
     */
    public static final Supplier<AttachmentType<Identifier>> FIRE_LOOK =
            ATTACHMENT_TYPES.register("fire_look",
                    () -> AttachmentType.builder(() -> Identifier.withDefaultNamespace("fire"))
                            .serialize(Identifier.CODEC.fieldOf("look"))
                            .sync(Identifier.STREAM_CODEC)
                            .build());

    /**
     * 盾击状态：上一次盾击的时刻和冷却到点的时刻。读写请走 {@link ibx.lib.api.ShieldApi}。
     * 不存盘；同步给自己和周围玩家，客户端据此放盾击动作。
     */
    public static final Supplier<AttachmentType<ShieldBash>> SHIELD_BASH =
            ATTACHMENT_TYPES.register("shield_bash",
                    () -> AttachmentType.builder(() -> ShieldBash.NONE)
                            .sync(ShieldBash.SYNC_HANDLER)
                            .build());

    /**
     * 召唤物身上的框架数据（主人、法术、名额、到期时刻）。读写请走 {@link ibx.lib.api.SummonApi}。
     * 存盘（跨维度搬运时随实体数据复制）；不同步。
     */
    public static final Supplier<AttachmentType<SummonData>> SUMMON =
            ATTACHMENT_TYPES.register("summon",
                    () -> AttachmentType.builder(() -> SummonData.NONE)
                            .serialize(SummonData.CODEC)
                            .build());

    /**
     * 主人身上的召唤物名单（按召唤顺序）和标记目标。读写请走 {@link ibx.lib.api.SummonApi}。
     * 名单存盘（生物当主人时随世界存）；名单只同步给主人自己（轮盘要知道有没有召唤物），标记目标不同步；
     * 不标 copyOnDeath（主人死了召唤物全走，名单本来就空了）。
     */
    public static final Supplier<AttachmentType<OwnerSummons>> OWNER_SUMMONS =
            ATTACHMENT_TYPES.register("owner_summons",
                    () -> AttachmentType.builder(() -> new OwnerSummons())
                            .serialize(OwnerSummons.CODEC)
                            .sync((holder, player) -> holder == player, OwnerSummons.STREAM_CODEC)
                            .build());

    /**
     * 实体的「当前锁定」：一次施法或一次物品使用选中的目标 / 落点。读写请走 {@link ibx.lib.target.TargetApi}。
     * 只在服务端用；不存盘（施法、使用物品都不跨存档）、不同步（地上的提示另外发包）。
     */
    public static final Supplier<AttachmentType<TargetLock>> TARGET_LOCK =
            ATTACHMENT_TYPES.register("target_lock",
                    () -> AttachmentType.<TargetLock>builder(() -> {
                        throw new IllegalStateException("当前锁定只能由 TargetApi.begin 写入");
                    }).build());

    /**
     * 玩家的两个选取时机设置（指向型、地点型）。源头是客户端配置，进服、改动时由 {@code TargetModesPayload} 发上来；
     * 存盘只为死亡后保留（原版要求带序列化才能 copyOnDeath），不同步。
     */
    public static final Supplier<AttachmentType<TargetModes>> TARGET_MODES =
            ATTACHMENT_TYPES.register("target_modes",
                    () -> AttachmentType.builder(() -> TargetModes.DEFAULT)
                            .serialize(TargetModes.CODEC)
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
