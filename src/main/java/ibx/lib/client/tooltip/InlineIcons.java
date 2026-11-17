package ibx.lib.client.tooltip;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ibx.lib.IbxLib;
import ibx.lib.client.attribute.AttributeIcons;
import ibx.lib.spell.Spell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.PlainTextRenderable;
import net.minecraft.client.gui.font.SingleSpriteSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写进文字里的小图标（物品说明里属性、效果、法术、物品名前面那个），用原版的「文字里嵌图集贴图」（{@code Component.object}）。
 * 只在客户端用；{@code TooltipLines} 的对应方法在客户端会自动带上。
 *
 * <table>
 *   <tr><th>图标</th><th>贴图</th><th>画多大</th></tr>
 *   <tr><td>属性</td><td>gui 图集 {@code hud/icon/<属性路径>}（同属性面板，9×9）</td><td>原尺寸 9×9</td></tr>
 *   <tr><td>药水效果</td><td>gui 图集 {@code mob_effect/<效果路径>}（原版效果图标，18×18）</td><td>缩一半 9×9</td></tr>
 *   <tr><td>法术</td><td>gui 图集 {@code spell/<法术路径>}（法术图标 16×16，lib 的 {@code atlases/gui.json} 把 {@code textures/spell} 加进 gui 图集）</td><td>缩一半 8×8</td></tr>
 *   <tr><td>物品</td><td>items 图集 {@code item/<物品路径>}，没有再找 blocks 图集 {@code block/<物品路径>}</td><td>缩一半 8×8</td></tr>
 * </table>
 *
 * <p>原版嵌贴图一律画 8×8、紧挨着下一个字；上面这几类路径（{@code hud/icon/}、{@code mob_effect/}、{@code spell/}、{@code item/}、
 * {@code block/}）改成按整数倍缩小（GUI 缩放下像素齐整）、后面空 1 像素，由 {@code mixin.AtlasGlyphProviderMixin} 接进去
 * （{@link #applies} / {@link #wrap}）。图标底边和文字底边对齐。</p>
 *
 * <p>图标固定白色、不带阴影，不跟着所在那行的颜色染色。效果、物品找不到贴图时返回 null（不画图标）；法术找不到用占位图标。</p>
 *
 * <p>每个图标带一段 fallback：取纯文本时（JEI 按说明搜索、复述）顶替图标的字，不要就传 {@code Component.empty()}；
 * 原版不给 fallback 时纯文本里是 {@code [贴图路径@图集]}。fallback 的字同时记在图标样式的 insertion 上，
 * 自动换行把一行拆开重建时靠它把图标原样还原（{@link #restore}）。</p>
 *
 * <p>物品说明可能被别的模组在后台线程里取（建搜索索引），所以贴图缓存用并发的表。</p>
 */
public final class InlineIcons {
    private InlineIcons() {}

    /** 文字里图标的最大边长：比这大的按整数倍缩到不超过它。 */
    private static final int MAX_SIZE = 9;
    private static final String[] PREFIXES = {"hud/icon/", "mob_effect/", "spell/", "item/", "block/"};
    private static final Identifier SPELL_MISSING = Identifier.fromNamespaceAndPath(IbxLib.MODID, "spell/missing");
    /** 嵌贴图的片段按带样式的方式读出来时的那个占位字符（原版 {@code ObjectContents}）。 */
    private static final char PLACEHOLDER = '\ufffc';

    /** 贴图 id -> 有没有这张贴图（gui / items / blocks 图集里的）；资源重载时清掉。 */
    private static final Map<Identifier, Boolean> EXISTS = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- 图标

    /** 属性图标（同属性面板，找不到画 unknown）。 */
    public static Component attribute(Holder<Attribute> attribute, Component fallback) {
        return icon(AtlasIds.GUI, AttributeIcons.sprite(attribute), fallback);
    }

    /** 药水效果图标；没有图标贴图返回 null。 */
    public static @Nullable Component effect(Holder<MobEffect> effect, Component fallback) {
        return effect.unwrapKey()
                .map(key -> key.identifier().withPrefix("mob_effect/"))
                .filter(sprite -> exists(sprite, "textures/"))
                .map(sprite -> icon(AtlasIds.GUI, sprite, fallback))
                .orElse(null);
    }

    /** 法术图标；没有图标的用占位图标。 */
    public static Component spell(Spell spell, Component fallback) {
        Identifier id = spell.id();
        Identifier sprite = id == null ? SPELL_MISSING : id.withPrefix("spell/");
        return icon(AtlasIds.GUI, exists(sprite, "textures/") ? sprite : SPELL_MISSING, fallback);
    }

    /** 物品图标：物品贴图 {@code item/<路径>}，没有再找方块贴图 {@code block/<路径>}；都没有（3D 模型、贴图名和 id 对不上的）返回 null。 */
    public static @Nullable Component item(ItemStack stack, Component fallback) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Identifier item = id.withPrefix("item/");
        if (exists(item, "textures/")) {
            return icon(AtlasIds.ITEMS, item, fallback);
        }
        Identifier block = id.withPrefix("block/");
        return exists(block, "textures/") ? icon(AtlasIds.BLOCKS, block, fallback) : null;
    }

    private static Component icon(Identifier atlas, Identifier sprite, Component fallback) {
        String plain = fallback.getString();
        return Component.object(new AtlasSprite(atlas, sprite), Component.literal(plain))
                .withStyle(style -> style.withColor(0xFFFFFF).withoutShadow().withInsertion(plain));
    }

    /**
     * 自动换行重建一行时用：按带样式的方式读出来的这一小段（style + text）如果是嵌的贴图，还原成贴图片段，不是就返回 null。
     * 本类做的图标带回它的 fallback（记在 insertion 上）；别处嵌的贴图没有 insertion，纯文本照原版的默认写法。
     */
    public static @Nullable Component restore(Style style, String text) {
        if (text.isEmpty() || !(style.getFont() instanceof FontDescription.AtlasSprite(Identifier atlas, Identifier sprite))) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != PLACEHOLDER) {
                return null;
            }
        }
        AtlasSprite info = new AtlasSprite(atlas, sprite);
        String plain = style.getInsertion();
        MutableComponent restored = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            restored.append((plain == null ? Component.object(info) : Component.object(info, Component.literal(plain))).setStyle(style));
        }
        return restored;
    }

    private static boolean exists(Identifier sprite, String folder) {
        return EXISTS.computeIfAbsent(sprite, s -> Minecraft.getInstance().getResourceManager()
                .getResource(s.withPath(path -> folder + path + ".png")).isPresent());
    }

    public static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(IbxLib.MODID, "inline_icons"),
                (ResourceManagerReloadListener) resourceManager -> EXISTS.clear());
    }

    // ---------------------------------------------------------------- 字形（mixin 用）

    /** 这张贴图写进文字里时要不要换画法（整数倍缩小 + 后面空 1 像素）。 */
    public static boolean applies(TextureAtlasSprite sprite) {
        String path = sprite.contents().name().getPath();
        for (String prefix : PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 把原版做好的字形换掉：宽高按整数倍缩到不超过 {@link #MAX_SIZE}，占位宽度 = 宽 + 1。 */
    public static GlyphSource wrap(GlyphSource vanilla, TextureAtlasSprite sprite) {
        BakedGlyph base = vanilla.getGlyph(0);
        int w = sprite.contents().width();
        int h = sprite.contents().height();
        int scale = Math.max(1, (Math.max(w, h) + MAX_SIZE - 1) / MAX_SIZE);
        float width = (float) w / scale;
        float height = (float) h / scale;
        GlyphInfo info = GlyphInfo.simple(width + 1.0F);
        return new SingleSpriteSource(new BakedGlyph() {
            @Override
            public GlyphInfo info() {
                return info;
            }

            @Override
            public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
                TextRenderable.Styled glyph = base.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset);
                return glyph == null ? null : new Instance(glyph, sprite, x, y, color, shadowColor, shadowOffset, width, height);
            }
        });
    }

    /** 原版的贴图字形换个宽高；渲染类型、贴图、样式都用原版那个的。底边和文字底边对齐。 */
    private record Instance(TextRenderable.Styled base, TextureAtlasSprite sprite, float x, float y, int color, int shadowColor,
                            float shadowOffset, float width, float height) implements PlainTextRenderable {
        @Override
        public float ascent() {
            return this.height - 1.0F;
        }

        @Override
        public void renderSprite(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, float offsetX, float offsetY, float z, int color) {
            float x0 = offsetX + this.left();
            float x1 = offsetX + this.right();
            float y0 = offsetY + this.top();
            float y1 = offsetY + this.bottom();
            buffer.addVertex(pose, x0, y0, z).setUv(this.sprite.getU0(), this.sprite.getV0()).setColor(color).setLight(packedLightCoords);
            buffer.addVertex(pose, x0, y1, z).setUv(this.sprite.getU0(), this.sprite.getV1()).setColor(color).setLight(packedLightCoords);
            buffer.addVertex(pose, x1, y1, z).setUv(this.sprite.getU1(), this.sprite.getV1()).setColor(color).setLight(packedLightCoords);
            buffer.addVertex(pose, x1, y0, z).setUv(this.sprite.getU1(), this.sprite.getV0()).setColor(color).setLight(packedLightCoords);
        }

        @Override
        public RenderType renderType(Font.DisplayMode displayMode, boolean blur) {
            return this.base.renderType(displayMode, blur);
        }

        @Deprecated
        @Override
        public RenderType renderType(Font.DisplayMode displayMode) {
            return this.base.renderType(displayMode);
        }

        @Override
        public GpuTextureView textureView() {
            return this.base.textureView();
        }

        @Override
        public RenderPipeline guiPipeline() {
            return this.base.guiPipeline();
        }

        @Override
        public Style style() {
            return this.base.style();
        }
    }
}
