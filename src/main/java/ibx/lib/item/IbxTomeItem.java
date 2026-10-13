package ibx.lib.item;

import ibx.lib.item.general.IbxMagicWeaponItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Ibx 法典：法典类法器。数值、法术、施法资格、施法动作都在 {@link IbxMagicWeaponItem} 里，法典特有的写在这里。
 * 参数含义和注册方式见 {@link IbxMagicWeaponItem}。
 */
public class IbxTomeItem extends IbxMagicWeaponItem {
    public IbxTomeItem(Item.Properties properties, int durability, float spellPower) {
        super(properties, durability, spellPower);
    }

    public IbxTomeItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed) {
        super(properties, durability, spellPower, castMoveSpeed);
    }

    public IbxTomeItem(Item.Properties properties, int durability, float spellPower, List<Identifier> spells) {
        super(properties, durability, spellPower, spells);
    }

    public IbxTomeItem(Item.Properties properties, int durability, float spellPower, float castMoveSpeed, List<Identifier> spells) {
        super(properties, durability, spellPower, castMoveSpeed, spells);
    }
}
