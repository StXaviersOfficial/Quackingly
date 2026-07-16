package com.quackcraft.quackingly.voice;

import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import com.quackcraft.quackingly.util.HttpUtils;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * OpenAI TTS client.
 *
 * POST https://api.openai.com/v1/audio/speech
 *   JSON: { "model": "tts-1", "voice": "alloy", "input": "<text>", "response_format": "mp3" }
 *   Response body: raw MP3 bytes
 *
 * Defaults: tts-1 (NOT tts-1-hd) — tts-1 is the fastest cloud TTS available,
 * ~250ms end-to-end latency. That's competitive with Verity's voice output.
 *
 * For higher-quality audio, users can switch to "tts-1-hd" in the config screen
 * (slower but better).
 *
 * Voices: alloy (neutral), echo (male), fable (British), onyx (deep male),
 *         nova (female), shimmer (soft female).
 * "alloy" is the closest match to Verity's default voice character.
 */
public class OpenAITTS {

    public static byte[] synthesise(String text) throws Exception {
        QuackinglyConfig.ConfigData cfg = QuackinglyConfig.get();
        String key = cfg.ttsApiKey;
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("OpenAI TTS key required (set in Mod Menu -> Voice)");
        }

        // Truncate to ~500 chars to keep TTS latency low and avoid runaway costs
        String truncated = text.length() > 500 ? text.substring(0, 497) + "..." : text;

        String body = String.format(
                "{\"model\":\"%s\",\"voice\":\"%s\",\"input\":%s,\"response_format\":\"mp3\"}",
                cfg.ttsModel,
                cfg.ttsVoice,
                quoteJson(truncated));

        HttpResponse<byte[]> resp = HttpUtils.postBytesWithHeaders(
                "https://api.openai.com/v1/audio/speech",
                body.getBytes(StandardCharsets.UTF_8),
                Map.of(
                        "Authorization", "Bearer " + key,
                        "Content-Type", "application/json"));

        if (resp.statusCode() >= 400) {
            String err = new String(resp.body(), StandardCharsets.UTF_8);
            throw new RuntimeException("OpenAI TTS HTTP " + resp.statusCode() + ": " + err);
        }
        return resp.body();
    }

    private static String quoteJson(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }
}
