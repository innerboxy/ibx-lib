package ibx.taverntales.lib.client.hud;

import ibx.taverntales.lib.TavernTalesLib;
import net.minecraft.resources.Identifier;

/**
 * HUD 用到的 GUI sprite，贴图在 {@code assets/taverntales_l1b/textures/gui/sprites/hud/}。
 * 尺寸和画法见设计稿 gitignores/HUD.md 第三节。
 */
final class HudSprites {
    private HudSprites() {}

    // 条：89×15（槽在 (4,4)，81×7）；光边 91×17；流光 12×7；蓝条宝石 7×7
    static final Identifier BAR_BACKGROUND = id("bar/background");
    static final Identifier BAR_MANA = id("bar/mana");
    static final Identifier BAR_CAST = id("bar/cast");
    static final Identifier BAR_CHANNEL = id("bar/channel");
    static final Identifier BAR_CHARGE = id("bar/charge");
    static final Identifier BAR_CHARGE_FULL = id("bar/charge_full");
    static final Identifier BAR_CHARGE_GLOW = id("bar/charge_glow");
    static final Identifier BAR_SHIMMER = id("bar/shimmer");
    static final Identifier BAR_GEM = id("bar/gem");
    static final Identifier BAR_RESULT_COMPLETE = id("bar/result_complete");
    static final Identifier BAR_RESULT_INTERRUPTED = id("bar/result_interrupted");
    static final Identifier BAR_RESULT_CANCELLED = id("bar/result_cancelled");
    static final Identifier BAR_RESULT_OUT_OF_MANA = id("bar/result_out_of_mana");

    // 生命条、饥饿条：2 倍精度，贴图 180×26 画成 90×13；生命条底板在左、槽 (19,3) 68×7；
    // 饥饿条底板在右、左块 (3,3) 33×7、右块 (38,3) 33×7。填充图只画槽里那一段，按 u 偏移截取
    static final Identifier HEALTH_FRAME = id("bar/health_frame");
    static final Identifier FOOD_FRAME = id("bar/food_frame");
    static final Identifier HEALTH = id("bar/health");
    static final Identifier HEALTH_POISON = id("bar/health_poison");
    static final Identifier HEALTH_WITHER = id("bar/health_wither");
    static final Identifier HEALTH_FROZEN = id("bar/health_frozen");
    static final Identifier HEALTH_ABSORPTION = id("bar/health_absorption");
    static final Identifier HUNGER = id("bar/hunger");
    static final Identifier HUNGER_EFFECT = id("bar/hunger_effect");
    static final Identifier SATURATION = id("bar/saturation");

    // 9×9 图标
    static final Identifier ICON_ARMOR = id("icon/armor");
    static final Identifier ICON_SPELL_RESISTANCE = id("icon/spell_resistance");
    static final Identifier ICON_TOUGHNESS = id("icon/toughness");
    static final Identifier ICON_ATTACK_DAMAGE = id("icon/attack_damage");
    static final Identifier ICON_RANGED_DAMAGE = id("icon/ranged_damage");
    static final Identifier ICON_SPELL_POWER = id("icon/spell_power");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "hud/" + path);
    }
}
