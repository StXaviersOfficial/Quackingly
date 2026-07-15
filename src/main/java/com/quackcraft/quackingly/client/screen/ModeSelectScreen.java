package com.quackcraft.quackingly.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;

/**
 * Mode Select screen. After picking a world, the user chooses:
 *   - Normal (Friendly) — Verity-without-the-horror
 *   - Unhinged (Grok-style roast) — chat tone only, no world changes
 *
 * On "Confirm", we:
 *   1. Save the chosen mode into QuackinglyConfig (persists for next session).
 *   2. Hand off to the vanilla "load world" code path by simulating what
 *      net.minecraft.client.gui.screen.world.WorldListWidget.WorldEntry does:
 *      we call LevelStorageSession.create + LevelLoadingScreen.
 *
 * For resilience we wrap the load in try/catch — if vanilla internals shift,
 * the screen shows an error instead of crashing the client.
 */
public class ModeSelectScreen extends Screen {
    private final Screen parent;
    private final File worldDir;
    private String selectedMode;

    public ModeSelectScreen(Screen parent, File worldDir) {
        super(Text.translatable("screen.quackingly.mode_select"));
        this.parent = parent;
        this.worldDir = worldDir;
        this.selectedMode = com.quackcraft.quackingly.config.QuackinglyConfig.get().defaultMode;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        // Normal
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.quackingly.mode.normal")
                                .copy().formatted(Formatting.GREEN),
                        b -> { selectedMode = "normal"; clearAndInit(); })
                .dimensions(cx - 200, cy - 30, 180, 40)
                .build());

        // Unhinged
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.quackingly.mode.unhinged")
                                .copy().formatted(Formatting.RED),
                        b -> { selectedMode = "unhinged"; clearAndInit(); })
                .dimensions(cx + 20, cy - 30, 180, 40)
                .build());

        // Description
        // (drawn in render())

        // Confirm
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.quackingly.button.play")
                                .copy().formatted(Formatting.AQUA, Formatting.BOLD),
                        b -> launchWorld())
                .dimensions(cx - 100, cy + 80, 200, 20)
                .build());

        // Back
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.quackingly.button.back"),
                        b -> client.setScreen(parent))
                .dimensions(cx - 100, height - 30, 200, 20)
                .build());
    }

    private void launchWorld() {
        try {
            com.quackcraft.quackingly.config.QuackinglyConfig.get().defaultMode = selectedMode;
            com.quackcraft.quackingly.config.QuackinglyConfig.save();

            // Hand off to vanilla world loading — this is the same code path that
            // WorldListWidget.WorldEntry#play uses. Wrapped to keep us crash-safe.
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            net.minecraft.world.level.storage.LevelStorage storage = mc.getLevelStorage();
            net.minecraft.world.level.storage.LevelStorage.Session session =
                    storage.createSession(worldDir.getName());

            net.minecraft.client.gui.screen.LevelLoadingScreen loading =
                    new net.minecraft.client.gui.screen.LevelLoadingScreen(
                            mc.createIntegratedServerLoader().createAndStart(
                                    session, mc.isDemo(), mc.getClientLevelSource()));
            mc.setScreen(loading);
        } catch (Throwable t) {
            com.quackcraft.quackingly.Quackingly.LOGGER.error("Failed to launch world from Quackingly picker", t);
            // Fallback: send user to vanilla world picker
            if (client != null) {
                client.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(parent));
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer,
                this.title.copy().formatted(Formatting.AQUA),
                width / 2, 20, 0xFFFFFFFF);

        // Selected-mode indicator
        String desc = selectedMode.equals("unhinged")
                ? "Sarcastic, brutally honest, no filter. Roasts you. Chat-tone only."
                : "Helpful, caring, friendly. Verity without the horror.";
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Selected: " + selectedMode).formatted(Formatting.YELLOW),
                width / 2, height / 2 + 5, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(desc).formatted(Formatting.GRAY),
                width / 2, height / 2 + 20, 0xFFFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }
}
