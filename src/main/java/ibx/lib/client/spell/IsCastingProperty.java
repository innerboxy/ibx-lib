package ibx.lib.client.spell;

import com.mojang.serialization.MapCodec;
import ibx.lib.IbxLib;
import ibx.lib.registry.TagRegistry;
import ibx.lib.spell.SpellCasting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 物品模型条件 {@code ibx_lib:casting}：这件物品正拿在手上施法（见 {@link #isCastingWith}）。
 * 和原版 {@code minecraft:using_item} 的区别是瞬发法术也算——瞬发不挂"使用物品"，但施法动作放着的这段时间里也为真。
 * 法典的 {@code items/*.json} 用它切换合上 / 翻开的手持模型。
 *
 * <p>施法动作要 Player Animation Library（可选依赖）：装了时 {@link CastAnimations} 注册时把「动作还在放」告诉这里
 * （{@link #setAnimationPlaying}）；没装就只看施法状态，瞬发法术时法典不翻开。这个类不引用动作库的类，没装也能加载。</p>
 */
public record IsCastingProperty() implements ConditionalItemModelProperty {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(IbxLib.MODID, "casting");
    public static final MapCodec<IsCastingProperty> MAP_CODEC = MapCodec.unit(new IsCastingProperty());

    /** 施法动作还在放的实体（瞬发法术的动作比施法本身长）；没装动作库时一律不算。 */
    private static Predicate<LivingEntity> animationPlaying = entity -> false;

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        return owner != null && isCastingWith(owner, stack);
    }

    @Override
    public MapCodec<IsCastingProperty> type() {
        return MAP_CODEC;
    }

    public static void onRegister(RegisterConditionalItemModelPropertyEvent event) {
        event.register(ID, MAP_CODEC);
    }

    /** 施法动作那边注册：哪些实体的施法动作还在放。 */
    static void setAnimationPlaying(Predicate<LivingEntity> playing) {
        animationPlaying = playing;
    }

    /**
     * 这件物品是不是正拿在手上施法：施法者正在施法（两端同步的施法状态），或施法动作还在放，
     * 并且物品就在施法那只手上。
     */
    static boolean isCastingWith(LivingEntity owner, ItemStack stack) {
        boolean casting = SpellCasting.isCasting(owner) || animationPlaying.test(owner);
        return casting && owner.getItemInHand(castingHand(owner)) == stack;
    }

    /** 施法用的手：主手拿着施法物品就是主手，否则副手拿着就是副手，都没有算主手。 */
    static InteractionHand castingHand(LivingEntity entity) {
        return !entity.getMainHandItem().is(TagRegistry.CAN_CAST_SPELL) && entity.getOffhandItem().is(TagRegistry.CAN_CAST_SPELL)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
