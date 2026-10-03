package ibx.taverntales.lib.api.event;

import ibx.taverntales.lib.spell.CastContext;
import ibx.taverntales.lib.spell.CastSource;
import ibx.taverntales.lib.spell.Spell;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 施法事件，发在 {@code NeoForge.EVENT_BUS} 上，只在服务端触发。其他模组和 KubeJS 可以借它介入施法。
 */
public abstract class SpellCastEvent extends Event {
    private final LivingEntity caster;
    private final Spell spell;
    private final CastSource source;

    protected SpellCastEvent(LivingEntity caster, Spell spell, CastSource source) {
        this.caster = caster;
        this.spell = spell;
        this.source = source;
    }

    public LivingEntity getCaster() {
        return this.caster;
    }

    public Spell getSpell() {
        return this.spell;
    }

    public CastSource getSource() {
        return this.source;
    }

    /**
     * 施法开始前：冷却、法力、法术自己的条件都已通过。可取消（这次施法失败，返回 {@code CONDITION_FAILED}，不发提示），
     * 也可改本次的耗蓝和冷却。
     *
     * <ul>
     *   <li>耗蓝是<b>基础值</b>，实际扣时还会乘 {@code mana_cost_multiplier}；引导是每次生效的耗蓝。</li>
     *   <li>冷却是已乘 {@code spell_cooldown_ratio} 的 tick 数。</li>
     * </ul>
     */
    public static final class Pre extends SpellCastEvent implements ICancellableEvent {
        private float manaCost;
        private int cooldownTicks;

        public Pre(LivingEntity caster, Spell spell, CastSource source, float manaCost, int cooldownTicks) {
            super(caster, spell, source);
            this.manaCost = manaCost;
            this.cooldownTicks = cooldownTicks;
        }

        public float getManaCost() {
            return this.manaCost;
        }

        public void setManaCost(float manaCost) {
            this.manaCost = Math.max(0.0F, manaCost);
        }

        public int getCooldownTicks() {
            return this.cooldownTicks;
        }

        public void setCooldownTicks(int cooldownTicks) {
            this.cooldownTicks = Math.max(0, cooldownTicks);
        }
    }

    /** 每次生效之后（{@code Spell#onCast} 之后；引导每次都发）：只通知（统计、成就、联动）。 */
    public static final class Post extends SpellCastEvent {
        private final CastContext context;

        public Post(CastContext context) {
            super(context.caster(), context.spell(), context.source());
            this.context = context;
        }

        public CastContext getContext() {
            return this.context;
        }
    }
}
