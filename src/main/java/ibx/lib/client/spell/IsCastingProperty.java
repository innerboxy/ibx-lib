package ibx.lib.client.spell;

import com.mojang.serialization.MapCodec;
import ibx.lib.IbxLib;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * 物品模型条件 {@code ibx_lib:casting}：这件物品正拿在手上施法（见 {@link CastAnimations#isCastingWith}）。
 * 和原版 {@code minecraft:using_item} 的区别是瞬发法术也算——瞬发不挂"使用物品"，但施法动作放着的这段时间里也为真。
 * 法典的 {@code items/*.json} 用它切换合上 / 翻开的手持模型。
 */
public record IsCastingProperty() implements ConditionalItemModelProperty {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(IbxLib.MODID, "casting");
    public static final MapCodec<IsCastingProperty> MAP_CODEC = MapCodec.unit(new IsCastingProperty());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        return owner != null && CastAnimations.isCastingWith(owner, stack);
    }

    @Override
    public MapCodec<IsCastingProperty> type() {
        return MAP_CODEC;
    }

    public static void onRegister(RegisterConditionalItemModelPropertyEvent event) {
        event.register(ID, MAP_CODEC);
    }
}
