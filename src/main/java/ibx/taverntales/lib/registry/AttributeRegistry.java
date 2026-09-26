package ibx.taverntales.lib.registry;

import ibx.taverntales.lib.TavernTalesLib;
import ibx.taverntales.lib.attribute.WeaponAttribute;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组注册的属性（Attribute）。
 *
 * <p>两个远程属性对标近战的 {@code attack_damage}：实体基础值 0，数值由武器物品的属性修饰符提供
 * （见 {@link ibx.taverntales.lib.item.ranged_weapon.RangedWeaponStats}）。
 * 本类注册的所有属性都挂到全部生物上，见 {@code attachToLivingEntities}。</p>
 */
public final class AttributeRegistry {
    private AttributeRegistry() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, TavernTalesLib.MODID);

    /** 武器"基础远程伤害"修饰符的 id（对标原版 {@code minecraft:base_attack_damage}）。 */
    public static final Identifier BASE_RANGED_DAMAGE_ID =
            Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "base_ranged_damage");

    /** 武器"基础拉弓时间"修饰符的 id。 */
    public static final Identifier BASE_DRAW_TIME_ID =
            Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "base_draw_time");

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
     * 拉弓时间：武器的基础拉弦秒数，实际秒数还要除以 {@link #DRAW_SPEED}。
     * 一般只由武器提供；射手自身拉得快慢走 {@link #DRAW_SPEED}，不要往本属性上挂修饰符。
     * 默认 0，最小 -1024，最大 1024。越大越坏，所以 Sentiment 取 NEGATIVE（加值显红、减值显蓝）。
     * 最小值放到负数只影响显示：原版只在最后截断总值，负修饰符在空手时也能如实显示成负数；
     * 总值小于等于 0 时，满蓄时间由 {@link ibx.taverntales.lib.api.RangedWeaponApi#getFullDrawTicks} 兜底为 1 tick。
     * 必须同步到客户端：使用时长与拉弓动画都在客户端按它算。
     */
    public static final DeferredHolder<Attribute, Attribute> DRAW_TIME =
            ATTRIBUTES.register("draw_time",
                    () -> new WeaponAttribute(
                            "attribute.name." + TavernTalesLib.MODID + ".draw_time",
                            0.0D,    // 默认值
                            -1024.0D, // 最小值
                            1024.0D, // 最大值
                            BASE_DRAW_TIME_ID,
                            SECONDS_VALUE_KEY
                    ).setSyncable(true).setSentiment(Attribute.Sentiment.NEGATIVE));

    /**
     * 拉弦速率：射手拉弦的快慢，公式为 {@code 实际拉弦时间 = draw_time ÷ 本值}。
     * 默认 1，最小 0.01（防除零，即最慢 100 倍时间），最大 1024。
     * 用除法是为了让加速收益递减、永远减不到 0：两个 +100% 是 3 倍速、1/3 的时间，而不是瞬发。
     * 加成建议用 {@code add_multiplied_base}，tooltip 会显示成百分比。
     * 必须同步到客户端，理由同 {@link #DRAW_TIME}。
     */
    public static final DeferredHolder<Attribute, Attribute> DRAW_SPEED =
            ATTRIBUTES.register("draw_speed",
                    () -> new RangedAttribute(
                            "attribute.name." + TavernTalesLib.MODID + ".draw_speed",
                            1.0D,    // 默认值
                            0.01D,   // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true));

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

    /**
     * 回复效率：受到治疗时的倍率，公式为 {@code 实际回复量 = 回复量 × 本值}。
     * 默认 1（不改变回复量），最小 0（完全无法回复），最大 1024。
     * 结算见 {@link ibx.taverntales.lib.event.HealHandler}。
     */
    public static final DeferredHolder<Attribute, Attribute> HEAL_EFFICIENCY =
            ATTRIBUTES.register("heal_efficiency",
                    () -> new RangedAttribute(
                            "attribute.name." + TavernTalesLib.MODID + ".heal_efficiency",
                            1.0D,    // 默认值
                            0.0D,    // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true));

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(AttributeRegistry::attachToLivingEntities);
    }

    /**
     * 把本类注册的所有属性挂到全部生物上（玩家、原版生物、其他模组注册了属性的生物）。
     * 直接遍历注册表条目，以后新加的属性自动挂上，不用再手动维护列表。
     *
     * <p>玩家必须挂：tooltip 渲染基础值时会调 {@code player.getAttributeBaseValue}，没有该属性会直接抛异常。
     * 生物挂上之后，装备和药水对这些属性的加减才能作用到它们身上。</p>
     */
    private static void attachToLivingEntities(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            for (DeferredHolder<Attribute, ? extends Attribute> attribute : ATTRIBUTES.getEntries()) {
                if (!event.has(type, attribute)) {
                    event.add(type, attribute);
                }
            }
        }
    }
}
