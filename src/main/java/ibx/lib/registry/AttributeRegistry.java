package ibx.lib.registry;

import ibx.lib.IbxLib;
import ibx.lib.attribute.FlagAttribute;
import ibx.lib.attribute.UnitAttribute;
import ibx.lib.attribute.WeaponAttribute;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组注册的属性（Attribute）。
 *
 * <p>两个远程属性对标近战的 {@code attack_damage}：实体基础值 0，数值由武器物品的属性修饰符提供
 * （见 {@link ibx.lib.item.general.IbxRangedWeaponStats}）。
 * 本类注册的所有属性都挂到全部生物上，见 {@code attachToLivingEntities}。</p>
 */
public final class AttributeRegistry {
    private AttributeRegistry() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, IbxLib.MODID);

    /** 武器"基础远程伤害"修饰符的 id（对标原版 {@code minecraft:base_attack_damage}）。 */
    public static final Identifier BASE_RANGED_DAMAGE_ID =
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "base_ranged_damage");

    /** 武器"基础拉弓时间"修饰符的 id。 */
    public static final Identifier BASE_DRAW_TIME_ID =
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "base_draw_time");

    /** 法器"基础法术强度"修饰符的 id。 */
    public static final Identifier BASE_SPELL_POWER_ID =
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "base_spell_power");

    /** 拉弓时间数值的语言键（"%s秒"）。 */
    public static final String SECONDS_VALUE_KEY = "attribute.value." + IbxLib.MODID + ".seconds";

    /**
     * 远程伤害：远程武器命中时的伤害点数。弩 = 本值，弓 = 本值 × 蓄力百分比。
     * 默认 0，最小 0，最大 2048。同步到客户端（tooltip 与客户端预测都要读）。
     */
    public static final DeferredHolder<Attribute, Attribute> RANGED_DAMAGE =
            ATTRIBUTES.register("ranged_damage",
                    () -> new WeaponAttribute(
                            "attribute.name." + IbxLib.MODID + ".ranged_damage",
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
     * 总值小于等于 0 时，满蓄时间由 {@link ibx.lib.api.RangedWeaponApi#getFullDrawTicks} 兜底为 1 tick。
     * 必须同步到客户端：使用时长与拉弓动画都在客户端按它算。
     */
    public static final DeferredHolder<Attribute, Attribute> DRAW_TIME =
            ATTRIBUTES.register("draw_time",
                    () -> new WeaponAttribute(
                            "attribute.name." + IbxLib.MODID + ".draw_time",
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
                            "attribute.name." + IbxLib.MODID + ".draw_speed",
                            1.0D,    // 默认值
                            0.01D,   // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true));

    /**
     * 暴击率：各类攻击触发暴击的概率。默认 0.05，最小 0，最大 1024。
     * 0~1 之间按概率判定，<b>大于等于 1 必定暴击</b>（上限留到 1024 是为了让加成能无脑往上堆）。
     * tooltip 里加法修饰符也显示成百分数（+0.15 → +15%）。
     * 判定与生效见 {@link ibx.lib.api.CriticalStrikeApi} 与
     * {@link ibx.lib.event.DamageHandler}。
     */
    public static final DeferredHolder<Attribute, Attribute> CRITICAL_CHANCE =
            ATTRIBUTES.register("critical_chance",
                    () -> new PercentageAttribute(
                            "attribute.name." + IbxLib.MODID + ".critical_chance",
                            0.05D,   // 默认值
                            0.0D,    // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true));

    /**
     * 暴击伤害：暴击时的<b>额外</b>伤害比例，公式为 {@code 最终伤害 = 原伤害 × (1 + 本值)}。
     * 默认 0.5（即暴击打 1.5 倍，与原版跳劈同值），最小 0，最大 2048。tooltip 显示成百分数（+0.2 → +20%）。
     */
    public static final DeferredHolder<Attribute, Attribute> CRITICAL_DAMAGE =
            ATTRIBUTES.register("critical_damage",
                    () -> new PercentageAttribute(
                            "attribute.name." + IbxLib.MODID + ".critical_damage",
                            0.5D,    // 默认值
                            0.0D,    // 最小值
                            2048.0D  // 最大值
                    ).setSyncable(true));

    /**
     * 回复效率：受到治疗时的倍率，公式为 {@code 实际回复量 = 回复量 × 本值}。
     * 默认 1（不改变回复量），最小 0（完全无法回复），最大 1024。
     * 结算见 {@link ibx.lib.event.HealHandler}。
     */
    public static final DeferredHolder<Attribute, Attribute> HEAL_EFFICIENCY =
            ATTRIBUTES.register("heal_efficiency",
                    () -> new RangedAttribute(
                            "attribute.name." + IbxLib.MODID + ".heal_efficiency",
                            1.0D,    // 默认值
                            0.0D,    // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true));

    /**
     * 反甲：被攻击后对攻击者造成本值点 {@code minecraft:thorns} 伤害，伤害来源算被攻击的这个实体。
     * 默认 0，最小 0，最大 2048。原版荆棘附魔改成每级 +1 本属性（数据包覆盖 {@code data/minecraft/enchantment/thorns.json}）。
     * 结算见 {@link ibx.lib.event.DamageHandler}。
     */
    public static final DeferredHolder<Attribute, Attribute> THORNS =
            ATTRIBUTES.register("thorns",
                    () -> new RangedAttribute(
                            "attribute.name." + IbxLib.MODID + ".thorns",
                            0.0D,    // 默认值
                            0.0D,    // 最小值
                            2048.0D  // 最大值
                    ).setSyncable(true));

    /** 每秒数值的语言键（"%s/秒"）。 */
    public static final String PER_SECOND_VALUE_KEY = "attribute.value." + IbxLib.MODID + ".per_second";

    /**
     * 最大法力值：当前法力的上限。默认 100，最小 0，最大 1,000,000。
     * 上限降低时当前法力被截断，上限提高时不自动补满（同原版血量）。
     * 当前法力存在附加数据里，读写见 {@link ibx.lib.api.ManaApi}。
     */
    public static final DeferredHolder<Attribute, Attribute> MAX_MANA =
            ATTRIBUTES.register("max_mana",
                    () -> new RangedAttribute(
                            "attribute.name." + IbxLib.MODID + ".max_mana",
                            100.0D,       // 默认值
                            0.0D,         // 最小值
                            1000000.0D    // 最大值
                    ).setSyncable(true));

    /**
     * 法力回复效率：每秒回复的法力点数（固定值，不按上限比例）。负数表示持续流失。
     * 默认 2（空蓝 50 秒回满），最小 -1024，最大 1024。tooltip 数值带"/秒"。
     * 结算见 {@link ibx.lib.event.ManaHandler}。
     */
    public static final DeferredHolder<Attribute, Attribute> MANA_REGENERATION =
            ATTRIBUTES.register("mana_regeneration",
                    () -> new UnitAttribute(
                            "attribute.name." + IbxLib.MODID + ".mana_regeneration",
                            2.0D,     // 默认值
                            -1024.0D, // 最小值
                            1024.0D,  // 最大值
                            PER_SECOND_VALUE_KEY
                    ).setSyncable(true));

    /**
     * 法力消耗倍率：实际消耗 = 基础消耗 × 本值。默认 1，最小 0（可以做出免费施法的装备），最大 1024。
     * 越大越坏，所以 Sentiment 取 NEGATIVE（加值显红、减值显蓝）。
     * 约定：减耗用 {@code add_multiplied_total}（连乘，永远叠不到 0）；
     * {@code add_multiplied_base} 的多个减值是相加的，两个 -50% 就成了免费。
     */
    public static final DeferredHolder<Attribute, Attribute> MANA_COST_MULTIPLIER =
            ATTRIBUTES.register("mana_cost_multiplier",
                    () -> new RangedAttribute(
                            "attribute.name." + IbxLib.MODID + ".mana_cost_multiplier",
                            1.0D,    // 默认值
                            0.0D,    // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true).setSentiment(Attribute.Sentiment.NEGATIVE));

    /**
     * 法术强度：法术伤害与治疗的基础值，法术按系数取用（如"160% 法术强度"）。对标 {@link #RANGED_DAMAGE}：
     * 实体基础值 0，由法器用 {@link #BASE_SPELL_POWER_ID} 挂 ADD_VALUE 修饰符提供，tooltip 显示成深绿合并行。
     * 默认 0，最小 0，最大 2048。暂无结算逻辑。
     */
    public static final DeferredHolder<Attribute, Attribute> SPELL_POWER =
            ATTRIBUTES.register("spell_power",
                    () -> new WeaponAttribute(
                            "attribute.name." + IbxLib.MODID + ".spell_power",
                            0.0D,    // 默认值
                            0.0D,    // 最小值
                            2048.0D, // 最大值
                            BASE_SPELL_POWER_ID,
                            null
                    ).setSyncable(true));

    /**
     * 法术冷却倍率：实际冷却 = 基础冷却 × 本值。默认 1，最小 0（无冷却），最大 1024。
     * 越大越坏，所以 Sentiment 取 NEGATIVE。减冷却的约定同 {@link #MANA_COST_MULTIPLIER}：用 {@code add_multiplied_total}。
     * 暂无结算逻辑。
     */
    public static final DeferredHolder<Attribute, Attribute> SPELL_COOLDOWN_RATIO =
            ATTRIBUTES.register("spell_cooldown_ratio",
                    () -> new RangedAttribute(
                            "attribute.name." + IbxLib.MODID + ".spell_cooldown_ratio",
                            1.0D,    // 默认值
                            0.0D,    // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true).setSentiment(Attribute.Sentiment.NEGATIVE));

    /**
     * 施法速率：公式为 {@code 实际吟唱时间 = 法术吟唱时间 ÷ 本值}，口径同 {@link #DRAW_SPEED}。
     * 默认 1，最小 0.01（防除零），最大 1024。加成建议用 {@code add_multiplied_base}。
     * 同步到客户端（读条在客户端也要算）。暂无结算逻辑。
     */
    public static final DeferredHolder<Attribute, Attribute> SPELL_CAST_SPEED =
            ATTRIBUTES.register("spell_cast_speed",
                    () -> new RangedAttribute(
                            "attribute.name." + IbxLib.MODID + ".spell_cast_speed",
                            1.0D,    // 默认值
                            0.01D,   // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true));

    /**
     * 法术抗性：法术伤害的"护甲点"，套原版护甲公式（{@code CombatRules#getDamageAfterAbsorb}），韧性用原版 {@code armor_toughness}：
     * {@code 减免比例 = clamp(R − 伤害 ÷ (2 + 韧性 ÷ 4), min(R × 0.2, 20), 20) ÷ 25}，R 为本值。
     * 最多减 80%（20 点）；超过 20 点不再提高减免上限，但让大伤害更难穿透。
     * 默认 0，最小 0，最大 4096。装备用 ADD_VALUE 给点数。
     *
     * <p>下限必须写成 {@code min(R × 0.2, 20)}：原版护甲最高 30 点不会碰到，本属性超过 100 点后
     * {@code R × 0.2} 会大于 20，而 {@code Mth.clamp} 在下限大于上限时返回下限，大伤害的减免会超过 80% 乃至 100%。</p>
     *
     * <p>暂无结算逻辑。</p>
     */
    public static final DeferredHolder<Attribute, Attribute> SPELL_RESISTANCE =
            ATTRIBUTES.register("spell_resistance",
                    () -> new RangedAttribute(
                            "attribute.name." + IbxLib.MODID + ".spell_resistance",
                            0.0D,    // 默认值
                            0.0D,    // 最小值
                            4096.0D  // 最大值
                    ).setSyncable(true));

    /**
     * 法术可暴击：开关，总值大于等于 1 时法术会按 {@link #CRITICAL_CHANCE} / {@link #CRITICAL_DAMAGE} 暴击，
     * 每个目标各掷一次；治疗法术同样适用。默认 0（法术不暴击），范围 0~1。装备挂 ADD_VALUE +1 打开，
     * tooltip 只显示"法术可暴击"不带数字（见 {@link FlagAttribute}）。同步到客户端（tooltip 与预测要读）。
     *
     * <p>暂无结算逻辑。</p>
     */
    public static final DeferredHolder<Attribute, Attribute> CAN_SPELL_CRIT =
            ATTRIBUTES.register("can_spell_crit",
                    () -> new FlagAttribute(
                            "attribute.name." + IbxLib.MODID + ".can_spell_crit"
                    ).setSyncable(true));

    /**
     * 召唤物上限：同时在场的召唤物最多占几个名额（每只占几个由召唤法术定，默认 1）。默认 1，最小 0，最大 1024。
     * 超了让最早召唤的那只消散，单只就超过上限时召唤法术放不出。结算见 {@link ibx.lib.api.SummonApi}。
     */
    public static final DeferredHolder<Attribute, Attribute> MAX_SUMMONS =
            ATTRIBUTES.register("max_summons",
                    () -> new RangedAttribute(
                            "attribute.name." + IbxLib.MODID + ".max_summons",
                            1.0D,    // 默认值
                            0.0D,    // 最小值
                            1024.0D  // 最大值
                    ).setSyncable(true));

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(AttributeRegistry::attachToLivingEntities);
        makeAttackDamageSyncable();
    }

    /**
     * 让原版的攻击伤害同步到客户端。
     *
     * <p>原版只同步护甲、韧性、攻速这类客户端要用的属性，攻击伤害没标同步；而手持物品的属性修饰符只在服务端加
     * （{@code LivingEntity.detectEquipmentUpdates} 在 {@code !isClientSide()} 里），所以客户端读到的永远是基础值，
     * HUD 属性行的"力量"拿剑也不变。本模组的远程伤害、法强等属性注册时都标了同步，没有这个问题。</p>
     *
     * <p>{@code setSyncable} 只是改个标记：服务端在属性变化时据此决定发不发属性包，变化很少（换武器、药水起落），流量可忽略。
     * 在模组构造时调用，早于任何实体生成。</p>
     */
    private static void makeAttackDamageSyncable() {
        Attributes.ATTACK_DAMAGE.value().setSyncable(true);
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
