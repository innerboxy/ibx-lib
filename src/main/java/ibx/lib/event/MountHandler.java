package ibx.lib.event;

import ibx.lib.IbxLib;
import ibx.lib.registry.AttributeRegistry;
import ibx.lib.util.AttributeUtil;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

/**
 * 坐骑属性的同步：骑手身上的 {@code mount_*} 属性每 tick 换成坐骑自己对应原版属性上的修饰符。
 *
 * <p>在坐骑这边结算：坐骑有操控它的骑手（{@code getControllingPassenger}，马要装鞍、猪要拿胡萝卜钓竿这些按原版规矩）时，
 * 读骑手的属性，给坐骑挂 / 更新一个固定 id 的临时修饰符（{@code add_multiplied_base}）；没人操控或者值为 0 时摘掉。
 * 下马、换装备、骑手死了都在下一 tick 跟上。临时修饰符不存档，坐骑卸载重进也不会残留。</p>
 *
 * <p>只在服务端改，原版会把这两个属性的修饰符同步给客户端（骑手的客户端按坐骑的属性算移动）。
 * 同一头坐骑上的其他乘客不算。</p>
 */
public final class MountHandler {
    private MountHandler() {}

    /** 骑手的属性 → 坐骑上的原版属性。修饰符 id 用骑手属性的 id。 */
    private record Link(Holder<Attribute> rider, Holder<Attribute> mount, Identifier modifierId) {
        Link(Holder<Attribute> rider, Holder<Attribute> mount, String path) {
            this(rider, mount, Identifier.fromNamespaceAndPath(IbxLib.MODID, path));
        }
    }

    private static List<Link> links;

    private static List<Link> links() {
        if (links == null) {
            links = List.of(
                    new Link(AttributeRegistry.MOUNT_SPEED, Attributes.MOVEMENT_SPEED, "mount_speed"),
                    new Link(AttributeRegistry.MOUNT_JUMP_STRENGTH, Attributes.JUMP_STRENGTH, "mount_jump_strength"));
        }
        return links;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MountHandler::onEntityTick);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity mount) || mount.level().isClientSide()) {
            return;
        }
        LivingEntity rider = mount.getControllingPassenger();
        for (Link link : links()) {
            AttributeInstance instance = mount.getAttribute(link.mount());
            if (instance == null) {
                continue;
            }
            double ratio = rider == null ? 0.0D : AttributeUtil.read(rider, link.rider());
            AttributeModifier current = instance.getModifier(link.modifierId());
            if (ratio == 0.0D) {
                if (current != null) {
                    instance.removeModifier(link.modifierId());
                }
            } else if (current == null || current.amount() != ratio) {
                instance.addOrUpdateTransientModifier(
                        new AttributeModifier(link.modifierId(), ratio, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }
}
