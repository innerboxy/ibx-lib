package ibx.lib.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 选取时机：玩家在 选项 → 控制 里设，指向型、地点型各一个（客户端配置，进服和改动时发给服务端，见 {@link TargetHandler}）。
 * 生物没有设置，按默认的生效时选取；AI 直接给了对象时一律当开始时锁定。
 */
public enum TargetMode {
    /** 生效时选取（默认）：开始时选中的只当后备，生效时按当时的准星重新选，没指着才用后备。 */
    EFFECT,
    /** 开始时锁定：开始时选中就锁死，读条中失效就取消。 */
    START;

    /** 玩家这次指向型 / 地点型用哪种；生物和没发过设置的玩家都是 {@link #EFFECT}。 */
    public static TargetMode of(LivingEntity holder, Targeting.Kind kind) {
        if (!(holder instanceof Player player)) {
            return EFFECT;
        }
        TargetModes modes = TargetModes.of(player);
        return kind == Targeting.Kind.POINT ? modes.point() : modes.unit();
    }
}
