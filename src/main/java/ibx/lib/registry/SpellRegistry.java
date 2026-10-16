package ibx.lib.registry;

import ibx.lib.IbxLib;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellRegistries;
import ibx.lib.summon.DismissSummonsSpell;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** lib 自己注册的法术：框架功能用的，不是具体内容（具体法术在各内容模组）。 */
public final class SpellRegistry {
    private SpellRegistry() {}

    private static final DeferredRegister<Spell> SPELLS = DeferredRegister.create(SpellRegistries.SPELL_KEY, IbxLib.MODID);

    /** 解散召唤物 Dismiss Summons：瞬发、不耗蓝、冷却 1 秒；身上有召唤物时自动出现在法术列表里。 */
    public static final DeferredHolder<Spell, DismissSummonsSpell> DISMISS_SUMMONS = SPELLS.register("dismiss_summons",
            () -> new DismissSummonsSpell(Spell.props().cooldown(1.0F)));

    public static void register(IEventBus modEventBus) {
        SPELLS.register(modEventBus);
    }
}
