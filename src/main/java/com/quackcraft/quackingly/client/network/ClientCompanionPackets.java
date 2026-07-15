package com.quackcraft.quackingly.client.network;

import com.quackcraft.quackingly.Quackingly;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.network.codec.PacketCodecs;

import java.nio.charset.StandardCharsets;

/**
 * Client -> Server packets for Quackingly.
 *
 * Packets:
 *   TOGGLE_SUMMON       — no payload, just toggle spawn state
 *   CHAT_TO_COMPANION   — String text, server forwards to CompanionManager
 *
 * Uses the Fabric API 1.21.1 CustomPayload + PacketCodec pattern.
 */
public class ClientCompanionPackets {

    public record ToggleSummonPayload() implements CustomPayload {
        public static final CustomPayload.Id<ToggleSummonPayload> ID =
                new CustomPayload.Id<>(Identifier.of(Quackingly.MOD_ID, "toggle_summon"));
        public static final PacketCodec<RegistryByteBuf, ToggleSummonPayload> CODEC =
                PacketCodec.unit(new ToggleSummonPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ChatToCompanionPayload(String text) implements CustomPayload {
        public static final CustomPayload.Id<ChatToCompanionPayload> ID =
                new CustomPayload.Id<>(Identifier.of(Quackingly.MOD_ID, "chat_to_companion"));
        public static final PacketCodec<RegistryByteBuf, ChatToCompanionPayload> CODEC =
                PacketCodecs.STRING.xmap(ChatToCompanionPayload::new, ChatToCompanionPayload::text);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
        public String text() { return text; }
    }

    public static void sendToggleSummon() {
        try {
            ClientPlayNetworking.send(new ToggleSummonPayload());
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to send toggle_summon packet", t);
        }
    }

    public static void sendChatToCompanion(String text) {
        try {
            ClientPlayNetworking.send(new ChatToCompanionPayload(text));
        } catch (Throwable t) {
            Quackingly.LOGGER.warn("Failed to send chat_to_companion packet", t);
        }
    }
}
