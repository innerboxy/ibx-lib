package ibx.lib.client.creativetab;

import ibx.lib.client.UiPalette;
import ibx.lib.client.attribute.WoodUi;
import ibx.lib.creativetab.CreativeSubTabs;
import ibx.lib.mixin.CreativeModeInventoryScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 创造模式背包左边的子栏列（分组见 {@link CreativeSubTabs}）：选中的创造栏建了子栏时，贴着界面左边一列图标，
 * 点一个只显示那一组的物品，再点一次回到整个创造栏。
 *
 * <ul>
 *   <li>每个创造栏选了哪一组分别记着：换到别的创造栏再回来、关了界面再开都还是那一组（同原版记着上次开的创造栏）。</li>
 *   <li>换物品的做法是直接改物品格子背后的列表（{@code ItemPickerMenu#items}），滚动条回到顶上。原版换创造栏、重建创造栏内容时
 *       会把列表填回整个创造栏，所以每帧画之前对一下：列表不是上次放进去的那些就再放一次。</li>
 *   <li>一列最多 6 个，多了用滚轮翻，上下有小三角提示还有。界面左边放不下（屏幕太窄）时不显示，物品格子也回到整个创造栏。</li>
 *   <li>手上拿着物品点在这一列上不会把物品扔出去：按下那一次松开时不交给界面（同属性面板）。</li>
 * </ul>
 */
public final class CreativeSubTabStrip extends AbstractWidget {
    /** 格子 18（图标 16 + 一圈边），格子间距 20；左右留 4、上下留 6（放翻页的小三角）；离界面左边 2。 */
    private static final int CELL = 18, PITCH = 20, PAD_X = 4, PAD_Y = 6, W = PAD_X * 2 + CELL, GAP = 2, MAX_VISIBLE = 6;

    /** 每个创造栏选中的是第几组（{@link CreativeSubTabs#subTabs()} 的序号），没有就是整个创造栏。 */
    private static final Map<Identifier, Integer> SELECTED = new HashMap<>();
    /** 每个创造栏的子栏列翻到第几个。 */
    private static final Map<Identifier, Integer> SCROLL = new HashMap<>();
    /** 这次按下鼠标点在子栏列上：松开那一下不交给界面。 */
    private static boolean pressConsumed;

    private final CreativeModeInventoryScreen screen;
    /** 当前创造栏的 id 和子栏（没建子栏都是 null），每帧画之前更新。 */
    private @Nullable Identifier tabId;
    private @Nullable CreativeSubTabs subTabs;
    /** 当前有物品的那些组（{@link CreativeSubTabs#subTabs()} 的序号），按顺序。 */
    private int[] shown = new int[0];
    /** 上次放进物品格子的列表，没放过（显示的是整个创造栏）就是 null。 */
    private @Nullable List<ItemStack> applied;

    private CreativeSubTabStrip(CreativeModeInventoryScreen screen) {
        super(0, 0, W, 0, Component.empty());
        this.screen = screen;
        this.visible = false;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CreativeSubTabStrip::onScreenInit);
        NeoForge.EVENT_BUS.addListener(CreativeSubTabStrip::onScreenRender);
        NeoForge.EVENT_BUS.addListener(CreativeSubTabStrip::onMouseReleased);
        NeoForge.EVENT_BUS.addListener(CreativeSubTabStrip::onMouseScrolled);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen creative) {
            CreativeSubTabStrip strip = new CreativeSubTabStrip(creative);
            event.addListener(strip);
            strip.sync(false);
        }
    }

    /** 界面上的子栏列（没有就是 null）：每次从界面自己的控件里找，不记全局的「当前」。 */
    private static @Nullable CreativeSubTabStrip stripOf(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof CreativeSubTabStrip strip) {
                return strip;
            }
        }
        return null;
    }

    private static void onScreenRender(ScreenEvent.Render.Pre event) {
        CreativeSubTabStrip strip = stripOf(event.getScreen());
        if (strip != null) {
            strip.sync(false);
        }
    }

    private static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!pressConsumed) {
            return;
        }
        pressConsumed = false;
        if (stripOf(event.getScreen()) != null) {
            event.setCanceled(true);
        }
    }

    private static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        CreativeSubTabStrip strip = stripOf(event.getScreen());
        if (strip != null && strip.tabId != null && event.getScrollDeltaY() != 0.0 && strip.shown.length > MAX_VISIBLE
                && strip.isMouseOver(event.getMouseX(), event.getMouseY())) {
            SCROLL.put(strip.tabId, strip.clampScroll(strip.scroll() - (int) Math.signum(event.getScrollDeltaY())));
            event.setCanceled(true);
        }
    }

    /**
     * 子栏列在这个界面上当前占的地方（界面坐标），没显示就是空：给物品列表模组让位用（JEI 见 {@code compat.jei.IbxJeiPlugin}）。
     */
    public static Optional<Rect2i> extraArea(AbstractContainerScreen<?> screen) {
        CreativeSubTabStrip strip = stripOf(screen);
        if (strip == null || !strip.visible) {
            return Optional.empty();
        }
        return Optional.of(new Rect2i(strip.getX(), strip.getY(), strip.getWidth(), strip.getHeight()));
    }

    // ---------------------------------------------------------------- 跟着创造栏更新

    /**
     * 对一遍当前创造栏：有没有子栏、哪几组有物品、位置，物品格子里该放哪些。
     *
     * @param force 刚点了子栏：不管物品格子现在是什么都按选中的重放
     */
    private void sync(boolean force) {
        CreativeModeTab tab = CreativeModeInventoryScreenAccessor.ibx_lib$getSelectedTab();
        Identifier id = tab.getType() == CreativeModeTab.Type.CATEGORY ? BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab) : null;
        CreativeSubTabs found = id == null ? null : CreativeSubTabs.get(id);
        if (found == null) {
            // 换到了没有子栏的创造栏：原版已经把物品格子换成那一栏的了
            this.tabId = null;
            this.subTabs = null;
            this.shown = new int[0];
            this.applied = null;
            this.visible = false;
            return;
        }
        if (!id.equals(this.tabId)) {
            // 刚换到这一栏：原版已经放好了整个创造栏
            this.applied = null;
        }
        this.tabId = id;
        this.subTabs = found;

        Collection<ItemStack> display = tab.getDisplayItems();
        List<List<ItemStack>> groups = new ArrayList<>();
        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < found.subTabs().size(); i++) {
            List<ItemStack> items = itemsIn(found.subTabs().get(i), display);
            groups.add(items);
            if (!items.isEmpty()) {
                nonEmpty.add(i);
            }
        }
        this.shown = nonEmpty.stream().mapToInt(Integer::intValue).toArray();
        SCROLL.put(id, this.clampScroll(this.scroll()));

        int rows = Math.min(this.shown.length, MAX_VISIBLE);
        int x = this.screen.getLeftPos() - GAP - W;
        this.setRectangle(W, PAD_Y * 2 + rows * PITCH - (PITCH - CELL), x, this.screen.getTopPos());
        this.visible = this.shown.length > 0 && x >= 0;

        int selected = this.visible ? this.selected() : -1;
        if (selected >= 0) {
            if (force || this.applied == null || !this.menuHolds(this.applied)) {
                this.fill(groups.get(selected));
            }
        } else if (this.applied != null) {
            // 取消了选中（或者放不下不显示了）：之前换过物品格子，换回整个创造栏
            if (force || this.menuHolds(this.applied)) {
                this.fill(List.copyOf(display));
            }
            this.applied = null;
        }
    }

    /** 一组里现在还在创造栏里的物品（别的模组可能用事件删掉了一些）。 */
    private static List<ItemStack> itemsIn(CreativeSubTabs.SubTab subTab, Collection<ItemStack> display) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : subTab.items()) {
            if (display.contains(stack)) {
                items.add(stack);
            }
        }
        return items;
    }

    /** 物品格子换成这些，滚动条回到顶上。 */
    private void fill(List<ItemStack> items) {
        NonNullList<ItemStack> menuItems = this.screen.getMenu().items;
        menuItems.clear();
        menuItems.addAll(items);
        ((CreativeModeInventoryScreenAccessor) this.screen).ibx_lib$setScrollOffs(0.0F);
        this.screen.getMenu().scrollTo(0.0F);
        this.applied = items;
    }

    /** 物品格子里放的还是不是这些（一个个比是不是同一个物品堆）。 */
    private boolean menuHolds(List<ItemStack> items) {
        NonNullList<ItemStack> menuItems = this.screen.getMenu().items;
        if (menuItems.size() != items.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (menuItems.get(i) != items.get(i)) {
                return false;
            }
        }
        return true;
    }

    /** 当前创造栏选中的组；没选、或者那一组现在没物品就是 -1。 */
    private int selected() {
        if (this.tabId == null) {
            return -1;
        }
        int index = SELECTED.getOrDefault(this.tabId, -1);
        for (int shownIndex : this.shown) {
            if (shownIndex == index) {
                return index;
            }
        }
        return -1;
    }

    private int scroll() {
        return this.tabId == null ? 0 : SCROLL.getOrDefault(this.tabId, 0);
    }

    private int clampScroll(int value) {
        return Mth.clamp(value, 0, Math.max(0, this.shown.length - MAX_VISIBLE));
    }

    /** 鼠标指着的那一组（{@link CreativeSubTabs#subTabs()} 的序号），没指着格子就是 -1。 */
    private int subTabAt(double mouseX, double mouseY) {
        if (!this.visible) {
            return -1;
        }
        int dx = Mth.floor(mouseX) - (this.getX() + PAD_X);
        int dy = Mth.floor(mouseY) - (this.getY() + PAD_Y);
        if (dx < 0 || dx >= CELL || dy < 0 || dy % PITCH >= CELL) {
            return -1;
        }
        int row = dy / PITCH;
        int rows = Math.min(this.shown.length, MAX_VISIBLE);
        return row < rows ? this.shown[this.scroll() + row] : -1;
    }

    // ---------------------------------------------------------------- 画

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (this.subTabs == null) {
            return;
        }
        int x = this.getX(), y = this.getY();
        WoodUi.panel(g, x, y, this.getWidth(), this.getHeight());
        int selected = this.selected();
        int hovered = this.subTabAt(mouseX, mouseY);
        int scroll = this.scroll();
        int rows = Math.min(this.shown.length, MAX_VISIBLE);
        for (int row = 0; row < rows; row++) {
            int index = this.shown[scroll + row];
            int cx = x + PAD_X, cy = y + PAD_Y + row * PITCH;
            if (index == selected) {
                selectedCell(g, cx, cy);
            } else {
                WoodUi.inset(g, cx, cy, CELL, CELL);
                if (index == hovered) {
                    g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, UiPalette.PANEL_LIGHT);
                }
            }
            g.item(this.subTabs.subTabs().get(index).icon(), cx + 1, cy + 1);
        }
        int mid = x + W / 2;
        if (scroll > 0) {
            g.fill(mid, y + 2, mid + 1, y + 3, UiPalette.ACCENT);
            g.fill(mid - 1, y + 3, mid + 2, y + 4, UiPalette.ACCENT);
            g.fill(mid - 2, y + 4, mid + 3, y + 5, UiPalette.ACCENT);
        }
        if (scroll + rows < this.shown.length) {
            int bottom = y + this.getHeight();
            g.fill(mid, bottom - 3, mid + 1, bottom - 2, UiPalette.ACCENT);
            g.fill(mid - 1, bottom - 4, mid + 2, bottom - 3, UiPalette.ACCENT);
            g.fill(mid - 2, bottom - 5, mid + 3, bottom - 4, UiPalette.ACCENT);
        }
        if (hovered >= 0) {
            g.setTooltipForNextFrame(Minecraft.getInstance().font, this.subTabs.subTabs().get(hovered).title(), mouseX, mouseY);
        }
    }

    /** 选中的格子：外面一圈琥珀框（最外一圈左上亮、右下暗），里面亮木色底。 */
    private static void selectedCell(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + CELL + 1, y + CELL + 1, UiPalette.WHEEL_SELECT_DARK);
        g.fill(x - 1, y - 1, x + CELL, y + CELL, UiPalette.WHEEL_SELECT_LIGHT);
        g.fill(x, y, x + CELL, y + CELL, UiPalette.ACCENT);
        g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, UiPalette.EDGE);
    }

    // ---------------------------------------------------------------- 鼠标

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isMouseOver(event.x(), event.y()) || this.tabId == null) {
            return false;
        }
        pressConsumed = true;
        int index = this.subTabAt(event.x(), event.y());
        if (event.button() == 0 && index >= 0) {
            playButtonClickSound(Minecraft.getInstance().getSoundManager());
            SELECTED.put(this.tabId, index == this.selected() ? -1 : index);
            this.sync(true);
        }
        return true;
    }

    /** 点了不拿焦点：拿了焦点界面会一直把它当成正在拖动的控件。 */
    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
