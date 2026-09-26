package ibx.taverntales.lib.client;

import ibx.taverntales.lib.item.TtCrossbowItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jspecify.annotations.Nullable;

/**
 * 第三人称下手持已装填 Tt 弩时的"端弩"姿势。
 *
 * <p>26.1 的玩家渲染判断端弩姿势时写死了 {@code is(Items.CROSSBOW)}，模组弩装好箭后只会普通地拎着
 * （21.1 时 NeoForge 把这处改成了 {@code instanceof CrossbowItem}，旧版没有这个问题）。
 * 好在它之前会先问物品的 {@link IClientItemExtensions#getArmPose}，这里给 Tt 弩挂上：
 * 已装填且没在挥手时返回端弩姿势，其余情况返回 null 交还原版逻辑。</p>
 *
 * <p>同一物品只能注册一份客户端扩展，重复注册会直接报错。所以这里在<b>最低优先级</b>注册
 * （mod 总线事件按优先级分轮派发，其他模组的普通优先级监听都已跑完），并跳过已经有扩展的物品：
 * 使用方若给自己的 Tt 弩注册了扩展（比如自定义渲染），本类让路，端弩姿势请在自己的
 * {@code getArmPose} 里调 {@link #crossbowHold} 补上。</p>
 */
public final class CrossbowArmPose implements IClientItemExtensions {
    private static final CrossbowArmPose INSTANCE = new CrossbowArmPose();

    private CrossbowArmPose() {}

    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        Item[] crossbows = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof TtCrossbowItem && !event.isItemRegistered(item))
                .toArray(Item[]::new);
        if (crossbows.length > 0) {
            event.registerItem(INSTANCE, crossbows);
        }
    }

    /** 已装填且没在挥手时返回端弩姿势，否则 null（交给原版决定）。自己注册了客户端扩展的 Tt 弩可以直接调用。 */
    public static HumanoidModel.@Nullable ArmPose crossbowHold(LivingEntity entity, ItemStack stack) {
        return !entity.swinging && CrossbowItem.isCharged(stack) ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
    }

    @Override
    public HumanoidModel.@Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return crossbowHold(entity, stack);
    }
}
