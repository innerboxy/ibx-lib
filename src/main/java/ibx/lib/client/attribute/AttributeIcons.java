package ibx.lib.client.attribute;

import ibx.lib.IbxLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 属性面板每行前面的 9×9 图标：按属性 id 的路径部分找同名贴图 {@code ibx_lib:hud/icon/<路径>}
 * （文件 {@code assets/ibx_lib/textures/gui/sprites/hud/icon/<路径>.png}），找不到画 {@code hud/icon/unknown}。
 * 不看命名空间，所以别的模组也能在自己的资源里放同路径的文件给自己的属性配图标。资源重载时重新找。
 */
public final class AttributeIcons {
    private AttributeIcons() {}

    public static final int SIZE = 9;
    private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath(IbxLib.MODID, "hud/icon/unknown");
    private static final Map<Identifier, Identifier> CACHE = new HashMap<>();

    public static void draw(GuiGraphicsExtractor g, Holder<Attribute> attribute, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(attribute), x, y, SIZE, SIZE);
    }

    private static Identifier sprite(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(key -> CACHE.computeIfAbsent(key.identifier(), id -> {
            Identifier sprite = Identifier.fromNamespaceAndPath(IbxLib.MODID, "hud/icon/" + id.getPath());
            Identifier texture = sprite.withPath(path -> "textures/gui/sprites/" + path + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent() ? sprite : UNKNOWN;
        })).orElse(UNKNOWN);
    }

    public static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(IbxLib.MODID, "attribute_icons"),
                (ResourceManagerReloadListener) resourceManager -> CACHE.clear());
    }
}
