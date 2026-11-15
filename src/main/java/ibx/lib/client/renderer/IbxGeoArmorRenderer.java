package ibx.lib.client.renderer;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedGeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import ibx.lib.compat.playeranim.PlayerAnimFirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
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
 *   <li>会摆的披风：模型里名字以 {@link #SWAY_PREFIX} 开头的骨骼当作垂着的布，穿在玩家身上时跟着原版披风的摆动量转
 *       （跑起来往后扬、转身往一边偏、走路时轻抖），见 {@link #swayBone}。几段披风做成一根套一根的骨骼链，轴心放在各自的上沿。
 *       穿在玩家以外的生物身上不动。</li>
 * </ul>
 */
public class IbxGeoArmorRenderer<T extends Item & GeoItem, R extends HumanoidRenderState & GeoRenderState> extends GeoArmorRenderer<T, R> {
    /** 会摆的骨骼的名字前缀。 */
    public static final String SWAY_PREFIX = "sway_";

    /** 穿着这件盔甲的是不是镜头跟着的那个实体：只有它可能被当作第一人称那一遍来画。 */
    private static final DataTicket<Boolean> WEARER_IS_CAMERA = DataTicket.create("ibx_lib_armor_wearer_is_camera", Boolean.class);
    /** 穿戴者这一帧的披风摆动量；穿戴者不是玩家时是 {@link CapeSway#STILL}。 */
    private static final DataTicket<CapeSway> CAPE_SWAY = DataTicket.create("ibx_lib_armor_cape_sway", CapeSway.class);

    /**
     * 披风的摆动量，三个数的含义和范围同原版玩家披风（{@code AvatarRenderState} 的 capeLean / capeLean2 / capeFlap），单位是度。
     *
     * @param lean 往后扬起多少（0~150）：身体往前走、披风落在后面
     * @param side 往左右偏多少（-20~20）：正数偏向穿戴者的右手边
     * @param flap 上下抖多少：下落时扬起、走路时随步子轻抖
     */
    public record CapeSway(float lean, float side, float flap) {
        public static final CapeSway STILL = new CapeSway(0, 0, 0);

        /** 这个生物这一帧的摆动量：只有玩家（带原版披风状态的）有，算法照搬原版披风；其余生物不动。 */
        public static CapeSway of(LivingEntity entity, float partialTick) {
            if (!(entity instanceof ClientAvatarEntity avatar)) {
                return STILL;
            }
            ClientAvatarState cloak = avatar.avatarState();
            double deltaX = cloak.getInterpolatedCloakX(partialTick) - Mth.lerp((double) partialTick, entity.xo, entity.getX());
            double deltaY = cloak.getInterpolatedCloakY(partialTick) - Mth.lerp((double) partialTick, entity.yo, entity.getY());
            double deltaZ = cloak.getInterpolatedCloakZ(partialTick) - Mth.lerp((double) partialTick, entity.zo, entity.getZ());
            float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
            double backX = Mth.sin(bodyYaw);
            double backZ = -Mth.cos(bodyYaw);
            float flying = entity.getFallFlyingTicks() + partialTick;
            float flyingScale = Mth.clamp(flying * flying / 100.0F, 0.0F, 1.0F);
            float lean = Mth.clamp((float) (deltaX * backX + deltaZ * backZ) * 100.0F * (1.0F - flyingScale), 0.0F, 150.0F);
            float side = Mth.clamp((float) (deltaX * backZ - deltaZ * backX) * 100.0F, -20.0F, 20.0F);
            float flap = Mth.clamp((float) deltaY * 10.0F, -6.0F, 32.0F)
                    + Mth.sin(cloak.getInterpolatedWalkDistance(partialTick) * 6.0F) * 32.0F * cloak.getInterpolatedBob(partialTick);
            return new CapeSway(lean, side, flap);
        }
    }

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
        renderState.addGeckolibData(CAPE_SWAY, renderData != null ? CapeSway.of(renderData.entity(), partialTick) : CapeSway.STILL);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        R renderState = renderPassInfo.renderState();
        CapeSway sway = renderState.getGeckolibData(CAPE_SWAY);
        EquipmentSlot slot = renderState.getGeckolibData(CURRENT_SLOT);
        if (sway == null || slot == null || sway.equals(CapeSway.STILL)) {
            return;
        }
        for (ArmorSegment segment : this.getSegmentsForSlot(renderState, slot)) {
            renderPassInfo.model().getBone(this.getBoneNameForSegment(renderState, segment))
                    .ifPresent(bone -> this.swayChildren(bone, 0, sway, snapshots));
        }
    }

    /** 在这根骨骼下面一层层找会摆的骨骼；depth 是它上面已经有几层会摆的骨骼。 */
    private void swayChildren(GeoBone bone, int depth, CapeSway sway, BoneSnapshots snapshots) {
        for (GeoBone child : bone.children()) {
            boolean sways = child.name().startsWith(SWAY_PREFIX);
            if (sways) {
                this.swayBone(snapshots.get(child), depth, sway);
            }
            this.swayChildren(child, sways ? depth + 1 : depth, sway, snapshots);
        }
    }

    /**
     * 把摆动量加到一根会摆的骨骼上（在模型自带的静止转角之上再转）。默认：整条链合起来和原版披风扬起一样多——
     * 最上面一段占六成，往下每段再加两成，所以越靠下摆得越开；左右偏只加在最上面一段。
     *
     * @param snapshot 这根骨骼这一帧的姿态，转角是弧度；X 负数是下沿往身后扬，Z 正数是下沿偏向穿戴者的右手边
     * @param depth    这根骨骼在链里排第几（最上面一段是 0）
     */
    protected void swayBone(BoneSnapshot snapshot, int depth, CapeSway sway) {
        float lift = (sway.lean() / 2.0F + sway.flap()) * (depth == 0 ? 0.6F : 0.2F);
        snapshot.setRotX(snapshot.getRotX() - lift * Mth.DEG_TO_RAD);
        if (depth == 0) {
            snapshot.setRotZ(snapshot.getRotZ() + sway.side() / 2.0F * Mth.DEG_TO_RAD);
        }
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
