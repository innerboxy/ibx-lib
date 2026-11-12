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
 * 属性面板每行前面的 9×9 图标，按属性 id 找同名贴图，不用登记：
 * <ol>
 *   <li>先找属性自己命名空间下的 {@code <命名空间>:hud/icon/<路径>}
 *       （文件 {@code assets/<命名空间>/textures/gui/sprites/hud/icon/<路径>.png}）——别的模组给自己的属性配图标就放这里；</li>
 *   <li>没有再找本模组的 {@code ibx_lib:hud/icon/<路径>}（原版属性的图标都在这里）；</li>
 *   <li>都没有画 {@code ibx_lib:hud/icon/unknown}。</li>
 * </ol>
 * 资源重载时重新找。
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
            Identifier own = Identifier.fromNamespaceAndPath(id.getNamespace(), "hud/icon/" + id.getPath());
            if (exists(own)) {
                return own;
            }
            Identifier shared = Identifier.fromNamespaceAndPath(IbxLib.MODID, "hud/icon/" + id.getPath());
            return exists(shared) ? shared : UNKNOWN;
        })).orElse(UNKNOWN);
    }

    private static boolean exists(Identifier sprite) {
        Identifier texture = sprite.withPath(path -> "textures/gui/sprites/" + path + ".png");
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }

    public static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(IbxLib.MODID, "attribute_icons"),
                (ResourceManagerReloadListener) resourceManager -> CACHE.clear());
    }
}
