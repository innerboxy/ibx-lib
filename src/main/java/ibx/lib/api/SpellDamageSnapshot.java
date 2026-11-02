package ibx.lib.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ibx.lib.registry.DamageTypeRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * 法术伤害快照：施法瞬间算好的一次伤害（或治疗），写在弹射物 / 范围实体上，命中时交给
 * {@link SpellDamageApi#hurt} 结算。之后换装备、buff 过期、施法者死亡都不影响它。
 *
 * <p>一般用 {@link SpellDamageApi#snapshot} 生成，再用 {@code withXxx} 改开关，例如
 * {@code SpellDamageApi.snapshot(caster, ctx -> ctx.spellPower() * 1.6F).withFire(80)}。</p>
 *
 * <p>暴击只记概率和倍率、不掷骰：命中时每个目标各掷一次。</p>
 *
 * @param damageType     伤害类型；应在 {@code #ibx_lib:is_spell} 里，否则会被护甲减免、不吃法术抗性
 * @param baseAmount     公式算出的基础值，未乘暴击
 * @param canCrit        能否暴击（施法时 {@code can_spell_crit} 大于等于 1）
 * @param critChance     施法时的暴击率
 * @param critMultiplier 施法时的暴击倍率（1 + {@code critical_damage}）
 * @param spellId        来自哪个法术，可空；给事件和以后按法术显示的死亡信息用
 * @param friendlyFire   是否允许打友军；默认 false，即友伤保护开着
 * @param ignoreIFrames  穿透已有无敌帧：命中前把目标的无敌时间清零
 * @param leaveNoIFrames 打完不留无敌帧：命中后把目标的无敌时间和上次伤害还原
 * @param fireTicks      命中后点燃的 tick 数，0 为不点燃
 * @param freezeTicks    命中后冰冻的大致 tick 数，0 为不冰冻（只有减速表现，不够原版冻伤门槛不掉血）
 * @param lifesteal      吸血比例：按实际造成的伤害给施法者回血，0 为不吸血
 * @param spellPenetration 施法时的法术穿透（{@code spell_penetration}）
 */
public record SpellDamageSnapshot(ResourceKey<DamageType> damageType, float baseAmount,
                                  boolean canCrit, float critChance, float critMultiplier,
                                  @Nullable Identifier spellId, boolean friendlyFire,
                                  boolean ignoreIFrames, boolean leaveNoIFrames,
                                  int fireTicks, int freezeTicks, float lifesteal, float spellPenetration) {

    /** 附加数据的默认值，只为满足注册要求；读弹射物上的快照请走 {@link SpellDamageApi#getProjectileSnapshot}。 */
    public static final SpellDamageSnapshot NONE =
            new SpellDamageSnapshot(DamageTypeRegistry.SPELL_GENERAL, 0.0F, false, 0.0F, 1.0F,
                    null, false, false, false, 0, 0, 0.0F, 0.0F);

    /** 存盘格式（26.1 起附加数据只接受 MapCodec）。除基础值外都可省略，省略即默认值。 */
    public static final MapCodec<SpellDamageSnapshot> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DAMAGE_TYPE).optionalFieldOf("damage_type", DamageTypeRegistry.SPELL_GENERAL)
                    .forGetter(SpellDamageSnapshot::damageType),
            Codec.FLOAT.fieldOf("base_amount").forGetter(SpellDamageSnapshot::baseAmount),
            Codec.BOOL.optionalFieldOf("can_crit", false).forGetter(SpellDamageSnapshot::canCrit),
            Codec.FLOAT.optionalFieldOf("crit_chance", 0.0F).forGetter(SpellDamageSnapshot::critChance),
            Codec.FLOAT.optionalFieldOf("crit_multiplier", 1.0F).forGetter(SpellDamageSnapshot::critMultiplier),
            Identifier.CODEC.optionalFieldOf("spell").forGetter(s -> Optional.ofNullable(s.spellId())),
            Codec.BOOL.optionalFieldOf("friendly_fire", false).forGetter(SpellDamageSnapshot::friendlyFire),
            Codec.BOOL.optionalFieldOf("ignore_iframes", false).forGetter(SpellDamageSnapshot::ignoreIFrames),
            Codec.BOOL.optionalFieldOf("leave_no_iframes", false).forGetter(SpellDamageSnapshot::leaveNoIFrames),
            Codec.INT.optionalFieldOf("fire_ticks", 0).forGetter(SpellDamageSnapshot::fireTicks),
            Codec.INT.optionalFieldOf("freeze_ticks", 0).forGetter(SpellDamageSnapshot::freezeTicks),
            Codec.FLOAT.optionalFieldOf("lifesteal", 0.0F).forGetter(SpellDamageSnapshot::lifesteal),
            Codec.FLOAT.optionalFieldOf("spell_penetration", 0.0F).forGetter(SpellDamageSnapshot::spellPenetration)
    ).apply(i, (damageType, baseAmount, canCrit, critChance, critMultiplier, spellId, friendlyFire,
                ignoreIFrames, leaveNoIFrames, fireTicks, freezeTicks, lifesteal, spellPenetration) ->
            new SpellDamageSnapshot(damageType, baseAmount, canCrit, critChance, critMultiplier, spellId.orElse(null),
                    friendlyFire, ignoreIFrames, leaveNoIFrames, fireTicks, freezeTicks, lifesteal, spellPenetration)));

    public static final Codec<SpellDamageSnapshot> CODEC = MAP_CODEC.codec();

    private static final StreamCodec<ByteBuf, ResourceKey<DamageType>> DAMAGE_TYPE_STREAM_CODEC =
            ResourceKey.streamCodec(Registries.DAMAGE_TYPE);
    private static final StreamCodec<ByteBuf, Optional<Identifier>> SPELL_ID_STREAM_CODEC =
            ByteBufCodecs.optional(Identifier.STREAM_CODEC);

    /** 网络格式。字段超过 {@code StreamCodec.composite} 的上限，所以手写；读写顺序必须一致。 */
    public static final StreamCodec<ByteBuf, SpellDamageSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                DAMAGE_TYPE_STREAM_CODEC.encode(buf, s.damageType());
                buf.writeFloat(s.baseAmount());
                buf.writeBoolean(s.canCrit());
                buf.writeFloat(s.critChance());
                buf.writeFloat(s.critMultiplier());
                SPELL_ID_STREAM_CODEC.encode(buf, Optional.ofNullable(s.spellId()));
                buf.writeBoolean(s.friendlyFire());
                buf.writeBoolean(s.ignoreIFrames());
                buf.writeBoolean(s.leaveNoIFrames());
                buf.writeInt(s.fireTicks());
                buf.writeInt(s.freezeTicks());
                buf.writeFloat(s.lifesteal());
                buf.writeFloat(s.spellPenetration());
            },
            buf -> new SpellDamageSnapshot(
                    DAMAGE_TYPE_STREAM_CODEC.decode(buf),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat(),
                    SPELL_ID_STREAM_CODEC.decode(buf).orElse(null),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat()));

    /** 用施法时的输入和公式结果生成快照，开关全取默认值。 */
    public static SpellDamageSnapshot of(SpellDamageContext ctx, ResourceKey<DamageType> damageType, float baseAmount) {
        return new SpellDamageSnapshot(damageType, baseAmount, ctx.canCrit(), ctx.critChance(), ctx.critMultiplier(),
                null, false, false, false, 0, 0, 0.0F, ctx.spellPenetration());
    }

    /** 基础值乘一个系数，给范围衰减、多段分摊用（比如爆炸边缘 {@code scaled(0.5F)}）。 */
    public SpellDamageSnapshot scaled(float factor) {
        return new SpellDamageSnapshot(this.damageType, this.baseAmount * factor, this.canCrit, this.critChance,
                this.critMultiplier, this.spellId, this.friendlyFire, this.ignoreIFrames, this.leaveNoIFrames,
                this.fireTicks, this.freezeTicks, this.lifesteal, this.spellPenetration);
    }

    public SpellDamageSnapshot withDamageType(ResourceKey<DamageType> damageType) {
        return new SpellDamageSnapshot(damageType, this.baseAmount, this.canCrit, this.critChance,
                this.critMultiplier, this.spellId, this.friendlyFire, this.ignoreIFrames, this.leaveNoIFrames,
                this.fireTicks, this.freezeTicks, this.lifesteal, this.spellPenetration);
    }

    public SpellDamageSnapshot withSpell(@Nullable Identifier spellId) {
        return new SpellDamageSnapshot(this.damageType, this.baseAmount, this.canCrit, this.critChance,
                this.critMultiplier, spellId, this.friendlyFire, this.ignoreIFrames, this.leaveNoIFrames,
                this.fireTicks, this.freezeTicks, this.lifesteal, this.spellPenetration);
    }

    public SpellDamageSnapshot withFriendlyFire(boolean friendlyFire) {
        return new SpellDamageSnapshot(this.damageType, this.baseAmount, this.canCrit, this.critChance,
                this.critMultiplier, this.spellId, friendlyFire, this.ignoreIFrames, this.leaveNoIFrames,
                this.fireTicks, this.freezeTicks, this.lifesteal, this.spellPenetration);
    }

    public SpellDamageSnapshot withIgnoreIFrames(boolean ignoreIFrames) {
        return new SpellDamageSnapshot(this.damageType, this.baseAmount, this.canCrit, this.critChance,
                this.critMultiplier, this.spellId, this.friendlyFire, ignoreIFrames, this.leaveNoIFrames,
                this.fireTicks, this.freezeTicks, this.lifesteal, this.spellPenetration);
    }

    public SpellDamageSnapshot withLeaveNoIFrames(boolean leaveNoIFrames) {
        return new SpellDamageSnapshot(this.damageType, this.baseAmount, this.canCrit, this.critChance,
                this.critMultiplier, this.spellId, this.friendlyFire, this.ignoreIFrames, leaveNoIFrames,
                this.fireTicks, this.freezeTicks, this.lifesteal, this.spellPenetration);
    }

    public SpellDamageSnapshot withFire(int fireTicks) {
        return new SpellDamageSnapshot(this.damageType, this.baseAmount, this.canCrit, this.critChance,
                this.critMultiplier, this.spellId, this.friendlyFire, this.ignoreIFrames, this.leaveNoIFrames,
                fireTicks, this.freezeTicks, this.lifesteal, this.spellPenetration);
    }

    public SpellDamageSnapshot withFreeze(int freezeTicks) {
        return new SpellDamageSnapshot(this.damageType, this.baseAmount, this.canCrit, this.critChance,
                this.critMultiplier, this.spellId, this.friendlyFire, this.ignoreIFrames, this.leaveNoIFrames,
                this.fireTicks, freezeTicks, this.lifesteal, this.spellPenetration);
    }

    public SpellDamageSnapshot withLifesteal(float lifesteal) {
        return new SpellDamageSnapshot(this.damageType, this.baseAmount, this.canCrit, this.critChance,
                this.critMultiplier, this.spellId, this.friendlyFire, this.ignoreIFrames, this.leaveNoIFrames,
                this.fireTicks, this.freezeTicks, lifesteal, this.spellPenetration);
    }
}
