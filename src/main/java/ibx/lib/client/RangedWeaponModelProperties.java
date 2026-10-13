package ibx.lib.client;

import com.mojang.serialization.MapCodec;
import ibx.lib.IbxLib;
import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.IbxBowItem;
import ibx.lib.item.IbxCrossbowItem;
import ibx.lib.item.vanilla.VanillaRangedWeapons;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Ibx 弓弩的物品模型属性（拉弓动画）。
 *
 * <p>26.1 的物品模型由 {@code assets/<命名空间>/items/<物品名>.json} 按"属性"挑选，旧版那套按物品实例
 * 注册的模型谓词（ItemProperties）已被移除。原版弓的拉弓阶段用的是 {@code minecraft:use_duration}
 * 配 {@code scale: 0.05}，等于写死 20 tick 拉满，所以这里注册 {@link #PULL}：
 * 已拉弓 tick ÷ 满蓄 tick（draw_time ÷ draw_speed 算出），让拉弓阶段跟着自定义拉弓时间走。
 * 属性按 id 全局生效，{@link IbxBowItem} / {@link IbxCrossbowItem} 及其子类（含其他模组注册的）都能直接用。</p>
 *
 * <p>模型定义写法：</p>
 * <ul>
 *   <li>弓：照抄原版 {@code items/bow.json}，把 {@code "property": "minecraft:use_duration"} 与
 *       {@code "scale": 0.05} 换成 {@code "property": "ibx_lib:pull"}，阈值 0.65 / 0.9 不变。</li>
 *   <li>弩：可直接照抄原版 {@code items/crossbow.json}。原版的 {@code minecraft:crossbow/pull} 读的是
 *       {@code CrossbowItem.getChargeDuration}，已由 {@code mixin.CrossbowItemMixin}
 *       对 Ibx 弩改成属性时长；想统一写法也可以换成本属性，结果相同。</li>
 * </ul>
 *
 * <p>原版弓：本模组的 {@code assets/minecraft/items/bow.json} 按上面的写法覆盖了原版定义，让被接管的原版弓
 * 也跟着属性走；关掉接管时本属性对原版弓按原版的 20 tick 计算，表现与原版一致。
 * 原版弩不用覆盖，它的 {@code crossbow/pull} 同样由 {@code mixin.CrossbowItemMixin} 改好了。</p>
 */
public final class RangedWeaponModelProperties {
    private RangedWeaponModelProperties() {}

    /** 拉弓进度属性的 id，模型定义里写 {@code "property": "ibx_lib:pull"}。 */
    public static final Identifier PULL = Identifier.fromNamespaceAndPath(IbxLib.MODID, "pull");

    static void onRegisterRangeSelectProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(PULL, Pull.MAP_CODEC);
    }

    /**
     * 拉弓进度：已拉弓 tick ÷ 满蓄 tick，拉满为 1（继续按住会超过 1，同原版）。
     * 弓只有正在拉的那把才有值；弩装填完毕后归 0（同原版 {@code crossbow/pull}）。
     */
    public record Pull() implements RangeSelectItemModelProperty {
        public static final MapCodec<Pull> MAP_CODEC = MapCodec.unit(new Pull());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            LivingEntity entity = owner == null ? null : owner.asLivingEntity();
            if (entity == null) {
                return 0.0F;
            }
            boolean idle = stack.getItem() instanceof IbxCrossbowItem
                    ? CrossbowItem.isCharged(stack)
                    : entity.getUseItem() != stack;
            if (idle) {
                return 0.0F;
            }
            float ticks = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
            if (stack.is(Items.BOW) && !VanillaRangedWeapons.isEnabled()) {
                // 原版弓的模型已改用本属性；关掉接管时它没有拉弦时间修饰符，退回原版的 20 tick 刻度。
                return ticks / (float) BowItem.MAX_DRAW_DURATION;
            }
            return ticks / (float) RangedWeaponApi.getFullDrawTicks(entity, stack);
        }

        @Override
        public MapCodec<Pull> type() {
            return MAP_CODEC;
        }
    }
}
