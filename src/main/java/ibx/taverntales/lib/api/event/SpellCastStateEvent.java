package ibx.taverntales.lib.api.event;

import ibx.taverntales.lib.spell.CastContext;
import ibx.taverntales.lib.spell.CastEndReason;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * 客户端：某个生物开始 / 结束施法，发在 {@code NeoForge.EVENT_BUS} 上，<b>只在客户端触发</b>（施法状态同步到达时）。
 * 自己和周围生物的施法都会收到，用来放施法动作、客户端特效。
 *
 * <p>客户端的 {@link CastContext} 只有施法者、法术、来源、开始时刻和读条长度，没有物品；要知道用哪只手施法，看施法者手上的物品。</p>
 */
public abstract class SpellCastStateEvent extends Event {
    private final LivingEntity caster;
    private final CastContext context;

    protected SpellCastStateEvent(LivingEntity caster, CastContext context) {
        this.caster = caster;
        this.context = context;
    }

    public LivingEntity getCaster() {
        return this.caster;
    }

    public CastContext getContext() {
        return this.context;
    }

    /** 开始施法。瞬发法术也有：紧接着就会收到结束。 */
    public static final class Start extends SpellCastStateEvent {
        public Start(LivingEntity caster, CastContext context) {
            super(caster, context);
        }
    }

    /** 结束施法。 */
    public static final class End extends SpellCastStateEvent {
        private final CastEndReason reason;

        public End(LivingEntity caster, CastContext context, CastEndReason reason) {
            super(caster, context);
            this.reason = reason;
        }

        public CastEndReason getReason() {
            return this.reason;
        }
    }
}
