package ibx.taverntales.lib.entity;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 自己决定法术弹射物从哪里出发的施法者（从嘴里、手里、腹口喷出来的生物）。没实现的施法者从下巴出发，
 * 见 {@link SpellProjectile} 的构造。
 */
public interface SpellOrigin {
    /** 法术弹射物中心的出发点（世界坐标）。只在服务端调。 */
    Vec3 getSpellOrigin();

    /**
     * 射线（{@link SpellBeam}）对准的点（世界坐标）；null 照默认（生物对准攻击目标、玩家对准准星）。
     * 想让射线跟着身体慢慢转、不直接锁死目标时用（比如玛门的翡翠射线）。只在服务端调。
     */
    default @Nullable Vec3 getSpellAimPoint() {
        return null;
    }
}
