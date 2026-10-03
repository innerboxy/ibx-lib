package ibx.taverntales.lib.api.event;

import ibx.taverntales.lib.spell.Spell;
import ibx.taverntales.lib.spell.SpellEntry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * 玩家的法术列表聚合完之后（{@code SpellCasting.getSpells}），发在 {@code NeoForge.EVENT_BUS} 上：可以增删条目——
 * 药水效果临时给法术、别的模组的装备接进来都靠它。
 *
 * <p><b>两端都会触发</b>：客户端画轮盘、HUD 时，服务端校验选中和施法时。两边算出的结果必须一致，
 * 否则轮盘里能选的法术会被服务端拒掉，所以监听里只看两端都有的数据（物品、药水效果、同步过的附加数据）。</p>
 */
public class SpellListEvent extends Event {
    private final Player player;
    private final List<SpellEntry> spells;

    public SpellListEvent(Player player, List<SpellEntry> spells) {
        this.player = player;
        this.spells = spells;
    }

    public Player getPlayer() {
        return this.player;
    }

    /** 可修改的列表，顺序就是轮盘里的顺序。同一法术不要加两次（{@link #add} 会自动跳过）。 */
    public List<SpellEntry> getSpells() {
        return this.spells;
    }

    /** 加到末尾；已经有这个法术时什么也不做。{@code sourceItem} 没有就填 {@link ItemStack#EMPTY}。 */
    public void add(Holder<Spell> spell, ItemStack sourceItem) {
        if (!this.contains(spell.value())) {
            this.spells.add(new SpellEntry(spell, sourceItem));
        }
    }

    public boolean contains(Spell spell) {
        return this.spells.stream().anyMatch(entry -> entry.value() == spell);
    }

    public void remove(Spell spell) {
        this.spells.removeIf(entry -> entry.value() == spell);
    }
}
