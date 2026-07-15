package com.quackcraft.quackingly.client.network;

import com.quackcraft.quackingly.Quackingly;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client -> Server packets for Quackingly.
 *
 * Packets:
 *   TOGGLE_SUMMON       — no payload, just toggle spawn state
 *   PUSH_TO_TALK_AUDIO  — not used (SVC handles audio transport natively)
 *   CHAT_TO_COMPANION   — String text, server forwards to CompanionManager
 */
public class ClientCompanionPackets {

    public record ToggleSummonPayload() implements CustomPayload {
        public static final Identifier ID = Identifier.of(Quackingly.MOD_ID, "toggle_summon");
        public static final PacketCodec<RegistryByteBuf, ToggleSummonPayload> CODEC =
                PacketCodec.unit(new ToggleSummonPayload());
        @Override public Identifier id() { return ID; }
    }

    public record ChatToCompanionPayload(String text) implements CustomPayload {
        public static final Identifier ID = Identifier.of(Quackingly.MOD_ID, "chat_to_companion");
        public static final PacketCodec<RegistryByteBuf, ChatToCompanionPayload> CODEC =
                PacketCodec.of(
                        (val, buf) -> buf.writeCharSequence(val.text()),
                        buf -> new ChatToCompanionPayload(buf.readCharSequence(32767, java.nio.charset.StandardCharsets.UTF_8).toString()));
        @Override public Identifier id() { return ID; }
    }

    public static void registerReceivers() {
        // Currently we only SEND from client; the server-side receiver is registered in
        // com.quackcraft.quackingly.network.ServerCompanionPackets (loaded on the server).
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
