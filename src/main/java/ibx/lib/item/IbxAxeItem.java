package ibx.lib.item;

import ibx.lib.compat.BetterCombatCompat;
import ibx.lib.item.general.IbxMeleeWeaponItem;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jspecify.annotations.Nullable;

/**
 * Ibx 斧：规则同原版斧。
 *
 * <ul>
 *   <li>物品设置走原版的 {@code Properties#axe}：材料给耐久、修复材料、附魔能力；斧类方块按材料速度挖、等级不够的方块不掉落；
 *       每次攻击掉 2 点耐久、挖一个方块掉 1 点；打中举盾的人把盾打掉 5 秒；主手挂攻击伤害、攻速（基础 id，tooltip 是深绿合并行）。</li>
 *   <li>右键方块同原版斧（{@code AxeItem#useOn}，那个类继承不了，照它写的）：依次试剥树皮、刮铜锈、刮蜡，成了掉 1 点耐久；
 *       副手拿着能格挡的东西又没蹲下时不触发，让右键去举盾。别的模组按 {@link ItemAbilities#DEFAULT_AXE_ACTIONS} 认它是斧。</li>
 *   <li>左键动作默认用 Better Combat 的 {@code bettercombat:axe}。</li>
 *   <li>自动进 {@code #c:tools/axe}。原版的 {@code #minecraft:axes}（效率、锋利这些附魔认它）不自动进。</li>
 * </ul>
 * 其余见 {@link IbxMeleeWeaponItem}。
 */
public class IbxAxeItem extends IbxMeleeWeaponItem {
    /** 左键动作的默认预设。 */
    public static final Identifier PRESET = BetterCombatCompat.builtin("axe");

    /**
     * @param properties   物品属性，必须带物品 id
     * @param material     材料：耐久、修复材料、附魔能力、挖掘速度和等级，攻击伤害再加上它的加成
     * @param attackDamage 伤害基数：+ 材料加成 + 空手 1 = 显示的攻击伤害（原版铁斧 6）
     * @param attackSpeed  攻速基数：+ 玩家的 4 = 显示的攻速（原版铁斧 -3.1）
     */
    public IbxAxeItem(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed) {
        this(properties, material, attackDamage, attackSpeed, PRESET);
    }

    /**
     * 同上，斧的变种（双刃斧这类）换自己的左键动作。
     *
     * @param preset Better Combat 的动作预设 id
     */
    protected IbxAxeItem(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed, Identifier preset) {
        super(properties, p -> p.axe(material, attackDamage, attackSpeed), preset);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (wantsToBlock(context)) {
            return InteractionResult.PASS;
        }
        BlockState changed = this.changedState(level, pos, player, level.getBlockState(pos), context);
        if (changed == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, stack);
        }
        level.setBlock(pos, changed, 11);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, changed));
        if (player != null) {
            stack.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
        }
        return InteractionResult.SUCCESS;
    }

    /** 主手拿斧、副手拿着能格挡的东西、没蹲下：这次右键是要举盾。 */
    private static boolean wantsToBlock(UseOnContext context) {
        Player player = context.getPlayer();
        return player != null
                && context.getHand() == InteractionHand.MAIN_HAND
                && player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)
                && !player.isSecondaryUseActive();
    }

    /** 这一下把方块变成什么：剥皮、刮锈、刮蜡依次试，都不行返回 null。音效和粒子在这里放。 */
    private @Nullable BlockState changedState(Level level, BlockPos pos, @Nullable Player player, BlockState state, UseOnContext context) {
        BlockState stripped = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false);
        if (stripped != null) {
            level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return stripped;
        }
        BlockState scraped = state.getToolModifiedState(context, ItemAbilities.AXE_SCRAPE, false);
        if (scraped != null) {
            soundAndParticle(level, pos, player, state, SoundEvents.AXE_SCRAPE, 3005);
            return scraped;
        }
        BlockState waxOff = state.getToolModifiedState(context, ItemAbilities.AXE_WAX_OFF, false);
        if (waxOff != null) {
            soundAndParticle(level, pos, player, state, SoundEvents.AXE_WAX_OFF, 3004);
            return waxOff;
        }
        return null;
    }

    /** 刮锈、刮蜡的音效和粒子；大箱子的另一半也冒一份（铜箱子）。 */
    private static void soundAndParticle(Level level, BlockPos pos, @Nullable Player player, BlockState state, SoundEvent sound, int particle) {
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.levelEvent(player, particle, pos, 0);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
            level.gameEvent(GameEvent.BLOCK_CHANGE, other, GameEvent.Context.of(player, level.getBlockState(other)));
            level.levelEvent(player, particle, other, 0);
        }
    }

    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability);
    }
}
