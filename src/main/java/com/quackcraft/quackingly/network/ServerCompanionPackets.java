package com.quackcraft.quackingly.network;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.client.network.ClientCompanionPackets;
import com.quackcraft.quackingly.companion.CompanionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side packet receivers + senders for Quackingly.
 *
 * Registered in Quackingly#onInitialize (common side) after payload types
 * are registered in QuackinglyPayloads.register().
 */
public final class ServerCompanionPackets {

    private ServerCompanionPackets() {}

    public static void register() {
        // Client -> Server: toggle summon/despawn (K keybind)
        ServerPlayNetworking.registerGlobalReceiver(ClientCompanionPackets.ToggleSummonPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player == null) return;
                    context.server().execute(() ->
                            CompanionManager.getInstance().toggle(player));
                });

        // Client -> Server: chat message to companion
        ServerPlayNetworking.registerGlobalReceiver(ClientCompanionPackets.ChatToCompanionPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player == null) return;
                    String text = payload.text();
                    context.server().execute(() ->
                            CompanionManager.getInstance().sendToCompanion(player, text));
                });

        // Client -> Server: summon with specific mode (from /quackingly confirmation flow)
        ServerPlayNetworking.registerGlobalReceiver(ClientCompanionPackets.SummonWithModePayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player == null) return;
                    String mode = payload.mode();
                    context.server().execute(() ->
                            CompanionManager.getInstance().summonWithMode(player, mode));
                });
    }

    /** Server -> Client: tell client to open the "Add Quackingly?" confirmation popup. */
    public static void sendOpenConfirmation(ServerPlayerEntity host) {
        try {
            ServerPlayNetworking.send(host, new ClientCompanionPackets.OpenConfirmationScreenPayload());
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to send open_confirmation packet", t);
        }
    }

    /** Server -> Client: Quackingly's reply text (triggers TTS on client). */
    public static void sendTtsReply(ServerPlayerEntity host, String replyText) {
        try {
            ServerPlayNetworking.send(host, new ClientCompanionPackets.CompanionReplyPayload(replyText));
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to send companion_reply packet", t);
        }
    }
}
