package ibx.taverntales.lib.registry;

import ibx.taverntales.lib.TavernTalesLib;
import ibx.taverntales.lib.attribute.WeaponAttribute;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * 本模组注册的属性（Attribute）。
 *
 * <p>两个远程属性对标近战的 {@code attack_damage}：实体基础值 0，数值由武器物品的属性修饰符提供
 * （见 {@link ibx.taverntales.lib.item.ranged_weapon.RangedWeaponStats}）。只挂在玩家身上；
 * 非玩家射手的兜底见 {@link ibx.taverntales.lib.api.RangedWeaponApi}。</p>
 */
public final class AttributeRegistry {
    private AttributeRegistry() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, TavernTalesLib.MODID);

    /** 武器"基础远程伤害"修饰符的 id（对标原版 {@code minecraft:base_attack_damage}）。 */
    public static final ResourceLocation BASE_RANGED_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(TavernTalesLib.MODID, "base_ranged_damage");

    /** 武器"基础拉弓时间"修饰符的 id。 */
    public static final ResourceLocation BASE_DRAW_TIME_ID =
            ResourceLocation.fromNamespaceAndPath(TavernTalesLib.MODID, "base_draw_time");

    /** 拉弓时间数值的语言键（"%s秒"）。 */
    public static final String SECONDS_VALUE_KEY = "attribute.value." + TavernTalesLib.MODID + ".seconds";

    /**
     * 远程伤害：远程武器命中时的伤害点数。弩 = 本值，弓 = 本值 × 蓄力百分比。
     * 默认 0，最小 0，最大 2048。同步到客户端（tooltip 与客户端预测都要读）。
     */
    public static final DeferredHolder<Attribute, Attribute> RANGED_DAMAGE =
            ATTRIBUTES.register("ranged_damage",
                    () -> new WeaponAttribute(
                            "attribute.name." + TavernTalesLib.MODID + ".ranged_damage",
                            0.0D,    // 默认值
                            0.0D,    // 最小值
                            2048.0D, // 最大值
                            BASE_RANGED_DAMAGE_ID,
                            null
                    ).setSyncable(true));

    /**
     * 拉弓时间：弓满蓄 / 弩装填所需秒数，满蓄 tick = 本值 × 20（最少 1 tick）。
     * 默认 0，最小 0，最大 1024。越大越坏，所以 Sentiment 取 NEGATIVE（加值显红、减值显蓝）。
     * 必须同步到客户端：使用时长与拉弓动画都在客户端按它算。
     */
    public static final DeferredHolder<Attribute, Attribute> DRAW_TIME =
            ATTRIBUTES.register("draw_time",
                    () -> new WeaponAttribute(
                            "attribute.name." + TavernTalesLib.MODID + ".draw_time",
                            0.0D,    // 默认值
                            0.0D,    // 最小值
                            1024.0D, // 最大值
                            BASE_DRAW_TIME_ID,
                            SECONDS_VALUE_KEY
                    ).setSyncable(true).setSentiment(Attribute.Sentiment.NEGATIVE));

    /**
     * 暴击率：各类攻击触发暴击的概率。默认 0.05，最小 0，最大 1024。
     * 0~1 之间按概率判定，<b>大于等于 1 必定暴击</b>（上限留到 1024 是为了让加成能无脑往上堆）。
     * 判定与生效见 {@link ibx.taverntales.lib.api.CriticalStrikeApi} 与
     * {@link ibx.taverntales.lib.event.DamageHandler}。
     */
    public static final DeferredHolder<Attribute, Attribute> CRITICAL_CHANCE =
            ATTRIBUTES.register("critical_chance",
                    () -> new RangedAttribute(
                            "attribute.name." + TavernTalesLib.MODID + ".critical_chance",
                            0.05D,   // 默认值
                            0.0D,    // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true));

    /**
     * 暴击伤害：暴击时的<b>额外</b>伤害比例，公式为 {@code 最终伤害 = 原伤害 × (1 + 本值)}。
     * 默认 0.5（即暴击打 1.5 倍，与原版跳劈同值），最小 0，最大 2048。
     */
    public static final DeferredHolder<Attribute, Attribute> CRITICAL_DAMAGE =
            ATTRIBUTES.register("critical_damage",
                    () -> new RangedAttribute(
                            "attribute.name." + TavernTalesLib.MODID + ".critical_damage",
                            0.5D,    // 默认值
                            0.0D,    // 最小值
                            2048.0D  // 最大值
                    ).setSyncable(true));

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(AttributeRegistry::attachToPlayer);
    }

    /**
     * 把远程属性挂到玩家上。必须挂：tooltip 渲染基础值时会调 {@code player.getAttributeBaseValue}，
     * 玩家没有该属性会直接抛异常。
     */
    private static void attachToPlayer(EntityAttributeModificationEvent event) {
        for (DeferredHolder<Attribute, Attribute> attribute :
                List.of(RANGED_DAMAGE, DRAW_TIME, CRITICAL_CHANCE, CRITICAL_DAMAGE)) {
            if (!event.has(EntityType.PLAYER, attribute)) {
                event.add(EntityType.PLAYER, attribute);
            }
        }
    }
}
