package ibx.lib.client.spell;

import ibx.lib.IbxLib;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellCooldowns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 法术图标。约定：<b>16×16</b>，放在 {@code assets/<命名空间>/textures/spell/<路径>.png}，按法术 id 推，注册时不用填；
 * 找不到时用占位图标 {@code ibx_lib:textures/spell/missing.png}。轮盘和 HUD 共用这里的画法。
 */
public final class SpellIcons {
    private SpellIcons() {}

    public static final int SIZE = 16;
    public static final Identifier MISSING = Identifier.fromNamespaceAndPath(IbxLib.MODID, "textures/spell/missing.png");
    private static final int COOLDOWN_OVERLAY = 0x7FFFFFFF;     // 同原版物品冷却
    private static final int NO_MANA_OVERLAY = 0xAA3C3C3C;      // 法力不足：压一层深灰

    private static final Map<Spell, Identifier> CACHE = new HashMap<>();

    /** 法术图标的贴图；资源重载时重新找。 */
    public static Identifier texture(Spell spell) {
        return CACHE.computeIfAbsent(spell, s -> {
            Identifier id = s.id();
            if (id == null) {
                return MISSING;
            }
            Identifier texture = id.withPath(path -> "textures/spell/" + path + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent() ? texture : MISSING;
        });
    }

    /**
     * 画一个图标：法力不足（{@code canAfford} 为 false）时压深灰，冷却中同原版物品冷却那样从下往上盖一层半透明白，
     * 剩得越多盖得越高。
     */
    public static void draw(GuiGraphicsExtractor graphics, Player player, Spell spell, int x, int y, boolean canAfford) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(spell), x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        if (!canAfford) {
            graphics.fill(x, y, x + SIZE, y + SIZE, NO_MANA_OVERLAY);
        }
        float cooldown = SpellCooldowns.getRemainingFraction(player, spell);
        if (cooldown > 0.0F) {
            int top = y + Mth.floor(SIZE * (1.0F - cooldown));
            graphics.fill(x, top, x + SIZE, top + Mth.ceil(SIZE * cooldown), COOLDOWN_OVERLAY);
        }
    }

    public static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(IbxLib.MODID, "spell_icons"),
                (ResourceManagerReloadListener) resourceManager -> CACHE.clear());
    }
}
