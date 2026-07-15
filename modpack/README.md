# Quackingly Modpack

This folder contains a Modrinth `.mrpack`-compatible manifest that bundles
all the dependencies Quackingly needs.

## To use

1. Wait for the GitHub Actions build to finish (push to `main` triggers it).
2. Download the `quackingly-mod-jar` artifact from the Actions run page.
3. Drop the built `quackingly-1.0.0.jar` into this `overrides/mods/` folder.
4. Zip this entire `modpack/` folder and rename to `Quackingly.mrpack`.
5. Import in Prism Launcher / MultiMC / ATLauncher / Modrinth App / Pojav / Mojo.

## Dependencies bundled by the manifest

| Mod | Version | Source |
|---|---|---|
| Fabric API | 0.115.1+1.21.1 | Modrinth |
| Carpet | 1.4.147 (MC 1.21.1) | Modrinth |
| Simple Voice Chat | 2.5.21 (Fabric 1.21.1) | Modrinth |
| Mod Menu | 11.0.2 | Modrinth |
| Cloth Config | 15.0.140 | Modrinth |

The hashes in `modrinth.index.json` are placeholders. When you regenerate the
pack with `packwiz refresh`, the real hashes will be computed and verified.

## Notes for Pojav / Mojo

- Install the latest PojavLauncher or Mojo release (Java 21 supported).
- Simple Voice Chat requires microphone permission in Android settings.
- The voice pipeline runs entirely in-process (Groq STT + OpenAI TTS).
  Mobile data charges apply if you use cloud STT/TTS — set voice off in config
  if you want to save bandwidth.
