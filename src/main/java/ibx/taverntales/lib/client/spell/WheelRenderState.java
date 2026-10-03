package ibx.taverntales.lib.client.spell;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * 轮盘底盘的一次绘制：把 {@link WheelShape} 缓存好的色段一次性交给 GUI 渲染器，每段一个四边形。
 * 色段有几千个，逐个 {@code graphics.fill} 每帧要建几千个对象，这里只建一个。
 *
 * @param x       形状左上角（GUI 像素）
 * @param overlay 指向那一格的选中框色段，没有为 null；画在底盘之后，盖在上面
 */
record WheelRenderState(Matrix3x2fc pose, int x, int y, int[] runs, int @Nullable [] overlay,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
    WheelRenderState(Matrix3x2fc pose, int x, int y, int size, int[] runs, int @Nullable [] overlay, @Nullable ScreenRectangle scissorArea) {
        this(pose, x, y, runs, overlay, scissorArea, bounds(pose, x, y, size, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        this.emit(consumer, this.runs);
        if (this.overlay != null) {
            this.emit(consumer, this.overlay);
        }
    }

    private void emit(VertexConsumer consumer, int[] runs) {
        for (int i = 0; i < runs.length; i += 4) {
            float x0 = this.x + runs[i];
            float x1 = this.x + runs[i + 1];
            float y0 = this.y + runs[i + 2];
            float y1 = y0 + 1;
            int color = runs[i + 3];
            consumer.addVertexWith2DPose(this.pose, x0, y0).setColor(color);
            consumer.addVertexWith2DPose(this.pose, x0, y1).setColor(color);
            consumer.addVertexWith2DPose(this.pose, x1, y1).setColor(color);
            consumer.addVertexWith2DPose(this.pose, x1, y0).setColor(color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    private static @Nullable ScreenRectangle bounds(Matrix3x2fc pose, int x, int y, int size, @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle bounds = new ScreenRectangle(x, y, size, size).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }
}
