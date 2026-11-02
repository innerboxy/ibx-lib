package ibx.lib.client.attribute;

import ibx.lib.IbxLib;
import ibx.lib.config.ClientConfig;
import ibx.lib.config.ClientConfig.AttributeSort;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 属性面板和它的设置界面列的东西：玩家身上有的全部属性（原版、本模组、其他模组），按设置排序、分组、筛选。
 *
 * <ul>
 *   <li>默认顺序：原版按 {@link #DEFAULT_ORDER}（生命、防御、攻击、移动……），本模组同理，没列到的按注册顺序接在各自后面；
 *       其他模组排在本模组之后，NeoForge 自己的放最后。</li>
 *   <li>按名称：按当前语言的显示名（中文按拼音）。自定义：设置界面里 ▲▼ 调出来的顺序，没调到的按默认顺序接在后面。</li>
 *   <li>分组：按属性 id 的命名空间分，组名优先用语言文件 {@code gui.ibx_lib.attribute_panel.mod.<命名空间>}，没有就用模组显示名。</li>
 * </ul>
 * 数值是客户端看到的值：服务端不同步给客户端的属性（{@code Attribute#isClientSyncable} 为 false）只能显示基础值。
 */
public final class AttributeList {
    private AttributeList() {}

    public sealed interface Entry permits Header, Row {}

    public record Header(Component name) implements Entry {}

    public record Row(Holder<Attribute> attribute) implements Entry {}

    private static @Nullable Collator collator;
    private static @Nullable String collatorLanguage;

    private static final List<String> DEFAULT_ORDER = List.of(
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
            "ibx_lib:draw_time", "ibx_lib:heal_efficiency", "ibx_lib:thorns", "ibx_lib:max_summons");

    /** 面板里要列的：去掉设置里关掉的；勾了「隐藏默认值」再去掉还是默认值的。 */
    public static List<Entry> forPanel(Player player) {
        Set<String> hidden = new HashSet<>(ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_HIDDEN));
        boolean hideDefault = ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_HIDE_DEFAULT);
        AttributeSupplier defaults = DefaultAttributes.getSupplier(EntityType.PLAYER);
        List<Holder<Attribute>> shown = new ArrayList<>();
        for (Holder<Attribute> attribute : sorted(player)) {
            if (hidden.contains(id(attribute))) {
                continue;
            }
            if (hideDefault && defaults.hasAttribute(attribute)
                    && Math.abs(player.getAttributeValue(attribute) - defaults.getValue(attribute)) < 1.0E-6) {
                continue;
            }
            shown.add(attribute);
        }
        return withHeaders(shown);
    }

    /** 设置界面里列的：全部属性（关掉的也列，好再勾回来），顺序、分组同面板。 */
    public static List<Entry> forSettings(Player player) {
        return withHeaders(sorted(player));
    }

    /** 按当前排序方式排好、分好组（分组只影响先后，标题由 {@link #withHeaders} 加）的全部属性。 */
    public static List<Holder<Attribute>> sorted(Player player) {
        List<Holder<Attribute>> all = new ArrayList<>();
        Map<Holder<Attribute>, Integer> registryIndex = new HashMap<>();
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(attribute -> {
            if (player.getAttributes().hasAttribute(attribute)) {
                registryIndex.put(attribute, all.size());
                all.add(attribute);
            }
        });
        Comparator<Holder<Attribute>> byDefault = Comparator
                .comparingInt((Holder<Attribute> a) -> namespaceRank(namespace(a)))
                .thenComparingInt(a -> {
                    int i = DEFAULT_ORDER.indexOf(id(a));
                    return i >= 0 ? i : DEFAULT_ORDER.size() + registryIndex.get(a);
                });
        Comparator<Holder<Attribute>> order = switch (ClientConfig.SPEC.isLoaded()
                ? ClientConfig.ATTRIBUTE_PANEL_SORT.get() : AttributeSort.DEFAULT) {
            case DEFAULT -> byDefault;
            case NAME -> Comparator.<Holder<Attribute>, String>comparing(a -> name(a).getString(), collator()).thenComparing(byDefault);
            case CUSTOM -> {
                List<? extends String> custom = ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_ORDER);
                yield Comparator.comparingInt((Holder<Attribute> a) -> {
                    int i = custom.indexOf(id(a));
                    return i >= 0 ? i : Integer.MAX_VALUE;
                }).thenComparing(byDefault);
            }
        };
        if (ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_GROUP_BY_MOD)) {
            order = Comparator.comparingInt((Holder<Attribute> a) -> namespaceRank(namespace(a)))
                    .thenComparing(AttributeList::namespace)
                    .thenComparing(order);
        }
        all.sort(order);
        return all;
    }

    private static List<Entry> withHeaders(List<Holder<Attribute>> attributes) {
        List<Entry> out = new ArrayList<>(attributes.size() + 4);
        if (!ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_GROUP_BY_MOD)) {
            attributes.forEach(a -> out.add(new Row(a)));
            return out;
        }
        Map<String, List<Holder<Attribute>>> groups = new LinkedHashMap<>();
        attributes.forEach(a -> groups.computeIfAbsent(namespace(a), k -> new ArrayList<>()).add(a));
        groups.forEach((namespace, members) -> {
            out.add(new Header(groupName(namespace)));
            members.forEach(a -> out.add(new Row(a)));
        });
        return out;
    }

    public static Component name(Holder<Attribute> attribute) {
        return Component.translatable(attribute.value().getDescriptionId());
    }

    /** 当前值，按属性自己的格式写（百分比属性带 %、布尔属性写开 / 关、秒数带单位……）。 */
    public static Component value(Player player, Holder<Attribute> attribute) {
        return attribute.value().toValueComponent(null, player.getAttributeValue(attribute), TooltipFlag.NORMAL);
    }

    public static String id(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(k -> k.identifier().toString()).orElse("");
    }

    private static String namespace(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(k -> k.identifier().getNamespace()).orElse("");
    }

    /** 原版最前，然后本模组，其他模组在中间，NeoForge 自己的最后。 */
    private static int namespaceRank(String namespace) {
        return switch (namespace) {
            case Identifier.DEFAULT_NAMESPACE -> 0;
            case IbxLib.MODID -> 1;
            case "neoforge" -> 3;
            default -> 2;
        };
    }

    private static Component groupName(String namespace) {
        String key = "gui.ibx_lib.attribute_panel.mod." + namespace;
        if (Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        return Component.literal(ModList.get().getModContainerById(namespace)
                .map(c -> c.getModInfo().getDisplayName()).orElse(namespace));
    }

    /** 按当前游戏语言比较名字（中文按拼音）；面板每帧都排，按语言缓存一个。 */
    private static Collator collator() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!language.equals(collatorLanguage)) {
            collator = Collator.getInstance(Locale.forLanguageTag(language.replace('_', '-')));
            collatorLanguage = language;
        }
        return collator;
    }
}
