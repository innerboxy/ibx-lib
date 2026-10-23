package ibx.lib.client.attribute;

import ibx.lib.IbxLib;
import ibx.lib.client.UiPalette;
import ibx.lib.compat.curios.CuriosScreens;
import ibx.lib.config.ClientConfig;
import ibx.lib.mixin.AbstractRecipeBookScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 背包左边的属性面板：列出玩家身上的全部属性（原版、本模组、其他模组），每行图标 + 名字 + 数值，可滚动。
 *
 * <ul>
 *   <li>开关按钮是人物框左下角的卷轴（和左上角的 Curios 按钮上下对称），开着没开记在客户端配置里，下次打开背包还是那样。</li>
 *   <li>面板 120×166，贴着背包左边、空 6 像素，和背包一样高；左下角的齿轮打开 {@link AttributeSettingsScreen}。</li>
 *   <li>和原版配方书占同一块地方：配方书开着时面板不画；点卷轴打开面板时先把配方书收起来。屏幕太窄放不下时也不画。</li>
 *   <li>手上拿着物品时点在面板上，原版会把松开那一下当成点到界面外、把物品扔出去，所以按在面板 / 卷轴上的那一次，
 *       松开时不交给背包界面；背包界面也不把滚轮转给子控件，滚轮在这里单独接。</li>
 *   <li>创造模式背包也有：卷轴同样在人物框左下角（那里的人物框小一些），只在「物品栏」那一页显示，
 *       切到别的物品分类页时卷轴和面板都不画。</li>
 *   <li>Curios 的饰品栏界面（布局同背包）只放卷轴：那里左边是饰品槽，不放面板；点卷轴就关掉饰品栏、回原版背包打开面板，
 *       见 {@link CuriosScreens}。</li>
 * </ul>
 */
public final class AttributePanel extends AbstractWidget {
    static final int W = 120, H = 166, GAP = 6;
    private static final int LIST_X = 7, LIST_Y = 17, LIST_W = 106, LIST_H = 128;
    private static final int ROWS_X = LIST_X + 2, ROWS_Y = LIST_Y + 2, ROWS_W = LIST_W - 10, ROW_H = 10;
    private static final int VISIBLE = (LIST_H - 4) / ROW_H;
    private static final int BAR_X = LIST_X + LIST_W - 6, BAR_H = LIST_H - 4;
    private static final int GEAR_X = 7, GEAR_Y = H - 20, GEAR_SIZE = 13;
    /** 卷轴按钮的位置（人物框左下角，离框边 2 像素）：生存背包的人物框 (26, 8)~(75, 78)，创造模式背包的 (73, 6)~(105, 49)。 */
    private static final int BUTTON_X = 28, BUTTON_Y = 66, CREATIVE_BUTTON_X = 75, CREATIVE_BUTTON_Y = 37, BUTTON_SIZE = 10;

    private static final Identifier SETTINGS = lib("attribute_panel/settings");
    private static final WidgetSprites BUTTON_SPRITES = new WidgetSprites(lib("attribute_panel/button"),
            lib("attribute_panel/button_highlighted"));
    private static final Component TITLE = Component.translatable("gui.ibx_lib.attribute_panel.title");
    private static final Component SETTINGS_TOOLTIP = Component.translatable("gui.ibx_lib.attribute_panel.settings");

    /** 这次按下鼠标点在面板 / 卷轴上：松开那一下不交给背包界面。 */
    private static boolean pressConsumed;
    /** 下一刻要重排的背包界面：点卷轴时收起了配方书，背包的位置要重算（不能在点击的过程里直接重建控件）。 */
    private static @Nullable Screen relayout;
    /** 滚到第几行，关了背包再开还在原处。 */
    private static int scroll;

    private final AbstractContainerScreen<?> screen;
    private List<AttributeList.Entry> entries = List.of();
    private boolean draggingBar;

    private AttributePanel(AbstractContainerScreen<?> screen) {
        super(0, 0, W, H, TITLE);
        this.screen = screen;
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(AttributeIcons::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(AttributePanel::onScreenInit);
        NeoForge.EVENT_BUS.addListener(AttributePanel::onScreenRender);
        NeoForge.EVENT_BUS.addListener(AttributePanel::onMouseReleased);
        NeoForge.EVENT_BUS.addListener(AttributePanel::onMouseScrolled);
        NeoForge.EVENT_BUS.addListener(AttributePanel::onMouseDragged);
        NeoForge.EVENT_BUS.addListener(AttributePanel::onClientTick);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        // 创造模式下生存背包在自己的 init 里就换成了创造模式背包：被换掉的那个不加（它的 Init.Post 还会在新界面之后才发）
        if (Minecraft.getInstance().screen != screen) {
            return;
        }
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen || CuriosScreens.isCuriosScreen(screen)) {
            AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
            event.addListener(new OpenButton(container));
            // 饰品栏界面里饰品槽占着面板的位置，只放卷轴（点了切回原版背包再显示面板）
            if (!CuriosScreens.isCuriosScreen(screen)) {
                event.addListener(new AttributePanel(container));
            }
        }
    }

    /** 界面上的面板（没有就是 null）：每次从界面自己的控件里找，不记全局的「当前」。 */
    private static @Nullable AttributePanel panelOf(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AttributePanel panel) {
                return panel;
            }
        }
        return null;
    }

    /** 创造模式背包换页时卷轴跟着显示 / 隐藏（藏起来的控件不画、点不到，指着也不换鼠标样子）。 */
    private static void onScreenRender(ScreenEvent.Render.Pre event) {
        for (GuiEventListener child : event.getScreen().children()) {
            if (child instanceof OpenButton button) {
                button.visible = onInventoryPage(button.screen);
            }
        }
    }

    private static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!pressConsumed) {
            return;
        }
        pressConsumed = false;
        AttributePanel panel = panelOf(event.getScreen());
        if (panel != null) {
            panel.draggingBar = false;
        }
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            event.setCanceled(true);
        }
    }

    private static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        AttributePanel panel = panelOf(event.getScreen());
        if (panel != null && event.getScrollDeltaY() != 0.0 && panel.isMouseOver(event.getMouseX(), event.getMouseY())) {
            scroll = panel.clampScroll(scroll - (int) Math.signum(event.getScrollDeltaY()));
            event.setCanceled(true);
        }
    }

    private static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        AttributePanel panel = panelOf(event.getScreen());
        if (panel != null && panel.draggingBar) {
            scroll = panel.scrollAt(event.getMouseY());
            event.setCanceled(true);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Screen screen = relayout;
        relayout = null;
        if (screen != null && Minecraft.getInstance().screen == screen) {
            screen.resize(screen.width, screen.height);
        }
    }

    // ---------------------------------------------------------------- 开关、位置

    private static boolean isOpen() {
        return ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_OPEN);
    }

    private static void setOpen(boolean open) {
        if (ClientConfig.SPEC.isLoaded()) {
            ClientConfig.ATTRIBUTE_PANEL_OPEN.set(open);
            ClientConfig.ATTRIBUTE_PANEL_OPEN.save();
        }
    }

    /** 点卷轴：饰品栏界面里是「打开面板」——关掉饰品栏回原版背包；别处是开关面板。 */
    private static void press(AbstractContainerScreen<?> screen) {
        if (CuriosScreens.isCuriosScreen(screen)) {
            setOpen(true);
            CuriosScreens.closeToInventory(screen);
            return;
        }
        boolean open = !isOpen();
        setOpen(open);
        if (open && recipeBookOpen(screen)) {
            ((AbstractRecipeBookScreenAccessor) screen).ibx_lib$getRecipeBookComponent().toggleVisibility();
            relayout = screen;
        }
    }

    private static boolean recipeBookOpen(AbstractContainerScreen<?> screen) {
        return screen instanceof AbstractRecipeBookScreen<?>
                && ((AbstractRecipeBookScreenAccessor) screen).ibx_lib$getRecipeBookComponent().isVisible();
    }

    /**
     * 面板在这个界面上当前占的地方（界面坐标），没显示就是空：给物品列表模组让位用（JEI 见 {@code compat.jei.IbxJeiPlugin}）。
     */
    public static Optional<Rect2i> extraArea(AbstractContainerScreen<?> screen) {
        AttributePanel panel = panelOf(screen);
        if (panel == null) {
            return Optional.empty();
        }
        panel.place();
        return panel.shown() ? Optional.of(new Rect2i(panel.getX(), panel.getY(), W, H)) : Optional.empty();
    }

    /** 生存背包总是；创造模式背包只有「物品栏」那一页（有人物框的那页）。 */
    private static boolean onInventoryPage(AbstractContainerScreen<?> screen) {
        return !(screen instanceof CreativeModeInventoryScreen creative) || creative.isInventoryOpen();
    }

    /** 跟着背包走（开关配方书时背包会左右挪）。 */
    private void place() {
        this.setX(this.screen.getLeftPos() - GAP - W);
        this.setY(this.screen.getTopPos());
    }

    private boolean shown() {
        return isOpen() && onInventoryPage(this.screen) && !recipeBookOpen(this.screen) && this.getX() >= 0;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        this.place();
        return this.shown() && super.isMouseOver(mouseX, mouseY);
    }

    // ---------------------------------------------------------------- 画

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        this.place();
        Player player = Minecraft.getInstance().player;
        if (!this.shown() || player == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        this.entries = AttributeList.forPanel(player);
        scroll = this.clampScroll(scroll);
        int x = this.getX(), y = this.getY();
        WoodUi.panel(g, x, y, W, H);
        g.text(font, TITLE, x + 8, y + 6, UiPalette.TITLE, false);
        WoodUi.inset(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);

        boolean icons = ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_SHOW_ICONS);
        boolean names = ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_SHOW_NAMES);
        int rx = x + ROWS_X;
        Component tooltip = null;
        for (int i = scroll; i < Math.min(this.entries.size(), scroll + VISIBLE); i++) {
            int ry = y + ROWS_Y + (i - scroll) * ROW_H;
            if (this.entries.get(i) instanceof AttributeList.Header header) {
                g.text(font, header.name(), rx, ry + 1, UiPalette.ACCENT, false);
                g.fill(rx + font.width(header.name()) + 2, ry + 5, rx + ROWS_W, ry + 6, UiPalette.EDGE);
                continue;
            }
            Holder<Attribute> attribute = ((AttributeList.Row) this.entries.get(i)).attribute();
            if (i % 2 == 1) {
                g.fill(rx, ry, rx + ROWS_W, ry + ROW_H, UiPalette.ROW_ALT);
            }
            int left = rx;
            if (icons) {
                AttributeIcons.draw(g, attribute, rx + 1, ry);
                left = rx + 12;
            }
            Component value = AttributeList.value(player, attribute);
            int right = rx + ROWS_W - 1 - font.width(value);
            g.text(font, value, right, ry + 1, UiPalette.VALUE, false);
            right -= 4;
            Component name = AttributeList.name(attribute);
            boolean cut = false;
            if (names) {
                cut = drawFitted(g, font, name, left, ry + 1, right - left, UiPalette.BODY);
            }
            boolean rowHovered = mouseX >= rx && mouseX < rx + ROWS_W && mouseY >= ry && mouseY < ry + ROW_H;
            if (rowHovered && (cut || !names)) {
                tooltip = name;
            }
        }
        WoodUi.scrollbar(g, x + BAR_X, y + ROWS_Y, BAR_H, scroll, VISIBLE, this.entries.size());

        boolean gearHovered = this.inGear(mouseX, mouseY);
        WoodUi.button(g, x + GEAR_X, y + GEAR_Y, GEAR_SIZE, GEAR_SIZE, gearHovered);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SETTINGS, x + GEAR_X + 2, y + GEAR_Y + 2, 9, 9);
        if (gearHovered) {
            tooltip = SETTINGS_TOOLTIP;
        }
        if (tooltip != null) {
            g.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    /** 写一行字，放不下就截断加省略号；返回有没有截断。 */
    static boolean drawFitted(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
        if (width <= 0) {
            return true;
        }
        if (font.width(text) <= width) {
            g.text(font, text, x, y, color, false);
            return false;
        }
        String cut = font.plainSubstrByWidth(text.getString(), width - font.width("…")) + "…";
        g.text(font, cut, x, y, color, false);
        return true;
    }

    // ---------------------------------------------------------------- 鼠标

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isMouseOver(event.x(), event.y())) {
            return false;
        }
        pressConsumed = true;
        if (event.button() == 0) {
            if (this.inGear(event.x(), event.y())) {
                playButtonClickSound(Minecraft.getInstance().getSoundManager());
                Minecraft.getInstance().setScreen(new AttributeSettingsScreen(this.screen));
            } else if (this.inBar(event.x(), event.y())) {
                this.draggingBar = true;
                scroll = this.scrollAt(event.y());
            }
        }
        return true;
    }

    private boolean inGear(double mx, double my) {
        int gx = this.getX() + GEAR_X, gy = this.getY() + GEAR_Y;
        return mx >= gx && mx < gx + GEAR_SIZE && my >= gy && my < gy + GEAR_SIZE;
    }

    private boolean inBar(double mx, double my) {
        int bx = this.getX() + BAR_X, by = this.getY() + ROWS_Y;
        return mx >= bx && mx < bx + 4 && my >= by && my < by + BAR_H;
    }

    private int scrollAt(double mouseY) {
        return WoodUi.scrollFromMouse(mouseY, this.getY() + ROWS_Y, BAR_H, VISIBLE, this.entries.size());
    }

    private int clampScroll(int value) {
        return Mth.clamp(value, 0, Math.max(0, this.entries.size() - VISIBLE));
    }

    /** 点了不拿焦点：滚动条拖动是自己接的，用不着；拿了焦点背包界面还会一直把它当成正在拖动的控件。 */
    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    private static Identifier lib(String path) {
        return Identifier.fromNamespaceAndPath(IbxLib.MODID, path);
    }

    /** 人物框左下角的卷轴按钮：开关面板；跟着背包挪。 */
    private static final class OpenButton extends ImageButton {
        private static final Component LABEL = Component.translatable("gui.ibx_lib.attribute_panel.button");
        private final AbstractContainerScreen<?> screen;

        OpenButton(AbstractContainerScreen<?> screen) {
            super(0, 0, BUTTON_SIZE, BUTTON_SIZE, BUTTON_SPRITES, b -> press(screen), LABEL);
            this.screen = screen;
            this.setTooltip(Tooltip.create(LABEL));
            this.place();
        }

        private void place() {
            boolean creative = this.screen instanceof CreativeModeInventoryScreen;
            this.setX(this.screen.getLeftPos() + (creative ? CREATIVE_BUTTON_X : BUTTON_X));
            this.setY(this.screen.getTopPos() + (creative ? CREATIVE_BUTTON_Y : BUTTON_Y));
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.place();
            super.extractContents(graphics, mouseX, mouseY, a);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            this.place();
            if (super.mouseClicked(event, doubleClick)) {
                pressConsumed = true;
                return true;
            }
            return false;
        }

        /** 点了不拿焦点：ImageButton 有焦点时一直画高亮的那张，鼠标移开了卷轴还亮着。 */
        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }
    }
}
