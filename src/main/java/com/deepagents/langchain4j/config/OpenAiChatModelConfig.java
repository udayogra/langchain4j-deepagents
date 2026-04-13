package com.deepagents.langchain4j.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.Objects;

/**
 * Minimal OpenAI settings (API key + model id) and a {@link #toChatModel()} factory. For advanced tuning (base URL,
 * temperature, etc.), build a {@link ChatModel} yourself and pass it via
 * {@link DeepAgentConfig.Builder#chatModel(dev.langchain4j.model.chat.ChatModel)}.
 */
public record OpenAiChatModelConfig(String apiKey, String modelName) {

    public OpenAiChatModelConfig {
        Objects.requireNonNull(apiKey, "apiKey");
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        Objects.requireNonNull(modelName, "modelName");
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
    }

    public static OpenAiChatModelConfig of(String apiKey, String modelName) {
        return new OpenAiChatModelConfig(apiKey, modelName);
    }

    /** Builds a LangChain4j {@link OpenAiChatModel} from this config. */
    public ChatModel toChatModel() {
        return OpenAiChatModel.builder().apiKey(apiKey).modelName(modelName).build();
    }

    /**
     * {@code OPENAI_API_KEY} (required) and {@code OPENAI_MODEL} (optional, default {@code gpt-4o}).
     *
     * @throws IllegalStateException if {@code OPENAI_API_KEY} is missing or blank
     */
    public static OpenAiChatModelConfig fromRequiredEnvironment() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set OPENAI_API_KEY (and optionally OPENAI_MODEL).");
        }
        String modelName = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o");
        return new OpenAiChatModelConfig(apiKey, modelName);
    }
}
