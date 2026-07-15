# Quackingly

A safe, non-horror **Verity-style** AI companion for Minecraft Fabric 1.21.1.

Quackingly is a player-model bot that follows you around, talks to you (text + voice), and acts like a real friend in single-player worlds. It uses the same kind of LLM brain as Verity (Groq by default, but auto-detects OpenAI / OpenRouter / Anthropic / Gemini / any OpenAI-compatible endpoint), with token-optimised memory so it doesn't burn through your API quota.

**No horror. No world corruption. No 18+ content.** Just a buddy.

---

## Features

| Feature | Status |
|---|---|
| Player-model companion (Carpet fake player) | ✅ |
| LLM chat — Groq / OpenAI / OpenRouter / Anthropic / Gemini / Custom | ✅ |
| Smart API key detection (`gsk_`, `sk-`, `sk-or-`, `sk-ant-`, `AIza`) | ✅ |
| Mod Menu config screen (Cloth Config) | ✅ |
| Voice input via Simple Voice Chat (Groq Whisper STT) | ✅ |
| Voice output (OpenAI TTS) | ✅ |
| `/quack skin set <username>` (Mojang session API) | ✅ |
| Default skin fetched from username "Quack" | ✅ |
| Title-screen keybind → World picker → Mode select → Launch | ✅ |
| Two chat modes: Normal (friendly) + Unhinged (Grok-style roast, chat-tone only) | ✅ |
| Token-optimised memory (compacting summariser) | ✅ |
| PojavLauncher / Mojo compatible (pure Java, no native libs) | ✅ |
| GitHub Actions CI produces `.jar` automatically | ✅ |

---

## Quick start

### 1. Get the `.jar`

Two options:

- **Recommended** — Go to [Actions tab](https://github.com/StXaviersOfficial/Quackingly/actions),
  click the latest successful "Build Quackingly" run, download the `quackingly-mod-jar` artifact.
  Unzip it to get `quackingly-1.0.0.jar`.
- Or build locally: clone the repo, run `./gradlew build` (needs JDK 21 + Gradle 8.8),
  find the jar in `build/libs/`.

### 2. Install dependencies

You need these mods in your `mods/` folder (all available on Modrinth):

| Mod | Why |
|---|---|
| [Fabric API](https://modrinth.com/mod/fabric-api) | Required by every Fabric mod |
| [Carpet](https://modrinth.com/mod/carpet) | Provides the fake-player system Quackingly uses |
| [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) | Voice input/output (optional but recommended) |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Config UI access |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Config screen backend |

The `modpack/` folder contains a Modrinth `.mrpack`-compatible manifest that bundles these
for you. Drop the built `quackingly-1.0.0.jar` into `modpack/overrides/mods/`, zip the
`modpack/` folder as `Quackingly.mrpack`, and import into your launcher.

### 3. Get API keys

- **LLM key** (required): Get a free Groq key from https://console.groq.com/keys.
  It looks like `gsk_...`. Paste it in Mod Menu → Quackingly → Settings → API Key.
- **TTS key** (only if you want voice): Get an OpenAI key from https://platform.openai.com/api-keys.
  Paste it in Mod Menu → Quackingly → Voice → OpenAI TTS API Key.

### 4. Configure

Launch Minecraft. From the title screen, press **Q** to open the Quackingly World Picker.
Pick a world → choose **Normal** or **Unhinged** mode → click **Play**.

Alternatively, launch a world normally and press **K** in-game to summon/despawn Quackingly.

### 5. Talk to him

- **Text chat**: just type `/quack ...` to talk to him, or use the push-to-talk key (default: **-**).
- **Voice**: hold **-** while you talk, release to send. Quackingly will reply in voice at his position.

---

## Commands

| Command | Effect |
|---|---|
| `/quack summon` | Spawn Quackingly next to you |
| `/quack despawn` | Despawn Quackingly |
| `/quack mode normal` | Switch to friendly mode |
| `/quack mode unhinged` | Switch to Grok-style roast mode (chat tone only) |
| `/quack skin set <username>` | Apply that Minecraft player's skin to Quackingly |
| `/quack skin reset` | Back to default ("Quack") |

---

## Keybinds

| Key | Action |
|---|---|
| **Q** (title screen) | Open Quackingly world picker |
| **K** (in-game) | Summon / despawn Quackingly |
| **-** (in-game, hold) | Push-to-talk to Quackingly |

All keybinds are rebindable in Options → Controls → Quackingly.

---

## How token optimisation works

Verity's "doesn't suck tokens" magic is actually a few standard tricks bundled together:

1. **Event-driven calls** — the LLM is only called when you send a message, not on every tick.
2. **Bounded conversation memory** — last 10 turns kept verbatim; older turns compacted into a
   ~400-token summary that gets prepended as a system message.
3. **Low max_tokens per reply** — 512 tokens hard cap. Quackingly keeps replies short.
4. **No image/world-state dumps** — only a one-line world context (player position, dimension, time).

Typical per-call cost: ~2.2k input tokens + ~150 output tokens. With Groq's free tier
(`llama-3.3-70b-versatile`), that's effectively unlimited for personal use.

---

## PojavLauncher / Mojo compatibility

Quackingly is **pure Java** — no JNI, no native libs. It works on:

- ✅ PojavLauncher (Java 21)
- ✅ Mojo Launcher
- ✅ Prism / MultiMC / ATLauncher / Modrinth App
- ✅ Vanilla launcher

**For voice on mobile**: install the Simple Voice Chat build that matches your launcher's
architecture (it has separate ARM64 builds). Quackingly will use it automatically.

**Note on STT/TTS**: Groq Whisper and OpenAI TTS are cloud calls. On mobile data, this
can be heavy. If you're on a metered connection, disable voice in the config and stick to
text chat — that's still a fully functional companion.

---

## Troubleshooting

**"Quackingly could not spawn (is Carpet installed?)"**
→ Make sure Carpet mod is in your `mods/` folder. Carpet provides the fake-player system.

**"LLM HTTP 401: Unauthorized"**
→ Your API key is wrong or expired. Re-check it in Mod Menu → Quackingly → Settings.

**"Mojang profile lookup failed"**
→ The username you typed doesn't exist. Skin defaults to Steve if the lookup 404s.

**"STT failed: Groq API key required"**
→ STT only works with Groq keys (must start with `gsk_`). If you're using OpenAI/Anthropic
for chat, you still need a separate Groq key for voice input.

**Voice doesn't work on Pojav**
→ Make sure you granted microphone permission to PojavLauncher in Android settings, and
that Simple Voice Chat is correctly configured (open its settings with V).

**GitHub Actions build failed**
→ Check the build log on the Actions tab. Most common causes: outdated dependency versions
in `gradle.properties` (bump them to match the latest 1.21.1 releases), or yarn mappings
mismatch (try the latest `1.21.1+build.X`).

---

## Architecture

```
Quackingly/
├── src/main/java/com/quackcraft/quackingly/
│   ├── Quackingly.java                  # common entrypoint
│   ├── QuackinglyClient.java            # client entrypoint + keybinds
│   ├── config/                          # config + Mod Menu screen
│   ├── llm/                             # providers + memory + prompts
│   │   ├── LLMProvider.java             # interface + 4 impls
│   │   ├── ProviderDetector.java        # key-prefix detection
│   │   ├── ConversationMemory.java      # token-optimised memory
│   │   └── PromptManager.java           # normal vs unhinged prompts
│   ├── companion/
│   │   └── CompanionManager.java        # Carpet fake player + LLM bridge
│   ├── voice/
│   │   ├── QuackinglyVoiceChatPlugin.java  # SVC plugin
│   │   ├── MicPacketCollector.java         # Opus → PCM → WAV
│   │   ├── GroqSTT.java                    # Whisper transcription
│   │   ├── OpenAITTS.java                  # voice synthesis
│   │   └── ClientVoiceController.java      # pipeline controller
│   ├── skin/
│   │   ├── SkinLoader.java                 # Mojang session API
│   │   └── SkinApplier.java                # apply to fake player
│   ├── screen/                             # WorldPicker + ModeSelect
│   ├── command/                            # /quack skin|summon|mode
│   ├── network/                            # client ↔ server packets
│   └── mixin/                              # TitleScreen hook (placeholder)
└── .github/workflows/build.yml             # CI: builds .jar
```

---

## Credits

- **Verity** (original horror mod) — the inspiration, by the Something ARG team / `VarmiteYT`'s JE port.
- **AI Player** mod by `shasankp000` (MIT) — the architectural blueprint for Carpet + LLM integration.
- **Carpet** mod by `gnembon` — fake-player system.
- **Simple Voice Chat** by `henkelmax` — voice transport + audio API.
- **Cloth Config** by `shedaniel` — config screen backend.
- **Mod Menu** by `TerraformersMC` — config screen entrypoint.

## License

MIT — see [LICENSE](LICENSE).
