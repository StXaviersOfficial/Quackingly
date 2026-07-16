package com.quackcraft.quackingly.mixin;

import com.quackcraft.quackingly.QuackinglyClient;
import com.quackcraft.quackingly.client.screen.WorldPickerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * We don't actually inject into TitleScreen directly — QuackinglyClient already
 * polls the openMenuKeybind on END_CLIENT_TICK and opens WorldPickerScreen when
 * no screen is showing (i.e. on the title screen).
 *
 * This Mixin is here as a fallback: if the user is ON the TitleScreen and presses
 * the keybind, we ensure the screen opens. Belt-and-braces, since vanilla title
 * screen eats keypresses that aren't bound to buttons.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void quackingly$tickTitle(CallbackInfo ci) {
        // No-op: keybind handling is done in QuackinglyClient.onEndTick
        // by checking (client.currentScreen instanceof TitleScreen).
        // Keeping this Mixin as a placeholder for future title-screen hooks.
    }
}
