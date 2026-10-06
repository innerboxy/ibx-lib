package ibx.taverntales.lib.spell;

import ibx.taverntales.lib.TavernTalesLib;
import ibx.taverntales.lib.api.ManaApi;
import ibx.taverntales.lib.api.event.SpellCastEvent;
import ibx.taverntales.lib.api.event.SpellListEvent;
import ibx.taverntales.lib.compat.CuriosCompat;
import ibx.taverntales.lib.registry.AttachmentRegistry;
import ibx.taverntales.lib.registry.AttributeRegistry;
import ibx.taverntales.lib.registry.SoundRegistry;
import ibx.taverntales.lib.registry.TagRegistry;
import ibx.taverntales.lib.util.AttributeUtil;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * 施法的唯一入口。施法者是玩家、生物都一样：{@code SpellCasting.cast(caster, spell)}。
 *
 * <ul>
 *   <li>{@link #cast}：检查（正在施法 → 法术条件 → 冷却 → 法力）→ {@link SpellCastEvent.Pre} → 按类型开始。
 *       失败时把原因发到玩家的 action bar。</li>
 *   <li>读条由框架推进（{@link ActiveCast} + 每 tick 的 {@link #onEntityTick}），生物 AI 不用自己计时，
 *       只要 {@link #isCasting} / {@link #stopCasting}。</li>
 *   <li>扣蓝、冷却、卷轴消耗按 {@link CastSource} 结算；生效过至少一次才进冷却、才消耗卷轴。</li>
 *   <li>扣了蓝，施法物品（右键的那件）有耐久就跟着掉，见 {@link #MANA_PER_DURABILITY}。</li>
 * </ul>
 *
 * <table>
 *   <caption>各类型的流程</caption>
 *   <tr><td>瞬发</td><td>扣蓝 → onCastStart → onCast → 结束（完成）</td></tr>
 *   <tr><td>吟唱</td><td>onCastStart → 每 tick onCastTick → 到点扣蓝、onCast → 结束（完成）；松手取消、受击打断都不扣不冷却</td></tr>
 *   <tr><td>引导</td><td>onCastStart → 立刻生效一次 → 之后每 tickInterval 扣蓝、onCast，其余 tick onCastTick；松手结束，蓝尽结束</td></tr>
 *   <tr><td>蓄力</td><td>onCastStart → 每 tick onCastTick → 松手（{@link #stopCasting}）按蓄力比例扣蓝、onCast；不足 minCharge 取消</td></tr>
 * </table>
 *
 * <p>所有结算只在服务端；客户端调用 {@link #cast} 只做判断（预测用），不改任何数据。</p>
 *
 * <p>玩家这一侧（{@link #getSpells}、选中、{@link #castSelected}）：法术来自身上的物品，右键手持
 * {@code #taverntales_l1b:can_cast_spell} 物品放当前选中的法术，并挂上原版"使用物品"——按住读条、松手结束。</p>
 */
public final class SpellCasting {
    private SpellCasting() {}

    /**
     * 施法物品的耐久换算：每扣这么多蓝掉 1 点耐久，按每次实际扣掉的蓝量（已乘法力消耗倍率、经过法力事件）算，四舍五入。
     * 零头留在 {@link CastContext} 里给同一次施法的下一跳，引导法术每跳扣得少也不会多扣或白嫖。
     * 用光就碎，同原版工具（耐久魔咒照常生效）；创造模式不扣蓝，也就不掉耐久。
     */
    public static final float MANA_PER_DURABILITY = 4.0F;
    private static final DecimalFormat SECONDS = new DecimalFormat("0.0");
    private static final EquipmentSlot[] ARMOR_ORDER = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** 按住右键时原版每 4 tick 重试一次使用物品，失败提示音只在一串连续失败的第一次响。 */
    private static final int FAIL_SOUND_GAP = 6;
    private static final Map<Player, Long> LAST_FAIL = new WeakHashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpellCasting::onEntityTick);
        // 最后才看：前面的监听取消了这次伤害（免疫、格挡类模组）就不该打断吟唱。
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpellCasting::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onDeath);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onLeaveLevel);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onUseItemStop);
        NeoForge.EVENT_BUS.addListener(SpellCasting::onEquipmentChange);
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.onCurioChange(SpellCasting::checkSpellStillAvailable);
        }
    }

    // ---------------------------------------------------------------- 数值

    /** 实际吟唱 / 满蓄 tick = 吟唱时间 × 20 ÷ {@code spell_cast_speed}，最少 1；瞬发、引导为 0。 */
    public static int getCastTicks(LivingEntity caster, Spell spell) {
        if (spell.castType() != CastType.CAST && spell.castType() != CastType.CHARGE) {
            return 0;
        }
        // spell_cast_speed 下限 0.01，不会除以零。
        double ticks = spell.castTime() * 20.0D / AttributeUtil.read(caster, AttributeRegistry.SPELL_CAST_SPEED);
        return Math.max(1, (int) Math.round(ticks));
    }

    /** 实际冷却 tick = 冷却 × 20 × {@code spell_cooldown_ratio}。 */
    public static int getCooldownTicks(LivingEntity caster, Spell spell) {
        double ticks = spell.cooldown() * 20.0D * AttributeUtil.read(caster, AttributeRegistry.SPELL_COOLDOWN_RATIO);
        return Math.max(0, (int) Math.round(ticks));
    }

    // ---------------------------------------------------------------- 施法

    /** 按施法者推断来源施法：玩家 → {@link CastSource#ITEM}，生物 → {@link CastSource#MOB}。 */
    public static CastResult cast(LivingEntity caster, Spell spell) {
        return cast(caster, spell, caster instanceof Player ? CastSource.ITEM : CastSource.MOB);
    }

    public static CastResult cast(LivingEntity caster, Holder<Spell> spell) {
        return cast(caster, spell.value());
    }

    public static CastResult cast(LivingEntity caster, Spell spell, CastSource source) {
        return cast(caster, spell, source, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    /**
     * 施法。
     *
     * @param triggerItem 右键的那件施法物品，没有填 {@link ItemStack#EMPTY}
     * @param sourceItem  提供这个法术的物品（卷轴消耗、法杖耐久靠它），没有填 {@link ItemStack#EMPTY}
     */
    public static CastResult cast(LivingEntity caster, Spell spell, CastSource source,
                                  ItemStack triggerItem, ItemStack sourceItem) {
        if (caster.level().isClientSide()) {
            return check(caster, spell, source);
        }
        ActiveCast active = caster.getData(AttachmentRegistry.ACTIVE_CAST);
        if (active.current != null) {
            if (source != CastSource.COMMAND) {
                return fail(caster, CastResult.ALREADY_CASTING, null);
            }
            end(caster, active, CastEndReason.CANCELLED);
        }

        CastContext ctx = new CastContext(caster, spell, source, triggerItem, sourceItem,
                caster.level().getGameTime(), getCastTicks(caster, spell));
        if (!spell.canCast(ctx)) {
            return fail(caster, CastResult.CONDITION_FAILED, ctx.failure);
        }
        if (source.triggersCooldown() && !SpellCooldowns.isReady(caster, spell)) {
            return fail(caster, CastResult.ON_COOLDOWN, Component.translatable(CastResult.ON_COOLDOWN.messageKey(),
                    SECONDS.format(SpellCooldowns.getRemaining(caster, spell) / 20.0D)));
        }
        if (source.consumesMana() && !ManaApi.canAfford(caster, spell.manaCost())) {
            return fail(caster, CastResult.NOT_ENOUGH_MANA, null);
        }
        SpellCastEvent.Pre pre = NeoForge.EVENT_BUS.post(new SpellCastEvent.Pre(caster, spell, source,
                spell.manaCost(), getCooldownTicks(caster, spell)));
        if (pre.isCanceled()) {
            return CastResult.CONDITION_FAILED;
        }
        ctx.manaCost = pre.getManaCost();
        ctx.cooldownTicks = pre.getCooldownTicks();

        if (spell.castType() == CastType.INSTANT) {
            // 先扣：施法前事件可能把耗蓝改高了。
            if (!pay(ctx)) {
                return fail(caster, CastResult.NOT_ENOUGH_MANA, null);
            }
            // 也走一遍"开始 → 结束"的同步，客户端的开始 / 结束钩子（起手音效、粒子）才会跑。
            start(caster, active, ctx);
            effect(ctx);
            end(caster, active, CastEndReason.COMPLETED);
            return CastResult.SUCCESS;
        }

        start(caster, active, ctx);
        if (spell.castType() == CastType.CHANNEL && active.current == ctx && !fire(ctx)) {
            end(caster, active, CastEndReason.OUT_OF_MANA);
        }
        return CastResult.SUCCESS;
    }

    /** 是否正在施法。两端都能问（客户端靠同步）。 */
    public static boolean isCasting(LivingEntity caster) {
        return getCurrentCast(caster) != null;
    }

    /** 正在施的那一次；没在施法为 null。两端都能问。 */
    public static @Nullable CastContext getCurrentCast(LivingEntity caster) {
        ActiveCast active = caster.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        return active == null ? null : active.current;
    }

    /**
     * 松手 / AI 主动停：吟唱取消、引导结束（生效过就进冷却），蓄力按当前比例结算（不足 minCharge 取消）。
     * 只在服务端生效；没在施法时什么也不做。
     */
    public static void stopCasting(LivingEntity caster) {
        stop(caster, true);
    }

    /** @param stopUsing 结束后要不要停掉施法物品的"使用"；松手事件里已经在停用，传 false。 */
    private static void stop(LivingEntity caster, boolean stopUsing) {
        if (caster.level().isClientSide()) {
            return;
        }
        ActiveCast active = caster.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        CastContext ctx = active == null ? null : active.current;
        if (ctx == null) {
            return;
        }
        if (ctx.spell().castType() == CastType.CHARGE) {
            ctx.charge = Math.min(1.0F, (float) ctx.elapsed() / ctx.castTicks());
            if (ctx.charge < ctx.spell().minCharge()) {
                end(caster, active, CastEndReason.CANCELLED, stopUsing);
            } else {
                end(caster, active, fire(ctx) ? CastEndReason.COMPLETED : CastEndReason.OUT_OF_MANA, stopUsing);
            }
            return;
        }
        end(caster, active, CastEndReason.CANCELLED, stopUsing);
    }

    /**
     * 强制结束（打断、死亡、离开世界……）：不结算蓄力，直接按 {@code reason} 结束；引导生效过的照常进冷却。
     * 只在服务端生效；没在施法时什么也不做。
     */
    public static void cancelCasting(LivingEntity caster, CastEndReason reason) {
        if (caster.level().isClientSide()) {
            return;
        }
        ActiveCast active = caster.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        if (active != null && active.current != null) {
            end(caster, active, reason);
        }
    }

    // ---------------------------------------------------------------- 玩家的法术列表与选中

    /**
     * 玩家现在能放的法术。扫身上所有容器（{@link SpellContainer}），顺序固定：
     * <b>主手 → 副手 → 饰品栏（装了 Curios 才扫，按其槽位顺序）→ 头 → 胸 → 腿 → 脚</b>；同一法术只列第一次出现，
     * 每条带提供它的物品。最后发 {@link SpellListEvent}，监听可以增删。
     *
     * <p>两端都能调：客户端画轮盘、HUD，服务端校验选中、施法。玩家本身没有"学会"的数据，卸下装备法术就没了。</p>
     */
    public static List<SpellEntry> getSpells(Player player) {
        List<SpellEntry> spells = new ArrayList<>();
        Consumer<ItemStack> collect = stack -> {
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

    /**
     * 放当前选中的法术，来源 {@link CastSource#ITEM}：触发物品是 {@code hand} 上的物品，来源物品是提供这个法术的容器。
     * 选中的已经不在列表里（装备卸了）就回退到列表第一个并提示；身上没有法术返回 {@link CastResult#NO_SPELL}。
     * 右键施法物品时由 lib 自己调，施法物品不用写任何代码。客户端调用只做判断。
     */
    public static CastResult castSelected(Player player, InteractionHand hand) {
        List<SpellEntry> spells = getSpells(player);
        SpellEntry entry = resolveSelected(player, spells);
        if (entry == null) {
            return fail(player, CastResult.NO_SPELL, null);
        }
        if (!player.level().isClientSide()) {
            Identifier selected = getSelectedId(player);
            Identifier actual = entry.value().id();
            if (actual != null && !actual.equals(selected)) {
                player.setData(AttachmentRegistry.SELECTED_SPELL, new SelectedSpell(actual));
                if (selected != null) {
                    player.sendOverlayMessage(Component.translatable(
                            "message." + TavernTalesLib.MODID + ".cast.selection_fallback", entry.value().getName()));
                }
            }
        }
        CastResult result = cast(player, entry.value(), CastSource.ITEM, player.getItemInHand(hand), entry.sourceItem());
        CastContext ctx = getCurrentCast(player);
        if (result.isSuccess() && ctx != null && !player.level().isClientSide()) {
            ctx.fromSelection = true;
        }
        return result;
    }

    /**
     * 右键施法物品放出法术后的收尾：成功且不是瞬发时挂上原版"使用物品"——按住读条、松手结束、物品离手取消、结束时自动停用——
     * 返回 {@code CONSUME}；瞬发成功返回 {@code SUCCESS}（挥手）；失败返回 {@code FAIL}。
     *
     * <p>两端都调：客户端那次只判断（{@code spell} 用来看是不是瞬发），同步挂上使用物品（手臂姿势、减速）。
     * 使用时长由 mixin 统一给施法物品 72000。</p>
     */
    private static InteractionResult holdCast(Player player, InteractionHand hand, Spell spell, CastResult result) {
        if (!result.isSuccess()) {
            return InteractionResult.FAIL;
        }
        boolean held;
        if (player.level().isClientSide()) {
            held = spell.castType() != CastType.INSTANT;
        } else {
            CastContext ctx = getCurrentCast(player);
            held = ctx != null;
            if (held) {
                ctx.heldByUse = true;
            }
        }
        if (held) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- 内部流程

    /** 客户端的判断：不跑法术条件（只在服务端），只看正在施法、冷却、法力。 */
    private static CastResult check(LivingEntity caster, Spell spell, CastSource source) {
        if (isCasting(caster)) {
            return CastResult.ALREADY_CASTING;
        }
        if (source.triggersCooldown() && !SpellCooldowns.isReady(caster, spell)) {
            return CastResult.ON_COOLDOWN;
        }
        if (source.consumesMana() && !ManaApi.canAfford(caster, spell.manaCost())) {
            return CastResult.NOT_ENOUGH_MANA;
        }
        return CastResult.SUCCESS;
    }

    private static void start(LivingEntity caster, ActiveCast active, CastContext ctx) {
        active.current = ctx;
        active.lastEnd = null;
        caster.syncData(AttachmentRegistry.ACTIVE_CAST);
        ctx.spell().onCastStart(ctx);
    }

    /** 扣蓝（按来源）+ 生效。付不起返回 false，不生效。 */
    private static boolean fire(CastContext ctx) {
        if (!pay(ctx)) {
            return false;
        }
        effect(ctx);
        return true;
    }

    private static boolean pay(CastContext ctx) {
        if (!ctx.source().consumesMana()) {
            return true;
        }
        float before = ManaApi.getMana(ctx.caster());
        if (!ManaApi.tryConsume(ctx.caster(), ctx.manaCost)) {
            return false;
        }
        wearTriggerItem(ctx, before - ManaApi.getMana(ctx.caster()));
        return true;
    }

    /** 施法物品按这次实际扣掉的蓝量掉耐久（{@link #MANA_PER_DURABILITY}）。物品已经不在手上就不扣。 */
    private static void wearTriggerItem(CastContext ctx, float spentMana) {
        ItemStack stack = ctx.triggerItem();
        if (spentMana <= 0.0F || !stack.isDamageableItem()) {
            return;
        }
        ctx.pendingWear += spentMana / MANA_PER_DURABILITY;
        int wear = (int) Math.floor(ctx.pendingWear + 0.5F);
        if (wear <= 0) {
            return;
        }
        ctx.pendingWear -= wear;
        LivingEntity caster = ctx.caster();
        EquipmentSlot slot = caster.getMainHandItem() == stack ? EquipmentSlot.MAINHAND
                : caster.getOffhandItem() == stack ? EquipmentSlot.OFFHAND : null;
        if (slot != null) {
            stack.hurtAndBreak(wear, caster, slot);
        }
    }

    private static void effect(CastContext ctx) {
        ctx.effects++;
        ctx.spell().onCast(ctx);
        NeoForge.EVENT_BUS.post(new SpellCastEvent.Post(ctx));
    }

    /**
     * 结束：先清状态并同步（法术在结束钩子里再施法时不会被当成"正在施法"，客户端也先收到结束），
     * 生效过就进冷却、消耗卷轴，最后调结束钩子。
     */
    private static void end(LivingEntity caster, ActiveCast active, CastEndReason reason) {
        end(caster, active, reason, true);
    }

    /**
     * @param stopUsing 右键施法物品放出的，结束后停掉原版"使用物品"（吟唱到点、蓝尽、被打断时手放下来）。
     *                  松手事件里原版正在停用，传 false：在那里再停一次会让 NeoForge 误报"物品被用坏了"。
     */
    private static void end(LivingEntity caster, ActiveCast active, CastEndReason reason, boolean stopUsing) {
        CastContext ctx = active.current;
        if (ctx == null) {
            return;
        }
        active.current = null;
        active.lastEnd = reason;
        if (ctx.effects > 0) {
            if (ctx.source().triggersCooldown() && !isCreative(caster)) {
                SpellCooldowns.start(caster, ctx.spell(), ctx.cooldownTicks);
            }
            if (ctx.source() == CastSource.SCROLL && !ctx.sourceItem().isEmpty()) {
                ctx.sourceItem().consume(1, caster);
            }
        }
        if (stopUsing && ctx.heldByUse && caster.isUsingItem()) {
            caster.stopUsingItem();
        }
        caster.syncData(AttachmentRegistry.ACTIVE_CAST);
        ctx.spell().onCastEnd(ctx, reason);
    }

    /**
     * 失败：玩家在 action bar 看到原因（{@code message} 为空时用结果自带的提示），并听到失败提示音。
     * 按住右键时原版每 4 tick 重试一次，提示音只在一串连续失败的第一次响。只在服务端。
     */
    private static CastResult fail(LivingEntity caster, CastResult result, @Nullable Component message) {
        if (caster instanceof ServerPlayer player) {
            player.sendOverlayMessage(message != null ? message : Component.translatable(result.messageKey()));
            long now = player.level().getGameTime();
            Long last = LAST_FAIL.put(player, now);
            if (last == null || now - last > FAIL_SOUND_GAP) {
                player.connection.send(new ClientboundSoundPacket(SoundRegistry.CAST_FAIL, SoundSource.PLAYERS,
                        player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F, player.getRandom().nextLong()));
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- 事件

    /** 推进读条。客户端只调法术的 onCastTick（表现用）。 */
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity caster)) {
            return;
        }
        ActiveCast active = caster.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        CastContext ctx = active == null ? null : active.current;
        if (ctx == null) {
            return;
        }
        Spell spell = ctx.spell();
        if (caster.level().isClientSide()) {
            spell.onCastTick(ctx);
            return;
        }
        if (caster.isDeadOrDying()) {
            return;
        }
        // 右键放出的：原版因为换了物品、打开容器等停用了物品（不经过松手事件），当作取消，同弓切走不放箭
        if (ctx.heldByUse && !caster.isUsingItem()) {
            end(caster, active, CastEndReason.CANCELLED, false);
            return;
        }
        switch (spell.castType()) {
            case CAST -> {
                if (ctx.elapsed() >= ctx.castTicks()) {
                    end(caster, active, fire(ctx) ? CastEndReason.COMPLETED : CastEndReason.OUT_OF_MANA);
                } else {
                    spell.onCastTick(ctx);
                }
            }
            case CHANNEL -> {
                // 按"到现在应生效几次"补齐，而不是 elapsed % 间隔 == 0：生物所在区块偶尔不跑实体 tick 时，
                // 取模会把那一次永久跳过；这里下一 tick 补上（每 tick 最多补一次）。开始那一 tick 的第一次已在 cast 里生效。
                int due = ctx.elapsed() / spell.tickInterval() + 1;
                if (ctx.effects < due) {
                    if (!fire(ctx)) {
                        end(caster, active, CastEndReason.OUT_OF_MANA);
                    }
                } else {
                    spell.onCastTick(ctx);
                }
            }
            case CHARGE -> spell.onCastTick(ctx);
            case INSTANT -> end(caster, active, CastEndReason.COMPLETED);
        }
    }

    /**
     * 吟唱中受击打断：只对吟唱、可打断的法术；带 {@code #cast_ignore} 的持续掉血不算。
     * Boss（{@code #c:bosses}）不会被打断：Boss 一直在挨打，能打断的话吟唱类招式（玛门的翠焰）几乎放不出来。
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        CastContext ctx = getCurrentCast(event.getEntity());
        if (ctx != null
                && ctx.spell().castType() == CastType.CAST
                && ctx.spell().interruptible()
                && event.getAmount() > 0.0F
                && !event.getSource().is(TagRegistry.CAST_IGNORE)
                && !event.getEntity().is(Tags.EntityTypes.BOSSES)) {
            cancelCasting(event.getEntity(), CastEndReason.INTERRUPTED);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        cancelCasting(event.getEntity(), CastEndReason.CANCELLED);
    }

    /**
     * 下线、换维度、卸载、被移除：施法状态不存盘，在这里补上结束钩子让法术收尾。
     *
     * <p>排到这一 tick 跑完再结束：区块降级时原版一边遍历区块里的实体一边发这个事件，法术收尾当场删掉或生成实体
     * （翡翠射线的 {@code beam.end()}）会改到正在遍历的列表，服务器直接崩（ConcurrentModificationException）。
     * 到时只结束离开时的那次施法；这期间又开了新的（换维度后马上放）就不动它。</p>
     */
    private static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        ActiveCast active = living.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
        CastContext leaving = active == null ? null : active.current;
        MinecraftServer server = event.getLevel().getServer();
        if (leaving == null || server == null) {
            return;
        }
        server.schedule(server.wrapRunnable(() -> {
            ActiveCast now = living.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
            if (now != null && now.current == leaving) {
                end(living, now, CastEndReason.CANCELLED);
            }
        }));
    }

    /**
     * 右键手持 {@code #can_cast_spell} 物品 = 放当前选中的法术，不走物品自己的 {@code use}。两端都会触发：
     * 客户端那次只做判断，用来同步挂上"使用物品"（手臂姿势、减速）；瞬发不挂，只挥手。
     */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getItemStack().is(TagRegistry.CAN_CAST_SPELL)) {
            return;
        }
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        event.setCanceled(true);
        CastResult result = castSelected(player, hand);
        SpellEntry entry = result.isSuccess() ? getSelectedSpell(player) : null;
        event.setCancellationResult(entry == null ? InteractionResult.FAIL : holdCast(player, hand, entry.value(), result));
    }

    /**
     * 松手：挂着使用物品的施法（{@link #holdCast}）在这里结束，蓄力在这里结算。
     * 这时物品自己的 {@code releaseUsing} 不再调用（弓进了标签也不会顺手射箭）；施法物品标签里的物品两端都这样处理。
     */
    private static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        CastContext ctx = getCurrentCast(event.getEntity());
        boolean held = ctx != null && ctx.heldByUse;   // 只有服务端知道
        if (held || event.getItem().is(TagRegistry.CAN_CAST_SPELL)) {
            event.setCanceled(true);
        }
        if (held) {
            stop(event.getEntity(), false);
        }
    }

    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        checkSpellStillAvailable(event.getEntity());
    }

    /** 提供正在放的法术的装备、饰品被卸下了：列表里没有这个法术就取消（引导生效过的照常进冷却）。只看"放当前选中"放出的。 */
    private static void checkSpellStillAvailable(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        CastContext ctx = getCurrentCast(player);
        if (ctx != null && ctx.fromSelection
                && getSpells(player).stream().noneMatch(entry -> entry.value() == ctx.spell())) {
            cancelCasting(player, CastEndReason.CANCELLED);
        }
    }

    private static boolean isCreative(LivingEntity entity) {
        return entity instanceof Player player && player.isCreative();
    }
}
