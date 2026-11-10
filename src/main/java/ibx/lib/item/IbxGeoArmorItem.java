package ibx.lib.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.util.GeckoLibUtil;
import ibx.lib.client.renderer.IbxGeoArmorRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Ibx 3D 盔甲：穿上的样子由 GeckoLib 画的盔甲件。护甲、耐久、穿戴部位这些照常由注册时的物品属性给（{@code humanoidArmor}），
 * 这个类只管「穿上长什么样」。要用它必须装 GeckoLib。
 *
 * <p>一套盔甲的几件传同一个模型 id，共用一个模型、一张贴图，资源路径和骨骼名见 {@link IbxGeoArmorRenderer}。
 * 默认没有动画；要动画的套装写子类重写 {@link #registerControllers}，要换画法的重写 {@link #createGeoRenderer}。</p>
 */
public class IbxGeoArmorItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Identifier model;
    private final boolean glow;

    /** 没有自发光贴图的盔甲。 */
    public IbxGeoArmorItem(Properties properties, Identifier model) {
        this(properties, model, false);
    }

    /**
     * @param properties 注册时给好材质、部位的属性
     * @param model      模型 id，一套的几件传同一个
     * @param glow       有没有自发光贴图（{@code textures/armor/<名字>_glowmask.png}）
     */
    public IbxGeoArmorItem(Properties properties, Identifier model, boolean glow) {
        super(properties);
        this.model = model;
        this.glow = glow;
    }

    /** 这件盔甲用的模型 id。 */
    public Identifier model() {
        return this.model;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private @Nullable GeoArmorRenderer<?, ?> renderer;

            @Override
            public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack itemStack, EquipmentSlot equipmentSlot) {
                if (this.renderer == null) {
                    this.renderer = new IbxGeoArmorRenderer<>(IbxGeoArmorItem.this.model, IbxGeoArmorItem.this.glow);
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
