package ibx.lib.item;

import ibx.lib.IbxLib;
import ibx.lib.api.CriticalStrikeApi;
import ibx.lib.api.ShieldApi;
import ibx.lib.item.general.IbxShieldItem;
import ibx.lib.target.Faction;
import ibx.lib.target.TargetApi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;
import java.util.function.Consumer;

/**
 * Ibx 双手盾：双手拿，主手多出普通攻击和盾击。格挡部分见 {@link IbxShieldItem}。
 *
 * <ul>
 *   <li>双手和普通攻击的动作都交给 Better Combat：每面双手盾的 {@code weapon_attributes} 写
 *       {@code {"parent": "ibx_lib:two_handed_shield"}}（双手 + 横扫 / 下砸两段）。主手拿着它时副手被当成空手，放在副手也当空手；
 *       没装 BC 时不限双手，副手照常拿东西、放在副手就是原版盾，普通攻击是原版挥臂。</li>
 *   <li>主手普通攻击：烤 {@code attack_damage} / {@code attack_speed} 修饰符（基础 id，tooltip 同剑的深绿行），左键就是原版普通攻击。
 *       盾的其他被动属性走构造参数 {@code extraModifiers}（注册时调 {@code Properties#attributes} 会被这里覆盖）。</li>
 *   <li>盾击：主手举盾中按左键。原版使用物品期间吞掉攻击点击，所以客户端吃掉攻击键发包（{@code client.ShieldInput}），
 *       服务端 {@link ShieldApi#tryBash} 校验后调 {@link #bash}：进独立冷却（{@link #bashCooldownTicks}，不能用物品冷却——
 *       那会让盾举不起来）、挥臂、放动作（{@link #bashAnimation}，默认 lib 的肩顶 {@code shield.bash}）、趁盾还举着结算
 *       {@link #onBash}，最后盾失效 {@link #bashDisableTicks}（物品冷却 + 停止使用，同斧子打掉但不响破碎音；
 *       右键还按着的话失效一过客户端会自动重新举起）。</li>
 *   <li>默认效果：掷一次暴击（lib 的暴击率 / 暴击伤害），前方 {@link #bashRange} 格、左右各 {@link #bashHalfAngle} 度扇形里看得见的
 *       每个目标挨一下盾的攻击伤害（走原版 {@code stabAttack}）+ 击退。数值都是可重写的取值方法；要换判定、单个目标的结算、
 *       打中后的附加效果（比如定身）分别重写 {@link #bashTargets} / {@link #bashHit} / {@link #onBashHit}，整个换掉重写 {@link #onBash}。</li>
 *   <li>举着它时左键只能是盾击：客户端吃掉按键，服务端取消这期间的普通攻击（{@link ShieldApi}）。</li>
 *   <li>主手举盾时放双手格挡动作 {@link #guardAnimation}（{@code client.ShieldAnimations}），盾击从这个姿势起手。物品定义里
 *       {@code using_item} 两个分支除了第一人称，其它显示都要一样（lib 的 {@code general_two_handed_shield_hand} / {@code general_two_handed_shield_block}，
 *       后者只把第一人称换成原版举盾）：第三人称不一样，盾击那一下停止使用、换回不举盾的显示，盾会在手里转 90°；
 *       物品栏里的这面盾举着时也走举盾分支，缺了 {@code gui} 显示图标就会变。</li>
 * </ul>
 */
public class IbxTwoHandedShieldItem extends IbxShieldItem {
    /** 盾击冷却 tick（独立计时），{@link #bashCooldownTicks} 的默认值。 */
    public static final int BASH_COOLDOWN_TICKS = 60;
    /** 盾击后盾失效 tick，{@link #bashDisableTicks} 的默认值。 */
    public static final int BASH_DISABLE_TICKS = 10;
    /** 盾击打击范围：前方多少格，{@link #bashRange} 的默认值。 */
    public static final double BASH_RANGE = 2.5;
    /** 盾击打击范围：正前方左右各多少度，{@link #bashHalfAngle} 的默认值。 */
    public static final float BASH_HALF_ANGLE = 45.0F;
    /** 盾击击退：受伤本身的击退之外再推这么多，{@link #bashKnockback} 的默认值。 */
    public static final float BASH_KNOCKBACK = 0.6F;

    private static final Identifier BASH_ANIMATION = Identifier.fromNamespaceAndPath(IbxLib.MODID, "shield.bash");
    private static final Identifier GUARD_ANIMATION = Identifier.fromNamespaceAndPath(IbxLib.MODID, "shield.guard");

    /** 格挡数值全原版，没有额外属性。 */
    public IbxTwoHandedShieldItem(Item.Properties properties, float attackDamage, float attackSpeed) {
        this(properties, 0.0F, 0.0F, 0.0F, attackDamage, attackSpeed, modifiers -> {});
    }

    /** 格挡数值全原版。 */
    public IbxTwoHandedShieldItem(Item.Properties properties, float attackDamage, float attackSpeed,
                                  Consumer<ItemAttributeModifiers.Builder> extraModifiers) {
        this(properties, 0.0F, 0.0F, 0.0F, attackDamage, attackSpeed, extraModifiers);
    }

    /** 没有额外属性。 */
    public IbxTwoHandedShieldItem(Item.Properties properties, float blockDelay, float blockFactor, float blockAngle,
                                  float attackDamage, float attackSpeed) {
        this(properties, blockDelay, blockFactor, blockAngle, attackDamage, attackSpeed, modifiers -> {});
    }

    /**
     * @param properties     注册时给好耐久、修复材料的属性
     * @param blockDelay     举盾几秒后生效；不大于 0 用原版值
     * @param blockFactor    挡掉的比例 0~1；不大于 0 用原版值
     * @param blockAngle     水平格挡角度；不大于 0 用原版值
     * @param attackDamage   主手普通攻击的伤害基数
     * @param attackSpeed    主手攻速基数
     * @param extraModifiers 往同一份 {@code attribute_modifiers} 里追加的修饰符（盾的被动属性，比如法术抗性）
     */
    public IbxTwoHandedShieldItem(Item.Properties properties, float blockDelay, float blockFactor, float blockAngle,
                                  float attackDamage, float attackSpeed, Consumer<ItemAttributeModifiers.Builder> extraModifiers) {
        super(properties.attributes(modifiers(attackDamage, attackSpeed, extraModifiers)), blockDelay, blockFactor, blockAngle);
    }

    private static ItemAttributeModifiers modifiers(float attackDamage, float attackSpeed,
                                                    Consumer<ItemAttributeModifiers.Builder> extraModifiers) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        extraModifiers.accept(builder);
        return builder.build();
    }

    // ---------------------------------------------------------------- 盾击数值（具体某面盾重写来调）

    /** 盾击冷却 tick（独立计时）。 */
    protected int bashCooldownTicks() {
        return BASH_COOLDOWN_TICKS;
    }

    /** 盾击后盾失效 tick。 */
    protected int bashDisableTicks() {
        return BASH_DISABLE_TICKS;
    }

    /** 盾击打击范围：前方多少格。 */
    protected double bashRange() {
        return BASH_RANGE;
    }

    /** 盾击打击范围：正前方左右各多少度。 */
    protected float bashHalfAngle() {
        return BASH_HALF_ANGLE;
    }

    /** 盾击击退：受伤本身的击退之外再推这么多（击退附魔另加）。 */
    protected float bashKnockback() {
        return BASH_KNOCKBACK;
    }

    // ---------------------------------------------------------------- 盾击

    /** 盾击框架。只在服务端调（{@link ShieldApi#tryBash} 校验过举盾中、不在冷却再调这里）。 */
    public void bash(ServerPlayer player, ItemStack stack) {
        ShieldApi.markBash(player, this.bashCooldownTicks());
        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, player.getSoundSource(), 1.0F, 1.0F);
        // 趁盾还举着结算：原版刺击在这只手正在用物品时不按攻击冷却打折
        this.onBash(player, stack);
        // 盾失效：同斧子打掉（物品冷却 + 停止使用），但不走 BlocksAttacks.disable，免得响破碎音
        player.getCooldowns().addCooldown(stack, this.bashDisableTicks());
        player.stopUsingItem();
        player.onAttack();
    }

    /**
     * 盾击的效果（冷却、动作已处理；这时盾还举着，结算完才放下）。默认：掷一次暴击，{@link #bashTargets} 里的每个目标挨一下
     * {@link #bashHit}，打中的再走 {@link #onBashHit}。
     */
    protected void onBash(ServerPlayer player, ItemStack stack) {
        boolean critical = CriticalStrikeApi.roll(player);
        for (LivingEntity target : this.bashTargets(player)) {
            if (this.bashHit(player, stack, target, critical)) {
                this.onBashHit(player, stack, target, critical);
            }
        }
    }

    /** 盾击打到谁：前方 {@link #bashRange} 格、左右各 {@link #bashHalfAngle} 度的扇形里看得见的敌方（{@link TargetApi#inFan}），近的先挨；无敌的、打不中的（{@code canBeHitByProjectile}）不算。 */
    protected List<LivingEntity> bashTargets(ServerPlayer player) {
        return TargetApi.inFan(player, this.bashRange(), this.bashHalfAngle(), true,
                TargetApi.affects(player, Faction.ENEMY, false).and(target -> !target.isInvulnerable() && target.canBeHitByProjectile()));
    }

    /**
     * 盾击打一个目标，返回打中没有。伤害 = 盾的攻击伤害，暴击时乘 lib 的暴击倍率；走原版 {@code stabAttack}
     * （附魔、耐久、统计同普通攻击，不吃横扫）。目标真受了伤才算打中（被盾挡住、无敌帧里不算），打中再一次性给击退。
     *
     * <p>击退不交给 {@code stabAttack}：它对玩家目标会立刻发速度包再把服务端速度还原，之后补的击退发出去会把它覆盖，
     * 打玩家时就只剩一部分。这里在受伤本身的击退之外一次给齐（{@link #bashKnockback} + 击退附魔）。</p>
     */
    protected boolean bashHit(ServerPlayer player, ItemStack stack, LivingEntity target, boolean critical) {
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (critical) {
            damage *= CriticalStrikeApi.getMultiplier(player);
        }
        if (!player.stabAttack(EquipmentSlot.MAINHAND, target, damage, true, false, false)) {
            return false;
        }
        if (critical) {
            CriticalStrikeApi.spawnEffects(target);
        }
        float yRot = player.getYRot() * Mth.DEG_TO_RAD;
        target.knockback(this.bashKnockback() + bonusKnockback(player, stack, target), Mth.sin(yRot), -Mth.cos(yRot));
        target.hurtMarked = true;
        return true;
    }

    /**
     * 盾击打中一个目标之后（伤害、击退已结算）的附加效果，默认没有。具体某面盾在这里加，比如给目标挂定身、点燃、减速。
     */
    protected void onBashHit(ServerPlayer player, ItemStack stack, LivingEntity target, boolean critical) {}

    /** 击退属性和击退附魔加的那份，同原版 {@code LivingEntity#getKnockback}（受保护，调不到）。 */
    private static float bonusKnockback(ServerPlayer player, ItemStack stack, LivingEntity target) {
        DamageSource source = stack.getDamageSource(player, () -> player.damageSources().playerAttack(player));
        float knockback = (float) player.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        return EnchantmentHelper.modifyKnockback(player.level(), stack, target, source, knockback) / 2.0F;
    }

    /** 盾击放一遍的动作，从 {@link #guardAnimation} 的姿势起手。 */
    public Identifier bashAnimation() {
        return BASH_ANIMATION;
    }

    /** 主手举盾格挡时循环放的动作（只管第三人称，第一人称照原版画举盾）。 */
    public Identifier guardAnimation() {
        return GUARD_ANIMATION;
    }
}
