package ibx.lib.item;

import ibx.lib.item.general.IbxAbstractShieldItem;
import net.minecraft.world.item.Item;

/**
 * Ibx 盾牌（单手盾）：只挡，主手副手都同原版盾（没有普通攻击和盾击），能和单手武器一起拿；双手盾见 {@link IbxTwoHandedShieldItem}。格挡数值见 {@link IbxAbstractShieldItem}。
 */
public class IbxShieldItem extends IbxAbstractShieldItem {
    /** 格挡数值全原版。 */
    public IbxShieldItem(Item.Properties properties) {
        this(properties, BASELINE_BLOCK_DELAY, BASELINE_BLOCK_FACTOR, BASELINE_BLOCK_ANGLE);
    }

    /**
     * @param properties  注册时给好耐久、修复材料的属性
     * @param blockDelay  举盾几秒后生效，0 是一举起来就能挡；负数用原版值
     * @param blockFactor 挡掉的比例 0~1；不大于 0 用原版值
     * @param blockAngle  水平格挡角度；不大于 0 用原版值
     */
    public IbxShieldItem(Item.Properties properties, float blockDelay, float blockFactor, float blockAngle) {
        super(properties, blockDelay, blockFactor, blockAngle);
    }
}
