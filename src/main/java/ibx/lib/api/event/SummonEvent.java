package ibx.lib.api.event;

import ibx.lib.spell.Spell;
import ibx.lib.summon.UnsummonReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jspecify.annotations.Nullable;

/** 召唤物框架的事件，都在服务端、{@code NeoForge.EVENT_BUS} 上发。 */
public abstract class SummonEvent extends Event {
    private final @Nullable LivingEntity owner;
    private final Mob summon;

    protected SummonEvent(@Nullable LivingEntity owner, Mob summon) {
        this.owner = owner;
        this.summon = summon;
    }

    /** 主人；{@link Unsummon} 里主人可能已经找不到了，{@link Transfer} 里是原主人（也可能找不到），这两种可能为 null。 */
    public @Nullable LivingEntity getOwner() {
        return this.owner;
    }

    public Mob getSummon() {
        return this.summon;
    }

    /** 召唤前：名额检查已过、还没挤掉旧的、还没放进世界。取消 = 这次不召（{@code SummonApi.summon} 返回 false）。 */
    public static final class Pre extends SummonEvent implements ICancellableEvent {
        private final @Nullable Spell spell;

        public Pre(LivingEntity owner, Mob summon, @Nullable Spell spell) {
            super(owner, summon);
            this.spell = spell;
        }

        /** 召它的法术；不是法术召的为 null。 */
        public @Nullable Spell getSpell() {
            return this.spell;
        }
    }

    /** 召唤物已经放进世界、记进主人名单。 */
    public static final class Post extends SummonEvent {
        public Post(LivingEntity owner, Mob summon) {
            super(owner, summon);
        }
    }

    /**
     * 换主人前（{@code SummonApi.transferOwner}）：{@link #getOwner()} 是原主人（找不到时为 null），{@link #getNewOwner()} 是要转给的。
     * 取消 = 这次不转（{@code transferOwner} 返回 false）。
     */
    public static final class Transfer extends SummonEvent implements ICancellableEvent {
        private final LivingEntity newOwner;

        public Transfer(@Nullable LivingEntity owner, Mob summon, LivingEntity newOwner) {
            super(owner, summon);
            this.newOwner = newOwner;
        }

        public LivingEntity getNewOwner() {
            return this.newOwner;
        }
    }

    /** 召唤物离场（框架让它走，或者被打死）。发的时候它已经从主人名单里划掉，被框架送走的还没移除。 */
    public static final class Unsummon extends SummonEvent {
        private final UnsummonReason reason;

        public Unsummon(@Nullable LivingEntity owner, Mob summon, UnsummonReason reason) {
            super(owner, summon);
            this.reason = reason;
        }

        public UnsummonReason getReason() {
            return this.reason;
        }
    }
}
