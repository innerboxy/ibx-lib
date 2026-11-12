package ibx.lib.client.hud;

import ibx.lib.IbxLib;
import ibx.lib.api.ManaApi;
import ibx.lib.client.spell.SpellIcons;
import ibx.lib.config.ClientConfig;
import ibx.lib.registry.TagRegistry;
import ibx.lib.spell.CastType;
import ibx.lib.spell.PlayerSpells;
import ibx.lib.spell.SpellEntry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * 本模组的 HUD：
 * <ul>
 *   <li>生命条（左）、饥饿条（右）：橙色底板上放原版图标 + 原版界面配色的条框和凹槽（2 倍精度贴图），代替原版的心形行和鸡腿行；
 *       骑着有血量的坐骑时饥饿条换成同样式的坐骑条；</li>
 *   <li>氧气不再单占一行（原版氧气泡行不画，免得把蓝条顶高），画在生命条的外框上，破泡的声音照原版补上；</li>
 *   <li>当前法术框：选中的法术画在副手栏外侧（不管副手有没有东西，位置都按副手栏在算），只在手持施法物品时画；
 *       别的模组的 HUD 压到它时往外让开（{@link HudAvoidance}）；</li>
 *   <li>吟唱条（左）和蓝条（右），按原版的堆叠规则叠在生命条 / 饥饿条上方；</li>
 *   <li>等级数字挪到经验条正中；原版护甲图标行隐藏。</li>
 * </ul>
 *
 * <p>原版层都是包住而不是删掉：本 HUD 不生效时（用原版 HUD、创造 / 旁观、隐藏 GUI）原样调用，配置能在游戏里随时切换。
 * 吟唱条、蓝条不看原版 HUD 那一项（{@link #barsVisible()}），用原版 HUD 时照样叠在心形 / 鸡腿（护甲、氧气泡）几行上方；
 * 当前法术框是功能不是装饰，不受 {@link #isActive()} 管：除旁观外都画，用原版 HUD 也画。</p>
 */
public final class IbxHud {
    private IbxHud() {}

    static final int BAR_W = 89;     // 条：描边 1 + 钢框 2 + 黑缝 1 + 槽 + 黑缝 1 + 钢框 2 + 描边 1
    static final int BAR_H = 15;
    private static final int TRACK_X = 4;
    private static final int TRACK_Y = 4;
    private static final int TRACK_W = 81;
    private static final int TRACK_H = 7;
    private static final int GEM_RISE = 3;          // 蓝条的宝石高出条顶的像素
    // 生命条、饥饿条：2 倍精度贴图（180×26）画成 90×13；生命条底板在左，饥饿条镜像、底板在右
    private static final int PLATE_W = 90;
    private static final int PLATE_H = 13;
    private static final int PLATE_TRACK_Y = 3;     // 槽 7 高
    private static final int HEALTH_TRACK_X = 19;   // 生命条的槽：19 ~ 87
    private static final int HEALTH_TRACK_W = 68;
    private static final int HUNGER_X = 3;          // 饥饿条左块饥饿 3 ~ 36、右块饱和 38 ~ 71，中间 2 像素隔断
    private static final int HUNGER_W = 33;
    private static final int SATURATION_X = 38;
    private static final int SATURATION_W = 33;
    private static final int MOUNT_TRACK_X = 3;     // 坐骑条的槽：3 ~ 71（底板在右，同饥饿条）
    private static final int MOUNT_TRACK_W = 68;
    private static final int AIR_X = 14;            // 氧气圈从底板右缘（14）往右按比例截到 88，右端两列满了才画
    private static final int AIR_W = 74;
    private static final int DROWN_FLASH_TICKS = 4; // 氧气耗尽时外框红 / 原色各停几 tick
    private static final int SPELL_SLOT = 22;       // 当前法术框（原版副手栏的格子）的边长

    private static final Identifier BARS_LAYER = Identifier.fromNamespaceAndPath(IbxLib.MODID, "cast_and_mana_bars");
    private static final Identifier LEVEL_LAYER = Identifier.fromNamespaceAndPath(IbxLib.MODID, "experience_level_on_bar");
    private static final Identifier SPELL_LAYER = Identifier.fromNamespaceAndPath(IbxLib.MODID, "selected_spell");
    private static final Identifier OFFHAND_LEFT = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final Identifier OFFHAND_RIGHT = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final Identifier FOOD_EMPTY = Identifier.withDefaultNamespace("hud/food_empty");
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_EMPTY_HUNGER = Identifier.withDefaultNamespace("hud/food_empty_hunger");
    private static final Identifier FOOD_FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");
    private static final Identifier HEART_VEHICLE_CONTAINER = Identifier.withDefaultNamespace("hud/heart/vehicle_container");
    private static final Identifier HEART_VEHICLE_FULL = Identifier.withDefaultNamespace("hud/heart/vehicle_full");

    private static final int WHITE = 0xFFFFFFFF;
    private static final int LEVEL_GREEN = 0xFF80FF20;
    private static final int SATURATION_TEXT = 0xFFFFF2B0;

    private static final int MANA_HOLD_TICKS = 60;      // 蓝回满后停留
    private static final int MANA_FADE_TICKS = 10;
    private static final int RESULT_HOLD_TICKS = 10;    // 施法结束后停留
    private static final int RESULT_FADE_TICKS = 6;

    private static long ticks;
    private static long manaLastNotFull = Long.MIN_VALUE / 2;
    /** 上一个响过破泡声的泡位置，同原版 {@code Gui.lastBubblePopSoundPlayed}。 */
    private static int lastBubblePopSound;

    /** HUD 自己的 tick 计数（暂停时不走），淡出、读条都按它算。 */
    public static long ticks() {
        return ticks;
    }

    /** 生命条、饥饿条这一套当前是否生效：没选原版 HUD，且 {@link #barsVisible()}。 */
    public static boolean isActive() {
        return !ClientConfig.get(ClientConfig.VANILLA_HUD) && barsVisible();
    }

    /** 吟唱条、蓝条画不画：GUI 没隐藏、能受伤的模式（生存 / 冒险）、镜头在玩家身上。不看原版 HUD 那一项。 */
    public static boolean barsVisible() {
        Minecraft mc = Minecraft.getInstance();
        return !mc.options.hideGui
                && mc.gameMode != null
                && mc.gameMode.canHurtPlayer()
                && mc.getCameraEntity() instanceof Player;
    }

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // 心形行 → 生命条
        event.wrapLayer(VanillaGuiLayers.PLAYER_HEALTH, old -> (graphics, deltaTracker) -> {
            if (isActive()) {
                renderHealthBar(graphics);
            } else {
                old.render(graphics, deltaTracker);
            }
        });
        // 鸡腿行 → 饥饿条；骑着有血量的坐骑时同原版不画，位置让给坐骑条
        event.wrapLayer(VanillaGuiLayers.FOOD_LEVEL, old -> (graphics, deltaTracker) -> {
            if (!isActive()) {
                old.render(graphics, deltaTracker);
            } else if (!(Minecraft.getInstance().getCameraEntity() instanceof Player player && mountWithHealth(player) != null)) {
                renderFoodBar(graphics);
            }
        });
        // 坐骑心形行 → 坐骑条
        event.wrapLayer(VanillaGuiLayers.VEHICLE_HEALTH, old -> (graphics, deltaTracker) -> {
            if (isActive()) {
                renderMountBar(graphics);
            } else {
                old.render(graphics, deltaTracker);
            }
        });
        // 氧气泡行：画在生命条外框上了，这里只照原版放破泡的声音
        event.wrapLayer(VanillaGuiLayers.AIR_LEVEL, old -> (graphics, deltaTracker) -> {
            if (!isActive()) {
                old.render(graphics, deltaTracker);
            } else if (Minecraft.getInstance().getCameraEntity() instanceof Player player) {
                playBubblePopSound(player);
            }
        });
        // 护甲图标行：生效时不画
        event.wrapLayer(VanillaGuiLayers.ARMOR_LEVEL, old -> (graphics, deltaTracker) -> {
            if (!isActive()) {
                old.render(graphics, deltaTracker);
            }
        });
        // 两条要叠在生命 / 饥饿（坐骑）这几行上面（原版 HUD 时还有护甲、氧气泡行），所以放在它们之后，读它们累加好的 leftHeight / rightHeight
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, BARS_LAYER, (graphics, deltaTracker) -> {
            if (barsVisible()) {
                renderBars(graphics, deltaTracker);
            }
        });
        event.registerAbove(VanillaGuiLayers.HOTBAR, SPELL_LAYER, (graphics, deltaTracker) -> renderSelectedSpell(graphics));
        // 等级数字压在经验条正中，要画在经验条填充之后，所以原版那层不画，另起一层放在 CONTEXTUAL_INFO_BAR 上面
        event.wrapLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, old -> (graphics, deltaTracker) -> {
            if (!isActive()) {
                old.render(graphics, deltaTracker);
            }
        });
        event.registerAbove(VanillaGuiLayers.CONTEXTUAL_INFO_BAR, LEVEL_LAYER, (graphics, deltaTracker) -> {
            if (isActive()) {
                renderLevel(graphics);
            }
        });
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            ticks++;
        }
        CastBar.tick(mc.player);
    }

    // ---------------------------------------------------------------- 生命条、饥饿条

    /**
     * 生命条：左端橙色底板上放原版心形（先垫空心容器当黑描边），右边原版格子式凹槽；数字写 {@code 10/20}，
     * 有吸收时写 {@code 10(4)/20} 并换黄心。红色画生命、后面接金色画吸收；生命 + 吸收超过最大值时整条按总量缩放。
     * 中毒 / 凋零 / 冰冻时图标和条一起变色；外框上画氧气（见 {@link #renderAirRing}）。
     * 底边和原版一行 9 高的底边齐平，画完让出条高 + 1。
     */
    private static void renderHealthBar(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof Player player)) {
            return;
        }
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - mc.gui.leftHeight - (PLATE_H - 9);
        int current = Mth.ceil(player.getHealth());
        int max = Mth.ceil(Math.max(player.getMaxHealth(), current));
        int absorption = Mth.ceil(player.getAbsorptionAmount());

        blit(graphics, HudSprites.HEALTH_FRAME, x, y, PLATE_W, PLATE_H);
        Gui.HeartType type = heartType(player);
        float total = Math.max(max, current + absorption);
        int red = Math.round(HEALTH_TRACK_W * current / total);
        int gold = Math.min(HEALTH_TRACK_W - red, Math.round(HEALTH_TRACK_W * absorption / total));
        plateSegment(graphics, healthFill(type), x, y, HEALTH_TRACK_X, HEALTH_TRACK_W, 0, red);
        plateSegment(graphics, HudSprites.HEALTH_ABSORPTION, x, y, HEALTH_TRACK_X, HEALTH_TRACK_W, red, gold);
        renderAirRing(graphics, player, x, y);

        // 同原版：凋零时吸收心也画成凋零心
        Gui.HeartType iconType = absorption > 0 && type != Gui.HeartType.WITHERED ? Gui.HeartType.ABSORBING : type;
        boolean hardcore = player.level().getLevelData().isHardcore();
        blit(graphics, Gui.HeartType.CONTAINER.getSprite(hardcore, false, false), x + 4, y + 2, 9, 9);
        blit(graphics, iconType.getSprite(hardcore, false, false), x + 4, y + 2, 9, 9);

        String text = absorption > 0 ? current + "(" + absorption + ")/" + max : current + "/" + max;
        centeredOutlined(graphics, mc.font, text, x + HEALTH_TRACK_X + HEALTH_TRACK_W / 2.0F, y + PLATE_TRACK_Y, WHITE,
                HEALTH_TRACK_W - 2);
        mc.gui.leftHeight += PLATE_H + 1;
    }

    /**
     * 氧气画在生命条外框上：上下两边（黑描边那行 + 亮 / 暗沿那行）从底板往右按氧气比例变蓝，满了右端那两列也亮；
     * 氧气耗尽时整圈红闪。显示条件同原版氧气泡行：眼睛在会淹死的液体里，或氧气没满。
     */
    private static void renderAirRing(GuiGraphicsExtractor graphics, Player player, int x, int y) {
        int max = player.getMaxAirSupply();
        if (max <= 0) {
            return;
        }
        int air = Mth.clamp(player.getAirSupply(), 0, max);
        if (air >= max && !isUnderWater(player)) {
            return;
        }
        if (air == 0) {
            if (ticks / DROWN_FLASH_TICKS % 2 == 0) {
                blit(graphics, HudSprites.HEALTH_DROWN, x, y, PLATE_W, PLATE_H);
            }
            return;
        }
        int width = air >= max ? PLATE_W : AIR_X + Math.round(AIR_W * (float) air / max);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HudSprites.HEALTH_AIR, PLATE_W, PLATE_H, 0, 0, x, y, width, PLATE_H, WHITE);
    }

    /** 同原版 {@code Gui.extractAirBubbles} 判断是否在水下（眼睛在会淹死的液体里）。 */
    private static boolean isUnderWater(Player player) {
        return player.getFluidInteraction().isEyeInFluidMatching(player, (entity, type, height) -> entity.canDrownInFluidType(type));
    }

    /** 原版氧气泡行每破一个泡响一声，越到后面越响、越尖；那一行不画了，声音照原版的算法补上（按 10 个泡算）。 */
    private static void playBubblePopSound(Player player) {
        int max = player.getMaxAirSupply();
        if (max <= 0) {
            return;
        }
        if (!isUnderWater(player)) {
            lastBubblePopSound = 0;
            return;
        }
        int air = Mth.clamp(player.getAirSupply(), 0, max);
        int full = airBubbles(air - 2, max);
        int popping = airBubbles(air, max);
        if (popping == full || popping < 1 || popping == lastBubblePopSound) {
            return;
        }
        int empty = 10 - airBubbles(air + (air != 0 ? 1 : 0), max);
        float volume = 0.5F + 0.1F * Math.max(0, empty - 3 + 1);
        float pitch = 1.0F + 0.1F * Math.max(0, empty - 5 + 1);
        player.playSound(SoundEvents.BUBBLE_POP, volume, pitch);
        lastBubblePopSound = popping;
    }

    private static int airBubbles(int air, int max) {
        return Mth.ceil((float) (air * 10) / max);
    }

    /**
     * 在槽里贴一段填充（单位 GUI 像素）。填充贴图四周有一圈同色系描边，长度又随数值变，所以拆成三块贴：
     * 贴图槽里第一列当左端头、最后一列当右端头，中间那段拿第二列起的部分（填充横向是均匀的）。
     *
     * @param trackX 槽在条里的起点；{@code trackW} 槽宽；{@code start} 这段从槽的第几列开始；{@code width} 段长
     */
    private static void plateSegment(GuiGraphicsExtractor graphics, Identifier fill, int x, int y, int trackX, int trackW,
                                     int start, int width) {
        if (width <= 0) {
            return;
        }
        int left = x + trackX + start;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fill, PLATE_W, PLATE_H, trackX, 0, left, y, 1, PLATE_H, WHITE);
        if (width > 2) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fill, PLATE_W, PLATE_H, trackX + 1, 0, left + 1, y, width - 2, PLATE_H, WHITE);
        }
        if (width > 1) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fill, PLATE_W, PLATE_H, trackX + trackW - 1, 0,
                    left + width - 1, y, 1, PLATE_H, WHITE);
        }
    }

    /** 心形类型，逻辑同原版 {@code Gui.HeartType.forPlayer}（它是 private），也走 NeoForge 的心形类型事件。 */
    private static Gui.HeartType heartType(Player player) {
        Gui.HeartType type;
        if (player.hasEffect(MobEffects.POISON)) {
            type = Gui.HeartType.POISIONED;
        } else if (player.hasEffect(MobEffects.WITHER)) {
            type = Gui.HeartType.WITHERED;
        } else if (player.isFullyFrozen()) {
            type = Gui.HeartType.FROZEN;
        } else {
            type = Gui.HeartType.NORMAL;
        }
        return ClientHooks.firePlayerHeartTypeEvent(player, type);
    }

    private static Identifier healthFill(Gui.HeartType type) {
        return switch (type) {
            case POISIONED -> HudSprites.HEALTH_POISON;
            case WITHERED -> HudSprites.HEALTH_WITHER;
            case FROZEN -> HudSprites.HEALTH_FROZEN;
            default -> HudSprites.HEALTH;
        };
    }

    /**
     * 饥饿条：右端橙色底板上放原版鸡腿（先垫空鸡腿当描边），左边凹槽分成两块：左块饥饿、右块饱和，各写各的数字。
     * 有饥饿效果时同原版换绿鸡腿，饥饿块也变绿。
     */
    private static void renderFoodBar(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof Player player)) {
            return;
        }
        int x = graphics.guiWidth() / 2 + 1;
        int y = graphics.guiHeight() - mc.gui.rightHeight - (PLATE_H - 9);
        boolean hunger = player.hasEffect(MobEffects.HUNGER);

        blit(graphics, HudSprites.FOOD_FRAME, x, y, PLATE_W, PLATE_H);
        FoodData food = player.getFoodData();
        int foodLevel = food.getFoodLevel();
        float saturation = Math.min(food.getSaturationLevel(), foodLevel);
        int foodWidth = Math.round(HUNGER_W * foodLevel / 20.0F);
        int saturationWidth = Math.round(SATURATION_W * saturation / 20.0F);
        plateSegment(graphics, hunger ? HudSprites.HUNGER_EFFECT : HudSprites.HUNGER, x, y, HUNGER_X, HUNGER_W, 0, foodWidth);
        plateSegment(graphics, HudSprites.SATURATION, x, y, SATURATION_X, SATURATION_W, 0, saturationWidth);

        blit(graphics, hunger ? FOOD_EMPTY_HUNGER : FOOD_EMPTY, x + 77, y + 2, 9, 9);
        blit(graphics, hunger ? FOOD_FULL_HUNGER : FOOD_FULL, x + 77, y + 2, 9, 9);

        String saturationText = saturation < 10.0F
                ? String.format(Locale.ROOT, "%.1f", saturation)
                : String.valueOf(Math.round(saturation));
        centeredOutlined(graphics, mc.font, String.valueOf(foodLevel), x + HUNGER_X + HUNGER_W / 2.0F, y + PLATE_TRACK_Y,
                WHITE, HUNGER_W - 2);
        centeredOutlined(graphics, mc.font, saturationText, x + SATURATION_X + SATURATION_W / 2.0F, y + PLATE_TRACK_Y,
                SATURATION_TEXT, SATURATION_W - 2);
        mc.gui.rightHeight += PLATE_H + 1;
    }

    /** 骑着的、要显示血量的坐骑；同原版，最大血量折成心数为 0 时不算（饥饿条照常画）。 */
    private static @Nullable LivingEntity mountWithHealth(Player player) {
        return player.getVehicle() instanceof LivingEntity vehicle && vehicle.showVehicleHealth()
                && (int) (vehicle.getMaxHealth() + 0.5F) / 2 > 0 ? vehicle : null;
    }

    /**
     * 坐骑条：骑着有血量的坐骑时代替饥饿条，样式同饥饿条（右端底板），底板上放原版坐骑心形（先垫坐骑空心容器当黑描边），
     * 槽只有一段，数字写 {@code 23/30}。
     */
    private static void renderMountBar(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof Player player) || !(mountWithHealth(player) instanceof LivingEntity mount)) {
            return;
        }
        int x = graphics.guiWidth() / 2 + 1;
        int y = graphics.guiHeight() - mc.gui.rightHeight - (PLATE_H - 9);
        int current = Mth.ceil(mount.getHealth());
        int max = Mth.ceil(Math.max(mount.getMaxHealth(), current));

        blit(graphics, HudSprites.MOUNT_FRAME, x, y, PLATE_W, PLATE_H);
        plateSegment(graphics, HudSprites.MOUNT, x, y, MOUNT_TRACK_X, MOUNT_TRACK_W, 0, Math.round(MOUNT_TRACK_W * current / (float) max));
        blit(graphics, HEART_VEHICLE_CONTAINER, x + 77, y + 2, 9, 9);
        blit(graphics, HEART_VEHICLE_FULL, x + 77, y + 2, 9, 9);
        centeredOutlined(graphics, mc.font, current + "/" + max, x + MOUNT_TRACK_X + MOUNT_TRACK_W / 2.0F, y + PLATE_TRACK_Y,
                WHITE, MOUNT_TRACK_W - 2);
        mc.gui.rightHeight += PLATE_H + 1;
    }

    // ---------------------------------------------------------------- 当前法术（副手栏外侧）

    /**
     * 选中的法术画在副手栏外侧，框用原版副手栏的贴图，图标带冷却遮罩、法力不足变灰。
     * 位置按"副手栏在"算，副手空着时那一格空着，拿起 / 放下东西时法术框不跳；左手习惯时副手栏在右边，法术框跟着到右边外侧。
     * 别的模组在这儿画了东西时（{@link HudAvoidance}，如 InventoryHUD+ 的箭数格）再往外让开一点；它那格随手里的东西变时，法术框跟着动。
     * 只在手持施法物品（{@code #can_cast_spell}，任一只手）时画；旁观、隐藏 GUI、身上没有法术时不画。
     */
    private static void renderSelectedSpell(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !(mc.getCameraEntity() instanceof Player player) || player.isSpectator()) {
            return;
        }
        if (!player.getMainHandItem().is(TagRegistry.CAN_CAST_SPELL) && !player.getOffhandItem().is(TagRegistry.CAN_CAST_SPELL)) {
            return;
        }
        SpellEntry entry = PlayerSpells.getSelectedSpell(player);
        if (entry == null) {
            return;
        }
        int cx = graphics.guiWidth() / 2;
        int bottom = graphics.guiHeight();
        boolean offhandLeft = player.getMainArm() == HumanoidArm.RIGHT;
        // 原版副手栏贴图 29×24：格子 22×22（贴图里 y 1 ~ 23），另 7 像素是和快捷栏之间的空隙（左副手栏空隙在右，右副手栏在左）
        int slotX = offhandLeft ? cx - 91 - 29 - 29 : cx + 91 + 29 + 7;
        // 别的模组画在这儿的东西（InventoryHUD+ 的箭数格之类）压到格子时，往外（离开快捷栏的方向）让开
        slotX = HudAvoidance.pushOut(player, graphics.guiWidth(), bottom, slotX, bottom - 22, SPELL_SLOT, SPELL_SLOT, offhandLeft);
        blit(graphics, offhandLeft ? OFFHAND_LEFT : OFFHAND_RIGHT, offhandLeft ? slotX : slotX - 7, bottom - 23, 29, 24);
        SpellIcons.draw(graphics, player, entry.value(), slotX + 3, bottom - 19, ManaApi.canAfford(player, entry.value().manaCost()));
    }

    /** ≥ 1000 写成 2.0k，≥ 1 000 000 写成 1.2M；整数写整数，有小数保留一位。 */
    static String format(double value) {
        if (value >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
        }
        if (value >= 1000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000);
        }
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.05 ? String.valueOf(rounded) : String.format(Locale.ROOT, "%.1f", value);
    }

    // ---------------------------------------------------------------- 吟唱条、蓝条

    /**
     * 按原版的堆叠规则摆在生命条 / 饥饿条（坐骑条）上方：原版每行 9 高、顶在 {@code H - leftHeight}，画完加 10。
     * 条 15 高，底边和一行 9 高的底边齐平（顶比一行高 6），画完让出条高 + 1（蓝条再加宝石的 3）。
     */
    private static void renderBars(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof Player player)) {
            return;
        }
        Font font = mc.font;
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        int cx = graphics.guiWidth() / 2;
        int bottom = graphics.guiHeight();

        int castY = bottom - mc.gui.leftHeight - (BAR_H - 9);
        if (renderCastBar(graphics, font, cx - 91, castY, partial)) {
            mc.gui.leftHeight += BAR_H + 1;
        }
        int manaY = bottom - mc.gui.rightHeight - (BAR_H - 9);
        if (renderManaBar(graphics, font, player, cx + 2, manaY, partial)) {
            mc.gui.rightHeight += BAR_H + 1 + GEM_RISE;
        }
    }

    private static boolean renderManaBar(GuiGraphicsExtractor graphics, Font font, Player player, int x, int y, float partial) {
        float max = ManaApi.getMaxMana(player);
        if (max <= 0.0F) {
            return false;
        }
        float mana = ManaApi.getMana(player);
        float alpha;
        if (ClientConfig.get(ClientConfig.ALWAYS_SHOW_MANA_BAR) || mana < max) {
            alpha = 1.0F;
            manaLastNotFull = ticks;
        } else {
            // 回满后停 3 秒再淡出
            float since = ticks - manaLastNotFull + partial;
            alpha = since < MANA_HOLD_TICKS ? 1.0F : 1.0F - (since - MANA_HOLD_TICKS) / MANA_FADE_TICKS;
        }
        if (alpha <= 0.0F) {
            return false;
        }
        drawBar(graphics, font, x, y, HudSprites.BAR_MANA, mana / max, format(Mth.floor(mana)) + "/" + format(max), alpha);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HudSprites.BAR_GEM, x + BAR_W / 2 - 3, y - GEM_RISE, 7, 7, ARGB.white(alpha));
        return true;
    }

    private static boolean renderCastBar(GuiGraphicsExtractor graphics, Font font, int x, int y, float partial) {
        if (CastBar.result != null) {
            float since = ticks - CastBar.resultTick + partial;
            if (since >= RESULT_HOLD_TICKS + RESULT_FADE_TICKS) {
                CastBar.clearResult();
                return false;
            }
            float alpha = since < RESULT_HOLD_TICKS ? 1.0F : 1.0F - (since - RESULT_HOLD_TICKS) / RESULT_FADE_TICKS;
            Identifier fill = switch (CastBar.result) {
                case COMPLETED -> HudSprites.BAR_RESULT_COMPLETE;
                case INTERRUPTED -> HudSprites.BAR_RESULT_INTERRUPTED;
                case CANCELLED -> HudSprites.BAR_RESULT_CANCELLED;
                case OUT_OF_MANA -> HudSprites.BAR_RESULT_OUT_OF_MANA;
            };
            drawBar(graphics, font, x, y, fill, CastBar.resultProgress, null, alpha);
            return true;
        }
        CastType kind = CastBar.kind;
        if (kind == null) {
            return false;
        }
        float progress = CastBar.progress(ticks, partial);
        switch (kind) {
            case CAST -> {
                float seconds = Math.max(0.0F, CastBar.totalTicks - CastBar.elapsed(ticks, partial)) / 20.0F;
                drawBar(graphics, font, x, y, HudSprites.BAR_CAST, progress, seconds(seconds), 1.0F);
            }
            case CHARGE -> {
                boolean full = progress >= 1.0F;
                drawBar(graphics, font, x, y, full ? HudSprites.BAR_CHARGE_FULL : HudSprites.BAR_CHARGE, progress,
                        Math.round(progress * 100) + "%", 1.0F);
                int marker = x + TRACK_X + Math.round(TRACK_W * CastBar.minCharge);
                graphics.fill(marker, y + TRACK_Y, marker + 1, y + TRACK_Y + TRACK_H, WHITE);
                if (full && ticks / 3 % 2 == 0) {
                    blit(graphics, HudSprites.BAR_CHARGE_GLOW, x - 1, y - 1, BAR_W + 2, BAR_H + 2);
                }
            }
            case CHANNEL -> {
                if (CastBar.channelRemaining == null) {
                    // 算不出剩余时间（不耗蓝）：满条 + 来回滑的流光
                    drawBar(graphics, font, x, y, HudSprites.BAR_CHANNEL, 1.0F, null, 1.0F);
                    int travel = TRACK_W - 12;
                    int step = (int) ((ticks + partial) * 2) % (travel * 2);
                    int offset = step < travel ? step : travel * 2 - step;
                    blit(graphics, HudSprites.BAR_SHIMMER, x + TRACK_X + offset, y + TRACK_Y, 12, TRACK_H);
                } else {
                    float seconds = Math.max(0, CastBar.channelRemaining.getAsInt()) / 20.0F;
                    drawBar(graphics, font, x, y, HudSprites.BAR_CHANNEL, progress, seconds(seconds), 1.0F);
                }
            }
        }
        return true;
    }

    private static String seconds(float seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    /** 底（钢框 + 槽）→ 按进度截宽度的填充（只有槽里有像素）→ 描边字。 */
    private static void drawBar(GuiGraphicsExtractor graphics, Font font, int x, int y, Identifier fill, float progress,
                                @Nullable String label, float alpha) {
        int color = ARGB.white(alpha);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HudSprites.BAR_BACKGROUND, x, y, BAR_W, BAR_H, color);
        int width = TRACK_X + Math.round(TRACK_W * Mth.clamp(progress, 0.0F, 1.0F));
        if (width > TRACK_X) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fill, BAR_W, BAR_H, 0, 0, x, y, width, BAR_H, color);
        }
        if (label != null && alpha > 0.1F) {
            centeredOutlined(graphics, font, label, x + BAR_W / 2.0F, y + TRACK_Y, ARGB.color(Math.round(alpha * 255), 0xFFFFFF), TRACK_W - 2);
        }
    }

    // ---------------------------------------------------------------- 等级数字

    private static void renderLevel(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !mc.gameMode.hasExperience() || mc.player.experienceLevel <= 0) {
            return;
        }
        Font font = mc.font;
        Component text = Component.translatable("gui.experience.level", mc.player.experienceLevel);
        int x = (graphics.guiWidth() - font.width(text)) / 2;
        int y = graphics.guiHeight() - 30;   // 7 像素高的数字正好压在经验条（H-29 ~ H-25）中间
        graphics.text(font, text, x + 1, y, 0xFF000000, false);
        graphics.text(font, text, x - 1, y, 0xFF000000, false);
        graphics.text(font, text, x, y + 1, 0xFF000000, false);
        graphics.text(font, text, x, y - 1, 0xFF000000, false);
        graphics.text(font, text, x, y, LEVEL_GREEN, false);
    }

    // ---------------------------------------------------------------- 工具

    private static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
    }

    /** 居中的黑描边字；比 {@code maxWidth} 宽时换 0.5 倍字号。 */
    private static void centeredOutlined(GuiGraphicsExtractor graphics, Font font, String text, float centerX, int y, int color, int maxWidth) {
        int width = font.width(text);
        int outline = color & 0xFF000000;
        if (width <= maxWidth) {
            outlined(graphics, font, text, Math.round(centerX - width / 2.0F), y, color, outline);
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centerX, y + 2);
        pose.scale(0.5F, 0.5F);
        outlined(graphics, font, text, -width / 2, 0, color, outline);
        pose.popMatrix();
    }

    private static void outlined(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, int outline) {
        graphics.text(font, text, x + 1, y, outline, false);
        graphics.text(font, text, x - 1, y, outline, false);
        graphics.text(font, text, x, y + 1, outline, false);
        graphics.text(font, text, x, y - 1, outline, false);
        graphics.text(font, text, x, y, color, false);
    }
}
