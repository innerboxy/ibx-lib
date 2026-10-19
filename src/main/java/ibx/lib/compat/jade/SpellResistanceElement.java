package ibx.lib.compat.jade;

import ibx.lib.IbxLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IDisplayHelper;

import java.text.DecimalFormat;

/**
 * 魔法抗性的提示框元素，和 Jade 画护甲的效果一样：魔抗先向下取整（同护甲的 {@code getArmorValue}），每个图标 2 点、向上取整、
 * 全画满格不画半格；一行最多 Jade「每行图标数」个、多行往上错开；超过 Jade「显示盔甲的最大值」就画一个图标加数字。
 * 几项设置都用 Jade 护甲的，两边一起变。图标是 lib HUD 的魔抗图标（{@code ibx_lib:hud/icon/spell_resistance}，9×9）。
 */
final class SpellResistanceElement extends Element {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(IbxLib.MODID, "hud/icon/spell_resistance");
    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0");

    private final int value;
    /** 数字模式下的文字；画图标时为 null。 */
    private final @Nullable String text;
    /** 图标模式：几个图标、一行几个、几行。 */
    private final int iconCount, iconsPerLine, lineCount;

    /** {@code value}：取整后的魔抗，大于 0。 */
    SpellResistanceElement(int value) {
        this.value = value;
        IPluginConfig config = IWailaConfig.get().plugin();
        if (value > config.getInt(JadeIds.MC_ENTITY_ARMOR_MAX_FOR_RENDER)) {
            this.text = FORMAT.format(value);
            this.iconCount = this.iconsPerLine = this.lineCount = 1;
            this.width = Minecraft.getInstance().font.width(this.text) + 10;
            this.height = 9;
        } else {
            int perLine = config.getInt(JadeIds.MC_ENTITY_HEALTH_ICONS_PER_LINE);
            float points = value * 0.5F;
            this.text = null;
            this.iconCount = Mth.ceil(points);
            this.iconsPerLine = Math.min(perLine, this.iconCount);
            this.lineCount = Mth.ceil(points / perLine);
            this.width = 8 * this.iconsPerLine + 1;
            this.height = 5 + 4 * this.lineCount;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        IDisplayHelper helper = IDisplayHelper.get();
        int x = this.getX(), y = this.getY();
        if (this.text != null) {
            helper.blitSprite(graphics, RenderPipelines.GUI_TEXTURED, ICON, x, y, 9, 9);
            helper.drawText(graphics, this.text, x + 10, y + 1, IThemeHelper.get().getNormalColor());
            return;
        }
        // 同 Jade 的护甲：从最后一个往前画，后画的压在前一个上面；每往上一行错 4 像素
        int xOffset = (this.iconCount - 1) % this.iconsPerLine * 8;
        int yOffset = this.lineCount * 4 - 4;
        for (int i = this.iconCount; i > 0; i--) {
            helper.blitSprite(graphics, RenderPipelines.GUI_TEXTURED, ICON, x + xOffset, y + yOffset, 9, 9);
            xOffset -= 8;
            if (xOffset < 0) {
                xOffset = this.iconsPerLine * 8 - 8;
                yOffset -= 4;
            }
        }
    }

    @Override
    public Component getNarration() {
        return Component.translatable("attribute.name." + IbxLib.MODID + ".spell_resistance").append(" " + this.value);
    }
}
