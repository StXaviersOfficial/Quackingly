package com.quackcraft.quackingly.command;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.companion.CompanionManager;
import com.quackcraft.quackingly.skin.SkinApplier;
import com.quackcraft.quackingly.skin.SkinLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /quack skin set <username>  — fetch the Mojang skin and apply to Quackingly
 * /quack skin reset           — back to default (config.defaultSkinUser, "Quack")
 * /quack summon               — spawn Quackingly
 * /quack despawn              — despawn Quackingly
 * /quack mode <normal|unhinged>  — switch chat mode on the live companion
 */
public class QuackSkinCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("quack")
            .then(literal("skin")
                .then(literal("set")
                    .then(argument("username", StringArgumentType.word())
                        .executes(QuackSkinCommand::skinSet)))
                .then(literal("reset")
                    .executes(QuackSkinCommand::skinReset)))
            .then(literal("summon").executes(QuackSkinCommand::summon))
            .then(literal("despawn").executes(QuackSkinCommand::despawn))
            .then(literal("mode")
                .then(literal("normal").executes(c -> mode(c, "normal")))
                .then(literal("unhinged").executes(c -> mode(c, "unhinged"))))
        );
    }

    private static int skinSet(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("Run as a player.").formatted(Formatting.RED), false);
            return 0;
        }
        String username = StringArgumentType.getString(ctx, "username");
        CompanionManager.CompanionSession s = CompanionManager.getInstance().getSession(player);
        if (s == null || !s.isAlive()) {
            player.sendMessage(Text.literal("Summon Quackingly first (/quack summon).").formatted(Formatting.RED));
            return 0;
        }
        try {
            SkinLoader.FetchedSkin skin = SkinLoader.fetch(username);
            // We can't reach the EntityPlayerMPFake directly without exposing it; route through manager.
            SkinApplier.applySkinFromUsername(s.getFakePlayer(), username);
            player.sendMessage(Text.translatable("chat.quackingly.skin_set", username).formatted(Formatting.GREEN));
        } catch (Exception e) {
            player.sendMessage(Text.translatable("chat.quackingly.skin_failed", username).formatted(Formatting.RED));
            Quackingly.LOGGER.warn("Skin set failed for '{}': {}", username, e.getMessage());
        }
        return 1;
    }

    private static int skinReset(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        CompanionManager.CompanionSession s = CompanionManager.getInstance().getSession(player);
        if (s == null || !s.isAlive()) {
            player.sendMessage(Text.literal("Summon Quackingly first.").formatted(Formatting.RED));
            return 0;
        }
        SkinApplier.applyDefaultSkin(s.getFakePlayer());
        player.sendMessage(Text.literal("Skin reset to default.").formatted(Formatting.GREEN));
        return 1;
    }

    private static int summon(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        CompanionManager.getInstance().toggle(p);
        return 1;
    }

    private static int despawn(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        CompanionManager.CompanionSession s = CompanionManager.getInstance().getSession(p);
        if (s != null && s.isAlive()) CompanionManager.getInstance().toggle(p);
        return 1;
    }

    private static int mode(CommandContext<ServerCommandSource> ctx, String m) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        CompanionManager.CompanionSession s = CompanionManager.getInstance().getSession(p);
        if (s == null || !s.isAlive()) {
            p.sendMessage(Text.literal("Summon Quackingly first.").formatted(Formatting.RED));
            return 0;
        }
        s.setMode(m);
        p.sendMessage(Text.literal("Quackingly mode: " + m).formatted(Formatting.AQUA));
        return 1;
    }
}
