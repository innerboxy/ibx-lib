package ibx.lib.item;

import ibx.lib.api.CriticalStrikeApi;
import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.general.IbxRangedWeaponItem;
import ibx.lib.item.general.IbxRangedWeaponStats;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Ibx 弓：可复用的弓物品类，数值由构造参数给定。
 *
 * <ul>
 *   <li>命中伤害 = 射手 ranged_damage × 蓄力百分比，发射瞬间快照到箭上，命中时覆盖原版伤害。</li>
 *   <li>蓄力百分比线性：已蓄力 tick ÷ 满蓄 tick，满蓄 tick 由射手的 draw_time ÷ draw_speed 决定。</li>
 *   <li>箭速 = {@link #getArrowVelocity} × 蓄力百分比，只管飞行手感，不影响伤害。</li>
 *   <li>低于 {@link #getMinCharge} 不发射、不消耗；只能主手使用。</li>
 *   <li>带 {@code auto_fire} 组件时拉满自动射出，见 {@link RangedWeaponApi#tickAutoFire}。</li>
 *   <li>自动进 {@code #c:tools}、{@code #c:tools/ranged_weapon}、{@code #c:tools/bow}（见 {@link ibx.lib.registry.AutoItemTags}）。</li>
 * </ul>
 * 需要改箭速、门槛、弹药的变种，继承后重写对应的 protected 方法即可；
 * 要在发射时结算额外代价、改这一发的伤害或给箭挂数据，重写 {@link #prepareShot}。
 *
 * <p>注册：26.1 起 {@link Item.Properties} 必须先带上物品 id 才能构造物品，请用
 * {@code DeferredRegister.Items#registerItem} 注册，在工厂里使用传进来的 Properties，
 * 耐久、稀有度、防火等也设在它上面（原版弓耐久 384，不设则不会损坏），例如
 * {@code ITEMS.registerItem("my_bow", p -> new IbxBowItem(p.durability(384), 8.0F, 1.0F))}。
 * 拉弓动画的模型写法见 {@link ibx.lib.client.RangedWeaponModelProperties}。</p>
 */
public class IbxBowItem extends BowItem implements IbxRangedWeaponItem {
    /** 本类型的基准满蓄秒数（原版弓 20 tick）。 */
    public static final float BASELINE_DRAW_TIME = 1.0F;
    /** 本类型的基准箭速，即满蓄时的初速度（原版弓 3.0）。 */
    public static final float BASELINE_ARROW_VELOCITY = 3.0F;
    /** 发射门槛：蓄力百分比低于此值不发射、不消耗（同原版的 0.1）。 */
    public static final float MIN_CHARGE = 0.1F;

    private final IbxRangedWeaponStats stats;
    private final Predicate<ItemStack> ammo;

    /** 箭速用基准值。 */
    public IbxBowItem(Item.Properties properties, float rangedDamage, float drawTime) {
        this(properties, rangedDamage, drawTime, 0.0F);
    }

    /**
     * @param arrowVelocity 满蓄时的初速度；不大于 0 则用 {@link #BASELINE_ARROW_VELOCITY}
     */
    public IbxBowItem(Item.Properties properties, float rangedDamage, float drawTime, float arrowVelocity) {
        this(properties, rangedDamage, drawTime, arrowVelocity, ARROW_ONLY);
    }

    /**
     * @param properties    物品属性，必须带物品 id（见类说明的注册方式）
     * @param rangedDamage  远程伤害（满蓄时）
     * @param drawTime      满蓄秒数；不大于 0 则用 {@link #BASELINE_DRAW_TIME}
     * @param arrowVelocity 满蓄时的初速度；不大于 0 则用 {@link #BASELINE_ARROW_VELOCITY}。
     *                      只影响弹道手感，不影响伤害（伤害在发射瞬间快照，与箭速解耦）。
     * @param ammo          弹药谓词。自定义弹药物品必须是 ArrowItem 的子类，
     *                      否则原版 createProjectile 会把它当成普通箭射出去。
     */
    public IbxBowItem(Item.Properties properties, float rangedDamage, float drawTime, float arrowVelocity,
                     Predicate<ItemStack> ammo) {
        super(properties);
        this.stats = new IbxRangedWeaponStats(rangedDamage, drawTime, arrowVelocity,
                BASELINE_DRAW_TIME, BASELINE_ARROW_VELOCITY);
        this.ammo = ammo;
    }

    @Override
    public IbxRangedWeaponStats stats() {
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

    /**
     * 发射前调用一次（服务端，已过发射门槛、马上要射）：子类在这里结算这一发的额外代价。
     * 返回的处理会在每支弹射物写好伤害快照之后、生成进世界之前调用（多重射击每支都调），
     * 可以改伤害快照（{@link RangedWeaponApi#setAmmoRangedDamage}）、给弹射物挂数据；返回 null 表示不额外处理。
     *
     * @param chargePercent 这一发的蓄力百分比，拉满为 1
     */
    protected @Nullable Consumer<Projectile> prepareShot(ServerLevel level, Player player, ItemStack stack,
                                                         float chargePercent) {
        return null;
    }

    /** 线性蓄力百分比：已蓄力 tick ÷ 满蓄 tick，clamp 到 1。 */
    public float getChargePercent(int chargeTicks, LivingEntity shooter, ItemStack stack) {
        return RangedWeaponApi.getChargePercent(chargeTicks, shooter, stack);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // 副手禁用：修饰符只挂主手，副手射击读不到武器数值。
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        IbxRangedWeaponItem.appendAutoFireTooltip(stack, tooltip);
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        RangedWeaponApi.tickAutoFire(entity, stack, remainingTicks);
    }

    /** 返回值同原版：真正射出才算这次使用成功（原版据此结算 use_cooldown 等组件效果）。 */
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        ItemStack ammoStack = player.getProjectile(stack);
        if (ammoStack.isEmpty()) {
            return false;
        }

        int charge = this.getUseDuration(stack, entity) - timeLeft;
        charge = EventHooks.onArrowLoose(stack, level, player, charge, true);
        if (charge < 0) {
            return false;
        }

        float pct = this.getChargePercent(charge, player, stack);
        if (pct < this.getMinCharge(stack)) {
            return false; // 低于门槛：不发射、不消耗
        }

        List<ItemStack> projectiles = draw(stack, ammoStack, player);
        if (level instanceof ServerLevel serverLevel && !projectiles.isEmpty()) {
            // 箭上只带"这一箭的基础远程伤害"：属性值 × 蓄力百分比，不乘任何倍率。
            // 暴击在这里掷骰（服务端），结果记在箭的原版暴击标记上，顺带白拿原版的暴击粒子；
            // 真正的伤害倍率由 DamageHandler 在命中时叠加。
            float damage = RangedWeaponApi.getRangedDamage(player, stack) * pct;
            Consumer<Projectile> extra = this.prepareShot(serverLevel, player, stack, pct);
            this.shootWithDamage(serverLevel, player, player.getUsedItemHand(), stack, projectiles,
                    this.getArrowVelocity(stack) * pct, 1.0F, CriticalStrikeApi.roll(player), null, damage, extra);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + pct * 0.5F);
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    /**
     * 原版 {@code ProjectileWeaponItem#shoot} 的复刻，多一个 damage 参数：每支箭生成后写入伤害快照。
     * 原版 createProjectile 没有蓄力参数，所以只能在这一层塞进去；散射、耐久、多重射击结构原样保留。
     * 生成同原版走 {@link Projectile#spawnProjectile}：火矢等附魔的"弹射物生成"效果是在那里触发的。
     *
     * @param extra {@link #prepareShot} 返回的额外处理，写完伤害快照后对每支弹射物调用；null 不处理
     */
    protected void shootWithDamage(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon,
                                   List<ItemStack> projectileItems, float velocity, float inaccuracy, boolean isCrit,
                                   @Nullable LivingEntity target, float damage, @Nullable Consumer<Projectile> extra) {
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
            int index = i;

            Projectile projectile = this.createProjectile(level, shooter, weapon, ammoStack, isCrit);
            RangedWeaponApi.setAmmoRangedDamage(projectile, damage);
            if (extra != null) {
                extra.accept(projectile);
            }
            Projectile.spawnProjectile(projectile, level, ammoStack,
                    spawned -> this.shootProjectile(shooter, spawned, index, velocity, inaccuracy, angle, target));

            weapon.hurtAndBreak(this.getDurabilityUse(ammoStack), shooter, hand.asEquipmentSlot());
            if (weapon.isEmpty()) {
                break;
            }
        }
    }
}
