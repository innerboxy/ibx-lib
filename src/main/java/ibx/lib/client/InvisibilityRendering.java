package ibx.lib.client;

import com.google.common.reflect.TypeToken;
import ibx.lib.config.Config;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * 隐身的生物连身上的装备和手里的物品也看不见。原版隐身只藏身体，盔甲、鞘翅、头上戴的南瓜 / 头颅和手里拿的东西照画，像飘在空中。
 *
 * <p>做法：抽取渲染状态之后，看不见这个生物时把状态里的这几样清空，画它们的那几层就什么都不画：</p>
 * <ul>
 *   <li>四个盔甲槽（{@link HumanoidRenderState}）：盔甲层和鞘翅层；GeckoLib 的 3D 盔甲也按这里的物品画，一起藏；</li>
 *   <li>头部物品（南瓜、头颅、旗帜等，{@link LivingEntityRenderState#headItem}）；</li>
 *   <li>两只手的物品（{@link ArmedEntityRenderState}：玩家、僵尸、骷髅等）；</li>
 *   <li>叼着 / 抱着的物品（{@link HoldingEntityRenderState}：村民、女巫、狐狸、熊猫、海豚）。</li>
 * </ul>
 *
 * <p>「看不见」按原版的 {@code isInvisibleToPlayer}：旁观者、开了「看见隐身队友」的队友本来就能看到半透明的身体，装备也照原版画。
 * 盔甲架不管：它的隐身是放置时的设置、不是隐身效果，地图里常拿隐身盔甲架摆展示的物品。第一人称手里的物品不受影响。</p>
 *
 * <p>服务端配置 {@code hideEquipmentWhenInvisible} 关掉时回到原版。读的是同步到客户端的值，由服务器统一决定。</p>
 */
public final class InvisibilityRendering {
    private InvisibilityRendering() {}

    /** MOD 总线：所有生物渲染器抽取状态之后执行。 */
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {}, (entity, state) -> {
            if (!state.isInvisibleToPlayer || entity instanceof ArmorStand || !Config.get(Config.HIDE_EQUIPMENT_WHEN_INVISIBLE)) return;
            hideEquipment(state);
        });
    }

    private static void hideEquipment(LivingEntityRenderState state) {
        state.headItem.clear();
        state.wornHeadType = null;
        state.wornHeadProfile = null;
        if (state instanceof ArmedEntityRenderState armed) {
            armed.rightHandItemState.clear();
            armed.leftHandItemState.clear();
            armed.rightHandItemStack = ItemStack.EMPTY;
            armed.leftHandItemStack = ItemStack.EMPTY;
        }
        if (state instanceof HumanoidRenderState humanoid) {
            humanoid.headEquipment = ItemStack.EMPTY;
            humanoid.chestEquipment = ItemStack.EMPTY;
            humanoid.legsEquipment = ItemStack.EMPTY;
            humanoid.feetEquipment = ItemStack.EMPTY;
        }
        if (state instanceof HoldingEntityRenderState holding) {
            holding.heldItem.clear();
        }
    }
}
