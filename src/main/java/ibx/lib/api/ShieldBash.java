package ibx.lib.api;

import ibx.lib.api.event.ShieldBashEvent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * 盾击状态（附加数据 {@code ibx_lib:shield_bash}）：上一次盾击的时刻。读写请走 {@link ShieldApi}。
 * 不存盘；同步给自己和周围玩家，客户端收到新的一次盾击时发 {@link ShieldBashEvent}，盾击动作靠它。
 * 冷却不记在这里，是盾的原版物品冷却。
 *
 * @param lastBashTick 上一次盾击的世界时刻，没出过为 -1
 */
public record ShieldBash(long lastBashTick) {
    public static final ShieldBash NONE = new ShieldBash(-1L);

    /** 刚同步过来的盾击离现在超过这么多 tick 就不算"刚出手"（进入视野时的首次同步会带着旧记录）。 */
    private static final long RECENT_TICKS = 40L;

    /**
     * 同步：每次盾击整份发。客户端按"这次的时刻和之前不同、而且就在刚才"判断是新的一次盾击。
     * 公开只是为了让 {@code AttachmentRegistry} 注册时能用，别处不要碰。
     */
    public static final AttachmentSyncHandler<ShieldBash> SYNC_HANDLER = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, ShieldBash value, boolean initialSync) {
            buf.writeVarLong(value.lastBashTick);
        }

        @Override
        public ShieldBash read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable ShieldBash previous) {
            ShieldBash value = new ShieldBash(buf.readVarLong());
            if (holder instanceof LivingEntity entity && value.lastBashTick >= 0L
                    && (previous == null || previous.lastBashTick != value.lastBashTick)
                    && Math.abs(entity.level().getGameTime() - value.lastBashTick) <= RECENT_TICKS) {
                NeoForge.EVENT_BUS.post(new ShieldBashEvent(entity, value));
            }
            return value;
        }
    };
}
