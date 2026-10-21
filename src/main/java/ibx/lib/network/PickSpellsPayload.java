package ibx.lib.network;

import ibx.lib.IbxLib;
import ibx.lib.registry.DataComponentRegistry;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellPicker;
import ibx.lib.spell.SpellPickerSource;
import ibx.lib.spell.SpellRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 选法术界面关掉时（有改动才）发的已选法术（客户端 → 服务端）：整张列表（按先后），写进主手那件 {@link SpellPicker} 的
 * {@code ibx_lib:spells} 组件。主手不是 {@link SpellPicker} 就不管。
 *
 * <p>每个法术要么本来就在这件物品上（也就是打开界面时就有的——界面里怎么移出又加回来都在这一个包里），要么在它的来源
 * （{@link SpellPicker#pickerSource}，{@link SpellPickerSource#CARRIED} 含物品自带的法术）此刻列得出来，否则跳过。
 * 查不到的、解散召唤物、重复的 id 也跳过。</p>
 */
public record PickSpellsPayload(List<Identifier> spells) implements CustomPacketPayload {
    public static final Type<PickSpellsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "pick_spells"));

    /** 一次最多收这么多个（比注册的法术总数大得多就行，防乱发的大包）。 */
    private static final int MAX_SPELLS = 256;

    public static final StreamCodec<ByteBuf, PickSpellsPayload> STREAM_CODEC =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SPELLS)).map(PickSpellsPayload::new, PickSpellsPayload::spells);

    @Override
    public Type<PickSpellsPayload> type() {
        return TYPE;
    }

    /** 服务端主线程上处理。 */
    static void handle(PickSpellsPayload payload, IPayloadContext context) {
        apply(context.player(), payload.spells());
    }

    /**
     * 按类说明的规则把 {@code ids} 写进玩家主手那件 {@link SpellPicker}（服务端调用），返回是否写了
     * （主手不是 {@link SpellPicker} 时为 false）。指令之类要替玩家改选也可以直接调它。
     */
    public static boolean apply(Player player, List<Identifier> ids) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SpellPicker picker)) {
            return false;
        }
        List<Holder<Spell>> allowed = new ArrayList<>(picker.getSpells(stack));
        allowed.addAll(picker.pickerSource(stack).spells(player, stack));
        List<Holder<Spell>> spells = new ArrayList<>();
        for (Identifier id : ids) {
            SpellRegistries.SPELL.get(id).ifPresent(h -> {
                if (SpellPickerSource.pickable(h) && allowed.contains(h) && !spells.contains(h)) {
                    spells.add(h);
                }
            });
        }
        stack.set(DataComponentRegistry.SPELLS.get(), List.copyOf(spells));
        return true;
    }
}
