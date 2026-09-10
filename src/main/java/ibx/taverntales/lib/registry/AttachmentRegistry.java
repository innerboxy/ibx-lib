package ibx.taverntales.lib.registry;

import com.mojang.serialization.Codec;
import ibx.taverntales.lib.TavernTalesLib;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 本模组注册的 Data Attachment（实体附加数据）。
 */
public final class AttachmentRegistry {
    private AttachmentRegistry() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TavernTalesLib.MODID);

    /**
     * 弹药上的远程伤害：Tt 弓弩发射的瞬间把算好的最终伤害写在箭上，命中时由
     * {@link ibx.taverntales.lib.event.DamageHandler} 直接覆盖原版伤害。
     * 原版弓弩射出的箭没有这个数据，完全不受影响。带序列化：箭跨区块加载、插在地上都不丢。
     * 读写请走 {@link ibx.taverntales.lib.api.RangedWeaponApi}。
     */
    public static final Supplier<AttachmentType<Float>> AMMO_RANGED_DAMAGE =
            ATTACHMENT_TYPES.register("ammo_ranged_damage",
                    () -> AttachmentType.builder(() -> 0.0F)
                            .serialize(Codec.FLOAT)
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
