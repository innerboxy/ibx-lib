package ibx.taverntales.lib.client;

import com.mojang.serialization.MapCodec;
import ibx.taverntales.lib.TavernTalesLib;
import ibx.taverntales.lib.api.RangedWeaponApi;
import ibx.taverntales.lib.item.TtBowItem;
import ibx.taverntales.lib.item.TtCrossbowItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Tt 弓弩的物品模型属性（拉弓动画）。
 *
 * <p>26.1 的物品模型由 {@code assets/<命名空间>/items/<物品名>.json} 按"属性"挑选，旧版那套按物品实例
 * 注册的模型谓词（ItemProperties）已被移除。原版弓的拉弓阶段用的是 {@code minecraft:use_duration}
 * 配 {@code scale: 0.05}，等于写死 20 tick 拉满，所以这里注册 {@link #PULL}：
 * 已拉弓 tick ÷ 满蓄 tick（draw_time ÷ draw_speed 算出），让拉弓阶段跟着自定义拉弓时间走。
 * 属性按 id 全局生效，{@link TtBowItem} / {@link TtCrossbowItem} 及其子类（含其他模组注册的）都能直接用。</p>
 *
 * <p>模型定义写法：</p>
 * <ul>
 *   <li>弓：照抄原版 {@code items/bow.json}，把 {@code "property": "minecraft:use_duration"} 与
 *       {@code "scale": 0.05} 换成 {@code "property": "taverntales_l1b:pull"}，阈值 0.65 / 0.9 不变。</li>
 *   <li>弩：可直接照抄原版 {@code items/crossbow.json}。原版的 {@code minecraft:crossbow/pull} 读的是
 *       {@code CrossbowItem.getChargeDuration}，已由 {@link ibx.taverntales.lib.mixin.CrossbowItemMixin}
 *       对 Tt 弩改成属性时长；想统一写法也可以换成本属性，结果相同。</li>
 * </ul>
 */
public final class RangedWeaponModelProperties {
    private RangedWeaponModelProperties() {}

    /** 拉弓进度属性的 id，模型定义里写 {@code "property": "taverntales_l1b:pull"}。 */
    public static final Identifier PULL = Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "pull");

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
            boolean idle = stack.getItem() instanceof TtCrossbowItem
                    ? CrossbowItem.isCharged(stack)
                    : entity.getUseItem() != stack;
            if (idle) {
                return 0.0F;
            }
            return (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks())
                    / (float) RangedWeaponApi.getFullDrawTicks(entity, stack);
        }

        @Override
        public MapCodec<Pull> type() {
            return MAP_CODEC;
        }
    }
}
