package ibx.lib.client.renderer;

import com.geckolib.animatable.GeoItem;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedGeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import ibx.lib.compat.playeranim.PlayerAnimFirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

/**
 * GeckoLib 3D 盔甲的通用渲染器：一套盔甲的几件共用一个模型、一张贴图，按部位只画自己的骨骼（骨骼名同 GeckoLib 盔甲模板：
 * armorHead、armorBody、armorRightArm、armorLeftArm、armorRightLeg、armorLeftLeg、armorRightBoot、armorLeftBoot）。
 *
 * <ul>
 *   <li>资源按模型 id {@code <命名空间>:<名字>} 找：模型 {@code geckolib/models/armor/<名字>.geo.json}、
 *       贴图 {@code textures/armor/<名字>.png}；开了自发光时再找 {@code textures/armor/<名字>_glowmask.png}。</li>
 *   <li>第一人称：Player Animation Library 播第一人称动作时会把玩家自己的身体临时画出来，它只会藏原版盔甲层，
 *       GeckoLib 盔甲不走那条路（头盔会糊在镜头上）。这里照它对原版盔甲的规矩处理，见 {@link #firstPersonSegments}。</li>
 * </ul>
 */
public class IbxGeoArmorRenderer<T extends Item & GeoItem, R extends HumanoidRenderState & GeoRenderState> extends GeoArmorRenderer<T, R> {
    /** 穿着这件盔甲的是不是镜头跟着的那个实体：只有它可能被当作第一人称那一遍来画。 */
    private static final DataTicket<Boolean> WEARER_IS_CAMERA = DataTicket.create("ibx_lib_armor_wearer_is_camera", Boolean.class);

    /** 没有自发光贴图的盔甲。 */
    public IbxGeoArmorRenderer(Identifier model) {
        this(model, false);
    }

    /**
     * @param model 模型 id，见类说明
     * @param glow  有没有自发光贴图（{@code _glowmask}）；没有这张图却传 true 会画出缺失贴图
     */
    public IbxGeoArmorRenderer(Identifier model, boolean glow) {
        super(new DefaultedGeoModel<>(model) {
            @Override
            protected String subtype() {
                return "armor";
            }
        });
        if (glow) {
            this.withRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    @Override
    public void addRenderData(T animatable, @Nullable RenderData renderData, R renderState, float partialTick) {
        renderState.addGeckolibData(WEARER_IS_CAMERA, renderData != null && renderData.entity() == Minecraft.getInstance().getCameraEntity());
    }

    @Override
    public List<ArmorSegment> getSegmentsForSlot(R renderState, EquipmentSlot slot) {
        List<ArmorSegment> segments = super.getSegmentsForSlot(renderState, slot);
        EntityRenderState wearer = Boolean.TRUE.equals(renderState.getGeckolibData(WEARER_IS_CAMERA)) ? this.findWearer(renderState, slot) : null;
        return wearer != null && PlayerAnimFirstPerson.isFirstPersonPass(wearer) ? this.firstPersonSegments(wearer, slot, segments) : segments;
    }

    /**
     * 第一人称那一遍里这件盔甲画哪些部位。默认同 Player Animation Library 对原版盔甲的处理：头盔、护腿、靴子不画；
     * 胸甲不画身体，手臂只有动作打开了「第一人称显示盔甲」并且显示那只手时才画。
     *
     * @param wearer   穿戴者（镜头实体）的渲染状态
     * @param segments 平时这个部位画的骨骼
     */
    protected List<ArmorSegment> firstPersonSegments(EntityRenderState wearer, EquipmentSlot slot, List<ArmorSegment> segments) {
        if (slot != EquipmentSlot.CHEST) {
            return List.of();
        }
        return segments.stream()
                .filter(segment -> segment == ArmorSegment.RIGHT_ARM && PlayerAnimFirstPerson.showsArmArmor(wearer, HumanoidArm.RIGHT)
                        || segment == ArmorSegment.LEFT_ARM && PlayerAnimFirstPerson.showsArmArmor(wearer, HumanoidArm.LEFT))
                .toList();
    }

    /**
     * 这份逐部位的渲染状态属于本帧世界里的哪个实体。GeckoLib 给每个部位单独备了一份状态、渲染器拿不到实体自己的那份，
     * 只能回头到本帧要画的实体里找；找不到（比如物品栏里的纸娃娃）返回 null。
     */
    private @Nullable EntityRenderState findWearer(R renderState, EquipmentSlot slot) {
        for (EntityRenderState entity : Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.entityRenderStates) {
            if (entity instanceof GeoRenderState geo) {
                EnumMap<EquipmentSlot, ? extends HumanoidRenderState> perSlot = geo.getGeckolibData(DataTickets.PER_SLOT_RENDER_DATA);
                if (perSlot != null && perSlot.get(slot) == renderState) {
                    return entity;
                }
            }
        }
        return null;
    }
}
