package ibx.lib.item.general;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

import java.util.function.UnaryOperator;

/**
 * 本模组加的两档物品稀有度，接在原版四档（普通白、罕见黄、稀有青、史诗紫）后面：
 *
 * <ul>
 *   <li>传说 {@link #legendary()}：金色（{@link ChatFormatting#GOLD}，#FFAA00，偏橙，和罕见的柠檬黄 #FFFF55 分得开），
 *       id {@code ibx_lib:legendary}。</li>
 *   <li>神话 {@link #mythic()}：红色（{@link ChatFormatting#RED}，#FF5555），id {@code ibx_lib:mythic}。</li>
 * </ul>
 *
 * <p>用 NeoForge 的枚举扩展直接加进原版的 {@link Rarity}（{@code META-INF/enumextensions.json}），所以用法和原版稀有度一样：
 * 注册时 {@link Item.Properties#rarity}，或者按物品栈改 {@code minecraft:rarity} 组件（数据里写 id，如
 * {@code /give @s stick[rarity="ibx_lib:legendary"]}）。</p>
 *
 * <p>原版附魔会把普通、罕见提成稀有，稀有提成史诗；传说、神话附魔后不变。</p>
 *
 * <pre>{@code
 * ITEMS.registerItem("dragon_heart", Item::new, p -> p.rarity(IbxRarities.legendary()));
 * }</pre>
 */
public final class IbxRarities {
    private IbxRarities() {}

    /** 枚举扩展的参数（由 enumextensions.json 引用）：id 由 NeoForge 分配（-1）、名字、名字的样式。 */
    public static final EnumProxy<Rarity> LEGENDARY_PROXY = new EnumProxy<>(Rarity.class,
            -1, "ibx_lib:legendary", (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.GOLD));

    /** 同 {@link #LEGENDARY_PROXY}。 */
    public static final EnumProxy<Rarity> MYTHIC_PROXY = new EnumProxy<>(Rarity.class,
            -1, "ibx_lib:mythic", (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.RED));

    /** 传说：金色名字。 */
    public static Rarity legendary() {
        return LEGENDARY_PROXY.getValue();
    }

    /** 神话：红色名字。 */
    public static Rarity mythic() {
        return MYTHIC_PROXY.getValue();
    }
}
