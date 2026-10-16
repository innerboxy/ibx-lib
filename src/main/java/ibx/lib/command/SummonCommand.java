package ibx.lib.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import ibx.lib.IbxLib;
import ibx.lib.api.SummonApi;
import ibx.lib.summon.SummonData;
import ibx.lib.summon.UnsummonReason;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;

/**
 * 召唤物指令（需要 2 级权限），不写目标即执行者：
 * <pre>
 * /taverntales summon list [主人]         列出主人在场的召唤物、已用 / 总名额、剩余时间
 * /taverntales summon dismiss [主人们]    解散全部召唤物
 * </pre>
 */
public final class SummonCommand {
    private SummonCommand() {}

    private static final String KEY = "commands." + IbxLib.MODID + ".summon.";
    private static final DecimalFormat SECONDS = new DecimalFormat("0.#");
    private static final SimpleCommandExceptionType NOT_LIVING =
            new SimpleCommandExceptionType(Component.translatable("commands." + IbxLib.MODID + ".spell.not_living"));

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SummonCommand::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("taverntales")
                .then(Commands.literal("summon")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("list")
                                .executes(c -> list(c.getSource(), c.getSource().getEntityOrException()))
                                .then(Commands.argument("owner", EntityArgument.entity())
                                        .executes(c -> list(c.getSource(), EntityArgument.getEntity(c, "owner")))))
                        .then(Commands.literal("dismiss")
                                .executes(c -> dismiss(c.getSource(), List.of(c.getSource().getEntityOrException())))
                                .then(Commands.argument("owners", EntityArgument.entities())
                                        .executes(c -> dismiss(c.getSource(), EntityArgument.getEntities(c, "owners")))))));
    }

    private static int list(CommandSourceStack source, Entity entity) throws CommandSyntaxException {
        if (!(entity instanceof LivingEntity owner)) {
            throw NOT_LIVING.create();
        }
        List<Mob> summons = SummonApi.getSummons(owner);
        source.sendSuccess(() -> Component.translatable(KEY + "list", owner.getDisplayName(), summons.size(),
                SummonApi.used(owner), SummonApi.capacity(owner)), false);
        long now = owner.level().getGameTime();
        for (Mob summon : summons) {
            SummonData data = SummonApi.data(summon);
            Component life = data.isPermanent()
                    ? Component.translatable(KEY + "permanent")
                    : Component.translatable(KEY + "remaining", SECONDS.format(Math.max(0L, data.expiresAt() - now) / 20.0D));
            source.sendSuccess(() -> Component.translatable(KEY + "list.entry", summon.getDisplayName(), data.cost(), life), false);
        }
        return summons.size();
    }

    private static int dismiss(CommandSourceStack source, Collection<? extends Entity> targets) throws CommandSyntaxException {
        List<LivingEntity> owners = targets.stream().filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();
        if (owners.isEmpty()) {
            throw NOT_LIVING.create();
        }
        int total = 0;
        for (LivingEntity owner : owners) {
            total += SummonApi.dismissAll(owner, UnsummonReason.DISMISSED);
        }
        int count = total;
        source.sendSuccess(() -> Component.translatable(KEY + "dismiss", count, owners.size()), true);
        return count;
    }
}
