package ibx.lib.compat.jei;

import ibx.lib.IbxLib;
import ibx.lib.client.attribute.AttributePanel;
import ibx.lib.client.creativetab.CreativeSubTabStrip;
import ibx.lib.compat.curios.CuriosScreens;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 联动（装了 JEI 才加载，JEI 按 {@link JeiPlugin} 注解找到它）：把背包框外面额外占的地方报给 JEI，让左边的书签栏让开。
 * <ul>
 *   <li>背包 / 创造模式背包左边的属性面板（开着时）；</li>
 *   <li>创造模式背包左边的创造栏子栏（选中的创造栏有子栏时）；</li>
 *   <li>Curios 饰品栏界面左边的饰品槽：Curios 自己给 JEI 的注册是注释掉的，书签栏会盖住饰品槽，这里替它报。</li>
 * </ul>
 */
@JeiPlugin
public final class IbxJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(IbxLib.MODID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<AbstractContainerScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
                List<Rect2i> areas = new ArrayList<>(CuriosScreens.slotAreas(screen));
                AttributePanel.extraArea(screen).ifPresent(areas::add);
                CreativeSubTabStrip.extraArea(screen).ifPresent(areas::add);
                return areas;
            }
        });
    }
}
