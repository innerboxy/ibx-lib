package ibx.lib.client.spell;

import ibx.lib.spell.SpellPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 主手拿着 {@link SpellPicker} 蹲下 + 左键：不攻击、不挖方块，打开选法术界面（{@link SpellPickerScreen}），
 * 右边列什么由物品的 {@link SpellPicker#pickerSource} 决定。
 */
public final class SpellPickerInput {
    private SpellPickerInput() {}

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!event.isAttack() || player == null || mc.screen != null || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SpellPicker picker)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        SpellPickerScreen.open(stack, picker.pickerSource(stack));
    }
}
