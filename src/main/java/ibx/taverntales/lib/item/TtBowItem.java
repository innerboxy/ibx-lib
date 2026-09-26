package ibx.taverntales.lib.item;

import ibx.taverntales.lib.api.CriticalStrikeApi;
import ibx.taverntales.lib.api.RangedWeaponApi;
import ibx.taverntales.lib.item.ranged_weapon.RangedWeaponStats;
import ibx.taverntales.lib.item.ranged_weapon.RangedWeaponItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tt 弓：可复用的弓物品类，数值由构造参数给定。
 *
 * <ul>
 *   <li>命中伤害 = 射手 ranged_damage × 蓄力百分比，发射瞬间快照到箭上，命中时覆盖原版伤害。</li>
 *   <li>蓄力百分比线性：已蓄力 tick ÷ 满蓄 tick，满蓄 tick 由射手的 draw_time ÷ draw_speed 决定。</li>
 *   <li>箭速 = {@link #getArrowVelocity} × 蓄力百分比，只管飞行手感，不影响伤害。</li>
 *   <li>低于 {@link #getMinCharge} 不发射、不消耗；只能主手使用。</li>
 *   <li>带 {@code auto_fire} 组件时拉满自动射出，见 {@link RangedWeaponApi#tickAutoFire}。</li>
 * </ul>
 * 需要改箭速、门槛、弹药的变种，继承后重写对应的 protected 方法即可。
 */
public class TtBowItem extends BowItem implements RangedWeaponItem {
    /** 本类型的基准满蓄秒数（原版弓 20 tick）。 */
    public static final float BASELINE_DRAW_TIME = 1.0F;
    /** 本类型的基准箭速，即满蓄时的初速度（原版弓 3.0）。 */
    public static final float BASELINE_ARROW_VELOCITY = 3.0F;
    /** 发射门槛：蓄力百分比低于此值不发射、不消耗（同原版的 0.1）。 */
    public static final float MIN_CHARGE = 0.1F;

    private final RangedWeaponStats stats;
    private final Predicate<ItemStack> ammo;

    /**
     * 便捷构造：只给耐久、伤害、拉弓秒数，箭速用基准值。
     *
     * @param durability 最大耐久（原版弓是 384）；不大于 0 则不会损坏
     */
    public TtBowItem(int durability, float rangedDamage, float drawTime) {
        this(durability, rangedDamage, drawTime, 0.0F);
    }

    /**
     * 便捷构造：耐久、伤害、拉弓秒数、箭速。要设稀有度、防火等别的物品属性时，
     * 改用带 {@link Item.Properties} 的那个构造器。
     *
     * @param durability    最大耐久（原版弓是 384）；不大于 0 则不会损坏
     * @param arrowVelocity 满蓄时的初速度；不大于 0 则用 {@link #BASELINE_ARROW_VELOCITY}
     */
    public TtBowItem(int durability, float rangedDamage, float drawTime, float arrowVelocity) {
        this(RangedWeaponItem.properties(durability), rangedDamage, drawTime, arrowVelocity);
    }

    public TtBowItem(Item.Properties properties, float rangedDamage, float drawTime) {
        this(properties, rangedDamage, drawTime, 0.0F);
    }

    public TtBowItem(Item.Properties properties, float rangedDamage, float drawTime, float arrowVelocity) {
        this(properties, rangedDamage, drawTime, arrowVelocity, ARROW_ONLY);
    }

    /**
     * @param rangedDamage  远程伤害（满蓄时）
     * @param drawTime      满蓄秒数；不大于 0 则用 {@link #BASELINE_DRAW_TIME}
     * @param arrowVelocity 满蓄时的初速度；不大于 0 则用 {@link #BASELINE_ARROW_VELOCITY}。
     *                      只影响弹道手感，不影响伤害（伤害在发射瞬间快照，与箭速解耦）。
     * @param ammo          弹药谓词。自定义弹药物品必须是 ArrowItem 的子类，
     *                      否则原版 createProjectile 会把它当成普通箭射出去。
     */
    public TtBowItem(Item.Properties properties, float rangedDamage, float drawTime, float arrowVelocity,
                     Predicate<ItemStack> ammo) {
        super(properties);
        this.stats = new RangedWeaponStats(rangedDamage, drawTime, arrowVelocity,
                BASELINE_DRAW_TIME, BASELINE_ARROW_VELOCITY);
        this.ammo = ammo;
    }

    @Override
    public RangedWeaponStats stats() {
        return this.stats;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return this.stats.modifiers();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return this.ammo;
    }

    /** 满蓄时的箭速。子类要按物品栈动态改箭速就重写这里。 */
    protected float getArrowVelocity(ItemStack stack) {
        return this.stats.arrowVelocity();
    }

    /** 发射门槛（蓄力百分比）。 */
    protected float getMinCharge(ItemStack stack) {
        return MIN_CHARGE;
    }

    /** 线性蓄力百分比：已蓄力 tick ÷ 满蓄 tick，clamp 到 1。 */
    public float getChargePercent(int chargeTicks, LivingEntity shooter, ItemStack stack) {
        return Math.min(1.0F, (float) chargeTicks / (float) RangedWeaponApi.getFullDrawTicks(shooter, stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 副手禁用：修饰符只挂主手，副手射击读不到武器数值。
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RangedWeaponItem.appendAutoFireTooltip(stack, tooltip);
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        RangedWeaponApi.tickAutoFire(entity, stack, remainingTicks);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack ammoStack = player.getProjectile(stack);
        if (ammoStack.isEmpty()) {
            return;
        }

        int charge = this.getUseDuration(stack, entity) - timeLeft;
        charge = EventHooks.onArrowLoose(stack, level, player, charge, true);
        if (charge < 0) {
            return;
        }

        float pct = this.getChargePercent(charge, player, stack);
        if (pct < this.getMinCharge(stack)) {
            return; // 低于门槛：不发射、不消耗
        }

        List<ItemStack> projectiles = draw(stack, ammoStack, player);
        if (level instanceof ServerLevel serverLevel && !projectiles.isEmpty()) {
            // 箭上只带"这一箭的基础远程伤害"：属性值 × 蓄力百分比，不乘任何倍率。
            // 暴击在这里掷骰（服务端），结果记在箭的原版暴击标记上，顺带白拿原版的暴击粒子；
            // 真正的伤害倍率由 DamageHandler 在命中时叠加。
            float damage = RangedWeaponApi.getRangedDamage(player, stack) * pct;
            this.shootWithDamage(serverLevel, player, player.getUsedItemHand(), stack, projectiles,
                    this.getArrowVelocity(stack) * pct, 1.0F, CriticalStrikeApi.roll(player), null, damage);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + pct * 0.5F);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /**
     * 原版 {@code ProjectileWeaponItem#shoot} 的复刻，多一个 damage 参数：每支箭生成后写入伤害快照。
     * 原版 createProjectile 没有蓄力参数，所以只能在这一层塞进去；散射、耐久、多重射击结构原样保留。
     */
    protected void shootWithDamage(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon,
                                   List<ItemStack> projectileItems, float velocity, float inaccuracy, boolean isCrit,
                                   @Nullable LivingEntity target, float damage) {
        float spread = EnchantmentHelper.processProjectileSpread(level, weapon, shooter, 0.0F);
        float step = projectileItems.size() == 1 ? 0.0F : 2.0F * spread / (float) (projectileItems.size() - 1);
        float offset = (float) ((projectileItems.size() - 1) % 2) * step / 2.0F;
        float sign = 1.0F;

        for (int i = 0; i < projectileItems.size(); i++) {
            ItemStack ammoStack = projectileItems.get(i);
            if (ammoStack.isEmpty()) {
                continue;
            }
            float angle = offset + sign * (float) ((i + 1) / 2) * step;
            sign = -sign;

            Projectile projectile = this.createProjectile(level, shooter, weapon, ammoStack, isCrit);
            RangedWeaponApi.setAmmoRangedDamage(projectile, damage);
            this.shootProjectile(shooter, projectile, i, velocity, inaccuracy, angle, target);
            level.addFreshEntity(projectile);

            weapon.hurtAndBreak(this.getDurabilityUse(ammoStack), shooter, LivingEntity.getSlotForHand(hand));
            if (weapon.isEmpty()) {
                break;
            }
        }
    }
}
