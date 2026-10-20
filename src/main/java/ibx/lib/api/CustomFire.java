package ibx.lib.api;

import ibx.lib.registry.AttachmentRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * 换颜色的火：让实体身上烧的火（第三人称身上的火苗、第一人称屏幕上的火）换成别的贴图，比如 2me 翠焰的绿火。
 *
 * <p>着火本身还是原版的（着火时间、伤害、水能浇灭、防火的不着）；这里只在实体上记“烧的是哪种火”（{@code fire_look}，
 * 存盘、同步给看得见它的玩家），火灭了就自动清掉。贴图是方块图集里的两张 {@code <命名空间>:block/<路径>_0}、{@code _1}，
 * 同原版的 {@code fire_0} / {@code fire_1}（放在 {@code textures/block/} 下，可以带 .mcmeta 动画）；第一人称屏幕四周的火焰边框是
 * {@code <命名空间>:textures/misc/<路径>_outline.png}（格式同 lib 的 {@code fire_outline.png}），没有就用原版火色的那张。
 * 客户端的换图在 {@code client.CustomFireRendering} 和 {@code mixin.FlameFeatureRendererMixin}。</p>
 *
 * <pre>{@code
 * // 点燃 3 秒，烧的是 taverntales_2me:block/emerald_fire_0 / _1
 * CustomFire.ignite(target, TavernTalesTome.id("emerald_fire"), 3.0F);
 * }</pre>
 *
 * <p>着着绿火又走进原版火里，还是显示绿火，直到整个火灭掉。</p>
 */
public final class CustomFire {
    private CustomFire() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CustomFire::onEntityTick);
    }

    /** 点燃 seconds 秒（同原版 {@link Entity#igniteForSeconds}，不缩短已有的），并记下烧的是哪种火。防火的实体不着，也不记。只在服务端调。 */
    public static void ignite(Entity entity, Identifier look, float seconds) {
        entity.igniteForSeconds(seconds);
        if (entity.isOnFire()) {
            entity.setData(AttachmentRegistry.FIRE_LOOK, look);
        }
    }

    /** 这个实体烧的是哪种火（贴图的基础名，不带 {@code block/} 和 {@code _0}）；原版火或者没着火为 null。两端都能问。 */
    public static @Nullable Identifier look(Entity entity) {
        return entity.getExistingDataOrNull(AttachmentRegistry.FIRE_LOOK);
    }

    /** 火灭了（时间到、被水浇灭）就清掉标记。 */
    private static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide() && entity.getRemainingFireTicks() <= 0 && entity.hasData(AttachmentRegistry.FIRE_LOOK)) {
            entity.removeData(AttachmentRegistry.FIRE_LOOK);
        }
    }
}
