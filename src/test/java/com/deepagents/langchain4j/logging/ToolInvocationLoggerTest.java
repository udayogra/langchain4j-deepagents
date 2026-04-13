package com.deepagents.langchain4j.logging;

import com.deepagents.langchain4j.flow.DeepAgentFlowListener;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolInvocationLoggerTest {

    @Test
    void truncateForLogPassesThroughShortStrings() {
        assertEquals("", ToolInvocationLogger.truncateForLog(null));
        assertEquals("{}", ToolInvocationLogger.truncateForLog("{}"));
    }

    @Test
    void truncateForLogTruncatesLongStrings() {
        String longStr = "x".repeat(9000);
        String out = ToolInvocationLogger.truncateForLog(longStr);
        assertTrue(out.length() < longStr.length());
        assertTrue(out.contains("truncated"));
    }

    @Test
    void wrapAllNoneLeavesExecutorsUnwrapped() {
        ToolSpecification spec =
                ToolSpecification.builder()
                        .name("t")
                        .description("d")
                        .parameters(JsonObjectSchema.builder().build())
                        .build();
        ToolExecutor inner = (r, m) -> "ok";
        Map<ToolSpecification, ToolExecutor> in = Map.of(spec, inner);
        Map<ToolSpecification, ToolExecutor> out =
                ToolInvocationLogger.wrapAll(in, "orchestrator", ToolInvocationLogMode.NONE);
        assertSame(inner, out.get(spec));
    }

    @Test
    void wrapAllNoneWithListenerWrapsAndNotifiesAfterExecute() {
        ToolSpecification spec =
                ToolSpecification.builder()
                        .name("t")
                        .description("d")
                        .parameters(JsonObjectSchema.builder().build())
                        .build();
        ToolExecutor inner = (r, m) -> "done";
        Map<ToolSpecification, ToolExecutor> in = Map.of(spec, inner);
        AtomicReference<String> captured = new AtomicReference<>();
        DeepAgentFlowListener listener =
                new DeepAgentFlowListener() {
                    @Override
                    public void onToolInvocation(
                            String context,
                            String toolName,
                            Object memoryId,
                            String argumentsJsonTruncated,
                            String resultTruncated) {
                        captured.set(resultTruncated);
                    }
                };
        Map<ToolSpecification, ToolExecutor> out =
                ToolInvocationLogger.wrapAll(in, "orchestrator", ToolInvocationLogMode.NONE, listener);
        assertNotSame(inner, out.get(spec));
        ToolExecutionRequest req =
                ToolExecutionRequest.builder().name("t").arguments("{\"x\":1}").build();
        assertEquals("done", out.get(spec).execute(req, "mem"));
        assertEquals("done", captured.get());
    }
}
