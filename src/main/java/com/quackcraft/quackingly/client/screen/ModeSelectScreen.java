package com.quackcraft.quackingly.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
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
 *   2. Hand off to the vanilla SelectWorldScreen. The user clicks Play on their
 *      world in the vanilla list — Quackingly's mode is already saved, so when
 *      the world loads and the user presses K to summon him, he'll use the
 *      chosen mode.
 *
 * Why we don't directly launch the world: vanilla's LevelLoadingScreen +
 * LevelStorage.Session APIs differ between MC versions and are easy to break.
 * Handing off to the vanilla world-select screen is rock-solid across versions
 * and still gets the user where they want to go in one extra click.
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

        // Confirm
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.quackingly.button.play")
                                .copy().formatted(Formatting.AQUA, Formatting.BOLD),
                        b -> confirmAndLaunch())
                .dimensions(cx - 100, cy + 80, 200, 20)
                .build());

        // Back
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.quackingly.button.back"),
                        b -> client.setScreen(parent))
                .dimensions(cx - 100, height - 30, 200, 20)
                .build());
    }

    private void confirmAndLaunch() {
        // Save the chosen mode
        com.quackcraft.quackingly.config.QuackinglyConfig.get().defaultMode = selectedMode;
        com.quackcraft.quackingly.config.QuackinglyConfig.save();

        // Hand off to vanilla world-select screen — user clicks their world there.
        // This is the most robust path across MC versions and avoids fragile calls
        // into LevelStorage.Session / LevelLoadingScreen internals.
        if (client != null) {
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
                Text.literal("After Play: pick your world in the vanilla list and press K in-game to summon.")
                        .formatted(Formatting.DARK_GRAY),
                width / 2, height / 2 + 50, 0xFFFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }
}
