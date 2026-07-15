package com.quackcraft.quackingly.network;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.client.network.ClientCompanionPackets;
import com.quackcraft.quackingly.companion.CompanionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side packet receivers for Quackingly.
 *
 * Registered in Quackingly#onInitialize (common side) so they're available on
 * any integrated or dedicated server.
 */
public final class ServerCompanionPackets {

    private ServerCompanionPackets() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ClientCompanionPackets.ToggleSummonPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player == null) return;
                    context.server().execute(() ->
                            CompanionManager.getInstance().toggle(player));
                });

        ServerPlayNetworking.registerGlobalReceiver(ClientCompanionPackets.ChatToCompanionPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player == null) return;
                    String text = payload.text();
                    context.server().execute(() ->
                            CompanionManager.getInstance().sendToCompanion(player, text));
                });
    }
}
