package com.deepagents.langchain4j.flow;

import com.deepagents.langchain4j.DeepAgent;
import com.deepagents.langchain4j.config.DeepAgentConfig;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Built-in {@link DeepAgentFlowListener} that records a chronological list of {@link DeepAgentFlowEvent}s. Prefer
 * {@link DeepAgentConfig.Builder#recordFlowTraceToStderr(boolean)}, then
 * {@link #printTimelineToStderr()} after {@link DeepAgent.Orchestrator#chat(Object, String)}. You can also pass this
 * instance to {@link DeepAgentConfig.Builder#flowListener(DeepAgentFlowListener)} or
 * {@link DeepAgent.Builder#flowListener(DeepAgentFlowListener)}. Thread-safe for concurrent tool callbacks.
 */
public final class DeepAgentFlowRecorder implements DeepAgentFlowListener {

    private static final int TIMELINE_SYSTEM_PREVIEW_CHARS = 1200;

    private final AtomicLong sequence = new AtomicLong();
    private final List<DeepAgentFlowEvent> events = new CopyOnWriteArrayList<>();

    private long nextSequence() {
        return sequence.incrementAndGet();
    }

    private static String memoryDisplay(Object memoryId) {
        return memoryId == null ? "" : String.valueOf(memoryId);
    }

    /** Immutable copy of events recorded so far, in sequence order. */
    public List<DeepAgentFlowEvent> snapshot() {
        return List.copyOf(events);
    }

    /** Clears the timeline (e.g. before a new session); sequence restarts from 1 on the next event. */
    public void clear() {
        events.clear();
        sequence.set(0);
    }

    /** Prints {@link #formatTimeline()} to {@link System#err} with a banner (for demos and local debugging). */
    public void printTimelineToStderr() {
        System.err.println();
        System.err.println("========== Deep agent flow trace ==========");
        System.err.print(formatTimeline());
        System.err.println("==========================================");
    }

    @Override
    public void onOrchestratorSystemReady(String assembledSystemPrompt) {
        Objects.requireNonNull(assembledSystemPrompt, "assembledSystemPrompt");
        events.add(
                new DeepAgentFlowEvent.OrchestratorSystemReady(
                        nextSequence(), Instant.now(), assembledSystemPrompt));
    }

    @Override
    public void onOrchestratorUserMessage(Object memoryId, String userMessageTruncated) {
        events.add(
                new DeepAgentFlowEvent.OrchestratorUserMessage(
                        nextSequence(),
                        Instant.now(),
                        memoryDisplay(memoryId),
                        userMessageTruncated == null ? "" : userMessageTruncated));
    }

    @Override
    public void onToolInvocation(
            String context,
            String toolName,
            Object memoryId,
            String argumentsJsonTruncated,
            String resultTruncated) {
        events.add(
                new DeepAgentFlowEvent.ToolInvocation(
                        nextSequence(),
                        Instant.now(),
                        context == null ? "" : context,
                        toolName == null ? "" : toolName,
                        memoryDisplay(memoryId),
                        argumentsJsonTruncated == null ? "" : argumentsJsonTruncated,
                        resultTruncated == null ? "" : resultTruncated));
    }

    @Override
    public void onSubAgentTaskStart(String subAgentType, String taskDescriptionTruncated) {
        events.add(
                new DeepAgentFlowEvent.SubAgentTaskStart(
                        nextSequence(),
                        Instant.now(),
                        subAgentType == null ? "" : subAgentType,
                        taskDescriptionTruncated == null ? "" : taskDescriptionTruncated));
    }

    @Override
    public void onSubAgentTaskComplete(
            String subAgentType, String taskDescriptionTruncated, String resultTruncated) {
        events.add(
                new DeepAgentFlowEvent.SubAgentTaskComplete(
                        nextSequence(),
                        Instant.now(),
                        subAgentType == null ? "" : subAgentType,
                        taskDescriptionTruncated == null ? "" : taskDescriptionTruncated,
                        resultTruncated == null ? "" : resultTruncated));
    }

    /**
     * Human-readable multi-line summary for logging or stderr; lines are prefixed {@code EVENT 001}, {@code EVENT 002}, …
     */
    public String formatTimeline() {
        StringBuilder sb = new StringBuilder(events.size() * 120);
        for (DeepAgentFlowEvent e : events) {
            if (e instanceof DeepAgentFlowEvent.OrchestratorSystemReady ev) {
                String p = ev.assembledSystemPrompt();
                sb.append(String.format(
                        "EVENT %03d [%s] orchestrator system ready (%d chars)%n",
                        ev.sequence(), ev.timestamp(), p.length()));
                String pv = preview(p, TIMELINE_SYSTEM_PREVIEW_CHARS);
                if (!pv.equals(p)) {
                    sb.append("    … preview: ")
                            .append(pv.replace("\n", "\n    "))
                            .append(String.format("%n    … (truncated preview, total %d chars)%n", p.length()));
                } else {
                    sb.append("    ").append(p.replace("\n", "\n    ")).append('\n');
                }
            } else if (e instanceof DeepAgentFlowEvent.OrchestratorUserMessage ev) {
                sb.append(String.format(
                        "EVENT %03d [%s] orchestrator user message memoryId=%s%n    text=%s%n",
                        ev.sequence(),
                        ev.timestamp(),
                        ev.memoryIdDisplay(),
                        ev.userMessageTruncated()));
            } else if (e instanceof DeepAgentFlowEvent.ToolInvocation ev) {
                sb.append(String.format(
                        "EVENT %03d [%s] tool context=%s name=%s memoryId=%s%n    args=%s%n    result=%s%n",
                        ev.sequence(),
                        ev.timestamp(),
                        ev.context(),
                        ev.toolName(),
                        ev.memoryIdDisplay(),
                        ev.argumentsTruncated(),
                        ev.resultTruncated()));
            } else if (e instanceof DeepAgentFlowEvent.SubAgentTaskStart ev) {
                sb.append(String.format(
                        "EVENT %03d [%s] task START subAgentType=%s%n    description=%s%n",
                        ev.sequence(), ev.timestamp(), ev.subAgentType(), ev.taskDescriptionTruncated()));
            } else if (e instanceof DeepAgentFlowEvent.SubAgentTaskComplete ev) {
                sb.append(String.format(
                        "EVENT %03d [%s] task COMPLETE subAgentType=%s%n    description=%s%n    result=%s%n",
                        ev.sequence(),
                        ev.timestamp(),
                        ev.subAgentType(),
                        ev.taskDescriptionTruncated(),
                        ev.resultTruncated()));
            }
        }
        return sb.toString();
    }

    private static String preview(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "...";
    }
}
