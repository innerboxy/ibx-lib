package ibx.lib.client.spell;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import ibx.lib.IbxLib;
import ibx.lib.api.ManaApi;
import ibx.lib.api.SummonApi;
import ibx.lib.client.UiPalette;
import ibx.lib.config.ClientConfig;
import ibx.lib.network.SelectSpellPayload;
import ibx.lib.registry.AttachmentRegistry;
import ibx.lib.registry.SoundRegistry;
import ibx.lib.spell.CastResult;
import ibx.lib.spell.CastType;
import ibx.lib.spell.SelectedSpell;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellCasting;
import ibx.lib.spell.SpellCooldowns;
import ibx.lib.spell.SpellEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.joml.Matrix3x2f;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 法术轮盘：<b>按住</b>轮盘键（默认 R，可改）弹出，格子就是 {@link SpellCasting#getSpells} 的结果；
 * <b>鼠标左键点</b>一格选中（发 {@link SelectSpellPayload}），松开轮盘键关上。只选不放，放法术是右键施法物品。
 * 选项 → 控制 里设成「可以不点击」（默认）时，松开轮盘键也会选中鼠标指着的那格，左键点选照常有效。
 * 圆心附近一小块不算指着任何格子（框停在当前选中上），指着那里松开、点击都不改。
 *
 * <ul>
 *   <li>不开界面：放出鼠标指针、视角停住，WASD 照常走（同铁魔法）。打开期间鼠标按键和滚轮都被吃掉，不会误攻击、误切快捷栏；
 *       轮盘键自己绑在鼠标键上时放行。</li>
 *   <li>样子见 {@link WheelShape}：几个法术就把圆分成几个扇形，不足 3 个按 3 格画，多出的格子空着、点不了。
 *       指着的那一格画选中框；没指着任何格子时框在当前选中的法术上。配色见 {@link ibx.lib.client.UiPalette}。</li>
 *   <li>轮盘上方写名字，右边是木色样式的说明框（描述、{@link Spell#getTooltip}、耗蓝 / 冷却 / 读条）。</li>
 * </ul>
 */
public final class SpellWheel {
    private SpellWheel() {}

    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(IbxLib.MODID, "spells"));
    public static final KeyMapping KEY = new KeyMapping("key." + IbxLib.MODID + ".spell_wheel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(IbxLib.MODID, "spell_wheel");
    private static final String TOOLTIP = "gui." + IbxLib.MODID + ".spell_tooltip.";
    private static final DecimalFormat SECONDS = new DecimalFormat("0.#");
    private static final int NAME_GAP = 12;          // 名字底到轮盘顶
    private static final int TOOLTIP_GAP = 10;       // 说明框到轮盘边
    private static final int DESCRIPTION_WIDTH = 160;
    /** 中心这一块（半径的四分之一，至少 8 像素）不算指着任何格子：打开时鼠标在正中，不动就松开轮盘键不会误选。 */
    private static final int DEAD_ZONE_MIN = 8;

    private static boolean open;
    private static List<SpellEntry> spells = List.of();
    /** 上一帧鼠标指着的格子，-1 为没指着。点击、松开轮盘键选中时用它。 */
    private static int hovered = -1;

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(KEY);
    }

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, SpellWheel::render);
    }

    // ---------------------------------------------------------------- 开关

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!open) {
            if (KEY.consumeClick() && mc.screen == null && player != null && !player.isSpectator()) {
                tryOpen(mc, player);
            }
            return;
        }
        while (KEY.consumeClick()) {
            // 按住期间的重复按下不算新的打开
        }
        if (player == null || player.isSpectator() || mc.screen != null) {
            close(mc, false);
            return;
        }
        if (!KEY.isDown()) {
            if (ClientConfig.get(ClientConfig.WHEEL_SELECT_ON_RELEASE)) {
                select(mc, false);
            }
            close(mc, true);
            return;
        }
        spells = SpellCasting.getSpells(player);
        if (spells.isEmpty()) {
            close(mc, true);
            return;
        }
        // 切出窗口再切回来时原版会把鼠标抓回去
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
    }

    private static void tryOpen(Minecraft mc, Player player) {
        spells = SpellCasting.getSpells(player);
        if (spells.isEmpty()) {
            player.sendOverlayMessage(Component.translatable(CastResult.NO_SPELL.messageKey()));
            return;
        }
        open = true;
        hovered = -1;
        mc.mouseHandler.releaseMouse();
        play(mc, SoundRegistry.SPELL_WHEEL_OPEN);
    }

    /** @param grab 把鼠标抓回去（回到正常视角操作）；因为打开了别的界面而关时不抓，交给那个界面。 */
    private static void close(Minecraft mc, boolean grab) {
        open = false;
        hovered = -1;
        spells = List.of();
        if (grab && mc.screen == null) {
            mc.mouseHandler.grabMouse();
        }
        play(mc, SoundRegistry.SPELL_WHEEL_CLOSE);
    }

    // ---------------------------------------------------------------- 输入

    /**
     * 打开期间吃掉鼠标<b>按下</b>（左键点选），松开照常放行：打开前就按着的攻击 / 使用键要能松掉，
     * 否则关上轮盘后会一直攻击。轮盘键自己绑在鼠标键上时也放行。
     */
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!open || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        InputConstants.Key key = KEY.getKey();
        if (key.getType() == InputConstants.Type.MOUSE && key.getValue() == event.getButton()) {
            return;
        }
        event.setCanceled(true);
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            select(Minecraft.getInstance(), true);
        }
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (open) {
            event.setCanceled(true);
        }
    }

    /**
     * 选中鼠标指着的那格。
     *
     * @param click 左键点的：点了就响选中音效（点已经选着的也响）；松开轮盘键选中的只在真的换了法术时响
     */
    private static void select(Minecraft mc, boolean click) {
        Player player = mc.player;
        if (player == null || hovered < 0 || hovered >= spells.size()) {
            return;
        }
        Identifier id = spells.get(hovered).value().id();
        if (id == null) {
            return;
        }
        boolean changed = !id.equals(SpellCasting.getSelectedId(player));
        if (changed) {
            // 本地先改，HUD 立刻换图标；服务端校验不过会把它的值重发回来
            player.setData(AttachmentRegistry.SELECTED_SPELL, new SelectedSpell(id));
            ClientPacketDistributor.sendToServer(new SelectSpellPayload(id));
        }
        if (click || changed) {
            play(mc, SoundRegistry.SPELL_WHEEL_SELECT);
        }
    }

    // ---------------------------------------------------------------- 绘制

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        List<SpellEntry> spells = SpellWheel.spells;
        if (!open || player == null || spells.isEmpty()) {
            return;
        }
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        // 按配置放大；屏幕放不下（上方还要留名字）时缩回去，但不小于刚好放得下图标的大小
        int fit = WheelShape.fitRadius(spells.size());
        int room = Math.min(cy - NAME_GAP - 4, cx - 4);
        int radius = Mth.clamp((int) Math.round(fit * ClientConfig.spellWheelScale()), fit, Math.max(fit, room));
        WheelShape shape = WheelShape.of(spells.size(), radius);
        int ox = cx - shape.half;
        int oy = cy - shape.half;

        Window window = mc.getWindow();
        double dx = mc.mouseHandler.getScaledXPos(window) - cx;
        double dy = mc.mouseHandler.getScaledYPos(window) - cy;
        int deadZone = Math.max(DEAD_ZONE_MIN, radius / 4);
        int slot = dx * dx + dy * dy < deadZone * deadZone ? -1 : shape.slotAt(dx, dy);
        hovered = slot < spells.size() ? slot : -1;
        SpellEntry selected = SpellCasting.resolveSelected(player, spells);
        int framed = hovered >= 0 ? hovered : spells.indexOf(selected);

        graphics.submitGuiElementRenderState(new WheelRenderState(new Matrix3x2f(graphics.pose()), ox, oy, shape.size,
                shape.baseRuns, framed >= 0 ? shape.hoverRuns[framed] : null, graphics.peekScissorStack()));
        for (int i = 0; i < spells.size(); i++) {
            Spell spell = spells.get(i).value();
            SpellIcons.draw(graphics, player, spell, ox + shape.iconX[i], oy + shape.iconY[i], ManaApi.canAfford(player, spell.manaCost()));
        }

        SpellEntry shown = hovered >= 0 ? spells.get(hovered) : selected;
        if (shown == null) {
            return;
        }
        Font font = mc.font;
        Component name = shown.value().getName().copy().withStyle(ChatFormatting.BOLD);
        graphics.text(font, name, cx - font.width(name) / 2, oy + shape.top - NAME_GAP, UiPalette.TITLE, true);

        List<ClientTooltipComponent> lines = new ArrayList<>();
        for (Component line : tooltip(player, shown.value())) {
            lines.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        List<FormattedCharSequence> description = font.split(shown.value().getDescription().copy().withColor(UiPalette.MUTED),
                DESCRIPTION_WIDTH);
        for (int i = 0; i < description.size(); i++) {
            lines.add(1 + i, ClientTooltipComponent.create(description.get(i)));
        }
        int right = ox + shape.right + 1 + TOOLTIP_GAP;
        int left = ox + shape.left - TOOLTIP_GAP;
        graphics.tooltip(font, lines, right, cy, (screenWidth, screenHeight, x, y, width, height) -> {
            // 右边放不下就放左边；竖直方向居中于轮盘中心
            int tx = x + width + 4 <= screenWidth ? x : Math.max(4, left - width);
            return new Vector2i(tx, Mth.clamp(y - height / 2, 4, Math.max(4, screenHeight - height - 4)));
        }, UiPalette.TOOLTIP_STYLE);
    }

    /** 说明框：名字、{@link Spell#getTooltip}、耗蓝、冷却、读条，冷却中 / 法力不足时再加一行红字（描述在名字下面另插）。 */
    private static List<Component> tooltip(Player player, Spell spell) {
        List<Component> lines = new ArrayList<>();
        lines.add(spell.getName().copy().withColor(UiPalette.TITLE).withStyle(ChatFormatting.BOLD));
        lines.addAll(spell.getTooltip(player));
        if (spell.isSummon()) {
            lines.add(Component.translatable(TOOLTIP + "summon", spell.summonCount(), spell.summonCost()).withColor(UiPalette.BODY));
            if (spell.summonDuration() > 0.0F) {
                lines.add(Component.translatable(TOOLTIP + "summon_duration", SECONDS.format(spell.summonDuration())).withColor(UiPalette.BODY));
            }
        }
        float cost = ManaApi.getActualCost(player, spell.manaCost());
        if (cost > 0.0F) {
            lines.add(Component.translatable(TOOLTIP + (spell.castType() == CastType.CHANNEL ? "mana_cost_per" : "mana_cost"),
                    SECONDS.format(cost)).withColor(UiPalette.MANA));
        }
        int cooldown = SpellCasting.getCooldownTicks(player, spell);
        if (cooldown > 0) {
            lines.add(Component.translatable(TOOLTIP + "cooldown", SECONDS.format(cooldown / 20.0)).withColor(UiPalette.MUTED));
        }
        lines.add((switch (spell.castType()) {
            case INSTANT -> Component.translatable(TOOLTIP + "instant");
            case CAST -> Component.translatable(TOOLTIP + "cast", SECONDS.format(SpellCasting.getCastTicks(player, spell) / 20.0));
            case CHARGE -> Component.translatable(TOOLTIP + "charge", SECONDS.format(SpellCasting.getCastTicks(player, spell) / 20.0));
            case CHANNEL -> Component.translatable(TOOLTIP + "channel", SECONDS.format(spell.tickInterval() / 20.0));
        }).withColor(UiPalette.BODY));
        int remaining = SpellCooldowns.getRemaining(player, spell);
        if (spell.isSummon() && !SummonApi.fits(player, spell.summonCost())) {
            lines.add(Component.translatable("message." + IbxLib.MODID + ".cast.summon_over_capacity",
                    spell.summonCost(), SummonApi.capacity(player)).withColor(UiPalette.NEGATIVE));
        } else if (remaining > 0) {
            lines.add(Component.translatable(CastResult.ON_COOLDOWN.messageKey(), SECONDS.format(remaining / 20.0)).withColor(UiPalette.NEGATIVE));
        } else if (!ManaApi.canAfford(player, spell.manaCost())) {
            lines.add(Component.translatable(CastResult.NOT_ENOUGH_MANA.messageKey()).withColor(UiPalette.NEGATIVE));
        }
        return lines;
    }

    private static void play(Minecraft mc, Holder<SoundEvent> sound) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
    }
}
