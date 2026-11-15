package ibx.lib.effect;

import ibx.lib.IbxLib;
import ibx.lib.config.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 修改原版中毒（服务端配置 {@link Config#MODIFY_VANILLA_POISON} 打开时，由 {@code mixin/PoisonMobEffectMixin} 接管扣血）。
 *
 * <ul>
 *   <li>每次扣血 = 中毒等级：I 级 1 点，每级 +1，不封顶；扣血间隔固定 {@value #INTERVAL} tick，不随等级变（原版是 {@code 25 >> 放大等级}）。</li>
 *   <li>伤害类型换成 {@link #DAMAGE_TYPE}：在 {@code neoforge:is_poison} 里（所以照原版中毒无视护甲、无击退等），
 *       另加了 {@code minecraft:bypasses_cooldown}，不吃受伤无敌时间。原版的 {@code neoforge:poison} 不改。</li>
 *   <li>照原版不会把生物毒死：血量剩 1 点就不再扣，扣血也最多扣到 1 点。</li>
 * </ul>
 */
public final class VanillaPoison {
    private VanillaPoison() {}

    /** 修改后的中毒伤害类型 {@code ibx_lib:poison}，定义在 {@code data/ibx_lib/damage_type/poison.json}。 */
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "poison"));

    /** 扣血间隔（tick），不随等级变。 */
    public static final int INTERVAL = 10;

    /** 配置有没有打开。 */
    public static boolean enabled() {
        return Config.get(Config.MODIFY_VANILLA_POISON);
    }

    /** 这一 tick 扣不扣血：tickCount 同原版（剩余时长，无限时长时是实体的 tick 数）。 */
    public static boolean shouldHurt(int tickCount) {
        return tickCount % INTERVAL == 0;
    }

    /** 扣一次中毒血：伤害 = 放大等级 + 1，最多扣到剩 1 点。 */
    public static void hurt(ServerLevel level, LivingEntity mob, int amplifier) {
        float health = mob.getHealth();
        if (health <= 1.0F) {
            return;
        }
        float damage = Math.min(amplifier + 1.0F, health - 1.0F);
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE));
        mob.hurtServer(level, source, damage);
    }
}
