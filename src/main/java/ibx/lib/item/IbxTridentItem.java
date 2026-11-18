package ibx.lib.item;

import ibx.lib.api.CriticalStrikeApi;
import ibx.lib.api.RangedWeaponApi;
import ibx.lib.compat.BetterCombatCompat;
import ibx.lib.entity.IbxThrownTrident;
import ibx.lib.item.general.IbxMeleeWeaponItem;
import ibx.lib.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Ibx 三叉戟：能近战、能掷出去的戟，操作同原版三叉戟。
 *
 * <ul>
 *   <li>规则照原版三叉戟自己写组件（原版没有 {@code Properties#trident}）：材料给耐久、修复材料、附魔能力和伤害加成；
 *       每次攻击掉 1 点耐久、打不掉盾、创造模式拿着不挖方块。主手挂攻击伤害、攻速（基础 id，tooltip 是深绿合并行），
 *       再挂一条远程伤害（{@link AttributeRegistry#RANGED_DAMAGE}，基础 id），就是掷出去那一下的伤害。</li>
 *   <li>右键按住满 {@value #THROW_THRESHOLD_TIME} tick 松手掷出：掉 1 点耐久，只剩 1 点耐久时掷不出去；有激流时改成在水里、雨里冲刺。
 *       只认主手（远程伤害只挂主手，副手读不到）。举戟的姿势跟着原版的使用动画走。</li>
 *   <li>掷出去的是这把戟配套的 {@link IbxThrownTrident}（构造时必须给实体类型，由注册这把戟的模组自己注册）：
 *       伤害是出手那一刻投掷者的远程伤害，暴击在出手时掷骰，命中结算同本模组的弓箭。</li>
 *   <li>左键动作默认用 Better Combat 的 {@code bettercombat:trident}（单手直刺）。</li>
 *   <li>自动进 {@code #c:tools/trident}、{@code #c:tools/ranged_weapon}（同原版三叉戟，近战、远程两个标签都在）
 *       和 {@code #ibx_lib:enchantable/trident}（能附原版三叉戟的全部附魔：忠诚、激流、引雷、穿刺）。</li>
 * </ul>
 * 有自己机制的戟继承本类，重写 {@link #onThrow}、{@link #onThrownHit}；要改飞行本身就继承 {@link IbxThrownTrident}。
 * 其余见 {@link IbxMeleeWeaponItem}。
 *
 * <pre>{@code
 * // 实体类型：直接用基类，或者用自己的子类
 * public static final DeferredHolder<EntityType<?>, EntityType<IbxThrownTrident>> MY_TRIDENT = ENTITIES.register("my_trident",
 *         key -> IbxThrownTrident.type(IbxThrownTrident::new).build(ResourceKey.create(Registries.ENTITY_TYPE, key)));
 *
 * // 物品：显示 9 攻击伤害（6 + 铁的 2 + 空手 1）、1.1 攻速、8 远程伤害，同原版三叉戟
 * ITEMS.registerItem("my_trident", p -> new IbxTridentItem(p, ToolMaterial.IRON, 6.0F, -2.9F, 8.0F, MY_TRIDENT));
 * }</pre>
 * 客户端还要给这个实体类型注册渲染器（{@code ibx.lib.client.renderer.IbxThrownTridentRenderer}）。
 */
public class IbxTridentItem extends IbxMeleeWeaponItem {
    /** 左键动作的默认预设。 */
    public static final Identifier PRESET = BetterCombatCompat.builtin("trident");
    /** 右键按住至少这么多 tick，松手才掷得出去（同原版）。 */
    public static final int THROW_THRESHOLD_TIME = 10;
    /** 掷出去的初速度（同原版）。 */
    public static final float THROW_POWER = 2.5F;

    private final Supplier<? extends EntityType<? extends IbxThrownTrident>> thrownType;

    /**
     * @param properties   物品属性，必须带物品 id
     * @param material     材料：耐久、修复材料、附魔能力，攻击伤害再加上它的加成
     * @param attackDamage 伤害基数：+ 材料加成 + 空手 1 = 显示的攻击伤害
     * @param attackSpeed  攻速基数：+ 玩家的 4 = 显示的攻速
     * @param rangedDamage 远程伤害：掷出去打中的伤害
     * @param thrownType   掷出去的实体类型，必须给
     */
    public IbxTridentItem(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed, float rangedDamage,
                          Supplier<? extends EntityType<? extends IbxThrownTrident>> thrownType) {
        super(properties, p -> trident(p, material, attackDamage, attackSpeed, rangedDamage), PRESET);
        this.thrownType = Objects.requireNonNull(thrownType, "thrownType");
    }

    private static Item.Properties trident(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed,
                                           float rangedDamage) {
        return properties.durability(material.durability())
                .repairable(material.repairItems())
                .enchantable(material.enchantmentValue())
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                                attackDamage + material.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                                attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(AttributeRegistry.RANGED_DAMAGE, new AttributeModifier(AttributeRegistry.BASE_RANGED_DAMAGE_ID,
                                rangedDamage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .build())
                .component(DataComponents.TOOL, new Tool(List.of(), 1.0F, 2, false))
                .component(DataComponents.WEAPON, new Weapon(1));
    }

    /** 这把戟掷出去的实体类型。 */
    public EntityType<? extends IbxThrownTrident> getThrownType() {
        return this.thrownType.get();
    }

    /**
     * 掷出去之前调一次（服务端）：{@code trident} 已经写好位置、主人、远程伤害和暴击，还没设方向和速度、没进世界。
     * 可以改它的伤害（{@link IbxThrownTrident#setRangedDamage}）、给它挂数据。
     *
     * @param thrown 掷出去的那一把（从手里分出来的一份，耐久已经扣过）
     */
    protected void onThrow(ServerLevel level, LivingEntity thrower, ItemStack thrown, IbxThrownTrident trident) {}

    /** 掷出去的戟打中生物并造成伤害之后调（服务端），附魔的命中效果已经结算。 */
    public void onThrownHit(ServerLevel level, IbxThrownTrident trident, LivingEntity target, DamageSource source) {}

    /** 造出掷出去的那个实体。 */
    protected IbxThrownTrident createThrown(ServerLevel level, LivingEntity thrower, ItemStack thrown, float rangedDamage, boolean critical) {
        IbxThrownTrident trident = this.getThrownType().create(level, EntitySpawnReason.TRIGGERED);
        if (trident == null) {
            throw new IllegalStateException("Thrown trident entity type " + this.getThrownType() + " is disabled");
        }
        trident.initThrown(thrower, thrown, rangedDamage);
        trident.setCritArrow(critical);
        this.onThrow(level, thrower, thrown, trident);
        return trident;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.OFF_HAND || stack.nextDamageWillBreak()) {
            return InteractionResult.FAIL;
        }
        if (EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0.0F && !player.isInWaterOrRain()) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player) || this.getUseDuration(stack, entity) - remainingTime < THROW_THRESHOLD_TIME) {
            return false;
        }
        float riptide = EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
        if (riptide > 0.0F && !(player.isInWaterOrRain() && !player.isPassenger()) || stack.nextDamageWillBreak()) {
            return false;
        }
        Holder<SoundEvent> sound = EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.TRIDENT_SOUND)
                .orElse(SoundEvents.TRIDENT_THROW);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (level instanceof ServerLevel serverLevel) {
            // 远程伤害、暴击都在戟还拿在手里时读：离手之后主手的修饰符就没了
            float rangedDamage = RangedWeaponApi.getRangedDamage(player, stack);
            stack.hurtWithoutBreaking(1, player);
            if (riptide == 0.0F) {
                boolean critical = CriticalStrikeApi.roll(player);
                ItemStack thrown = stack.consumeAndReturn(1, player);
                IbxThrownTrident trident = Projectile.spawnProjectileFromRotation(
                        (spawnLevel, owner, item) -> this.createThrown(spawnLevel, owner, item, rangedDamage, critical),
                        serverLevel, thrown, player, 0.0F, THROW_POWER, 1.0F);
                if (player.hasInfiniteMaterials()) {
                    trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
                level.playSound(null, trident, sound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                return true;
            }
        }
        if (riptide > 0.0F) {
            float yRot = player.getYRot();
            float xRot = player.getXRot();
            float xd = -Mth.sin(yRot * Mth.DEG_TO_RAD) * Mth.cos(xRot * Mth.DEG_TO_RAD);
            float yd = -Mth.sin(xRot * Mth.DEG_TO_RAD);
            float zd = Mth.cos(yRot * Mth.DEG_TO_RAD) * Mth.cos(xRot * Mth.DEG_TO_RAD);
            float dist = Mth.sqrt(xd * xd + yd * yd + zd * zd);
            player.push(xd * riptide / dist, yd * riptide / dist, zd * riptide / dist);
            player.startAutoSpinAttack(20, 8.0F, stack);
            if (player.onGround()) {
                player.move(MoverType.SELF, new Vec3(0.0, 1.1999999F, 0.0));
            }
            level.playSound(null, player, sound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        return ItemAbilities.DEFAULT_TRIDENT_ACTIONS.contains(ability);
    }
}
