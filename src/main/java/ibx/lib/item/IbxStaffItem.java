package ibx.lib.item;

import ibx.lib.IbxLib;
import ibx.lib.compat.BetterCombatCompat;
import ibx.lib.item.general.IbxAttributeModifiers;
import ibx.lib.item.general.IbxMagicWeaponItem;
import ibx.lib.spell.CastType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;

/**
 * Ibx 法杖：法杖类法器。数值、法术、施法资格都在 {@link IbxMagicWeaponItem} 里，法杖特有的写在这里。
 * 参数含义和注册方式见 {@link IbxMagicWeaponItem}。
 *
 * <p>主手攻速：烤 {@code attack_speed} 修饰符（基础 id，tooltip 同剑的深绿行），不填用 {@link #DEFAULT_ATTACK_SPEED}。
 * 法杖拿去近战时，挥一下的快慢看它。</p>
 *
 * <p>左键动作默认用 Better Combat 的 {@code bettercombat:wand}（{@link #PRESET}），写成物品的默认组件，不用每根法杖再写
 * {@code weapon_attributes} 文件；想换，在传进来的 Properties 上先调一次 {@link BetterCombatCompat#preset}。没装 Better Combat 时是原版挥臂。</p>
 *
 * <p>施法动作用法杖自己的三段（lib 的 {@code player_animations/staff_cast.json}）：右手抬起、法杖向前倾、左手微微往后；
 * 瞬发摆一下就收回，吟唱时右手连着法杖微微转圈，引导、蓄力时微微抖动。
 * 动作按这样的握法做：第三人称法杖和手臂垂直（手持模型沿 +Y、握点在模型中心、第三人称显示不旋转），
 * 所以手臂往前抬起时法杖才立起来。新法杖的手持模型照这个握法摆，就能直接用这套动作。</p>
 */
public class IbxStaffItem extends IbxMagicWeaponItem {
    /** 攻速基数默认值：+ 玩家的 4 = 显示的 1.2，同镐。 */
    public static final float DEFAULT_ATTACK_SPEED = -2.8F;
    /** 左键动作的默认预设。 */
    public static final Identifier PRESET = BetterCombatCompat.builtin("wand");

    private static final Identifier CAST_INSTANT = Identifier.fromNamespaceAndPath(IbxLib.MODID, "staff.cast_instant");
    private static final Identifier CAST_HOLD = Identifier.fromNamespaceAndPath(IbxLib.MODID, "staff.cast_hold");
    private static final Identifier CAST_CHANNEL = Identifier.fromNamespaceAndPath(IbxLib.MODID, "staff.cast_channel");

    public IbxStaffItem(Item.Properties properties, int durability, float spellPower) {
        this(properties, durability, spellPower, BASELINE_CAST_MOVE_SPEED, DEFAULT_ATTACK_SPEED, List.of());
    }

    public IbxStaffItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed) {
        this(properties, durability, spellPower, castMoveSpeed, DEFAULT_ATTACK_SPEED, List.of());
    }

    public IbxStaffItem(Item.Properties properties, int durability, float spellPower, List<Identifier> spells) {
        this(properties, durability, spellPower, BASELINE_CAST_MOVE_SPEED, DEFAULT_ATTACK_SPEED, spells);
    }

    public IbxStaffItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed, List<Identifier> spells) {
        this(properties, durability, spellPower, castMoveSpeed, DEFAULT_ATTACK_SPEED, spells);
    }

    /**
     * 其余参数同 {@link IbxMagicWeaponItem}；只想改攻速时 {@code castMoveSpeed} 给 {@link #BASELINE_CAST_MOVE_SPEED}。
     *
     * @param attackSpeed 主手攻速基数，+ 玩家的 4 = 显示的攻速
     */
    public IbxStaffItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed, float attackSpeed,
                        List<Identifier> spells) {
        // 先加攻速、父类再加法强：后加的排前面，说明框里是法强、攻速、注册时给的被动属性
        super(BetterCombatCompat.preset(IbxAttributeModifiers.add(properties, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build()), PRESET), durability, spellPower, castMoveSpeed, spells);
    }

    /** 瞬发放一遍；吟唱、引导循环，引导和蓄力共用一段。 */
    @Override
    public Identifier castAnimation(CastType type) {
        return switch (type) {
            case INSTANT -> CAST_INSTANT;
            case CAST -> CAST_HOLD;
            case CHANNEL, CHARGE -> CAST_CHANNEL;
        };
    }
}
