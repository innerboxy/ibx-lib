package ibx.lib.spell;

import ibx.lib.registry.SpellRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Stream;

/**
 * 选法术界面（{@code SpellPickerScreen}）右边那本列哪些法术，打开界面时传进去。两端都能算：客户端画界面，
 * 服务端校验选上来的法术（{@code PickSpellsPayload}）。解散召唤物两种都不列——它跟着召唤物自己出现，不该固定进物品。
 */
public enum SpellPickerSource {
    /** 游戏里注册的全部法术，按注册顺序。 */
    REGISTERED,
    /**
     * 正在改的这件物品自带的法术（{@link SpellContainer#innateSpells}，移出了也能加回来），再加上玩家身上其他装备现在能放的法术
     * （{@link SpellCasting#getSpellsExcept}，按轮盘里的顺序）。
     */
    CARRIED;

    /**
     * 右边列的法术。
     *
     * @param player 打开界面的玩家
     * @param picker 正在改的那件物品（玩家手上的那个物品栈）；{@link #CARRIED} 只算它自带的法术，不算它现在选着的
     */
    public List<Holder<Spell>> spells(Player player, ItemStack picker) {
        Stream<Holder<Spell>> all = switch (this) {
            case REGISTERED -> SpellRegistries.SPELL.listElements().map(h -> h);
            case CARRIED -> Stream.concat(
                    picker.getItem() instanceof SpellContainer container ? container.innateSpells(picker).stream() : Stream.empty(),
                    SpellCasting.getSpellsExcept(player, picker).stream().map(SpellEntry::spell));
        };
        return all.filter(SpellPickerSource::pickable).distinct().toList();
    }

    /** 能不能放进物品里：除了解散召唤物都能。 */
    public static boolean pickable(Holder<Spell> spell) {
        return !spell.is(SpellRegistry.DISMISS_SUMMONS.getKey());
    }
}
