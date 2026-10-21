package ibx.lib.client.spell;

import ibx.lib.network.PickSpellsPayload;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellContainer;
import ibx.lib.spell.SpellPicker;
import ibx.lib.spell.SpellPickerSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 选法术界面：照原版配方书的样子并排两本——左边「已选法术」（手上那件物品的 {@code ibx_lib:spells}，按选的先后），
 * 右边按打开时传的 {@link SpellPickerSource} 列可选的法术（游戏里注册的全部，或物品自带的加玩家身上其他装备的；都不含解散召唤物），
 * 再加上打开界面时物品上已经有的法术——这一次里移出了还能点回来，关了界面就不算了。
 * <ul>
 *   <li>两边各有搜索框（按显示名或 id 匹配，不分大小写）、5 × 4 一页、翻页箭头和页码，鼠标滚轮也能翻页。</li>
 *   <li>左键点左边的法术把它移出；左键点右边的法术把它选上（加到已选末尾），右边已经选了的格子带绿框，再点一下就移出。</li>
 *   <li>关界面时（有改动才）把整张已选列表发给服务端（{@link PickSpellsPayload}）写回主手那件物品。主手换了物品或换了快捷栏格子
 *       就直接关掉、不发，免得写到别的物品上。</li>
 *   <li>右边的法术每 tick 重算一次，界面开着时换装备右边会跟着变。</li>
 * </ul>
 * 一般由 {@link SpellPickerInput} 打开（主手拿着 {@link SpellPicker} 蹲下 + 左键），别处要开就用 {@link #open}。
 */
public class SpellPickerScreen extends Screen {
    private static final Identifier RECIPE_BOOK = Identifier.withDefaultNamespace("textures/gui/recipe_book.png");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("recipe_book/slot_craftable");
    private static final WidgetSprites PAGE_FORWARD = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_forward"), Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted"));
    private static final WidgetSprites PAGE_BACKWARD = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_backward"), Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted"));
    private static final Component SEARCH_HINT = Component.translatable("gui.recipebook.search_hint").withStyle(EditBox.SEARCH_HINT_STYLE);
    /** 配方书面板的大小、两本之间的空隙、标题在面板上方多高。 */
    private static final int BOOK_W = 147, BOOK_H = 166, GAP = 6, TITLE_H = 12;
    private static final int COLS = 5, ROWS = 4, PER_PAGE = COLS * ROWS, CELL = 25;
    private static final int SELECTED_BORDER = 0xFF55FF55;
    private static final int HOVER = 0x80FFFFFF;

    private final Item item;
    private final int slot;
    private final SpellPickerSource source;
    /** 打开时物品上的法术：这一次里一直算可选（撤回用），也用来判断有没有改动。 */
    private final List<Holder<Spell>> initial;
    private final List<Holder<Spell>> selected = new ArrayList<>();
    /** 主手换了物品 / 换了格子：关界面时不发改动。 */
    private boolean abandoned;
    private List<Holder<Spell>> available = List.of();
    private final Panel left = new Panel(true, () -> this.selected);
    private final Panel right = new Panel(false, () -> this.available);

    /**
     * @param stack  要改的物品（玩家主手上的）；左边列它现在的法术
     * @param source 右边列哪些法术
     */
    public SpellPickerScreen(ItemStack stack, SpellPickerSource source) {
        super(Component.translatable("gui.ibx_lib.spell_picker.title"));
        this.item = stack.getItem();
        LocalPlayer player = Minecraft.getInstance().player;
        this.slot = player != null ? player.getInventory().getSelectedSlot() : -1;
        this.source = source;
        this.initial = stack.getItem() instanceof SpellContainer container ? List.copyOf(container.getSpells(stack)) : List.of();
        this.selected.addAll(this.initial);
    }

    /** 打开选法术界面，改主手那件物品；右边按 {@code source} 列。主手不是 {@link SpellPicker} 时服务端不会收下改动。 */
    public static void open(ItemStack stack, SpellPickerSource source) {
        Minecraft.getInstance().setScreen(new SpellPickerScreen(stack, source));
    }

    @Override
    protected void init() {
        this.updateAvailable();
        int x0 = (this.width - BOOK_W * 2 - GAP) / 2;
        int y0 = (this.height - BOOK_H + TITLE_H) / 2;
        this.left.init(x0, y0);
        this.right.init(x0 + BOOK_W + GAP, y0);
    }

    @Override
    public void tick() {
        LocalPlayer player = this.minecraft.player;
        if (player == null || player.getMainHandItem().getItem() != this.item || player.getInventory().getSelectedSlot() != this.slot) {
            this.abandoned = true;
            this.onClose();
            return;
        }
        if (this.updateAvailable()) {
            this.right.refresh();
        }
    }

    /** 重算右边的法术（来源列的，再补上打开时就有的），返回有没有变。 */
    private boolean updateAvailable() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return false;
        }
        List<Holder<Spell>> now = new ArrayList<>(this.source.spells(player, player.getMainHandItem()));
        for (Holder<Spell> spell : this.initial) {
            if (!now.contains(spell)) {
                now.add(spell);
            }
        }
        if (now.equals(this.available)) {
            return false;
        }
        this.available = now;
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.left.drawBook(graphics);
        this.right.drawBook(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.left.drawContents(graphics, mouseX, mouseY);
        this.right.drawContents(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        for (Panel panel : List.of(this.left, this.right)) {
            Holder<Spell> spell = panel.spellAt(event.x(), event.y());
            if (spell == null) {
                continue;
            }
            if (panel.removes || this.selected.contains(spell)) {
                this.selected.remove(spell);
            } else {
                this.selected.add(spell);
            }
            this.left.refresh();
            this.right.refresh();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        for (Panel panel : List.of(this.left, this.right)) {
            if (panel.contains(x, y) && scrollY != 0.0) {
                panel.turn(scrollY < 0.0 ? 1 : -1);
                return true;
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!this.abandoned && !this.selected.equals(this.initial) && this.minecraft.getConnection() != null) {
            this.send();
        }
        super.removed();
    }

    private void send() {
        List<Identifier> ids = new ArrayList<>(this.selected.size());
        for (Holder<Spell> spell : this.selected) {
            spell.unwrapKey().ifPresent(key -> ids.add(key.identifier()));
        }
        ClientPacketDistributor.sendToServer(new PickSpellsPayload(ids));
    }

    private static boolean matches(Holder<Spell> spell, String query) {
        if (query.isEmpty() || spell.value().getName().getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return spell.unwrapKey().map(k -> k.identifier().toString().contains(query)).orElse(false);
    }

    /** 一本「配方书」：搜索框 + 5 × 4 格 + 翻页。removes = 左边那本（点了移出），否则点了选上 / 再点移出。 */
    private final class Panel {
        final boolean removes;
        private final Supplier<List<Holder<Spell>>> spells;
        private List<Holder<Spell>> shown = List.of();
        private int x, y, page;
        private @Nullable EditBox search;
        private @Nullable ImageButton forward, backward;

        Panel(boolean removes, Supplier<List<Holder<Spell>>> spells) {
            this.removes = removes;
            this.spells = spells;
        }

        void init(int x, int y) {
            this.x = x;
            this.y = y;
            String old = this.search != null ? this.search.getValue() : "";
            this.search = new EditBox(SpellPickerScreen.this.font, x + 25, y + 13, 81, 9 + 5, Component.translatable("itemGroup.search"));
            this.search.setMaxLength(50);
            this.search.setTextColor(-1);
            this.search.setValue(old);
            this.search.setHint(SEARCH_HINT);
            this.search.setResponder(text -> {
                this.page = 0;
                this.refresh();
            });
            addRenderableWidget(this.search);
            Component next = Component.translatable("gui.recipebook.next_page");
            Component previous = Component.translatable("gui.recipebook.previous_page");
            this.forward = addRenderableWidget(new ImageButton(x + 93, y + 137, 12, 17, PAGE_FORWARD, b -> this.turn(1), next));
            this.forward.setTooltip(Tooltip.create(next));
            this.backward = addRenderableWidget(new ImageButton(x + 38, y + 137, 12, 17, PAGE_BACKWARD, b -> this.turn(-1), previous));
            this.backward.setTooltip(Tooltip.create(previous));
            this.refresh();
        }

        /** 按搜索框重新筛一遍，页码夹回范围里，翻页箭头只在能翻时显示。 */
        void refresh() {
            String query = this.search != null ? this.search.getValue().trim().toLowerCase(Locale.ROOT) : "";
            this.shown = this.spells.get().stream().filter(s -> matches(s, query)).toList();
            this.page = Mth.clamp(this.page, 0, this.pages() - 1);
            if (this.forward != null) {
                this.forward.visible = this.page < this.pages() - 1;
            }
            if (this.backward != null) {
                this.backward.visible = this.page > 0;
            }
        }

        void turn(int delta) {
            this.page = Mth.clamp(this.page + delta, 0, this.pages() - 1);
            this.refresh();
        }

        int pages() {
            return Math.max(1, Mth.positiveCeilDiv(this.shown.size(), PER_PAGE));
        }

        boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + BOOK_W && my >= this.y && my < this.y + BOOK_H;
        }

        int slotX(int i) {
            return this.x + 11 + CELL * (i % COLS);
        }

        int slotY(int i) {
            return this.y + 31 + CELL * (i / COLS);
        }

        @Nullable Holder<Spell> spellAt(double mx, double my) {
            for (int i = 0; i < PER_PAGE; i++) {
                int idx = this.page * PER_PAGE + i;
                if (idx >= this.shown.size()) {
                    break;
                }
                if (mx >= this.slotX(i) && mx < this.slotX(i) + CELL && my >= this.slotY(i) && my < this.slotY(i) + CELL) {
                    return this.shown.get(idx);
                }
            }
            return null;
        }

        void drawBook(GuiGraphicsExtractor g) {
            g.blit(RenderPipelines.GUI_TEXTURED, RECIPE_BOOK, this.x, this.y, 1.0F, 1.0F, BOOK_W, BOOK_H, 256, 256);
        }

        void drawContents(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            Font font = SpellPickerScreen.this.font;
            String titleKey = this.removes ? "gui.ibx_lib.spell_picker.selected"
                    : "gui.ibx_lib.spell_picker." + SpellPickerScreen.this.source.name().toLowerCase(Locale.ROOT);
            Component title = Component.translatable(titleKey, this.spells.get().size());
            g.text(font, title, this.x + (BOOK_W - font.width(title)) / 2, this.y - TITLE_H + 1, 0xFFFFFFFF, true);
            Holder<Spell> hovered = null;
            for (int i = 0; i < PER_PAGE; i++) {
                int idx = this.page * PER_PAGE + i;
                if (idx >= this.shown.size()) {
                    break;
                }
                Holder<Spell> spell = this.shown.get(idx);
                int sx = this.slotX(i), sy = this.slotY(i);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, sx, sy, CELL, CELL);
                g.blit(RenderPipelines.GUI_TEXTURED, SpellIcons.texture(spell.value()), sx + 4, sy + 4, 0, 0,
                        SpellIcons.SIZE, SpellIcons.SIZE, SpellIcons.SIZE, SpellIcons.SIZE);
                if (!this.removes && SpellPickerScreen.this.selected.contains(spell)) {
                    g.outline(sx + 1, sy + 1, CELL - 2, CELL - 2, SELECTED_BORDER);
                }
                if (mouseX >= sx && mouseX < sx + CELL && mouseY >= sy && mouseY < sy + CELL) {
                    g.fill(sx + 4, sy + 4, sx + 4 + SpellIcons.SIZE, sy + 4 + SpellIcons.SIZE, HOVER);
                    hovered = spell;
                }
            }
            if (this.shown.isEmpty()) {
                Component empty = Component.translatable(this.spells.get().isEmpty()
                        ? (this.removes ? "gui.ibx_lib.spell_picker.empty" : "gui.ibx_lib.spell_picker.none")
                        : "gui.ibx_lib.spell_picker.no_match");
                g.text(font, empty, this.x + (BOOK_W - font.width(empty)) / 2, this.y + 80, 0xFFA0A0A0, false);
            }
            if (this.pages() > 1) {
                String pageText = (this.page + 1) + "/" + this.pages();
                g.text(font, pageText, this.x - font.width(pageText) / 2 + 73, this.y + 141, -1);
            }
            if (hovered != null) {
                g.setTooltipForNextFrame(this.tooltip(hovered), mouseX, mouseY);
            }
        }

        private List<FormattedCharSequence> tooltip(Holder<Spell> spell) {
            Font font = SpellPickerScreen.this.font;
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(spell.value().getName().getVisualOrderText());
            spell.unwrapKey().ifPresent(k -> lines.add(Component.literal(k.identifier().toString()).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()));
            lines.addAll(font.split(spell.value().getDescription().copy().withStyle(ChatFormatting.GRAY), 180));
            boolean removes = this.removes || SpellPickerScreen.this.selected.contains(spell);
            lines.add(Component.translatable(removes ? "gui.ibx_lib.spell_picker.remove" : "gui.ibx_lib.spell_picker.add")
                    .withStyle(removes ? ChatFormatting.RED : ChatFormatting.GREEN).getVisualOrderText());
            return lines;
        }
    }
}
