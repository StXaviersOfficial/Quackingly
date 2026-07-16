package com.quackcraft.quackingly.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import net.minecraft.entity.player.PlayerEntity;

import com.quackcraft.quackingly.skin.SkinLoader.FetchedSkin;

/**
 * Applies a Mojang-fetched skin to a (fake) player.
 *
 * Strategy: GameProfile properties carry a "textures" Property whose value is the
 * base64 blob from Mojang's session API. We replace the textures property in-place.
 *
 * Client-side, Minecraft's PlayerSkinProvider will then fetch the actual PNG from
 * textures.minecraft.net and render it on the fake player.
 *
 * For /quack skin set <username>: we fetch from Mojang live, then apply.
 * For the default "Quack" skin: we fetch once on spawn, cache, and reuse.
 *
 * NOTE: We intentionally use PlayerEntity (the vanilla base class) here, not
 * Carpet's EntityPlayerMPFake. This way SkinApplier doesn't depend on Carpet
 * being present at runtime. If Carpet is missing, the apply is just a no-op
 * (no fake player exists to apply to anyway).
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

    /** Apply an already-fetched skin's GameProfile properties to the (fake) player. */
    public static void applyFetched(PlayerEntity entity, FetchedSkin skin) {
        if (entity == null || skin == null) return;
        try {
            GameProfile profile = entity.getGameProfile();
            if (profile == null) {
                Quackingly.LOGGER.warn("Cannot apply skin — player has no GameProfile.");
                return;
            }
            // Replace any existing textures property
            profile.getProperties().removeAll("textures");
            profile.getProperties().put("textures", new Property(
                    "textures", skin.textureBase64, skin.signatureBase64));
            Quackingly.LOGGER.info("[Quackingly] Applied skin '{}' to player.", skin.username);
        } catch (Throwable t) {
            Quackingly.LOGGER.error("Failed to apply skin to player", t);
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
