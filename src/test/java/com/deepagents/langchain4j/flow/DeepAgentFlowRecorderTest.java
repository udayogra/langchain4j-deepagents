package com.deepagents.langchain4j.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepAgentFlowRecorderTest {

    @Test
    void recordsCallbacksInOrderAndFormatsTimeline() {
        DeepAgentFlowRecorder r = new DeepAgentFlowRecorder();
        r.onOrchestratorSystemReady("SYS");
        r.onOrchestratorUserMessage("session-a", "Do the thing");
        r.onSubAgentTaskStart("code-reviewer", "check X");
        r.onToolInvocation("sub-agent:code-reviewer", "read_file", "m1", "{}", "ok");
        r.onSubAgentTaskComplete("code-reviewer", "check X", "done");

        var snap = r.snapshot();
        assertEquals(5, snap.size());
        assertEquals(1, snap.get(0).sequence());
        assertInstanceOf(DeepAgentFlowEvent.OrchestratorSystemReady.class, snap.get(0));
        assertInstanceOf(DeepAgentFlowEvent.OrchestratorUserMessage.class, snap.get(1));
        assertInstanceOf(DeepAgentFlowEvent.SubAgentTaskStart.class, snap.get(2));
        assertInstanceOf(DeepAgentFlowEvent.ToolInvocation.class, snap.get(3));
        assertInstanceOf(DeepAgentFlowEvent.SubAgentTaskComplete.class, snap.get(4));

        String timeline = r.formatTimeline();
        assertTrue(timeline.contains("EVENT 001"));
        assertTrue(timeline.contains("EVENT 002"));
        assertTrue(timeline.contains("orchestrator user message"));
        assertTrue(timeline.contains("session-a"));
        assertTrue(timeline.contains("Do the thing"));
        assertTrue(timeline.contains("orchestrator system ready"));
        assertTrue(timeline.contains("task START"));
        assertTrue(timeline.contains("read_file"));
        assertTrue(timeline.contains("task COMPLETE"));
    }

    @Test
    void clearResetsSequence() {
        DeepAgentFlowRecorder r = new DeepAgentFlowRecorder();
        r.onOrchestratorSystemReady("a");
        r.clear();
        r.onOrchestratorSystemReady("b");
        assertEquals(1, r.snapshot().get(0).sequence());
        assertEquals("b", ((DeepAgentFlowEvent.OrchestratorSystemReady) r.snapshot().get(0)).assembledSystemPrompt());
    }
}
