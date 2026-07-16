package com.quackcraft.quackingly.network;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.client.network.ClientCompanionPackets;
import com.quackcraft.quackingly.companion.CompanionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side packet receivers + senders for Quackingly.
 *
 * Registered in Quackingly#onInitialize (common side) so they're available on
 * any integrated or dedicated server.
 */
public final class ServerCompanionPackets {

    private ServerCompanionPackets() {}

    public static void register() {
        // Client -> Server: toggle summon/despawn
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
    }

    /**
     * Server -> Client: tell the host client that Quackingly just said something.
     * The client will then synthesise TTS audio and play it.
     */
    public static void sendTtsReply(ServerPlayerEntity host, String replyText) {
        try {
            ServerPlayNetworking.send(host, new ClientCompanionPackets.CompanionReplyPayload(replyText));
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to send companion_reply packet", t);
        }
    }
}
