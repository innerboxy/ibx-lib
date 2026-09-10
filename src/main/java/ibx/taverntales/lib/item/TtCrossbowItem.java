package ibx.taverntales.lib.item;

import ibx.taverntales.lib.api.CriticalStrikeApi;
import ibx.taverntales.lib.api.RangedWeaponApi;
import ibx.taverntales.lib.item.ranged_weapon.RangedWeaponStats;
import ibx.taverntales.lib.item.ranged_weapon.RangedWeaponItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Tt 弩：可复用的弩物品类，数值由构造参数给定。
 *
 * <ul>
 *   <li>命中伤害 = 射手 ranged_damage（弩天生满装发射），发射瞬间快照到箭上，命中时覆盖原版伤害。</li>
 *   <li>装填时长由射手的 draw_time ÷ draw_speed 决定。原版的 {@code getChargeDuration} 是 static 无法重写，
 *       所以重写了调用它的三个入口：{@link #getUseDuration}、{@link #onUseTick}、{@link #releaseUsing}；
 *       发射流程（shoot / performShooting）不动，多重射击等原版机制照旧。</li>
 *   <li>箭速由 {@link #getArrowVelocity} 决定，只管飞行手感，不影响伤害；烟花火箭仍走原版速度。</li>
 *   <li>装填音效改为按 tick 等值判断，不再用原版那两个所有玩家共用的 Item 级标记字段。</li>
 *   <li>只能主手使用。</li>
 * </ul>
 */
public class TtCrossbowItem extends CrossbowItem implements RangedWeaponItem {
    /** 本类型的基准装填秒数（原版弩 25 tick）。 */
    public static final float BASELINE_DRAW_TIME = 1.25F;
    /** 本类型的基准箭速（原版弩射箭 3.15）。 */
    public static final float BASELINE_ARROW_VELOCITY = 3.15F;
    /** 原版在装填时长之外多留的使用 tick，作为松手判定的余量。 */
    private static final int RELEASE_SLACK_TICKS = 3;
    private static final float START_SOUND_PERCENT = 0.2F;
    private static final float MID_SOUND_PERCENT = 0.5F;
    private static final ChargingSounds DEFAULT_SOUNDS = new ChargingSounds(
            Optional.of(SoundEvents.CROSSBOW_LOADING_START),
            Optional.of(SoundEvents.CROSSBOW_LOADING_MIDDLE),
            Optional.of(SoundEvents.CROSSBOW_LOADING_END));

    private final RangedWeaponStats stats;
    private final Predicate<ItemStack> ammo;
    private final Predicate<ItemStack> heldAmmo;

    /**
     * 便捷构造：只给耐久、伤害、装填秒数，箭速用基准值。
     *
     * @param durability 最大耐久（原版弩是 465）；不大于 0 则不会损坏
     */
    public TtCrossbowItem(int durability, float rangedDamage, float drawTime) {
        this(durability, rangedDamage, drawTime, 0.0F);
    }

    /**
     * 便捷构造：耐久、伤害、装填秒数、箭速。要设稀有度、防火等别的物品属性时，
     * 改用带 {@link Item.Properties} 的那个构造器。
     *
     * @param durability    最大耐久（原版弩是 465）；不大于 0 则不会损坏
     * @param arrowVelocity 发射初速度；不大于 0 则用 {@link #BASELINE_ARROW_VELOCITY}
     */
    public TtCrossbowItem(int durability, float rangedDamage, float drawTime, float arrowVelocity) {
        this(RangedWeaponItem.properties(durability), rangedDamage, drawTime, arrowVelocity);
    }

    public TtCrossbowItem(Item.Properties properties, float rangedDamage, float drawTime) {
        this(properties, rangedDamage, drawTime, 0.0F);
    }

    public TtCrossbowItem(Item.Properties properties, float rangedDamage, float drawTime, float arrowVelocity) {
        this(properties, rangedDamage, drawTime, arrowVelocity, ARROW_ONLY, true);
    }

    /**
     * @param rangedDamage   远程伤害
     * @param drawTime       装填秒数；不大于 0 则用 {@link #BASELINE_DRAW_TIME}
     * @param arrowVelocity  发射初速度；不大于 0 则用 {@link #BASELINE_ARROW_VELOCITY}。
     *                       只影响弹道手感，不影响伤害（伤害在发射瞬间快照，与箭速解耦）。
     * @param ammo           可从背包取用的弹药谓词。自定义弹药物品必须是 ArrowItem 的子类，
     *                       否则原版 createProjectile 会把它当成普通箭射出去。
     * @param allowFireworks 手持的烟花火箭是否可作弹药（同原版弩）
     */
    public TtCrossbowItem(Item.Properties properties, float rangedDamage, float drawTime, float arrowVelocity,
                          Predicate<ItemStack> ammo, boolean allowFireworks) {
        super(properties);
        this.stats = new RangedWeaponStats(rangedDamage, drawTime, arrowVelocity,
                BASELINE_DRAW_TIME, BASELINE_ARROW_VELOCITY);
        this.ammo = ammo;
        this.heldAmmo = allowFireworks ? ammo.or(stack -> stack.is(Items.FIREWORK_ROCKET)) : ammo;
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

    @Override
    @SuppressWarnings("deprecation")
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.heldAmmo;
    }

    /** 发射初速度。子类要按物品栈动态改箭速就重写这里。 */
    protected float getArrowVelocity(ItemStack stack) {
        return this.stats.arrowVelocity();
    }

    /**
     * 用武器自己的箭速替换调用方传进来的速度。
     *
     * <p>原版算速度的 {@code getShootingPower} 是 private static，改不了，只能在这一层换：
     * 玩家射击（{@code use}）和生物射击（{@code CrossbowAttackMob#performCrossbowAttack}）
     * 都要经过本方法，所以两条路一起生效——<b>包括掠夺者、疣猪兽用它射出的弩箭</b>
     * （原版生物固定按 1.6 发射，改后按武器箭速走）。</p>
     *
     * <p>装的是烟花火箭时保持原样：烟花本来就是另一套速度（1.6），且它不是箭。</p>
     */
    @Override
    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand, ItemStack weapon,
                                float velocity, float inaccuracy, @Nullable LivingEntity target) {
        ChargedProjectiles charged = weapon.get(DataComponents.CHARGED_PROJECTILES);
        boolean firework = charged != null && charged.contains(Items.FIREWORK_ROCKET);
        float actualVelocity = firework ? velocity : this.getArrowVelocity(weapon);
        super.performShooting(level, shooter, hand, weapon, actualVelocity, inaccuracy, target);
    }

    /** 装填音效。原版同名方法是包私有的，子类无法重写，这里另开钩子。 */
    protected ChargingSounds getChargingSoundsFor(ItemStack stack) {
        return EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS)
                .orElse(DEFAULT_SOUNDS);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 副手禁用：装填与发射都从 use() 进，这里一并挡住，主手装填后挪到副手也射不出去。
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return RangedWeaponApi.getFullDrawTicks(entity, stack) + RELEASE_SLACK_TICKS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (level.isClientSide) {
            return;
        }
        int fullTicks = RangedWeaponApi.getFullDrawTicks(entity, stack);
        int elapsed = this.getUseDuration(stack, entity) - remainingTicks;
        ChargingSounds sounds = this.getChargingSoundsFor(stack);
        if (elapsed == Mth.ceil(fullTicks * START_SOUND_PERCENT)) {
            playChargingSound(level, entity, sounds.start(), SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        if (elapsed == Mth.ceil(fullTicks * MID_SOUND_PERCENT)) {
            playChargingSound(level, entity, sounds.mid(), SoundSource.PLAYERS, 0.5F, 1.0F);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int elapsed = this.getUseDuration(stack, entity) - timeLeft;
        if (elapsed >= RangedWeaponApi.getFullDrawTicks(entity, stack) && !isCharged(stack)
                && tryLoadProjectiles(entity, stack)) {
            playChargingSound(level, entity, this.getChargingSoundsFor(stack).end(), entity.getSoundSource(),
                    1.0F, 1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
        }
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        // 原版给玩家射出的弩箭是恒打暴击标记的，这里改成按 critical_chance 掷骰（本方法只在服务端跑）。
        // 标记只用来拿原版的暴击粒子、并告诉 DamageHandler 这一箭暴了，伤害倍率在命中时才乘。
        Projectile projectile = super.createProjectile(level, shooter, weapon, ammo, CriticalStrikeApi.roll(shooter));
        if (projectile instanceof AbstractArrow) {
            RangedWeaponApi.setAmmoRangedDamage(projectile, RangedWeaponApi.getRangedDamage(shooter, weapon));
        }
        return projectile;
    }

    /** 原版 private static 同名方法的复刻：从射手处取弹药装进弩。 */
    private static boolean tryLoadProjectiles(LivingEntity shooter, ItemStack crossbow) {
        List<ItemStack> list = draw(crossbow, shooter.getProjectile(crossbow), shooter);
        if (list.isEmpty()) {
            return false;
        }
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(list));
        return true;
    }

    private static void playChargingSound(Level level, LivingEntity entity, Optional<Holder<SoundEvent>> sound,
                                          SoundSource source, float volume, float pitch) {
        sound.ifPresent(holder -> level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                holder.value(), source, volume, pitch));
    }
}
