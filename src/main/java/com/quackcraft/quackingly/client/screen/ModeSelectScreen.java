package com.quackcraft.quackingly.client.screen;

import com.quackcraft.quackingly.Quackingly;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.level.storage.LevelStorage;

import java.io.File;

/**
 * Mode Select screen. After picking a world, the user chooses:
 *   - Normal (Friendly) — Verity-without-the-horror
 *   - Unhinged (Grok-style roast) — chat tone only, no world changes
 *
 * On "Play", we:
 *   1. Save the chosen mode into QuackinglyConfig.
 *   2. Directly launch the selected world via vanilla's LevelStorage.Session +
 *      IntegratedServerLoader.start(). This is the same code path that
 *      vanilla's WorldListWidget.WorldEntry#play uses.
 *
 * If the world fails to load for any reason, we fall back to the vanilla
 * SelectWorldScreen so the user can try manually.
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

        // Play — directly launches the world
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
        // Save the chosen mode
        com.quackcraft.quackingly.config.QuackinglyConfig.get().defaultMode = selectedMode;
        com.quackcraft.quackingly.config.QuackinglyConfig.save();

        if (client == null || worldDir == null) return;

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            LevelStorage storage = mc.getLevelStorage();
            // createSession takes the world folder name (e.g. "New World")
            LevelStorage.Session session = storage.createSession(worldDir.getName());

            // Start loading the world — this shows LevelLoadingScreen automatically.
            // Same code path as vanilla WorldListWidget.WorldEntry#play.
            mc.createIntegratedServerLoader().start(session, mc.isDemo());
        } catch (Throwable t) {
            Quackingly.LOGGER.error("[Quackingly] Failed to launch world '{}' directly, falling back to vanilla world list",
                    worldDir.getName(), t);
            // Fallback: open vanilla select-world screen so user can try manually
            client.setScreen(new SelectWorldScreen(parent));
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer,
                this.title.copy().formatted(Formatting.AQUA),
                width / 2, 20, 0xFFFFFFFF);

        // Selected-mode indicator + description
        String desc = selectedMode.equals("unhinged")
                ? "Sarcastic, brutally honest, no filter. Roasts you. Chat-tone only."
                : "Helpful, caring, friendly. Verity without the horror.";
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Selected: " + selectedMode).formatted(Formatting.YELLOW),
                width / 2, height / 2 + 5, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(desc).formatted(Formatting.GRAY),
                width / 2, height / 2 + 20, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("World: " + worldDir.getName()).formatted(Formatting.DARK_GRAY),
                width / 2, height / 2 + 50, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Press Play to launch — then press K in-game to summon Quackingly.")
                        .formatted(Formatting.DARK_GRAY),
                width / 2, height / 2 + 65, 0xFFFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }
}
