package ibx.lib.item.general;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.BlocksAttacks;

import java.util.List;
import java.util.Optional;

/**
 * Ibx 盾牌的基类：格挡部分。子类按单双手分：{@link ibx.lib.item.IbxOneHandedShieldItem} 只挡（同原版盾），
 * {@link ibx.lib.item.IbxTwoHandedShieldItem} 双手拿，主手还能普通攻击和盾击。
 *
 * <ul>
 *   <li>格挡全走原版的 {@code blocks_attacks} 组件，由构造参数烤：举盾延迟（0 是一举起来就能挡，负数用原版盾的 0.25 秒）、
 *       挡掉比例、水平角度（这两项不大于 0 用原版盾的全挡、90°）；耐久函数、无视格挡的伤害（{@code #minecraft:bypasses_shield}）、格挡音、破碎音都同原版盾。
 *       副手装备位（{@code equippableUnswappable(OFFHAND)}）也在这里挂，注册时只给耐久和修复材料。</li>
 *   <li>自动进 {@code #c:tools}、{@code #c:tools/shield}，子类再进各自的 {@code #c:tools/one_handed_shield}、
 *       {@code #c:tools/two_handed_shield}（见 {@link ibx.lib.registry.AutoItemTags}）。</li>
 *   <li>继承 {@link ShieldItem}：原版第一人称渲染给"不是 ShieldItem 的格挡物品"额外加一套举剑格挡的位移旋转，
 *       盾会被转出视野；{@code ShieldItem} 本身只多一个按旗帜颜色换名字的 {@code getName}。</li>
 * </ul>
 */
public abstract class IbxShieldItem extends ShieldItem {
    /** 原版盾：举盾 0.25 秒后生效。 */
    public static final float BASELINE_BLOCK_DELAY = 0.25F;
    /** 原版盾：全挡。 */
    public static final float BASELINE_BLOCK_FACTOR = 1.0F;
    /** 原版盾：正面 ±90°。 */
    public static final float BASELINE_BLOCK_ANGLE = 90.0F;

    /**
     * @param properties  注册时给好耐久、修复材料的属性；{@code blocks_attacks}、副手装备位、破碎音由这里挂
     * @param blockDelay  举盾几秒后生效，0 是一举起来就能挡；负数用 {@link #BASELINE_BLOCK_DELAY}
     * @param blockFactor 挡掉的比例 0~1；不大于 0 用 {@link #BASELINE_BLOCK_FACTOR}
     * @param blockAngle  水平格挡角度；不大于 0 用 {@link #BASELINE_BLOCK_ANGLE}
     */
    protected IbxShieldItem(Item.Properties properties, float blockDelay, float blockFactor, float blockAngle) {
        super(bake(properties, blockDelay, blockFactor, blockAngle));
    }

    private static Item.Properties bake(Item.Properties properties, float blockDelay, float blockFactor, float blockAngle) {
        float delay = blockDelay >= 0.0F ? blockDelay : BASELINE_BLOCK_DELAY;
        float factor = blockFactor > 0.0F ? Mth.clamp(blockFactor, 0.0F, 1.0F) : BASELINE_BLOCK_FACTOR;
        float angle = blockAngle > 0.0F ? blockAngle : BASELINE_BLOCK_ANGLE;
        return properties.equippableUnswappable(EquipmentSlot.OFFHAND)
                // 无视格挡的伤害是标签，要等注册表加载完再解析，同原版盾的注册
                .delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(
                        delay,
                        1.0F,
                        List.of(new BlocksAttacks.DamageReduction(angle, Optional.empty(), 0.0F, factor)),
                        new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
                        Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                        Optional.of(SoundEvents.SHIELD_BLOCK),
                        Optional.of(SoundEvents.SHIELD_BREAK)))
                .component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
    }
}
