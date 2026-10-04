package ibx.taverntales.lib.item;

import ibx.taverntales.lib.item.magic_weapon.TtMagicWeaponItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Tt 法杖：法杖类法器。数值、法术、施法资格都在 {@link TtMagicWeaponItem} 里，法杖自己的施法动作写在这里。
 * 参数含义和注册方式见 {@link TtMagicWeaponItem}。
 */
public class TtStaffItem extends TtMagicWeaponItem {
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
}
