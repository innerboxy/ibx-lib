package ibx.lib.spell;

import ibx.lib.registry.DataComponentRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * 能在选法术界面里改自己法术的物品：主手拿着蹲下 + 左键打开（客户端 {@code SpellPickerInput} / {@code SpellPickerScreen}），
 * 左边是它现在的法术，右边按 {@link #pickerSource} 列可选的法术；左键点左边移出、点右边选上。
 * 选好的写进物品栈的 {@link DataComponentRegistry#SPELLS} 组件（由 {@code PickSpellsPayload} 发给服务端校验后写），
 * 所以物品读法术也要读这个组件——Ibx 法器（{@link ibx.lib.item.general.IbxMagicWeaponItem}）就是，实现这个接口即可。
 */
public interface SpellPicker extends SpellContainer {
    /** 右边那本列哪些法术。 */
    SpellPickerSource pickerSource(ItemStack stack);
}
