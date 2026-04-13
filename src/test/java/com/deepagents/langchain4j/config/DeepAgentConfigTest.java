package com.deepagents.langchain4j.config;

import com.deepagents.langchain4j.flow.DeepAgentFlowListener;
import com.deepagents.langchain4j.logging.ToolInvocationLogMode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepAgentConfigTest {

    @Test
    void buildRequiresExactlyOneModelSource() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DeepAgentConfig.builder()
                                .workspace(Path.of("/tmp/ws"))
                                .build());
        ChatModel model =
                OpenAiChatModel.builder().apiKey("x").modelName("gpt-4o-mini").build();
        assertThrows(
                IllegalStateException.class,
                () ->
                        DeepAgentConfig.builder()
                                .workspace(Path.of("/tmp/ws"))
                                .openAi(OpenAiChatModelConfig.of("k", "gpt-4o"))
                                .chatModel(model));
    }

    @Test
    void chatModelPathBuilds() {
        ChatModel model =
                OpenAiChatModel.builder().apiKey("test").modelName("gpt-4o-mini").build();
        DeepAgentConfig cfg =
                DeepAgentConfig.builder()
                        .workspace(Path.of("/tmp/ws"))
                        .chatModel(model)
                        .build();
        assertNotNull(cfg.chatModel());
        assertEquals(DeepAgentConfig.DEFAULT_TOOL_INVOCATION_LOG_MODE, cfg.toolInvocationLogMode());
    }

    @Test
    void additionalToolsDefaultsToEmpty() {
        ChatModel model =
                OpenAiChatModel.builder().apiKey("test").modelName("gpt-4o-mini").build();
        DeepAgentConfig cfg =
                DeepAgentConfig.builder()
                        .workspace(Path.of("/tmp/ws"))
                        .chatModel(model)
                        .build();
        assertTrue(cfg.additionalTools().isEmpty());
    }

    @Test
    void additionalToolsRoundTrip() {
        ChatModel model =
                OpenAiChatModel.builder().apiKey("test").modelName("gpt-4o-mini").build();
        ToolSpecification spec =
                ToolSpecification.builder()
                        .name("ping")
                        .description("test")
                        .parameters(JsonObjectSchema.builder().build())
                        .build();
        ToolExecutor exec = (request, memoryId) -> "pong";
        DeepAgentConfig cfg =
                DeepAgentConfig.builder()
                        .workspace(Path.of("/tmp/ws"))
                        .chatModel(model)
                        .additionalTools(Map.of(spec, exec))
                        .build();
        assertEquals(1, cfg.additionalTools().size());
        assertEquals("pong", cfg.additionalTools().get(spec).execute(null, null));
    }

    @Test
    void toolInvocationLogModeRoundTrip() {
        ChatModel model =
                OpenAiChatModel.builder().apiKey("test").modelName("gpt-4o-mini").build();
        DeepAgentConfig cfg =
                DeepAgentConfig.builder()
                        .workspace(Path.of("/tmp/ws"))
                        .chatModel(model)
                        .toolInvocationLogMode(ToolInvocationLogMode.NONE)
                        .build();
        assertEquals(ToolInvocationLogMode.NONE, cfg.toolInvocationLogMode());
    }

    @Test
    void flowListenerRoundTrip() {
        ChatModel model =
                OpenAiChatModel.builder().apiKey("test").modelName("gpt-4o-mini").build();
        DeepAgentFlowListener listener = new DeepAgentFlowListener() {};
        DeepAgentConfig cfg =
                DeepAgentConfig.builder()
                        .workspace(Path.of("/tmp/ws"))
                        .chatModel(model)
                        .flowListener(listener)
                        .build();
        assertEquals(listener, cfg.flowListener());
    }

    @Test
    void recordFlowTraceToStderrInstallsRecorderAndMatchesFlowListener() {
        ChatModel model =
                OpenAiChatModel.builder().apiKey("test").modelName("gpt-4o-mini").build();
        DeepAgentConfig cfg =
                DeepAgentConfig.builder()
                        .workspace(Path.of("/tmp/ws"))
                        .chatModel(model)
                        .recordFlowTraceToStderr(true)
                        .build();
        assertTrue(cfg.stderrFlowRecorder().isPresent());
        assertSame(cfg.stderrFlowRecorder().orElseThrow(), cfg.flowListener());
    }

    @Test
    void recordFlowTraceToStderrConflictsWithFlowListener() {
        ChatModel model =
                OpenAiChatModel.builder().apiKey("test").modelName("gpt-4o-mini").build();
        assertThrows(
                IllegalStateException.class,
                () ->
                        DeepAgentConfig.builder()
                                .workspace(Path.of("/tmp/ws"))
                                .chatModel(model)
                                .flowListener(new DeepAgentFlowListener() {})
                                .recordFlowTraceToStderr(true)
                                .build());
    }
}
