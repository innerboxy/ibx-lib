package ibx.lib.compat;

import net.mehvahdjukaar.dummmmmmy.common.TargetDummyEntity;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/**
 * MmmMmmMmmMmm 试验假人（modId {@code dummmmmmy}）联动。本模组对它是<b>非强制依赖</b>。
 *
 * <p><b>凡是会碰到 {@code net.mehvahdjukaar.dummmmmmy} 的方法，调用前必须先过
 * {@link #isLoaded()}</b>，否则玩家没装这个模组时会 NoClassDefFoundError。</p>
 *
 * <p>假人显示"12 (1.5x)"这种暴击数字，靠的是 {@code TargetDummyEntity.moist(攻击者, 倍率)}
 * 先登记一次暴击：它把记录加进当 tick 的列表，等自己的 {@code hurtServer()} 里再与伤害来源配对。
 * <b>所以登记必须发生在假人 hurtServer() 之前</b>。近战由假人自己监听 NeoForge 的暴击事件完成登记，
 * 不用本类；远程弹射物不经过那个事件，需要本模组在弹射物命中事件里手动登记；法术在
 * {@link ibx.lib.api.SpellDamageApi#hurt} 里登记。</p>
 *
 * <p>法术伤害数字的颜色见 {@link #SPELL_DAMAGE_COLOR}，由 mixin {@code mixin.dummmmmmy.ClientConfigsMixin} 接入；
 * 同一个 mixin 顺带修了假人 3.0.1 默认配色没有不透明度、暴击等彩色数字全透明的 bug。
 * 那个 mixin 配置只在装了假人时加载（mods.toml 里的 {@code requiredMods}）。</p>
 */
public final class DummmmmmyCompat {
    public static final String MODID = "dummmmmmy";

    /**
     * 法术伤害（{@code #ibx_lib:is_spell}）在假人身上跳出的数字颜色，<b>ARGB</b>。取 HUD 蓝条填充的主色
     * {@code #3A7CFF}，和法力一眼对上；与假人默认的各色都不撞（最近的溺水是偏青的 {@code #1898E3}）。
     *
     * <p><b>最高字节必须是 FF</b>：假人 26.1 版的数字粒子用颜色自己的最高字节当不透明度（{@code withAlpha} 里
     * {@code (color >>> 24) × 淡出}），写成 {@code 0x3A7CFF} 会画成全透明——数字生成了却看不见。
     * 它"没匹配上"时返回的 -1 也是 {@code 0xFFFFFFFF}（不透明白）。</p>
     *
     * <p>只在假人配置里<b>没有任何条目匹配</b>这个伤害类型时才用：玩家在 {@code dummmmmmy-client.toml} 的
     * {@code damage_type_colors} 里写 {@code "#ibx_lib:is_spell"}（或具体类型）就能改成别的颜色；
     * 自建类型打了原版 {@code is_fire} 之类的标签，会先匹配假人自带的那一条（火焰橙）。暴击照假人的暴击样式显示。</p>
     */
    public static final int SPELL_DAMAGE_COLOR = 0xFF3A7CFF;

    private DummmmmmyCompat() {}

    /** 试验假人是否已加载。碰它的 API 之前都要先问这个。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    /**
     * 若 {@code target} 是试验假人，登记一次由 {@code attacker} 造成、倍率为 {@code multiplier}
     * 的暴击，让它把伤害数字显示成暴击样式。必须在假人受到这次伤害<b>之前</b>调用。
     */
    public static void notifyCrit(Entity target, Entity attacker, float multiplier) {
        if (target instanceof TargetDummyEntity dummy) {
            dummy.moist(attacker, multiplier);
        }
    }
}
