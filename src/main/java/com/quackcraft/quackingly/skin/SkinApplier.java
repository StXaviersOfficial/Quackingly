package com.quackcraft.quackingly.skin;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

import com.quackcraft.quackingly.skin.SkinLoader.FetchedSkin;

/**
 * Applies a Mojang-fetched skin to a Carpet fake player.
 *
 * Strategy: GameProfile properties carry a "textures" Property whose value is the
 * base64 blob from Mojang's session API. Carpet's EntityPlayerMPFake builds its
 * GameProfile from the username only — so we replace the textures property in-place.
 *
 * Client-side, Minecraft's PlayerSkinProvider will then fetch the actual PNG from
 * textures.minecraft.net and render it on the fake player.
 *
 * For /quack skin set <username>: we fetch from Mojang live, then apply.
 * For the default "Quack" skin: we fetch once on spawn, cache, and reuse.
 */
public final class SkinApplier {

    private static FetchedSkin defaultSkinCache;

    private SkinApplier() {}

    /** Apply the default skin (from config.defaultSkinUser, default "Quack"). */
    public static void applyDefaultSkin(PlayerEntity entity) {
        String user = QuackinglyConfig.get().defaultSkinUser;
        if (user == null || user.isBlank()) user = "Quack";
        applySkinFromUsername(entity, user);
    }

    /** Apply a skin by fetching the given username from Mojang. */
    public static void applySkinFromUsername(PlayerEntity entity, String username) {
        try {
            FetchedSkin skin = SkinLoader.fetch(username);
            applyFetched(entity, skin);
        } catch (Exception e) {
            Quackingly.LOGGER.warn("Could not fetch skin for '{}': {}", username, e.getMessage());
            // Fallback: leave the player with Steve/Alex (default MC behaviour).
        }
    }

    /** Apply an already-fetched skin's GameProfile properties to the fake player. */
    public static void applyFetched(PlayerEntity entity, FetchedSkin skin) {
        if (!(entity instanceof EntityPlayerMPFake fake)) return;
        try {
            GameProfile profile = fake.getGameProfile();
            // Replace any existing textures property
            profile.getProperties().removeAll("textures");
            profile.getProperties().put("textures", new Property(
                    "textures", skin.textureBase64, skin.signatureBase64));
            Quackingly.LOGGER.info("[Quackingly] Applied skin '{}' to fake player.", skin.username);
        } catch (Throwable t) {
            Quackingly.LOGGER.error("Failed to apply skin to fake player", t);
        }
    }

    /** Get-or-fetch the default skin (cached after first call). */
    public static FetchedSkin getDefaultSkinCached() {
        if (defaultSkinCache != null) return defaultSkinCache;
        try {
            String user = QuackinglyConfig.get().defaultSkinUser;
            if (user == null || user.isBlank()) user = "Quack";
            defaultSkinCache = SkinLoader.fetch(user);
        } catch (Exception e) {
            Quackingly.LOGGER.warn("Could not load default Quack skin: {}", e.getMessage());
        }
        return defaultSkinCache;
    }
}
