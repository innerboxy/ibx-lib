package ibx.taverntales.lib.compat;

import net.mehvahdjukaar.dummmmmmy.common.TargetDummyEntity;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/**
 * MmmMmmMmmMmm 试验假人（modId {@code dummmmmmy}）联动。本模组对它是<b>非强制依赖</b>。
 *
 * <p><b>凡是会碰到 {@code net.mehvahdjukaar.dummmmmmy} 的方法，调用前必须先过
 * {@link #isLoaded()}</b>，否则玩家没装这个模组时会 NoClassDefFoundError。</p>
 *
 * <p>假人显示"12 (1.5x)"这种暴击数字，靠的是 {@code TargetDummyEntity.moist(攻击者, 倍率)}
 * 先登记一次暴击：它把记录加进当 tick 的列表，等自己的 {@code hurt()} 里再与伤害来源配对。
 * <b>所以登记必须发生在假人 hurt() 之前</b>。近战由假人自己监听 NeoForge 的暴击事件完成登记，
 * 不用本类；远程弹射物不经过那个事件，需要本模组在弹射物命中事件里手动登记。</p>
 */
public final class DummmmmmyCompat {
    public static final String MODID = "dummmmmmy";

    private DummmmmmyCompat() {}

    /** 试验假人是否已加载。碰它的 API 之前都要先问这个。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    /**
     * 若 {@code target} 是试验假人，登记一次由 {@code attacker} 造成、倍率为 {@code multiplier}
     * 的暴击，让它把伤害数字显示成暴击样式。必须在假人受到这次伤害<b>之前</b>调用。
     */
    public static void notifyCrit(Entity target, Entity attacker, float multiplier) {
        if (target instanceof TargetDummyEntity dummy) {
            dummy.moist(attacker, multiplier);
        }
    }
}
