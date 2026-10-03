package ibx.taverntales.lib.spell;

import ibx.taverntales.lib.TavernTalesLib;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * 法术注册表 {@code taverntales_l1b:spell}。各模组用 {@code DeferredRegister.create(SpellRegistries.SPELL_KEY, 模组id)} 注册法术。
 *
 * <p>用注册表而不是静态 Map：有命名空间 id、网络同步的数字 id、能做指令参数、存档直接存 id，还自带标签支持
 * （{@code data/<命名空间>/tags/taverntales_l1b/spell/}）。同步到客户端；不允许数据包覆盖。</p>
 */
public final class SpellRegistries {
    private SpellRegistries() {}

    public static final ResourceKey<Registry<Spell>> SPELL_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "spell"));

    public static final Registry<Spell> SPELL = new RegistryBuilder<>(SPELL_KEY).sync(true).create();

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SpellRegistries::onNewRegistry);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.register(SPELL);
    }
}
