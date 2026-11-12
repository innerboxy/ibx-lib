package ibx.lib.compat.inventoryhud;

import dlovin.inventoryhud.InventoryHUDCommon;
import dlovin.inventoryhud.armorstatus.ArmorStatus;
import dlovin.inventoryhud.config.ArmorView;
import dlovin.inventoryhud.gui.InventoryGui;
import dlovin.inventoryhud.gui.renderers.ArmorRenderer;
import dlovin.inventoryhud.gui.renderers.BlockArmorRenderer;
import dlovin.inventoryhud.gui.renderers.EachArmorRenderer;
import dlovin.inventoryhud.gui.renderers.HotBarArmorRenderer;
import dlovin.inventoryhud.utils.EachItem;
import dlovin.inventoryhud.utils.WidgetAligns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * InventoryHUD+ 盔甲栏 HUD 这一帧画的每一格占的地方：16×16 的图标（乘它的缩放），加上图标旁边的字（耐久、个数）。
 *
 * <p>读的是它运行中的渲染器，按键开关、游戏里改设置都跟着变；格子坐标、字在哪边这些它没公开的算法，照 3.4.31 的三种摆法抄过来：
 * {@code HotBarArmorRenderer}（贴着快捷栏两边，默认）、{@code BlockArmorRenderer}（整块摆在别处）、
 * {@code EachArmorRenderer}（每格各自摆，「整体移动」关掉时）。它的坐标约定：居中对齐时 x 为正是往<b>左</b>数。</p>
 *
 * <p>只在装了 InventoryHUD+ 时由 {@code compat.InventoryHudCompat} 注册。它换了版本、字段对不上时抛的错由
 * {@link ibx.lib.client.hud.HudAvoidance} 接住、停用这个来源。</p>
 */
public final class ArmorHudArea {
    // 格子下标同它的 items / armorRL
    private static final int HEAD = 0;
    private static final int CHEST = 1;
    private static final int LEGS = 2;
    private static final int FEET = 3;
    private static final int OFFHAND = 4;
    private static final int MAINHAND = 5;
    private static final int FREE_SLOTS = 6;   // 背包空格数
    private static final int ARROWS = 7;       // 箭数
    private static final EquipmentSlot[] EQUIPMENT = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND};
    // HotBarArmorRenderer 里各格的位置：x 从左 / 右两列的基准算，y 从屏幕底往上数
    private static final int[] HOTBAR_X = {-96, -96, 96, 96, 96, -96};
    private static final int[] HOTBAR_Y = {56, 38, 56, 38, 74, 74, 20, 20};

    private final ArmorRenderer hud;
    private final Player player;
    private final Font font;
    private final Consumer<Rect2i> out;
    private final float scale;

    private ArmorHudArea(ArmorRenderer hud, Player player, Font font, Consumer<Rect2i> out) {
        this.hud = hud;
        this.player = player;
        this.font = font;
        this.out = out;
        this.scale = hud.armScale;
    }

    /** {@link ibx.lib.client.hud.HudAvoidance.Source}：盔甲栏 HUD 这一帧画出来的格子。 */
    public static void collect(Player player, int width, int height, Consumer<Rect2i> out) {
        InventoryHUDCommon common = InventoryHUDCommon.getInstance();
        InventoryGui gui = common == null ? null : common.getInventoryGui();
        if (!InventoryHUDCommon.armorHUD || gui == null || gui.armorRenderer == null) {
            return;
        }
        // 同 InventoryGui.renderArmor：F3 界面开着时看它「调试时显示」那一项
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.getDebugOverlay().showDebugScreen() && !gui.armorRenderer.withDebug) {
            return;
        }
        ArmorHudArea area = new ArmorHudArea(gui.armorRenderer, player, mc.font, out);
        if (gui.armorRenderer instanceof HotBarArmorRenderer) {
            area.hotbar(width, height);
        } else if (gui.armorRenderer instanceof BlockArmorRenderer) {
            area.block(width, height);
        } else if (gui.armorRenderer instanceof EachArmorRenderer) {
            area.each(width, height);
        }
    }

    // ---------------------------------------------------------------- 三种摆法

    /**
     * 照 {@code HotBarArmorRenderer.render}：快捷栏左边一列（主手、头、胸、箭数）、右边一列（副手、腿、脚、空格数）。
     * 箭数、空格数两格的 x 和字在哪边看原版副手格有没有画出来；缩放 ≥ 1 时其余格另用一套 x。
     */
    private void hotbar(int width, int height) {
        boolean viewOff = hud.armView == ArmorView.OFF;
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        boolean offhandEmpty = player.getOffhandItem().isEmpty();
        boolean offhandShownLeft = rightHanded && !offhandEmpty;    // 原版副手格画在快捷栏左边
        boolean offhandShownRight = !rightHanded && !offhandEmpty;  // 画在右边（左撇子）
        int leftX = width / 2 - 96 + (int) (-16.0F * scale)
                + (!viewOff || offhandShownLeft ? (offhandShownLeft && scale < 1.0F ? -26 : (int) (-24.0F * scale)) : 0);
        boolean leftTextRight = !viewOff && (!rightHanded || offhandEmpty);
        int rightX = width / 2 + 96
                + (!viewOff || offhandShownRight ? (offhandShownRight && scale < 1.0F ? 26 : (int) (24.0F * scale)) : 0);
        boolean rightTextRight = viewOff || offhandShownRight;

        int itemLeftX = leftX;
        int itemRightX = rightX;
        boolean itemLeftTextRight = leftTextRight;
        boolean itemRightTextRight = rightTextRight;
        if (scale >= 1.0F) {
            itemLeftX = width / 2 + (int) (-16.0F * scale);
            itemRightX = width / 2;
            if (!viewOff) {
                itemLeftX = (int) (itemLeftX - 24.0F * scale);
                itemRightX = (int) (itemRightX + 24.0F * scale);
            }
            itemLeftTextRight = !viewOff;
            itemRightTextRight = viewOff;
        }

        int handRise = hud.showArmor ? 0 : 36;   // 不显示盔甲时两只手往下落到盔甲那几格
        for (int index = HEAD; index <= ARROWS; index++) {
            if (!shown(index)) {
                continue;
            }
            boolean left = index == HEAD || index == CHEST || index == MAINHAND || index == ARROWS;
            int y = (int) (height - (HOTBAR_Y[index] - (index == MAINHAND || index == OFFHAND ? handRise : 0)) * scale);
            if (index >= FREE_SLOTS) {
                slot(index, left ? leftX : rightX, y, left ? leftTextRight : rightTextRight);
            } else {
                slot(index, (left ? itemLeftX : itemRightX) + HOTBAR_X[index], y, left ? itemLeftTextRight : itemRightTextRight);
            }
        }
    }

    /**
     * 照 {@code BlockArmorRenderer}：整块（90×70，乘缩放）按它的坐标、对齐摆；块里一列盔甲（头到脚），
     * 旁边一列主手、副手，再下面空格数、箭数。整块靠右时盔甲列在右边、字写在图标左边。
     */
    private void block(int width, int height) {
        int left = alignX(width, (int) (90.0F * scale), hud.armX, hud.ArmAligns.HorAlign);
        int top = alignY(height, (int) (70.0F * scale), hud.armY, hud.ArmAligns.VertAlign);
        boolean right = rightSide(hud.armX, hud.ArmAligns.HorAlign);
        int armorX = right ? 75 : 0;
        int handX = hud.showArmor ? (right ? 33 : 42) : armorX;
        int helperX = hud.showArmor ? (right ? armorX - 42 : armorX + 42) : armorX;
        int helperY = 36 - (hud.showMain ? 0 : 18) - (hud.showOff ? 0 : 18);
        int[] localX = {armorX, armorX, armorX, armorX, handX, handX, helperX, helperX};
        int[] localY = {0, 18, 36, 54, hud.showMain ? 18 : 0, 0, helperY, hud.showInv ? helperY + 18 : helperY};
        for (int index = HEAD; index <= ARROWS; index++) {
            if (shown(index)) {
                slot(index, left + localX[index] * scale, top + localY[index] * scale, !right);
            }
        }
    }

    /** 照 {@code EachArmorRenderer}：每格按自己的坐标、对齐摆，字写在朝屏幕中间的那边。 */
    private void each(int width, int height) {
        int size = (int) (16.0F * scale);
        for (int index = HEAD; index <= ARROWS; index++) {
            if (shown(index)) {
                EachItem pos = hud.items[index];
                slot(index, alignX(width, size, pos.x, pos.aligns.HorAlign), alignY(height, size, pos.y, pos.aligns.VertAlign),
                        !rightSide(pos.x, pos.aligns.HorAlign));
            }
        }
    }

    // ---------------------------------------------------------------- 格子

    /** 这一格开没开：盔甲四格看「显示盔甲」，两只手各看各的，箭数见 {@link #arrowsShown()}。 */
    private boolean shown(int index) {
        return switch (index) {
            case MAINHAND -> hud.showMain;
            case OFFHAND -> hud.showOff;
            case FREE_SLOTS -> hud.showInv;
            case ARROWS -> arrowsShown();
            default -> hud.showArmor;
        };
    }

    /** 同 {@code ArmorRenderer.showArrows}：手里有远程武器，或设了「没拿武器也显示」。 */
    private boolean arrowsShown() {
        return hud.showArrows && (hud.showArrowsWithoutWeapon
                || player.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || player.getOffhandItem().getItem() instanceof ProjectileWeaponItem);
    }

    /**
     * 左上角在 (x, y) 的一格：物品格照 {@code renderSlot / renderItem}——有耐久写耐久，没耐久的东西不写字（两只手开了「显示数量」时写个数），
     * 空格只在「显示空格图标」时画；空格数、箭数两格写数字。
     */
    private void slot(int index, float x, float y, boolean textRight) {
        if (index == FREE_SLOTS) {
            element(x, y, textRight, String.valueOf(freeSlots()));
            return;
        }
        if (index == ARROWS) {
            element(x, y, textRight, String.valueOf(ammo()));
            return;
        }
        ItemStack stack = player.getItemBySlot(EQUIPMENT[index]);
        int damage = ArmorStatus.getDamage(stack);
        if (damage >= 0 && damage <= hud.armAbove) {
            element(x, y, textRight, switch (hud.armView) {
                case PERCENTAGE -> damage + "%";
                case DAMAGE -> String.valueOf(stack.getDamageValue());
                case DAMAGE_LEFT -> String.valueOf(stack.getMaxDamage() - stack.getDamageValue());
                case OFF -> "";
            });
        } else if (damage == -2) {
            boolean count = (index == MAINHAND || index == OFFHAND) && hud.showCount;
            element(x, y, textRight, count ? String.valueOf(count(stack)) : "");
        } else if (hud.showEmpty) {
            element(x, y, textRight, "");
        }
    }

    /** 图标 16 见方，字在图标右边（从 17 起）或左边（隔 1），往下 4；都乘缩放。 */
    private void element(float x, float y, boolean textRight, String text) {
        float icon = 16.0F * scale;
        out.accept(rect(x, y, icon, icon));
        if (!text.isEmpty()) {
            int width = font.width(text);
            float textX = textRight ? x + 17.0F * scale : x - (1 + width) * scale;
            out.accept(rect(textX, y + 4.0F * scale, width * scale, font.lineHeight * scale));
        }
    }

    // ---------------------------------------------------------------- 数字（只用来量字宽）

    /** 背包（不算装备栏）里的空格数，同 {@code getFreeAndAmmo}。 */
    private int freeSlots() {
        int free = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /**
     * 箭数：手里远程武器能用的弹药（没拿远程武器就数箭），背包加副手，同 {@code getFreeAndAmmo / getAllProj}。
     * 它用的是 NeoForge 标了过时的、不看物品堆的那两个方法，这里照用，数出来才和它写的一样。
     */
    @SuppressWarnings("deprecation")
    private int ammo() {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offhand = player.getOffhandItem();
        Predicate<ItemStack> ammo;
        if (mainHand.getItem() instanceof ProjectileWeaponItem weapon) {
            ammo = weapon.getSupportedHeldProjectiles().and(stack -> stack == offhand).or(weapon.getAllSupportedProjectiles());
        } else if (offhand.getItem() instanceof ProjectileWeaponItem weapon) {
            ammo = weapon.getSupportedHeldProjectiles().and(stack -> stack == mainHand).or(weapon.getAllSupportedProjectiles());
        } else {
            ammo = stack -> stack.getItem() instanceof ArrowItem;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && ammo.test(stack)) {
                count += stack.getCount();
            }
        }
        if (!offhand.isEmpty() && ammo.test(offhand)) {
            count += offhand.getCount();
        }
        return count;
    }

    /** 背包加副手里和 {@code item} 同种的个数，同 {@code getItemCount}。 */
    private int count(ItemStack item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (ItemStack.isSameItem(stack, item)) {
                count += stack.getCount();
            }
        }
        if (ItemStack.isSameItem(player.getOffhandItem(), item)) {
            count += player.getOffhandItem().getCount();
        }
        return count;
    }

    // ---------------------------------------------------------------- 对齐（同 HudRenderer / ArmorRenderer.getRight）

    private static int alignX(int width, int size, int x, WidgetAligns.HAlign align) {
        int result = switch (align) {
            case LEFT -> x;
            case MIDDLE -> width / 2 - size / 2 - x;
            case RIGHT -> width - x;
        };
        return Mth.clamp(result, 0, width - size);
    }

    private static int alignY(int height, int size, int y, WidgetAligns.VAlign align) {
        int result = switch (align) {
            case TOP -> y;
            case CENTER -> height / 2 - size / 2 - y;
            case BOTTOM -> height - y;
        };
        return Mth.clamp(result, 0, height - size);
    }

    /** 格子在不在「右边」：它据此把字写到另一边（朝屏幕中间）。 */
    private static boolean rightSide(int x, WidgetAligns.HAlign align) {
        return switch (align) {
            case LEFT -> false;
            case MIDDLE -> x > 0;
            case RIGHT -> true;
        };
    }

    /** 小数坐标往外取整成整像素的矩形。 */
    private static Rect2i rect(float x, float y, float width, float height) {
        int left = Mth.floor(x);
        int top = Mth.floor(y);
        return new Rect2i(left, top, Mth.ceil(x + width) - left, Mth.ceil(y + height) - top);
    }
}
