package ibx.lib.api;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 法术伤害的伤害来源：在原版来源上多带这次命中用的快照和暴击结果，由 {@link SpellDamageApi#hurt} 构造。
 *
 * <p>"是不是法术伤害"请用伤害类型标签 {@code #ibx_lib:is_spell} 判断，不要 {@code instanceof} 本类——
 * 别的模组的魔法伤害进了标签也算法术。本类只在需要快照内容（附带效果、事件）时才用。</p>
 *
 * <p>死亡信息用伤害类型自己的 {@code message_id}；以后要按法术显示，在这里重写 {@code getLocalizedDeathMessage}。</p>
 */
public class SpellDamageSource extends DamageSource {
    private final SpellDamageSnapshot snapshot;
    private final boolean critical;

    public SpellDamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity,
                             @Nullable Vec3 sourcePosition, SpellDamageSnapshot snapshot, boolean critical) {
        super(type, directEntity, causingEntity, sourcePosition);
        this.snapshot = snapshot;
        this.critical = critical;
    }

    public SpellDamageSnapshot snapshot() {
        return this.snapshot;
    }

    /** 这一下是否暴击（命中时掷的骰）。 */
    public boolean isCritical() {
        return this.critical;
    }
}
