package ibx.taverntales.lib.registry;

import com.mojang.serialization.Codec;
import ibx.taverntales.lib.TavernTalesLib;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组注册的物品数据组件（Data Component）。
 *
 * <p>物品栈上的数据只能用组件：NeoForge 的 {@code ItemStack}（20.5 起，26.1 仍是如此）不支持 Data Attachment
 * （那套只给实体、方块实体、区块、维度用，见 {@link AttachmentRegistry}）。</p>
 */
public final class DataComponentRegistry {
    private DataComponentRegistry() {}

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TavernTalesLib.MODID);

    /**
     * 自动射击：为 true 的 Tt 弓弩拉满即自动释放——弓直接射出，弩装填完毕；按住右键就是连发。
     * 缺省视为 false，不烤进物品默认组件，只有显式打开的物品栈才带它，例如
     * {@code /give @s taverntales_l1b:test_bow[taverntales_l1b:auto_fire=true]}。
     * 读取走 {@link ibx.taverntales.lib.api.RangedWeaponApi#isAutoFire}，
     * 生效见 {@link ibx.taverntales.lib.api.RangedWeaponApi#tickAutoFire}。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AUTO_FIRE =
            DATA_COMPONENTS.registerComponentType("auto_fire",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
