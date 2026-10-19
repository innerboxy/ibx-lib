package ibx.lib.target;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ibx.lib.registry.AttachmentRegistry;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家的两个选取时机设置（附加数据 {@code ibx_lib:target_modes}）。客户端配置是源头，进服、改动时发上来；
 * 存盘只是为了死亡重生后还在（进服时客户端会再发一次覆盖），不同步。
 *
 * @param unit  指向型
 * @param point 地点型
 */
public record TargetModes(TargetMode unit, TargetMode point) {
    public static final TargetModes DEFAULT = new TargetModes(TargetMode.EFFECT, TargetMode.EFFECT);

    public static final MapCodec<TargetModes> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("unit_at_start").forGetter(modes -> modes.unit == TargetMode.START),
            Codec.BOOL.fieldOf("point_at_start").forGetter(modes -> modes.point == TargetMode.START)
    ).apply(instance, (unit, point) -> new TargetModes(unit ? TargetMode.START : TargetMode.EFFECT, point ? TargetMode.START : TargetMode.EFFECT)));

    public static TargetModes of(Player player) {
        TargetModes modes = player.getExistingDataOrNull(AttachmentRegistry.TARGET_MODES);
        return modes != null ? modes : DEFAULT;
    }
}
