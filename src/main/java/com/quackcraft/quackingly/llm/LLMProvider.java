package com.quackcraft.quackingly.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quackcraft.quackingly.Quackingly;
import com.quackcraft.quackingly.config.QuackinglyConfig;
import com.quackcraft.quackingly.util.HttpUtils;

import java.net.http.HttpResponse;
import java.util.List;

/**
 * LLM provider interface. Single method: chat(messages) -> reply string.
 */
public interface LLMProvider {
    String chat(List<ChatMessage> messages) throws Exception;
    String getProviderName();
    String getModel();
    boolean isReady();

    /* ---- Factory ---- */

    static LLMProvider fromConfig() {
        QuackinglyConfig.ConfigData cfg = QuackinglyConfig.get();
        String provider = ProviderDetector.detect(cfg.apiKey);
        String baseUrl  = ProviderDetector.baseUrlFor(provider);
        String model    = cfg.model != null && !cfg.model.isBlank() ? cfg.model
                          : ProviderDetector.defaultModelFor(provider);

        switch (provider) {
            case "anthropic": return new AnthropicProvider(cfg.apiKey, model);
            case "gemini":    return new GeminiProvider(cfg.apiKey, model);
            case "none":      Quackingly.LOGGER.warn("No API key set; LLM disabled");
            default:          return new OpenAICompatibleProvider(provider, baseUrl, cfg.apiKey, model);
        }
    }

    /* ---- OpenAI-compatible implementation (Groq, OpenAI, OpenRouter, custom) ---- */

    class OpenAICompatibleProvider implements LLMProvider {
        private final String provider;
        private final String baseUrl;
        private final String apiKey;
        private final String model;

        public OpenAICompatibleProvider(String provider, String baseUrl, String apiKey, String model) {
            this.provider = provider;
            this.baseUrl  = baseUrl;
            this.apiKey   = apiKey;
            this.model    = model;
        }

        @Override public String getProviderName() { return provider; }
        @Override public String getModel()        { return model; }
        @Override public boolean isReady()        { return apiKey != null && !apiKey.isBlank(); }

        @Override
        public String chat(List<ChatMessage> messages) throws Exception {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("temperature", 0.7);
            body.addProperty("max_tokens", 512);
            JsonArray arr = new JsonArray();
            for (ChatMessage m : messages) {
                JsonObject o = new JsonObject();
                o.addProperty("role", m.role.name().toLowerCase());
                o.addProperty("content", m.content);
                arr.add(o);
            }
            body.add("messages", arr);

            HttpResponse<String> resp = HttpUtils.postJson(
                    baseUrl + "/chat/completions",
                    "Bearer " + apiKey,
                    body.toString());

            if (resp.statusCode() >= 400) {
                throw new RuntimeException("LLM " + provider + " HTTP " + resp.statusCode()
                        + ": " + resp.body());
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();
        }
    }

    /* ---- Anthropic (different protocol: /messages, x-api-key header) ---- */

    class AnthropicProvider implements LLMProvider {
        private final String apiKey;
        private final String model;
        public AnthropicProvider(String apiKey, String model) {
            this.apiKey = apiKey; this.model = model;
        }
        @Override public String getProviderName() { return "anthropic"; }
        @Override public String getModel()        { return model; }
        @Override public boolean isReady()        { return apiKey != null && !apiKey.isBlank(); }

        @Override
        public String chat(List<ChatMessage> messages) throws Exception {
            String systemPrompt = "";
            JsonArray arr = new JsonArray();
            for (ChatMessage m : messages) {
                if (m.role == ChatMessage.Role.SYSTEM) { systemPrompt = m.content; continue; }
                JsonObject o = new JsonObject();
                o.addProperty("role", m.role == ChatMessage.Role.USER ? "user" : "assistant");
                o.addProperty("content", m.content);
                arr.add(o);
            }

            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 512);
            body.addProperty("system", systemPrompt);
            body.add("messages", arr);

            HttpResponse<String> resp = HttpUtils.postJsonWithHeaders(
                    "https://api.anthropic.com/v1/messages",
                    body.toString(),
                    java.util.Map.of(
                            "x-api-key", apiKey,
                            "anthropic-version", "2023-06-01",
                            "content-type", "application/json"));

            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Anthropic HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            return json.getAsJsonArray("content")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString().trim();
        }
    }

    /* ---- Gemini ---- */

    class GeminiProvider implements LLMProvider {
        private final String apiKey;
        private final String model;
        public GeminiProvider(String apiKey, String model) {
            this.apiKey = apiKey; this.model = model;
        }
        @Override public String getProviderName() { return "gemini"; }
        @Override public String getModel()        { return model; }
        @Override public boolean isReady()        { return apiKey != null && !apiKey.isBlank(); }

        @Override
        public String chat(List<ChatMessage> messages) throws Exception {
            String systemPrompt = "";
            JsonArray contents = new JsonArray();
            for (ChatMessage m : messages) {
                if (m.role == ChatMessage.Role.SYSTEM) { systemPrompt = m.content; continue; }
                JsonObject part = new JsonObject();
                part.addProperty("text", m.content);
                JsonArray parts = new JsonArray(); parts.add(part);
                JsonObject o = new JsonObject();
                o.addProperty("role", m.role == ChatMessage.Role.USER ? "user" : "model");
                o.add("parts", parts);
                contents.add(o);
            }
            JsonObject body = new JsonObject();
            body.add("contents", contents);
            if (!systemPrompt.isEmpty()) {
                JsonObject sys = new JsonObject();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", systemPrompt);
                JsonArray sysParts = new JsonArray(); sysParts.add(sysPart);
                sys.add("parts", sysParts);
                body.add("systemInstruction", sys);
            }

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + apiKey;
            HttpResponse<String> resp = HttpUtils.postJson(url, "Bearer " + apiKey, body.toString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Gemini HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            return json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString().trim();
        }
    }
}
