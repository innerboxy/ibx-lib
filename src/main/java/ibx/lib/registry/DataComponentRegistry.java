package ibx.lib.registry;

import com.mojang.serialization.Codec;
import ibx.lib.IbxLib;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * 本模组注册的物品数据组件（Data Component）。
 *
 * <p>物品栈上的数据只能用组件：NeoForge 的 {@code ItemStack}（20.5 起，26.1 仍是如此）不支持 Data Attachment
 * （那套只给实体、方块实体、区块、维度用，见 {@link AttachmentRegistry}）。</p>
 */
public final class DataComponentRegistry {
    private DataComponentRegistry() {}

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IbxLib.MODID);

    /**
     * 自动射击：为 true 的 Ibx 弓弩（以及被接管的原版弓弩）拉满即自动释放——弓直接射出，弩装填完毕；按住右键就是连发。
     * 缺省视为 false，不烤进物品默认组件，只有显式打开的物品栈才带它，例如
     * {@code /give @s minecraft:bow[ibx_lib:auto_fire=true]}。
     * 读取走 {@link ibx.lib.api.RangedWeaponApi#isAutoFire}，
     * 生效见 {@link ibx.lib.api.RangedWeaponApi#tickAutoFire}。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AUTO_FIRE =
            DATA_COMPONENTS.registerComponentType("auto_fire",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /**
     * 物品提供的法术（法典、卷轴、法杖、护甲、饰品……），按轮盘里的顺序。带它的物品就是法术容器，
     * 见 {@link ibx.lib.spell.SpellContainer}；玩家能放的法术是身上所有容器的并集。
     * 存的是法术 id，例如 {@code /give @s minecraft:book[ibx_lib:spells=["examplemod:fireball"]]}。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Holder<Spell>>>> SPELLS =
            DATA_COMPONENTS.registerComponentType("spells",
                    builder -> builder.persistent(SpellRegistries.SPELL.holderByNameCodec().listOf())
                            .networkSynchronized(ByteBufCodecs.idMapper(SpellRegistries.SPELL.asHolderIdMap()).apply(ByteBufCodecs.list()))
                            .cacheEncoding());

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
