package ibx.taverntales.lib.item;

import ibx.taverntales.lib.TavernTalesLib;
import ibx.taverntales.lib.item.magic_weapon.TtMagicWeaponItem;
import ibx.taverntales.lib.spell.CastType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Tt 法杖：法杖类法器。数值、法术、施法资格都在 {@link TtMagicWeaponItem} 里，法杖特有的写在这里。
 * 参数含义和注册方式见 {@link TtMagicWeaponItem}。
 *
 * <p>施法动作用法杖自己的三段（lib 的 {@code player_animations/staff_cast.json}）：右手抬起、法杖向前倾、左手微微往后；
 * 瞬发摆一下就收回，吟唱时右手连着法杖微微转圈，引导、蓄力时微微抖动。
 * 动作按这样的握法做：第三人称法杖和手臂垂直（手持模型沿 +Y、握点在模型中心、第三人称显示不旋转），
 * 所以手臂往前抬起时法杖才立起来。新法杖的手持模型照这个握法摆，就能直接用这套动作。</p>
 */
public class TtStaffItem extends TtMagicWeaponItem {
    private static final Identifier CAST_INSTANT = Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "staff.cast_instant");
    private static final Identifier CAST_HOLD = Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "staff.cast_hold");
    private static final Identifier CAST_CHANNEL = Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "staff.cast_channel");

    public TtStaffItem(Item.Properties properties, int durability, float spellPower) {
        super(properties, durability, spellPower);
    }

    public TtStaffItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed) {
        super(properties, durability, spellPower, castMoveSpeed);
    }

    public TtStaffItem(Item.Properties properties, int durability, float spellPower, List<Identifier> spells) {
        super(properties, durability, spellPower, spells);
    }

    public TtStaffItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed, List<Identifier> spells) {
        super(properties, durability, spellPower, castMoveSpeed, spells);
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
