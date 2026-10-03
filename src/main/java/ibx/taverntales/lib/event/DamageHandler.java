package ibx.taverntales.lib.event;

import ibx.taverntales.lib.api.CriticalStrikeApi;
import ibx.taverntales.lib.api.RangedWeaponApi;
import ibx.taverntales.lib.api.SpellDamageSnapshot;
import ibx.taverntales.lib.api.SpellDamageSource;
import ibx.taverntales.lib.compat.DummmmmmyCompat;
import ibx.taverntales.lib.config.Config;
import ibx.taverntales.lib.registry.AttributeRegistry;
import ibx.taverntales.lib.registry.TagRegistry;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import org.jspecify.annotations.Nullable;

/**
 * 本模组统一的伤害处理入口。所有伤害相关的结算都写在这里，按 {@link #onIncomingDamage} 里的
 * 调用顺序依次执行，不要再另开事件监听，否则各步骤之间的先后关系会散落在多处、难以推断。
 *
 * <p><b>顺序约定</b>：先定"基础伤害"，再叠倍率，最后做减免。新增步骤时按这个次序插进去
 * ——{@link #applyAmmoRangedDamage} 属于第一类，它是直接<b>覆盖</b>伤害值的，必须排在所有
 * 乘区之前，否则乘区会被它抹掉。</p>
 *
 * <p><b>优先级 HIGHEST</b>：让本模组先把基础伤害定下来，其他模组的乘区和减免叠在它之上。</p>
 *
 * <p><b>近战暴击走另一个事件</b>：原版在 {@code Player#attack} 里就把暴击算进伤害了，等到
 * 伤害事件已经来不及区分，所以近战由 {@link #onCriticalHit} 处理，远程由
 * {@link #applyCriticalDamage} 处理。两条路的概率与倍率都取自同一套属性。
 * 法术暴击在 {@link ibx.taverntales.lib.api.SpellDamageApi#hurt} 里掷，进本事件时伤害已经乘好了。</p>
 *
 * <p><b>命中后的附带效果</b>（法术的点燃、冰冻、吸血）不改伤害量，放在伤害结算完之后的
 * {@link #onDamagePost}，同样写在本类里。</p>
 */
public final class DamageHandler {
    private DamageHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DamageHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DamageHandler::onCriticalHit);
        NeoForge.EVENT_BUS.addListener(DamageHandler::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(DamageHandler::onDamagePost);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        applyAmmoRangedDamage(event);
        applyCriticalDamage(event);
        applyAmmoDifficultyScaling(event);
        applySpellResistance(event);
    }

    /** 伤害结算完之后（所有减免都做完、已扣血）：只放命中后的附带效果，不再改伤害量。 */
    private static void onDamagePost(LivingDamageEvent.Post event) {
        applySpellHitEffects(event);
    }

    /**
     * 远程武器命中结算：箭上带着发射瞬间写入的远程伤害，就用它覆盖原版伤害。
     *
     * <p>原版伤害 = 箭速 × 基础伤害（向上取整），这里整个丢弃，让伤害与箭速彻底解耦。
     * 没被接管的弓弩（关掉接管开关的原版弓弩、其他模组的弓弩）射出的箭没有这个数据，完全不受影响。</p>
     */
    private static void applyAmmoRangedDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
            RangedWeaponApi.getAmmoRangedDamage(arrow).ifPresent(event::setAmount);
        }
    }

    /**
     * 补回难度缩放：生物射中玩家时，按难度调整本模组的远程伤害（简单约减半、困难 ×1.5）。
     *
     * <p>原版在 {@code Player#hurtServer} 里、触发本事件<b>之前</b>就按难度缩放了伤害，
     * {@link #applyAmmoRangedDamage} 的覆盖会把它一起抹掉，所以在这里对覆盖后的值重新缩放一次。
     * 用的是同一个缩放函数，由伤害类型自己决定缩不缩（玩家射的箭、打生物都不缩），口径与原版一致。</p>
     *
     * <p>放在暴击之后：原版缩放的也是含暴击加成在内的整支箭伤害。</p>
     */
    private static void applyAmmoDifficultyScaling(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)
                || RangedWeaponApi.getAmmoRangedDamage(arrow).isEmpty()) {
            return;
        }
        DamageSource source = event.getSource();
        event.setAmount(Math.max(0.0F, source.type().scaling().getScalingFunction()
                .scaleDamage(source, player, event.getAmount(), player.level().getDifficulty())));
    }

    /**
     * 远程暴击：发射时已经掷过骰并把结果记在箭的原版暴击标记上，这里只负责乘倍率。
     *
     * <p>只处理带本模组远程伤害的箭。未被接管的弓弩射出的暴击箭在
     * {@code AbstractArrow#onHitEntity} 里已经加过原版的随机暴击加成，
     * 再乘一次就成了双重结算，所以放过它们。</p>
     */
    private static void applyCriticalDamage(LivingIncomingDamageEvent event) {
        if (!isTtCriticalArrow(event.getSource().getDirectEntity())) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity shooter) {
            event.setAmount(event.getAmount() * CriticalStrikeApi.getMultiplier(shooter));
            CriticalStrikeApi.spawnEffects(event.getEntity());
        }
    }

    /**
     * 法术抗性：伤害类型在 {@code #taverntales_l1b:is_spell} 里时，按原版护甲公式减免（法术伤害本身不吃护甲）。
     *
     * <p>公式同 {@code CombatRules#getDamageAfterAbsorb}，法术抗性当护甲点、韧性用原版 {@code armor_toughness}：
     * {@code 减免比例 = clamp(R − 伤害 ÷ (2 + 韧性 ÷ 4), min(R × 0.2, 20), 20) ÷ 25}，最多减 80%。
     * <b>下限必须是 {@code min(R × 0.2, 20)}</b>：法术抗性上限远超原版护甲的 30，R 超过 100 后 {@code R × 0.2} 大于 20，
     * 而 {@code Mth.clamp} 在下限大于上限时返回下限，减免会超过 80% 乃至 100%（伤害变负）。</p>
     *
     * <p>放在最后：符合"先基础、再倍率、最后减免"。按标签而不是 {@code instanceof SpellDamageSource} 判断，
     * 别的模组的魔法伤害进了标签也一起减免。原版在本事件之前就做完了难度缩放，这里不覆盖伤害值，不用补。</p>
     */
    private static void applySpellResistance(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(TagRegistry.IS_SPELL)) {
            return;
        }
        LivingEntity target = event.getEntity();
        float resistance = (float) readAttribute(target, AttributeRegistry.SPELL_RESISTANCE);
        if (resistance <= 0.0F) {
            return;
        }
        float damage = event.getAmount();
        float toughness = (float) readAttribute(target, Attributes.ARMOR_TOUGHNESS);
        float effective = Mth.clamp(resistance - damage / (2.0F + toughness / 4.0F),
                Math.min(resistance * 0.2F, 20.0F), 20.0F);
        event.setAmount(damage * (1.0F - effective / 25.0F));
    }

    /**
     * 法术命中后的附带效果：点燃、冰冻、吸血，参数来自快照。只认本模组的 {@link SpellDamageSource}。
     *
     * <p>放在伤害结算完之后：吸血按实际造成的伤害（所有减免之后、含被伤害吸收挡掉的部分）算；
     * 冰冻 tick 不在细雪里时每 tick 减 2，所以加两倍，让持续时间大致等于快照里写的 tick 数。</p>
     */
    private static void applySpellHitEffects(LivingDamageEvent.Post event) {
        if (!(event.getSource() instanceof SpellDamageSource source)) {
            return;
        }
        SpellDamageSnapshot snapshot = source.snapshot();
        LivingEntity target = event.getEntity();
        if (snapshot.fireTicks() > 0) {
            target.igniteForTicks(snapshot.fireTicks());
        }
        if (snapshot.freezeTicks() > 0 && target.canFreeze()) {
            target.setTicksFrozen(target.getTicksFrozen() + snapshot.freezeTicks() * 2);
        }
        if (snapshot.lifesteal() > 0.0F && source.getEntity() instanceof LivingEntity caster && caster.isAlive()) {
            caster.heal(snapshot.lifesteal() * event.getInflictedDamage());
        }
    }

    /** 本模组的属性挂在所有生物上，这里只防万一：没挂的实体按属性默认值算。 */
    private static double readAttribute(LivingEntity entity, Holder<Attribute> attribute) {
        return entity.getAttributes().hasAttribute(attribute)
                ? entity.getAttributeValue(attribute)
                : attribute.value().getDefaultValue();
    }

    /**
     * 试验假人联动：命中前先把这次暴击登记给假人，让它把伤害数字显示成"12 (1.5x)"这种暴击样式。
     *
     * <p>近战不用管，假人自己监听 NeoForge 的暴击事件就能识别；远程弹射物不经过那个事件，
     * 只能由本模组补登记。<b>时机很讲究</b>：假人是在自己的 {@code hurtServer()} 里把登记记录与伤害来源
     * 配对的，所以必须赶在 {@code hurtServer()} 之前——弹射物命中事件正好在原版造成伤害之前触发。</p>
     */
    private static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!DummmmmmyCompat.isLoaded() || !(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (!isTtCriticalArrow(event.getProjectile())) {
            return;
        }
        if (event.getProjectile().getOwner() instanceof LivingEntity shooter) {
            DummmmmmyCompat.notifyCrit(hit.getEntity(), shooter, CriticalStrikeApi.getMultiplier(shooter));
        }
    }

    /**
     * 是不是一支"本模组射出且暴击了"的箭。
     *
     * <p>两个条件缺一不可：带本模组的远程伤害（排除未被接管的弓弩，它们的暴击已由原版自己结算），
     * 且带着发射时掷骰打上的原版暴击标记。</p>
     */
    private static boolean isTtCriticalArrow(@Nullable Entity projectile) {
        return projectile instanceof AbstractArrow arrow
                && arrow.isCritArrow()
                && RangedWeaponApi.getAmmoRangedDamage(arrow).isPresent();
    }

    /**
     * 近战暴击：接管原版 {@code Player#attack} 的暴击判定与倍率。
     *
     * <p>原版跳劈（下落中攻击必定暴击）按配置决定保不保留，见
     * {@link Config#ENABLE_VANILLA_CRITICAL}；概率暴击一律走 {@code critical_chance}，
     * 倍率一律走 {@code critical_damage}，不再是原版写死的 1.5 倍。</p>
     *
     * <p><b>掷骰只在服务端做。</b>本事件在客户端也会触发（用于本地预测特效），
     * 两边各掷一次会得到不同结果，出现"有暴击粒子但伤害没暴击"这种对不上的情况。
     * 客户端这边只做确定性的部分，真正的暴击粒子由服务端广播过来。</p>
     */
    private static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        boolean critical = event.isVanillaCritical() && Config.ENABLE_VANILLA_CRITICAL.getAsBoolean();
        if (!critical && !player.level().isClientSide()) {
            critical = CriticalStrikeApi.roll(player);
        }

        event.setCriticalHit(critical);
        if (critical) {
            event.setDamageMultiplier(CriticalStrikeApi.getMultiplier(player));
        }
    }
}
