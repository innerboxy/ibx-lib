package ibx.lib.compat.dummmmmmy;

import net.mehvahdjukaar.dummmmmmy.common.TargetDummyEntity;
import net.minecraft.world.entity.Entity;

/**
 * 给试验假人登记暴击，{@code DummmmmmyCompat} 转过来的实现。
 * 引用了假人模组的类，只在装了它时调用（先过 {@code DummmmmmyCompat.isLoaded()}）。
 */
public final class DummmmmmyAccess {
    private DummmmmmyAccess() {}

    /** 若 {@code target} 是试验假人，登记一次由 {@code attacker} 造成、倍率为 {@code multiplier} 的暴击。 */
    public static void notifyCrit(Entity target, Entity attacker, float multiplier) {
        if (target instanceof TargetDummyEntity dummy) {
            dummy.moist(attacker, multiplier);
        }
    }
}
