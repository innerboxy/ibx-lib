package ibx.lib.gearset;

import ibx.lib.compat.CuriosCompat;
import ibx.lib.effect.GrantedEffects;
import ibx.lib.registry.AttachmentRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 套装件数的统计和效果的挂摘，只在服务端。玩家和生物都算。
 *
 * <ul>
 *   <li><b>什么时候重数</b>：盔甲槽、双手里换了物品（{@link LivingEquipmentChangeEvent}），饰品栏换了饰品。
 *       实体刚进世界的第一刻这两样都会对身上已有的装备各报一次，所以读档、重生之后不用另外补。</li>
 *   <li><b>手持件的保留</b>：靠手上的物品算上的件位，离手后再保留 {@link #HAND_GRACE_TICKS} tick 才掉；
 *       期间拿回来、或换成同一件位的另一样，保留就取消。盔甲和饰品摘下立刻掉。</li>
 *   <li><b>属性</b>：档位生效时挂临时修饰符，掉档摘掉。</li>
 *   <li><b>常驻效果</b>：档位生效期间每刻检查，没有、或者等级比套装给的低就补上；掉档时只去掉套装给的那个
 *       （被更强的药水盖住时，把压在下面的那份摘掉），见 {@link GrantedEffects}。</li>
 *   <li><b>触发钩子</b>（{@link SetTriggers}）：档位生效 / 掉档时调 {@code onActivate} / {@code onDeactivate}，生效期间每刻调 {@code tick}；
 *       战斗里的时机由 {@link SetTriggerHandler} 分发。</li>
 * </ul>
 */
public final class GearSetHandler {
    private GearSetHandler() {}

    /** 手持的件离手后还算数多久（tick）。 */
    public static final int HAND_GRACE_TICKS = 20;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GearSetHandler::onEquipmentChange);
        NeoForge.EVENT_BUS.addListener(GearSetHandler::onEntityTick);
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.onCurioChange(GearSetHandler::recount);
        }
        SetTriggerHandler.register();
    }

    // ---------------------------------------------------------------- 事件

    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        ItemStack from = event.getFrom();
        ItemStack to = event.getTo();
        // 同一种物品只是耐久、组件变了：件数不变
        if (from.getItem() == to.getItem()) {
            return;
        }
        if (GearSets.piecesOf(from.getItem()).isEmpty() && GearSets.piecesOf(to.getItem()).isEmpty()) {
            return;
        }
        recount(event.getEntity());
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) {
            return;
        }
        GearSetState state = entity.getExistingDataOrNull(AttachmentRegistry.GEAR_SETS);
        if (state == null) {
            return;
        }
        if (state.nextExpiry <= entity.level().getGameTime()) {
            recount(entity);
        }
        if (!entity.isAlive()) {
            return;
        }
        if (state.hasEffects) {
            refreshEffects(entity, state);
        }
        for (SetTriggers triggers : state.triggers) {
            triggers.tick(entity);
        }
    }

    // ---------------------------------------------------------------- 统计

    /**
     * 重数这个实体身上各套装的件数，档位有变就挂上 / 摘掉对应的效果。装备、饰品变化时会自动调；
     * 用别的办法改了「物品在哪里才算在位」的结果（比如换了物品标签）才需要自己调。客户端调了不做事。
     */
    public static void recount(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        Set<GearSet.Piece> solid = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<GearSet.Piece> held = Collections.newSetFromMap(new IdentityHashMap<>());
        GearSets.scan(entity, (piece, viaHand) -> (viaHand ? held : solid).add(piece));

        GearSetState state = entity.getExistingDataOrNull(AttachmentRegistry.GEAR_SETS);
        if (state == null) {
            if (solid.isEmpty() && held.isEmpty()) {
                return;
            }
            state = entity.getData(AttachmentRegistry.GEAR_SETS);
        }

        long now = entity.level().getGameTime();
        // 上次靠手持算上、现在哪儿都不在位的件位：开始保留
        for (GearSet.Piece piece : state.held) {
            if (!held.contains(piece) && !solid.contains(piece)) {
                state.graceUntil.putIfAbsent(piece, now + HAND_GRACE_TICKS);
            }
        }
        state.graceUntil.entrySet().removeIf(entry -> entry.getValue() <= now
                || held.contains(entry.getKey()) || solid.contains(entry.getKey()));
        state.held = held;
        state.nextExpiry = state.graceUntil.values().stream().mapToLong(Long::longValue).min().orElse(Long.MAX_VALUE);

        Set<GearSet.Piece> counted = Collections.newSetFromMap(new IdentityHashMap<>());
        counted.addAll(solid);
        counted.addAll(held);
        counted.addAll(state.graceUntil.keySet());
        Map<GearSet, Integer> counts = new IdentityHashMap<>();
        for (GearSet.Piece piece : counted) {
            counts.merge(piece.set(), 1, Integer::sum);
        }

        Map<GearSet, Integer> before = state.counts;
        state.counts = counts;
        // 先把这次生效的钩子名单定下来，再挂摘：onActivate / onDeactivate 里查到的已经是新状态
        boolean hasEffects = false;
        List<SetTriggers> triggers = new ArrayList<>();
        for (GearSet set : GearSets.all()) {
            int is = counts.getOrDefault(set, 0);
            for (GearSet.Bonus bonus : set.bonuses()) {
                if (is >= bonus.pieces()) {
                    hasEffects |= !bonus.effects().isEmpty();
                    triggers.addAll(bonus.triggers());
                }
            }
        }
        state.hasEffects = hasEffects;
        state.triggers = List.copyOf(triggers);

        for (GearSet set : GearSets.all()) {
            int was = before.getOrDefault(set, 0);
            int is = counts.getOrDefault(set, 0);
            if (was == is) {
                continue;
            }
            for (GearSet.Bonus bonus : set.bonuses()) {
                boolean wasActive = was >= bonus.pieces();
                boolean isActive = is >= bonus.pieces();
                if (isActive && !wasActive) {
                    apply(entity, bonus);
                } else if (wasActive && !isActive) {
                    remove(entity, bonus);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 挂摘效果

    private static void apply(LivingEntity entity, GearSet.Bonus bonus) {
        for (GearSet.AttributeBonus attribute : bonus.attributes()) {
            AttributeInstance instance = entity.getAttribute(attribute.attribute());
            if (instance != null) {
                instance.addOrUpdateTransientModifier(attribute.modifier());
            }
        }
        for (GearSet.EffectBonus effect : bonus.effects()) {
            GrantedEffects.grant(entity, effect.effect(), effect.amplifier());
        }
        for (SetTriggers triggers : bonus.triggers()) {
            triggers.onActivate(entity);
        }
    }

    private static void remove(LivingEntity entity, GearSet.Bonus bonus) {
        for (GearSet.AttributeBonus attribute : bonus.attributes()) {
            AttributeInstance instance = entity.getAttribute(attribute.attribute());
            if (instance != null) {
                instance.removeModifier(attribute.id());
            }
        }
        for (GearSet.EffectBonus effect : bonus.effects()) {
            GrantedEffects.revoke(entity, effect.effect(), effect.amplifier());
        }
        for (SetTriggers triggers : bonus.triggers()) {
            triggers.onDeactivate(entity);
        }
    }

    private static void refreshEffects(LivingEntity entity, GearSetState state) {
        for (Map.Entry<GearSet, Integer> entry : state.counts.entrySet()) {
            for (GearSet.Bonus bonus : entry.getKey().bonuses()) {
                if (entry.getValue() >= bonus.pieces()) {
                    for (GearSet.EffectBonus effect : bonus.effects()) {
                        GrantedEffects.grant(entity, effect.effect(), effect.amplifier());
                    }
                }
            }
        }
    }
}
