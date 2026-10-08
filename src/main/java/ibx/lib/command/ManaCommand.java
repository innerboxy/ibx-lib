package ibx.lib.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import ibx.lib.IbxLib;
import ibx.lib.api.ManaApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 法力指令（需要 2 级权限）：
 * <pre>
 * /taverntales mana get &lt;目标&gt;
 * /taverntales mana set &lt;目标们&gt; &lt;数值&gt;
 * /taverntales mana add &lt;目标们&gt; &lt;数值&gt;   （可为负数，走 ManaApi.add，会触发获得事件）
 * /taverntales mana fill &lt;目标们&gt;
 * </pre>
 * 目标里不是生物的实体会被忽略。
 */
public final class ManaCommand {
    private ManaCommand() {}

    private static final String KEY = "commands." + IbxLib.MODID + ".mana.";
    private static final DecimalFormat FORMAT = new DecimalFormat("0.##");
    private static final SimpleCommandExceptionType NO_LIVING =
            new SimpleCommandExceptionType(Component.translatable(KEY + "no_living"));

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ManaCommand::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        // 权限挂在 mana 这一层：taverntales 根节点可能被本系列别的模组共用，合并时以先注册的为准。
        event.getDispatcher().register(Commands.literal("taverntales")
                .then(Commands.literal("mana")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("get")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(c -> get(c.getSource(), EntityArgument.getEntity(c, "target")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                                .executes(c -> apply(c.getSource(), EntityArgument.getEntities(c, "targets"),
                                                        FloatArgumentType.getFloat(c, "amount"), "set", ManaApi::set)))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg())
                                                .executes(c -> apply(c.getSource(), EntityArgument.getEntities(c, "targets"),
                                                        FloatArgumentType.getFloat(c, "amount"), "add", ManaApi::add)))))
                        .then(Commands.literal("fill")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(c -> apply(c.getSource(), EntityArgument.getEntities(c, "targets"),
                                                0.0F, "fill", (entity, ignored) -> ManaApi.set(entity, ManaApi.getMaxMana(entity))))))));
    }

    private static int get(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        if (!(target instanceof LivingEntity living)) {
            throw NO_LIVING.create();
        }
        source.sendSuccess(() -> Component.translatable(KEY + "get", living.getDisplayName(),
                FORMAT.format(ManaApi.getMana(living)), FORMAT.format(ManaApi.getMaxMana(living))), false);
        return Math.round(ManaApi.getMana(living));
    }

    private static int apply(CommandSourceStack source, Collection<? extends Entity> targets, float amount,
                             String action, BiConsumer<LivingEntity, Float> operation) throws CommandSyntaxException {
        List<LivingEntity> living = targets.stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .toList();
        if (living.isEmpty()) {
            throw NO_LIVING.create();
        }
        living.forEach(entity -> operation.accept(entity, amount));
        source.sendSuccess(() -> Component.translatable(KEY + action, living.size(), FORMAT.format(amount)), true);
        return living.size();
    }
}
