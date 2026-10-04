package ibx.taverntales.lib.item;

import ibx.taverntales.lib.item.magic_weapon.TtMagicWeaponItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Tt 法典：法典类法器。数值、法术、施法资格都在 {@link TtMagicWeaponItem} 里，法典自己的施法动作写在这里。
 * 参数含义和注册方式见 {@link TtMagicWeaponItem}。
 */
public class TtTomeItem extends TtMagicWeaponItem {
    public TtTomeItem(Item.Properties properties, int durability, float spellPower) {
        super(properties, durability, spellPower);
    }

    public TtTomeItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed) {
        super(properties, durability, spellPower, castMoveSpeed);
    }

    public TtTomeItem(Item.Properties properties, int durability, float spellPower, List<Identifier> spells) {
        super(properties, durability, spellPower, spells);
    }

    public TtTomeItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed, List<Identifier> spells) {
        super(properties, durability, spellPower, castMoveSpeed, spells);
    }
}
