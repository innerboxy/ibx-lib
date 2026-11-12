package ibx.lib.item.general;

import ibx.lib.IbxLib;
import ibx.lib.registry.AttributeRegistry;
import ibx.lib.registry.DataComponentRegistry;
import ibx.lib.spell.CastType;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellContainer;
import ibx.lib.spell.SpellRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.UseEffects;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ibx 法器：所有施法物品的基类，数值由构造参数给定。按法器种类分子类（{@link ibx.lib.item.IbxTomeItem} 法典、{@link ibx.lib.item.IbxStaffItem} 法杖……），
 * 随种类变化的东西（比如专属施法动作，重写 {@link #castAnimation}）写在子类里。
 *
 * <ul>
 *   <li>有耐久：施法时按实际扣掉的蓝量掉耐久，用光就碎，同原版工具（见 {@link ibx.lib.spell.SpellCasting#MANA_PER_DURABILITY}）。
 *       耐久给负数就是无法破坏：没有耐久条、施法不掉耐久，挂原版的 {@code unbreakable} 组件。</li>
 *   <li>主手拿着加法强：用 {@link AttributeRegistry#BASE_SPELL_POWER_ID} 挂主手，tooltip 显示成深绿合并行（同剑的攻击伤害）。
 *       写进 {@code attribute_modifiers} 组件；注册时用 {@code Properties#attributes} 给的被动属性会和它合并，排在后面
 *       （见 {@link IbxAttributeModifiers}），子类要加自己的修饰符也用它。</li>
 *   <li>施法时的移速倍率写进 {@code use_effects} 组件，0 是施法时站住不动，不填用 {@link #BASELINE_CAST_MOVE_SPEED}。</li>
 *   <li>不可堆叠（无法破坏的也是）。</li>
 *   <li>自带法术按 id 给，不填为空。物品栈带 {@code spells} 组件时以组件为准（指令、战利品可以覆盖，给空列表就是没有法术）。
 *       id 查不到的跳过，所以可以写别的子模组的法术而不依赖它。</li>
 *   <li>施法动作见 {@link #castAnimation}，法术自己指定了动作时以法术为准。</li>
 *   <li>自动进 {@code #ibx_lib:can_cast_spell}、{@code #c:tools}、{@code #c:tools/magic_weapon}，子类再进各自的
 *       {@code #c:tools/tome}、{@code #c:tools/staff}（见 {@link ibx.lib.registry.AutoItemTags}），data 里不用再写。</li>
 * </ul>
 *
 * <p>注册：同 {@link ibx.lib.item.IbxBowItem}，用 {@code DeferredRegister.Items#registerItem}，在工厂里使用传进来的 Properties，例如
 * {@code ITEMS.registerItem("my_tome", p -> new IbxTomeItem(p, 1000, 6.0F))}、
 * {@code ITEMS.registerItem("fire_tome", p -> new IbxTomeItem(p, 1500, 8.0F, List.of(Identifier.parse("examplemod:fireball"))))}。</p>
 */
public class IbxMagicWeaponItem extends Item implements SpellContainer {
    /** 施法时移速倍率的基准值（原版使用物品默认 0.2，太慢）。 */
    public static final float BASELINE_CAST_MOVE_SPEED = 0.5F;
    /** 默认施法动作（法典的），见 {@link #defaultCastAnimation}。动画在 lib 的 {@code player_animations/}。 */
    private static final Identifier CAST_INSTANT = Identifier.fromNamespaceAndPath(IbxLib.MODID, "tome.cast_instant");
    private static final Identifier CAST_HOLD = Identifier.fromNamespaceAndPath(IbxLib.MODID, "tome.cast_hold");
    private static final Identifier CAST_CHANNEL = Identifier.fromNamespaceAndPath(IbxLib.MODID, "tome.cast_channel");

    private final float spellPower;
    private final List<Identifier> defaultSpellIds;
    /** 自带法术解析成的 Holder；法术注册表在启动时就定了，第一次用到时解析一次。 */
    private @Nullable List<Holder<Spell>> defaultSpells;

    /** 施法移速用基准值，不带法术。 */
    public IbxMagicWeaponItem(Item.Properties properties, int durability, float spellPower) {
        this(properties, durability, spellPower, BASELINE_CAST_MOVE_SPEED, List.of());
    }

    /** 不带法术。 */
    public IbxMagicWeaponItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed) {
        this(properties, durability, spellPower, castMoveSpeed, List.of());
    }

    /** 施法移速用基准值。 */
    public IbxMagicWeaponItem(Item.Properties properties, int durability, float spellPower, List<Identifier> spells) {
        this(properties, durability, spellPower, BASELINE_CAST_MOVE_SPEED, spells);
    }

    /**
     * @param properties    物品属性，必须带物品 id（见类说明的注册方式）
     * @param durability    耐久；负数是无法破坏
     * @param spellPower    法强（主手）
     * @param castMoveSpeed 施法时的移速倍率，截到 0~1，0 是施法时站住不动；负数用 {@link #BASELINE_CAST_MOVE_SPEED}
     * @param spells        自带法术的 id，按轮盘里的顺序
     */
    public IbxMagicWeaponItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed, List<Identifier> spells) {
        super(durability(IbxAttributeModifiers.add(properties, modifiers(spellPower)), durability).component(DataComponents.USE_EFFECTS,
                new UseEffects(false, true, castMoveSpeed >= 0.0F ? Mth.clamp(castMoveSpeed, 0.0F, 1.0F) : BASELINE_CAST_MOVE_SPEED)));
        this.spellPower = spellPower;
        this.defaultSpellIds = List.copyOf(spells);
    }

    /** 负数：无法破坏，不设耐久（没有耐久条），照样不可堆叠。 */
    private static Item.Properties durability(Item.Properties properties, int durability) {
        return durability >= 0 ? properties.durability(durability)
                : properties.stacksTo(1).component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
    }

    private static ItemAttributeModifiers modifiers(float spellPower) {
        // 这里只存 Holder，不解析属性；物品注册早于属性注册，DeferredHolder 到时候自己会绑定。
        return ItemAttributeModifiers.builder()
                .add(AttributeRegistry.SPELL_POWER,
                        new AttributeModifier(AttributeRegistry.BASE_SPELL_POWER_ID, spellPower, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public float spellPower() {
        return this.spellPower;
    }

    /**
     * 默认施法动作，按施法类型分：瞬发放一遍（施法手前伸、另一只手往后摆一点）；吟唱循环（另一只手画圈）；
     * 引导、蓄力共用一段循环。动作都按右手施法做，左手施法时整段镜像。不是 Ibx 法器的施法物品也用它。
     */
    public static Identifier defaultCastAnimation(CastType type) {
        return switch (type) {
            case INSTANT -> CAST_INSTANT;
            case CAST -> CAST_HOLD;
            case CHANNEL, CHARGE -> CAST_CHANNEL;
        };
    }

    /**
     * 这件法器施法时放的动作，默认 {@link #defaultCastAnimation}。特殊的法器可以重写，按施法类型换动作。
     * 法术自己指定了动作（{@link Spell#castAnimation}）时以法术为准。
     */
    public Identifier castAnimation(CastType type) {
        return defaultCastAnimation(type);
    }

    @Override
    public List<Holder<Spell>> getSpells(ItemStack stack) {
        List<Holder<Spell>> own = stack.get(DataComponentRegistry.SPELLS.get());
        return own != null ? own : this.defaultSpells();
    }

    /** 构造时给的自带法术（物品栈上的 {@code spells} 组件改过也还是这几个）。 */
    @Override
    public List<Holder<Spell>> innateSpells(ItemStack stack) {
        return this.defaultSpells();
    }

    private List<Holder<Spell>> defaultSpells() {
        if (this.defaultSpells == null) {
            List<Holder<Spell>> resolved = new ArrayList<>(this.defaultSpellIds.size());
            for (Identifier id : this.defaultSpellIds) {
                SpellRegistries.SPELL.get(id).ifPresent(resolved::add);
            }
            this.defaultSpells = List.copyOf(resolved);
        }
        return this.defaultSpells;
    }
}
