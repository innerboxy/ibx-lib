package ibx.lib.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import ibx.lib.IbxLib;
import ibx.lib.spell.CastResult;
import ibx.lib.spell.CastSource;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellCasting;
import ibx.lib.spell.SpellCooldowns;
import ibx.lib.spell.SpellRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 法术指令（需要 2 级权限），施法者是指令的执行者（用 {@code /execute as} 让别的生物施法）：
 * <pre>
 * /taverntales spell cast &lt;法术&gt; [来源]     来源：item / scroll / mob / command，不写按执行者推断（玩家 item、生物 mob）
 * /taverntales spell stop                     松手：吟唱取消、引导结束、蓄力按当前比例结算
 * /taverntales spell cooldown clear [目标们]   不写目标即执行者
 * /taverntales spell list                     列出所有已注册的法术
 * </pre>
 */
public final class SpellCommand {
    private SpellCommand() {}

    private static final String KEY = "commands." + IbxLib.MODID + ".spell.";
    private static final DecimalFormat FORMAT = new DecimalFormat("0.##");
    private static final DynamicCommandExceptionType UNKNOWN_SPELL =
            new DynamicCommandExceptionType(id -> Component.translatable(KEY + "unknown", String.valueOf(id)));
    private static final DynamicCommandExceptionType UNKNOWN_SOURCE =
            new DynamicCommandExceptionType(name -> Component.translatable(KEY + "unknown_source", String.valueOf(name)));
    private static final SimpleCommandExceptionType NOT_LIVING =
            new SimpleCommandExceptionType(Component.translatable(KEY + "not_living"));

    private static final SuggestionProvider<CommandSourceStack> SPELLS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(SpellRegistries.SPELL.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> SOURCES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(CastSource.values()).map(source -> source.name().toLowerCase(Locale.ROOT)), builder);

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpellCommand::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        // 权限挂在 spell 这一层：taverntales 根节点和 mana 指令共用。
        event.getDispatcher().register(Commands.literal("taverntales")
                .then(Commands.literal("spell")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("cast")
                                .then(Commands.argument("spell", IdentifierArgument.id()).suggests(SPELLS)
                                        .executes(c -> cast(c.getSource(), spell(c), null))
                                        .then(Commands.argument("source", StringArgumentType.word()).suggests(SOURCES)
                                                .executes(c -> cast(c.getSource(), spell(c), source(c))))))
                        .then(Commands.literal("stop")
                                .executes(c -> stop(c.getSource())))
                        .then(Commands.literal("cooldown")
                                .then(Commands.literal("clear")
                                        .executes(c -> clearCooldowns(c.getSource(), List.of(c.getSource().getEntityOrException())))
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(c -> clearCooldowns(c.getSource(), EntityArgument.getEntities(c, "targets"))))))
                        .then(Commands.literal("list")
                                .executes(c -> list(c.getSource())))));
    }

    private static Spell spell(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "spell");
        Spell spell = SpellRegistries.SPELL.getValue(id);
        if (spell == null) {
            throw UNKNOWN_SPELL.create(id);
        }
        return spell;
    }

    private static CastSource source(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "source");
        try {
            return CastSource.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_SOURCE.create(name);
        }
    }

    private static LivingEntity caster(CommandSourceStack source) throws CommandSyntaxException {
        if (!(source.getEntityOrException() instanceof LivingEntity living)) {
            throw NOT_LIVING.create();
        }
        return living;
    }

    private static int cast(CommandSourceStack source, Spell spell, @Nullable CastSource castSource) throws CommandSyntaxException {
        LivingEntity caster = caster(source);
        CastResult result = castSource == null ? SpellCasting.cast(caster, spell) : SpellCasting.cast(caster, spell, castSource);
        source.sendSuccess(() -> Component.translatable(KEY + "cast", caster.getDisplayName(),
                String.valueOf(spell.id()), result.name().toLowerCase(Locale.ROOT)), false);
        return result.isSuccess() ? 1 : 0;
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        LivingEntity caster = caster(source);
        if (!SpellCasting.isCasting(caster)) {
            source.sendFailure(Component.translatable(KEY + "not_casting", caster.getDisplayName()));
            return 0;
        }
        SpellCasting.stopCasting(caster);
        source.sendSuccess(() -> Component.translatable(KEY + "stop", caster.getDisplayName()), false);
        return 1;
    }

    private static int clearCooldowns(CommandSourceStack source, Collection<? extends Entity> targets) throws CommandSyntaxException {
        List<LivingEntity> living = targets.stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .toList();
        if (living.isEmpty()) {
            throw NOT_LIVING.create();
        }
        living.forEach(SpellCooldowns::clear);
        source.sendSuccess(() -> Component.translatable(KEY + "cooldown_clear", living.size()), true);
        return living.size();
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(KEY + "list", SpellRegistries.SPELL.size()), false);
        for (Spell spell : SpellRegistries.SPELL) {
            source.sendSuccess(() -> Component.translatable(KEY + "list.entry", String.valueOf(spell.id()),
                    spell.castType().name().toLowerCase(Locale.ROOT), FORMAT.format(spell.manaCost()),
                    FORMAT.format(spell.cooldown()), FORMAT.format(spell.castTime())), false);
        }
        return SpellRegistries.SPELL.size();
    }
}
