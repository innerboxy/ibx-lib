package ibx.lib.entity;

import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.IbxTridentItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * 掷出去的 Ibx 三叉戟：{@link IbxTridentItem} 掷出的实体的基类。行为照原版的 {@code ThrownTrident} 重写了一份
 * （原版那个类写死了 {@code minecraft:trident} 这个实体类型和 8 点伤害，没法直接继承来用）：
 *
 * <ul>
 *   <li>只打中一个目标，打中后落下；水里不减速；只有主人能捡，捡回来是掷出去的那一把（附魔、耐久都在）。</li>
 *   <li>忠诚：打中东西或落地后飞回主人手里。</li>
 *   <li>伤害是出手时记下的远程伤害（{@link #getRangedDamage}），先过戟上的附魔（穿刺），再走本模组弓箭的命中结算
 *       （{@link ibx.lib.event.DamageHandler}：暴击倍率、难度缩放）。伤害类型是原版的 {@code minecraft:trident}。</li>
 *   <li>引雷：原版附魔的数据只认 {@code minecraft:trident} 这个实体类型，这里按同样的条件补上——雷暴时打中露天的生物、
 *       或者扎在露天的避雷针上，落一道闪电。</li>
 *   <li>掷出去的物品同步给客户端（{@link #getItem}），渲染器靠它画出这把戟自己的模型。</li>
 * </ul>
 *
 * 每把戟要有自己注册的实体类型：直接用本类（{@code IbxThrownTrident.type(IbxThrownTrident::new)}），或者继承本类改飞行、命中。
 * 子类要留 {@code (EntityType, Level)} 这个构造方法，物品靠实体类型造它，再调 {@link #initThrown}。
 */
public class IbxThrownTrident extends AbstractArrow {
    private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(IbxThrownTrident.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<ItemStack> ID_ITEM = SynchedEntityData.defineId(IbxThrownTrident.class, EntityDataSerializers.ITEM_STACK);

    /** 没记远程伤害时（指令召出来的）用的伤害，同原版三叉戟。 */
    public static final float FALLBACK_RANGED_DAMAGE = 8.0F;
    /** 引雷落雷的音量（同原版）。 */
    private static final float THUNDER_VOLUME = 5.0F;

    private boolean dealtDamage;
    private float rangedDamage = FALLBACK_RANGED_DAMAGE;
    /** 往回飞了多少 tick，只用来在刚掉头时放一次音效。 */
    private int returnTicks;

    public IbxThrownTrident(EntityType<? extends IbxThrownTrident> type, Level level) {
        super(type, level);
    }

    /** 实体类型的构造器，大小、追踪距离、同步间隔同原版三叉戟。 */
    public static <T extends IbxThrownTrident> EntityType.Builder<T> type(EntityType.EntityFactory<T> factory) {
        return EntityType.Builder.of(factory, MobCategory.MISC)
                .noLootTable()
                .sized(0.5F, 0.5F)
                .eyeHeight(0.13F)
                .clientTrackingRange(4)
                .updateInterval(20);
    }

    /**
     * 掷出去之前调（服务端）：摆到投掷者眼前、记下主人、掷出的物品和远程伤害。方向和速度由调用方再设。
     *
     * @param stack        掷出去的那一把，这里存一份拷贝
     * @param rangedDamage 打中的伤害（不含附魔、暴击）
     */
    public void initThrown(LivingEntity thrower, ItemStack stack, float rangedDamage) {
        this.setPos(thrower.getX(), thrower.getEyeY() - 0.1F, thrower.getZ());
        this.setPickupItemStack(stack.copy());
        this.applyComponentsFromItemStack(stack);
        this.setOwner(thrower);
        this.rangedDamage = rangedDamage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(ID_LOYALTY, (byte) 0);
        entityData.define(ID_ITEM, ItemStack.EMPTY);
    }

    /** 掷出去的那一把（客户端也读得到）；还没设过时是空的。 */
    public ItemStack getItem() {
        return this.entityData.get(ID_ITEM);
    }

    /** 打中的伤害：出手时投掷者的远程伤害，不含附魔、暴击。 */
    public float getRangedDamage() {
        return this.rangedDamage;
    }

    public void setRangedDamage(float rangedDamage) {
        this.rangedDamage = rangedDamage;
    }

    /** 已经打中过东西或者落地了：之后不再伤人，有忠诚就往回飞。 */
    public boolean hasDealtDamage() {
        return this.dealtDamage;
    }

    @Override
    protected void setPickupItemStack(ItemStack stack) {
        super.setPickupItemStack(stack);
        if (this.level() instanceof ServerLevel level) {
            ItemStack origin = this.getPickupItemStackOrigin();
            this.entityData.set(ID_ITEM, origin.copy());
            this.entityData.set(ID_LOYALTY,
                    (byte) Mth.clamp(EnchantmentHelper.getTridentReturnToOwnerAcceleration(level, origin, this), 0, 127));
        }
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity owner = this.getOwner();
        int loyalty = this.entityData.get(ID_LOYALTY);
        if (loyalty > 0 && (this.dealtDamage || this.isNoPhysics()) && owner != null) {
            if (!this.isAcceptableReturnOwner(owner)) {
                if (this.level() instanceof ServerLevel level && this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(level, this.getPickupItem(), 0.1F);
                }
                this.discard();
            } else {
                if (!(owner instanceof Player) && this.position().distanceTo(owner.getEyePosition()) < owner.getBbWidth() + 1.0) {
                    this.discard();
                    return;
                }
                this.setNoPhysics(true);
                Vec3 toOwner = owner.getEyePosition().subtract(this.position());
                this.setPosRaw(this.getX(), this.getY() + toOwner.y * 0.015 * loyalty, this.getZ());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.95).add(toOwner.normalize().scale(0.05 * loyalty)));
                if (this.returnTicks == 0) {
                    this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
                }
                this.returnTicks++;
            }
        }

        super.tick();
    }

    private boolean isAcceptableReturnOwner(Entity owner) {
        return owner.isAlive() && !(owner instanceof ServerPlayer && owner.isSpectator());
    }

    @Override
    protected @Nullable EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
        return this.dealtDamage ? null : super.findHitEntity(from, to);
    }

    @Override
    protected Collection<EntityHitResult> findHitEntities(Vec3 from, Vec3 to) {
        EntityHitResult hit = this.findHitEntity(from, to);
        return hit != null ? List.of(hit) : List.of();
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        Entity entity = hitResult.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().trident(this, owner == null ? this : owner);
        float damage = this.rangedDamage;
        if (this.level() instanceof ServerLevel level) {
            damage = EnchantmentHelper.modifyDamage(level, this.getWeaponItem(), entity, source, damage);
            // 伤害结算会用箭上记的远程伤害盖掉传进去的数；记成过完附魔的值，穿刺的加成才留得住
            RangedWeaponApi.setAmmoRangedDamage(this, damage);
        }

        this.dealtDamage = true;
        boolean hurt = entity.hurtOrSimulate(source, damage);
        // 暴击只管打中的这一下：标记留着的话，落下、飞回来的路上还会一直冒暴击粒子
        this.setCritArrow(false);
        if (hurt) {
            if (entity.is(EntityType.ENDERMAN)) {
                return;
            }
            if (this.level() instanceof ServerLevel level) {
                EnchantmentHelper.doPostAttackEffectsWithItemSourceOnBreak(level, entity, source, this.getWeaponItem(),
                        weapon -> this.kill(level));
                if (this.hasChanneling(level) && level.isThundering() && level.canSeeSky(entity.blockPosition())) {
                    this.summonLightning(level, entity.position(), entity.getSoundSource());
                }
            }
            if (entity instanceof LivingEntity living) {
                this.doKnockback(living, source);
                this.doPostHurtEffects(living);
                if (this.level() instanceof ServerLevel level && this.getWeaponItem().getItem() instanceof IbxTridentItem item) {
                    item.onThrownHit(level, this, living, source);
                }
            }
        }

        this.deflect(ProjectileDeflection.REVERSE, entity, this.owner, false);
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.02, 0.2, 0.02));
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected void hitBlockEnchantmentEffects(ServerLevel level, BlockHitResult hitResult, ItemStack weapon) {
        BlockPos blockPos = hitResult.getBlockPos();
        Vec3 position = blockPos.clampLocationWithin(hitResult.getLocation());
        BlockState state = level.getBlockState(blockPos);
        EnchantmentHelper.onHitBlock(level, weapon, this.getOwner() instanceof LivingEntity living ? living : null, this, null,
                position, state, item -> this.kill(level));
        if (this.hasChanneling(level) && level.isThundering() && state.is(BlockTags.LIGHTNING_RODS) && level.canSeeSky(blockPos)) {
            this.summonLightning(level, position, this.getSoundSource());
        }
    }

    private boolean hasChanneling(ServerLevel level) {
        ItemStack weapon = this.getWeaponItem();
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.CHANNELING)
                .map(channeling -> weapon.getEnchantmentLevel(channeling) > 0)
                .orElse(false);
    }

    /** 落一道闪电并放雷声，同原版引雷的效果；闪电算在主人头上（玩家才记）。 */
    private void summonLightning(ServerLevel level, Vec3 position, SoundSource soundSource) {
        BlockPos blockPos = BlockPos.containing(position);
        if (!Level.isInSpawnableBounds(blockPos)) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.spawn(level, blockPos, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            if (this.getOwner() instanceof ServerPlayer player) {
                bolt.setCause(player);
            }
            bolt.snapTo(position.x, position.y, position.z, bolt.getYRot(), bolt.getXRot());
        }
        level.playSound(null, position.x, position.y, position.z, SoundEvents.TRIDENT_THUNDER.value(), soundSource, THUNDER_VOLUME, 1.0F);
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player) || this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem());
    }

    /** 没有物品数据时的兜底（指令召出来、存档里缺了物品）。 */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.TRIDENT);
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void playerTouch(Player player) {
        if (this.ownedBy(player) || this.getOwner() == null) {
            super.playerTouch(player);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.dealtDamage = input.getBooleanOr("DealtDamage", false);
        this.rangedDamage = input.getFloatOr("RangedDamage", FALLBACK_RANGED_DAMAGE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("DealtDamage", this.dealtDamage);
        output.putFloat("RangedDamage", this.rangedDamage);
    }

    @Override
    public void tickDespawn() {
        if (this.pickup != AbstractArrow.Pickup.ALLOWED || this.entityData.get(ID_LOYALTY) <= 0) {
            super.tickDespawn();
        }
    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }

    @Override
    public boolean shouldRender(double camX, double camY, double camZ) {
        return true;
    }
}
