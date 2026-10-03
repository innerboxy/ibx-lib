package ibx.taverntales.lib.registry;

import ibx.taverntales.lib.TavernTalesLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的音效。具体放什么声音写在 {@code assets/taverntales_l1b/sounds.json}：目前都借原版音效事件（{@code "type": "event"}），
 * 材质包可以换成自己的 ogg。
 */
public final class SoundRegistry {
    private SoundRegistry() {}

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, TavernTalesLib.MODID);

    /** 法术轮盘打开、点选了一个法术、关上（只在本机播放）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_WHEEL_OPEN = register("spell_wheel.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_WHEEL_SELECT = register("spell_wheel.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPELL_WHEEL_CLOSE = register("spell_wheel.close");

    /** 玩家施法失败（冷却中、法力不足……），只放给施法者自己听。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CAST_FAIL = register("spell.cast_fail");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
