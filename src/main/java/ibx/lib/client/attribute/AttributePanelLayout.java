package ibx.lib.client.attribute;

import ibx.lib.config.ClientConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 属性面板的默认排法，别的模组在这里登记自己的属性：默认排在哪、默认显不显示。只在客户端，在客户端模组入口里调：
 *
 * <pre>{@code
 * AttributePanelLayout.order(MyAttributes.RAGE, MyAttributes.RAGE_REGENERATION, MyAttributes.BLOCK_CHANCE);
 * AttributePanelLayout.hideByDefault(MyAttributes.INTERNAL_COUNTER);
 * }</pre>
 *
 * <ul>
 *   <li>{@link #order}：这几个属性按写的先后排；没登记的属性按注册顺序接在登记过的后面。命名空间之间的先后不归这里管
 *       （原版最前，然后本模组，其他模组，NeoForge 最后，见 {@link AttributeList}）。</li>
 *   <li>{@link #hideByDefault}：默认不列出来。玩家的设置（客户端配置 {@code attributePanelHidden}）说了算：
 *       每个登记的属性只替玩家隐藏一次，之后玩家在设置界面里勾回来就一直显示；点「恢复默认」会再隐藏。</li>
 * </ul>
 * 图标不用登记，按路径放贴图就行，见 {@link AttributeIcons}。
 */
public final class AttributePanelLayout {
    private AttributePanelLayout() {}

    /** 默认顺序（属性 id）：原版和本模组的写在这里，别的模组登记的接在后面。模组并行加载，所以用并发安全的列表。 */
    private static final List<String> ORDER = new CopyOnWriteArrayList<>(List.of(
            "minecraft:max_health", "minecraft:max_absorption", "minecraft:armor", "minecraft:armor_toughness",
            "minecraft:knockback_resistance", "minecraft:attack_damage", "minecraft:attack_speed", "minecraft:attack_knockback",
            "minecraft:sweeping_damage_ratio", "minecraft:movement_speed", "minecraft:sneaking_speed", "minecraft:jump_strength",
            "minecraft:step_height", "minecraft:gravity", "minecraft:safe_fall_distance", "minecraft:fall_damage_multiplier",
            "minecraft:scale", "minecraft:luck", "minecraft:block_interaction_range", "minecraft:entity_interaction_range",
            "minecraft:block_break_speed", "minecraft:mining_efficiency", "minecraft:submerged_mining_speed",
            "minecraft:oxygen_bonus", "minecraft:water_movement_efficiency", "minecraft:movement_efficiency",
            "minecraft:burning_time", "minecraft:explosion_knockback_resistance", "minecraft:camera_distance",
            "ibx_lib:max_mana", "ibx_lib:mana_regeneration", "ibx_lib:mana_cost_multiplier", "ibx_lib:spell_power",
            "ibx_lib:spell_resistance", "ibx_lib:spell_penetration", "ibx_lib:spell_cast_speed", "ibx_lib:spell_cooldown_ratio",
            "ibx_lib:can_spell_crit", "ibx_lib:critical_chance", "ibx_lib:critical_damage", "ibx_lib:armor_penetration",
            "ibx_lib:ranged_damage", "ibx_lib:draw_speed",
            "ibx_lib:draw_time", "ibx_lib:heal_efficiency", "ibx_lib:thorns", "ibx_lib:max_summons"));
    /** 别的模组登记的「默认不显示」。 */
    private static final List<String> HIDDEN_BY_DEFAULT = new CopyOnWriteArrayList<>();
    /** 有还没并进玩家配置的登记项。 */
    private static volatile boolean pendingDefaults;

    /** 这几个属性的默认先后（已经登记过的不动）。可以分几次调，后调的接在先调的后面。 */
    @SafeVarargs
    public static void order(Holder<Attribute>... attributes) {
        for (Holder<Attribute> attribute : attributes) {
            String id = AttributeList.id(attribute);
            if (!id.isEmpty() && !ORDER.contains(id)) {
                ORDER.add(id);
            }
        }
    }

    /** 这几个属性默认不在面板里列出来（玩家可以在设置界面里勾回来）。 */
    @SafeVarargs
    public static void hideByDefault(Holder<Attribute>... attributes) {
        for (Holder<Attribute> attribute : attributes) {
            String id = AttributeList.id(attribute);
            if (!id.isEmpty() && !HIDDEN_BY_DEFAULT.contains(id)) {
                HIDDEN_BY_DEFAULT.add(id);
                pendingDefaults = true;
            }
        }
    }

    /** 这个属性在默认顺序里排第几；没登记过为 -1。 */
    static int orderIndex(String id) {
        return ORDER.indexOf(id);
    }

    static int orderSize() {
        return ORDER.size();
    }

    /** 「恢复默认」时不显示的属性：配置自带的那几个，加上别的模组登记的。 */
    static List<String> defaultHidden() {
        List<String> hidden = new ArrayList<>(ClientConfig.ATTRIBUTE_PANEL_HIDDEN.getDefault());
        for (String id : HIDDEN_BY_DEFAULT) {
            if (!hidden.contains(id)) {
                hidden.add(id);
            }
        }
        return hidden;
    }

    /**
     * 把还没替玩家应用过的「默认不显示」并进配置（每个属性只并一次，记在 {@code attributePanelDefaultsApplied} 里），
     * 列属性之前调。配置还没加载、没有新登记项时什么也不做。
     */
    static void applyDefaults() {
        if (!pendingDefaults || !ClientConfig.SPEC.isLoaded()) {
            return;
        }
        pendingDefaults = false;
        List<String> applied = new ArrayList<>(ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_DEFAULTS_APPLIED));
        List<String> hidden = new ArrayList<>(ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_HIDDEN));
        boolean changed = false;
        for (String id : HIDDEN_BY_DEFAULT) {
            if (applied.contains(id)) {
                continue;
            }
            applied.add(id);
            if (!hidden.contains(id)) {
                hidden.add(id);
            }
            changed = true;
        }
        if (changed) {
            ClientConfig.ATTRIBUTE_PANEL_HIDDEN.set(hidden);
            ClientConfig.ATTRIBUTE_PANEL_HIDDEN.save();
            ClientConfig.ATTRIBUTE_PANEL_DEFAULTS_APPLIED.set(applied);
            ClientConfig.ATTRIBUTE_PANEL_DEFAULTS_APPLIED.save();
        }
    }
}
