package ibx.lib.summon;

import ibx.lib.api.SummonApi;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 召唤物实体实现它。主人取自框架数据（{@link SummonData}），所以法术友伤（按 {@link OwnableEntity#getRootOwner}）自动覆盖。
 *
 * <p>接口盖不了实体类的方法，实现类自己要重写两处，都转给 {@link SummonApi}：</p>
 * <pre>{@code
 * @Override protected boolean considersEntityAsAlly(Entity other) {
 *     return SummonApi.isAlly(this, other) || super.considersEntityAsAlly(other);
 * }
 * @Override public @Nullable PlayerTeam getTeam() {
 *     PlayerTeam own = super.getTeam();
 *     return own != null ? own : SummonApi.ownerTeam(this);
 * }
 * }</pre>
 *
 * <p>AI 目标见 {@code ibx.lib.summon.goal}：跟随主人、优先打主人标记的目标、打主人打的、打打主人的、抄主人的目标。</p>
 */
public interface Summoned extends OwnableEntity {
    @Override
    default @Nullable EntityReference<LivingEntity> getOwnerReference() {
        UUID owner = SummonApi.ownerId((Entity) this);
        return owner == null ? null : EntityReference.of(owner);
    }

    /**
     * 框架让它离场（到期、被挤掉、解散、主人死亡 / 下线 / 不见了），只在服务端调。默认冒一小股烟再移除；
     * 有自己离场演出的召唤物重写，最后一定要移除自己（{@code discard()}）。被打死不走这里。
     */
    default void onUnsummon(UnsummonReason reason) {
        Entity self = (Entity) this;
        if (self.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ(),
                    12, self.getBbWidth() * 0.4, self.getBbHeight() * 0.3, self.getBbWidth() * 0.4, 0.02);
        }
        self.discard();
    }
}
