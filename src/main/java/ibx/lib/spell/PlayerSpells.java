package ibx.lib.spell;

import ibx.lib.IbxLib;
import ibx.lib.api.event.SpellListEvent;
import ibx.lib.compat.CuriosCompat;
import ibx.lib.registry.AttachmentRegistry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 玩家的法术列表和当前选中。法术来自身上的物品（{@link SpellContainer}），玩家本身没有"学会"的数据，卸下装备法术就没了。
 *
 * <ul>
 *   <li>{@link #getSpells}：扫身上的容器得到列表；</li>
 *   <li>{@link #getSelectedSpell} / {@link #select}：选中的是哪个、改选；</li>
 *   <li>{@link #castSelected}：放当前选中的法术（右键施法物品时由 {@link SpellItemUse} 调）；</li>
 *   <li>提供正在放的法术的装备、饰品被卸下时取消这次施法。</li>
 * </ul>
 *
 * <p>施法流程本身见 {@link SpellCasting}。</p>
 */
public final class PlayerSpells {
    private PlayerSpells() {}

    private static final EquipmentSlot[] ARMOR_ORDER = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerSpells::onEquipmentChange);
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.onCurioChange(PlayerSpells::checkSpellStillAvailable);
        }
    }

    // ---------------------------------------------------------------- 列表

    /**
     * 玩家现在能放的法术。扫身上所有容器（{@link SpellContainer}），顺序固定：
     * <b>主手 → 副手 → 饰品栏（装了 Curios 才扫，按其槽位顺序）→ 头 → 胸 → 腿 → 脚</b>；同一法术只列第一次出现，
     * 每条带提供它的物品。最后发 {@link SpellListEvent}，监听可以增删。
     *
     * <p>两端都能调：客户端画轮盘、HUD，服务端校验选中、施法。</p>
     */
    public static List<SpellEntry> getSpells(Player player) {
        return getSpellsExcept(player, ItemStack.EMPTY);
    }

    /**
     * 同 {@link #getSpells}，但不算 {@code excluded} 这一个物品栈（按是不是同一个对象认）：比如选法术界面
     * （{@link SpellPickerSource#CARRIED}）列「身上的法术」时，不算正在改的那件物品自己的。
     */
    public static List<SpellEntry> getSpellsExcept(Player player, ItemStack excluded) {
        List<SpellEntry> spells = new ArrayList<>();
        Consumer<ItemStack> collect = stack -> {
            if (stack == excluded) {
                return;
            }
            for (Holder<Spell> spell : SpellContainer.spellsOf(stack)) {
                if (spells.stream().noneMatch(entry -> entry.value() == spell.value())) {
                    spells.add(new SpellEntry(spell, stack));
                }
            }
        };
        collect.accept(player.getMainHandItem());
        collect.accept(player.getOffhandItem());
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.forEachCurio(player, collect);
        }
        for (EquipmentSlot slot : ARMOR_ORDER) {
            collect.accept(player.getItemBySlot(slot));
        }
        return NeoForge.EVENT_BUS.post(new SpellListEvent(player, spells)).getSpells();
    }

    // ---------------------------------------------------------------- 选中

    /** 选中的法术 id，不校验还在不在列表里；没选过为 null。两端都能问（只同步给本人）。 */
    public static @Nullable Identifier getSelectedId(Player player) {
        SelectedSpell selected = player.getExistingDataOrNull(AttachmentRegistry.SELECTED_SPELL);
        return selected == null ? null : selected.spell();
    }

    /** 当前会放的那一条：选中的还在列表里就是它，否则列表第一个；身上没有法术为 null。两端都能调。 */
    public static @Nullable SpellEntry getSelectedSpell(Player player) {
        return resolveSelected(player, getSpells(player));
    }

    /** 同上，用已经算好的列表（{@link #getSpells}），避免一帧里扫两遍。 */
    public static @Nullable SpellEntry resolveSelected(Player player, List<SpellEntry> spells) {
        if (spells.isEmpty()) {
            return null;
        }
        Identifier selected = getSelectedId(player);
        for (SpellEntry entry : spells) {
            if (Objects.equals(entry.value().id(), selected)) {
                return entry;
            }
        }
        return spells.getFirst();
    }

    /**
     * 改选：{@code spell} 在该玩家的列表里才写入（自动同步给本人），返回是否成功。只在服务端生效。
     * 轮盘的选中包（{@code SelectSpellPayload}）走这里；不在列表里时把服务端的值重发一遍，纠正客户端先改的那份。
     */
    public static boolean select(Player player, Identifier spell) {
        if (player.level().isClientSide()) {
            return false;
        }
        if (getSpells(player).stream().anyMatch(entry -> spell.equals(entry.value().id()))) {
            player.setData(AttachmentRegistry.SELECTED_SPELL, new SelectedSpell(spell));
            return true;
        }
        player.syncData(AttachmentRegistry.SELECTED_SPELL);
        return false;
    }

    // ---------------------------------------------------------------- 放当前选中

    /**
     * 放当前选中的法术，来源 {@link CastSource#ITEM}：触发物品是 {@code hand} 上的物品，来源物品是提供这个法术的容器。
     * 选中的已经不在列表里（装备卸了）就回退到列表第一个并提示；身上没有法术返回 {@link CastResult#NO_SPELL}。
     * 客户端调用只做判断。
     */
    public static CastResult castSelected(Player player, InteractionHand hand) {
        List<SpellEntry> spells = getSpells(player);
        SpellEntry entry = resolveSelected(player, spells);
        if (entry == null) {
            return SpellCasting.fail(player, CastResult.NO_SPELL, null);
        }
        if (!player.level().isClientSide()) {
            Identifier selected = getSelectedId(player);
            Identifier actual = entry.value().id();
            if (actual != null && !actual.equals(selected)) {
                player.setData(AttachmentRegistry.SELECTED_SPELL, new SelectedSpell(actual));
                if (selected != null) {
                    player.sendOverlayMessage(Component.translatable(
                            "message." + IbxLib.MODID + ".cast.selection_fallback", entry.value().getName()));
                }
            }
        }
        CastResult result = SpellCasting.cast(player, entry.value(), CastSource.ITEM, player.getItemInHand(hand), entry.sourceItem());
        CastContext ctx = SpellCasting.getCurrentCast(player);
        if (result.isSuccess() && ctx != null && !player.level().isClientSide()) {
            ctx.fromSelection = true;
        }
        return result;
    }

    // ---------------------------------------------------------------- 事件

    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        checkSpellStillAvailable(event.getEntity());
    }

    /** 提供正在放的法术的装备、饰品被卸下了：列表里没有这个法术就取消（引导生效过的照常进冷却）。只看"放当前选中"放出的。 */
    private static void checkSpellStillAvailable(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        CastContext ctx = SpellCasting.getCurrentCast(player);
        if (ctx != null && ctx.fromSelection
                && getSpells(player).stream().noneMatch(entry -> entry.value() == ctx.spell())) {
            SpellCasting.cancelCasting(player, CastEndReason.CANCELLED);
        }
    }
}
