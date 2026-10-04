package ibx.taverntales.lib.entity;

import net.minecraft.world.phys.Vec3;

/**
 * 自己决定法术弹射物从哪里出发的施法者（从嘴里、手里、腹口喷出来的生物）。没实现的施法者从下巴出发，
 * 见 {@link SpellProjectile} 的构造。
 */
public interface SpellOrigin {
    /** 法术弹射物中心的出发点（世界坐标）。只在服务端调。 */
    Vec3 getSpellOrigin();
}
