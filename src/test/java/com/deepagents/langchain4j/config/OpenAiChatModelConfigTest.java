package com.deepagents.langchain4j.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiChatModelConfigTest {

    @Test
    void rejectsBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () -> OpenAiChatModelConfig.of("", "gpt-4o"));
        assertThrows(IllegalArgumentException.class, () -> OpenAiChatModelConfig.of("  ", "gpt-4o"));
    }

    @Test
    void fromRequiredEnvironmentFailsWhenKeyMissing() {
        // Do not assume OPENAI_API_KEY is unset in CI; only assert when absent
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank()) {
            assertThrows(IllegalStateException.class, OpenAiChatModelConfig::fromRequiredEnvironment);
        } else {
            assertDoesNotThrow(OpenAiChatModelConfig::fromRequiredEnvironment);
        }
    }
}
