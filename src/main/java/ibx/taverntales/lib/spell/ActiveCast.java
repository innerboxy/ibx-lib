package ibx.taverntales.lib.spell;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jspecify.annotations.Nullable;

/**
 * 正在施的法术（附加数据 {@code taverntales_l1b:active_cast}）。读写请走 {@link SpellCasting}。
 *
 * <ul>
 *   <li>不存盘：下线、换维度、死亡都会结束施法。</li>
 *   <li>同步给自己和周围玩家：法术、来源、开始时刻、读条长度；结束时同步结束原因。
 *       客户端收到时在这里调法术的客户端钩子（{@link Spell#onCastStart} / {@link Spell#onCastEnd}），
 *       吟唱条（阶段 5）也从这里取数据。</li>
 * </ul>
 */
public final class ActiveCast {
    private static final StreamCodec<io.netty.buffer.ByteBuf, Spell> SPELL_STREAM_CODEC =
            ByteBufCodecs.idMapper(SpellRegistries.SPELL);

    /** 正在施的那一次；null 表示没在施法。 */
    @Nullable CastContext current;
    /** 上一次施法的结束原因（给客户端的结束钩子和吟唱条用）。 */
    @Nullable CastEndReason lastEnd;

    public @Nullable CastContext current() {
        return this.current;
    }

    public @Nullable CastEndReason lastEnd() {
        return this.lastEnd;
    }

    /**
     * 同步：每次变化都整份发（数据很小）。客户端按"之前有没有在施、施的是不是同一次"判断开始 / 结束，
     * 调法术的客户端钩子。同一 tick 里结束又开始（指令施法会先取消原来的）时，客户端先收结束、再收开始。
     * 公开只是为了让 {@code AttachmentRegistry} 注册时能用，别处不要碰。
     */
    public static final AttachmentSyncHandler<ActiveCast> SYNC_HANDLER = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, ActiveCast value, boolean initialSync) {
            CastContext ctx = value.current;
            buf.writeBoolean(ctx != null);
            if (ctx != null) {
                SPELL_STREAM_CODEC.encode(buf, ctx.spell());
                buf.writeEnum(ctx.source());
                buf.writeVarLong(ctx.startTick());
                buf.writeVarInt(ctx.castTicks());
            } else {
                buf.writeBoolean(value.lastEnd != null);
                if (value.lastEnd != null) {
                    buf.writeEnum(value.lastEnd);
                }
            }
        }

        @Override
        public ActiveCast read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable ActiveCast previous) {
            ActiveCast value = previous != null ? previous : new ActiveCast();
            LivingEntity caster = (LivingEntity) holder;
            if (buf.readBoolean()) {
                Spell spell = SPELL_STREAM_CODEC.decode(buf);
                CastSource source = buf.readEnum(CastSource.class);
                long startTick = buf.readVarLong();
                int castTicks = buf.readVarInt();
                CastContext old = value.current;
                if (old == null || old.spell() != spell || old.startTick() != startTick) {
                    if (old != null) {
                        value.current = null;
                        old.spell().onCastEnd(old, CastEndReason.CANCELLED);
                    }
                    CastContext ctx = new CastContext(caster, spell, source, ItemStack.EMPTY, ItemStack.EMPTY, startTick, castTicks);
                    value.current = ctx;
                    value.lastEnd = null;
                    spell.onCastStart(ctx);
                }
            } else {
                CastEndReason reason = buf.readBoolean() ? buf.readEnum(CastEndReason.class) : null;
                CastContext old = value.current;
                value.current = null;
                value.lastEnd = reason;
                if (old != null) {
                    old.spell().onCastEnd(old, reason != null ? reason : CastEndReason.CANCELLED);
                }
            }
            return value;
        }
    };
}
