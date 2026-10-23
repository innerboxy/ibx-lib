package ibx.lib.client.attribute;

import ibx.lib.client.UiPalette;
import ibx.lib.config.ClientConfig;
import ibx.lib.config.ClientConfig.AttributeSort;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 属性面板的设置界面（面板左下角的齿轮打开），关掉回到背包。
 *
 * <ul>
 *   <li>左栏：显示图标、显示名字、按模组分组、隐藏默认值的属性四个开关（数值总是显示）；排序按钮在默认顺序 / 按名称 / 自定义之间轮换。</li>
 *   <li>右栏：玩家身上的全部属性，每行一个勾选框（不勾的面板里不列）和 ▲▼。点 ▲▼ 就按当前看到的顺序换位置，
 *       排序随之变成自定义；分组时只能在组内挪。</li>
 *   <li>恢复默认：上面这些全部回到默认（面板开着没开不动）。改动立即写进客户端配置。</li>
 * </ul>
 */
public final class AttributeSettingsScreen extends Screen {
    private static final int W = 300, H = 196;
    private static final int OPTION_Y = 31, OPTION_STEP = 12;
    private static final int SORT_X = 8, SORT_W = 96, SORT_H = 16;
    private static final int LIST_X = 114, LIST_Y = 20, LIST_W = W - 122, LIST_H = H - 50;
    private static final int ROWS_X = LIST_X + 3, ROWS_Y = LIST_Y + 2, ROWS_W = LIST_W - 10, ROW_H = 10;
    private static final int VISIBLE = (LIST_H - 4) / ROW_H;
    private static final int BAR_X = LIST_X + LIST_W - 6, BAR_H = LIST_H - 4;
    private static final int ARROW_W = 8;
    private static final int BUTTON_W = 60, BUTTON_H = 16;

    private static final List<Option> OPTIONS = List.of(
            new Option("show_icons", ClientConfig.ATTRIBUTE_PANEL_SHOW_ICONS),
            new Option("show_names", ClientConfig.ATTRIBUTE_PANEL_SHOW_NAMES),
            new Option("group_by_mod", ClientConfig.ATTRIBUTE_PANEL_GROUP_BY_MOD),
            new Option("hide_default", ClientConfig.ATTRIBUTE_PANEL_HIDE_DEFAULT));

    private record Option(String key, ModConfigSpec.BooleanValue value) {
        Component label() {
            return Component.translatable("gui.ibx_lib.attribute_panel." + this.key);
        }
    }

    private final Screen parent;
    /** 打开前背包 2×2 合成结果格里的东西，回去时放回（见 {@link #onClose}）。 */
    private final ItemStack craftResult;
    private int x, y, scroll;
    private boolean draggingBar;
    private List<AttributeList.Entry> entries = List.of();

    public AttributeSettingsScreen(Screen parent) {
        super(Component.translatable("gui.ibx_lib.attribute_panel.settings.title"));
        this.parent = parent;
        this.craftResult = parent instanceof AbstractContainerScreen<?> container && container.getMenu() instanceof InventoryMenu menu
                ? menu.getResultSlot().getItem().copy() : ItemStack.EMPTY;
    }

    @Override
    protected void init() {
        this.x = (this.width - W) / 2;
        this.y = (this.height - H) / 2 - 6;
        this.refresh();
    }

    private void refresh() {
        Player player = this.minecraft.player;
        this.entries = player != null ? AttributeList.forSettings(player) : List.of();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.entries.size() - VISIBLE));
    }

    /**
     * 回到背包。离开背包时原版在客户端清掉了 2×2 合成的结果格（服务端没动、也不会重发），合成格里有东西时结果格会一直空着，
     * 这里把打开前看到的放回去。
     */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
        if (!this.craftResult.isEmpty() && this.parent instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof InventoryMenu menu && menu.getResultSlot().getItem().isEmpty()) {
            menu.getResultSlot().set(this.craftResult);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int x = this.x, y = this.y;
        WoodUi.panel(g, x, y, W, H);
        g.text(this.font, this.title, x + 8, y + 6, UiPalette.TITLE, false);

        g.text(this.font, Component.translatable("gui.ibx_lib.attribute_panel.display"), x + 8, y + 20, UiPalette.ACCENT, false);
        for (int i = 0; i < OPTIONS.size(); i++) {
            Option option = OPTIONS.get(i);
            int oy = y + OPTION_Y + i * OPTION_STEP;
            WoodUi.checkbox(g, x + 8, oy, ClientConfig.get(option.value()));
            g.text(this.font, option.label(), x + 19, oy + 1, UiPalette.BODY, false);
        }
        int sortY = this.sortY();
        g.text(this.font, Component.translatable("gui.ibx_lib.attribute_panel.sort"), x + 8, sortY - 11, UiPalette.ACCENT, false);
        this.button(g, x + SORT_X, sortY, SORT_W, SORT_H, sortLabel(), mouseX, mouseY);

        Component tooltip = this.extractList(g, mouseX, mouseY);

        int by = y + H - 8 - BUTTON_H;
        this.button(g, this.resetX(), by, BUTTON_W, BUTTON_H, Component.translatable("gui.ibx_lib.attribute_panel.reset"), mouseX, mouseY);
        this.button(g, this.doneX(), by, BUTTON_W, BUTTON_H, Component.translatable("gui.done"), mouseX, mouseY);
        if (tooltip != null) {
            g.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        }
    }

    private @Nullable Component extractList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        WoodUi.inset(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);
        List<? extends String> hidden = ClientConfig.get(ClientConfig.ATTRIBUTE_PANEL_HIDDEN);
        int rx = x + ROWS_X;
        Component tooltip = null;
        for (int i = this.scroll; i < Math.min(this.entries.size(), this.scroll + VISIBLE); i++) {
            int ry = this.rowY(i);
            if (this.entries.get(i) instanceof AttributeList.Header header) {
                g.text(this.font, header.name(), rx, ry + 1, UiPalette.ACCENT, false);
                g.fill(rx + this.font.width(header.name()) + 2, ry + 5, rx + ROWS_W, ry + 6, UiPalette.EDGE);
                continue;
            }
            Holder<Attribute> attribute = ((AttributeList.Row) this.entries.get(i)).attribute();
            boolean on = !hidden.contains(AttributeList.id(attribute));
            WoodUi.checkbox(g, rx, ry, on);
            AttributeIcons.draw(g, attribute, rx + 11, ry);
            Component name = AttributeList.name(attribute);
            int arrows = this.upX();
            boolean cut = AttributePanel.drawFitted(g, this.font, name, rx + 23, ry + 1, arrows - 4 - (rx + 23),
                    on ? UiPalette.BODY : UiPalette.MUTED);
            this.arrow(g, "▲", this.upX(), ry, mouseX, mouseY);
            this.arrow(g, "▼", this.upX() + ARROW_W, ry, mouseX, mouseY);
            if (cut && mouseX >= rx + 23 && mouseX < arrows && mouseY >= ry && mouseY < ry + ROW_H) {
                tooltip = name;
            }
        }
        WoodUi.scrollbar(g, x + BAR_X, y + ROWS_Y, BAR_H, this.scroll, VISIBLE, this.entries.size());
        return tooltip;
    }

    private void arrow(GuiGraphicsExtractor g, String glyph, int ax, int ry, int mouseX, int mouseY) {
        boolean hovered = mouseX >= ax && mouseX < ax + ARROW_W && mouseY >= ry && mouseY < ry + ROW_H;
        g.text(this.font, glyph, ax + (ARROW_W - this.font.width(glyph)) / 2, ry + 1, hovered ? UiPalette.ACCENT : UiPalette.MUTED, false);
    }

    private void button(GuiGraphicsExtractor g, int bx, int by, int w, int h, Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, bx, by, w, h);
        WoodUi.button(g, bx, by, w, h, hovered);
        g.text(this.font, label, bx + (w - this.font.width(label)) / 2, by + (h - 8) / 2, hovered ? UiPalette.VALUE : UiPalette.BODY, false);
    }

    private static Component sortLabel() {
        AttributeSort sort = ClientConfig.SPEC.isLoaded() ? ClientConfig.ATTRIBUTE_PANEL_SORT.get() : AttributeSort.DEFAULT;
        return Component.translatable("gui.ibx_lib.attribute_panel.sort." + sort.name().toLowerCase(java.util.Locale.ROOT));
    }

    // ---------------------------------------------------------------- 鼠标

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0 || !ClientConfig.SPEC.isLoaded()) {
            return false;
        }
        double mx = event.x(), my = event.y();
        for (int i = 0; i < OPTIONS.size(); i++) {
            int oy = this.y + OPTION_Y + i * OPTION_STEP;
            if (inside(mx, my, this.x + 8, oy, 11 + this.font.width(OPTIONS.get(i).label()), 9)) {
                ModConfigSpec.BooleanValue value = OPTIONS.get(i).value();
                set(value, !value.get());
                return this.clicked();
            }
        }
        if (inside(mx, my, this.x + SORT_X, this.sortY(), SORT_W, SORT_H)) {
            AttributeSort[] all = AttributeSort.values();
            set(ClientConfig.ATTRIBUTE_PANEL_SORT, all[(ClientConfig.ATTRIBUTE_PANEL_SORT.get().ordinal() + 1) % all.length]);
            return this.clicked();
        }
        int by = this.y + H - 8 - BUTTON_H;
        if (inside(mx, my, this.resetX(), by, BUTTON_W, BUTTON_H)) {
            this.reset();
            return this.clicked();
        }
        if (inside(mx, my, this.doneX(), by, BUTTON_W, BUTTON_H)) {
            AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
            this.onClose();
            return true;
        }
        if (inside(mx, my, this.x + BAR_X, this.y + ROWS_Y, 4, BAR_H)) {
            this.draggingBar = true;
            this.scroll = WoodUi.scrollFromMouse(my, this.y + ROWS_Y, BAR_H, VISIBLE, this.entries.size());
            return true;
        }
        int row = this.rowAt(mx, my);
        if (row >= 0 && this.entries.get(row) instanceof AttributeList.Row(Holder<Attribute> attribute)) {
            if (mx >= this.upX() + ARROW_W) {
                this.move(attribute, 1);
            } else if (mx >= this.upX()) {
                this.move(attribute, -1);
            } else {
                this.toggleHidden(attribute);
            }
            return this.clicked();
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingBar) {
            this.scroll = WoodUi.scrollFromMouse(event.y(), this.y + ROWS_Y, BAR_H, VISIBLE, this.entries.size());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, this.entries.size() - VISIBLE));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private boolean clicked() {
        AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
        this.refresh();
        return true;
    }

    // ---------------------------------------------------------------- 改设置

    private void toggleHidden(Holder<Attribute> attribute) {
        List<String> hidden = new ArrayList<>(ClientConfig.ATTRIBUTE_PANEL_HIDDEN.get());
        String id = AttributeList.id(attribute);
        if (!hidden.remove(id)) {
            hidden.add(id);
        }
        set(ClientConfig.ATTRIBUTE_PANEL_HIDDEN, hidden);
    }

    /** 按当前看到的顺序和上 / 下一个换位置，写成自定义顺序；分组时不跨组。 */
    private void move(Holder<Attribute> attribute, int direction) {
        Player player = this.minecraft.player;
        if (player == null) {
            return;
        }
        List<Holder<Attribute>> order = new ArrayList<>(AttributeList.sorted(player));
        int i = order.indexOf(attribute), j = i + direction;
        if (i < 0 || j < 0 || j >= order.size()) {
            return;
        }
        if (ClientConfig.ATTRIBUTE_PANEL_GROUP_BY_MOD.get() && !Objects.equals(namespace(order.get(i)), namespace(order.get(j)))) {
            return;
        }
        Collections.swap(order, i, j);
        set(ClientConfig.ATTRIBUTE_PANEL_ORDER, order.stream().map(AttributeList::id).toList());
        set(ClientConfig.ATTRIBUTE_PANEL_SORT, AttributeSort.CUSTOM);
    }

    private void reset() {
        for (Option option : OPTIONS) {
            set(option.value(), option.value().getDefault());
        }
        set(ClientConfig.ATTRIBUTE_PANEL_SORT, AttributeSort.DEFAULT);
        set(ClientConfig.ATTRIBUTE_PANEL_ORDER, List.of());
        set(ClientConfig.ATTRIBUTE_PANEL_HIDDEN, ClientConfig.ATTRIBUTE_PANEL_HIDDEN.getDefault());
    }

    private static <T> void set(ModConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        value.save();
    }

    private static String namespace(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(k -> k.identifier().getNamespace()).orElse("");
    }

    // ---------------------------------------------------------------- 位置

    private int sortY() {
        return this.y + OPTION_Y + OPTIONS.size() * OPTION_STEP + 15;
    }

    private int doneX() {
        return this.x + W - 8 - BUTTON_W;
    }

    private int resetX() {
        return this.doneX() - 6 - BUTTON_W;
    }

    private int upX() {
        return this.x + ROWS_X + ROWS_W - 2 * ARROW_W;
    }

    private int rowY(int index) {
        return this.y + ROWS_Y + (index - this.scroll) * ROW_H;
    }

    private int rowAt(double mx, double my) {
        int rx = this.x + ROWS_X;
        if (mx < rx || mx >= rx + ROWS_W || my < this.y + ROWS_Y) {
            return -1;
        }
        int i = this.scroll + (int) ((my - this.y - ROWS_Y) / ROW_H);
        return i < Math.min(this.entries.size(), this.scroll + VISIBLE) ? i : -1;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
