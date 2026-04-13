package com.deepagents.langchain4j.logging;

import com.deepagents.langchain4j.flow.DeepAgentFlowListener;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wraps {@link ToolExecutor} instances so invocations can be logged and/or reported to a {@link DeepAgentFlowListener}.
 * Use a distinct {@code context} label for orchestrator vs sub-agents to trace nested {@code task} calls.
 */
public final class ToolInvocationLogger {

    private static final Logger log = LoggerFactory.getLogger(ToolInvocationLogger.class);

    /** Avoid huge log lines (e.g. long task descriptions). */
    private static final int MAX_ARGUMENTS_LOG_CHARS = 8000;

    private ToolInvocationLogger() {}

    /**
     * Same as {@link #wrapAll(Map, String, ToolInvocationLogMode, DeepAgentFlowListener)} with {@link ToolInvocationLogMode#INFO}
     * and no flow listener.
     */
    public static Map<ToolSpecification, ToolExecutor> wrapAll(
            Map<ToolSpecification, ToolExecutor> tools, String context) {
        return wrapAll(tools, context, ToolInvocationLogMode.INFO, null);
    }

    /**
     * Same as {@link #wrapAll(Map, String, ToolInvocationLogMode, DeepAgentFlowListener)} with no flow listener.
     */
    public static Map<ToolSpecification, ToolExecutor> wrapAll(
            Map<ToolSpecification, ToolExecutor> tools, String context, ToolInvocationLogMode mode) {
        return wrapAll(tools, context, mode, null);
    }

    /**
     * @param tools          tool map from factories (not null)
     * @param context        short label, e.g. {@code orchestrator} or {@code sub-agent:code-reviewer}
     * @param mode           {@link ToolInvocationLogMode#NONE} skips SLF4J lines unless {@code flowListener} is non-null (listener still wraps)
     * @param flowListener   optional; when non-null, invoked after each tool with {@link ToolInvocationLogger#truncateForLog}
     *     applied to args and result. With {@link ToolInvocationLogMode#NONE}, truncation runs only for this callback, not when
     *     {@code flowListener} is {@code null}.
     */
    public static Map<ToolSpecification, ToolExecutor> wrapAll(
            Map<ToolSpecification, ToolExecutor> tools,
            String context,
            ToolInvocationLogMode mode,
            DeepAgentFlowListener flowListener) {
        Objects.requireNonNull(mode, "mode");
        if (tools.isEmpty()) {
            return Map.copyOf(tools);
        }
        if (mode == ToolInvocationLogMode.NONE && flowListener == null) {
            return new LinkedHashMap<>(tools);
        }
        String ctx = context == null || context.isBlank() ? "agent" : context;
        boolean logInfo = mode == ToolInvocationLogMode.INFO;
        boolean logDebug = mode == ToolInvocationLogMode.DEBUG;
        Map<ToolSpecification, ToolExecutor> out = new LinkedHashMap<>();
        for (Map.Entry<ToolSpecification, ToolExecutor> e : tools.entrySet()) {
            ToolSpecification spec = e.getKey();
            ToolExecutor inner = e.getValue();
            String specName = spec.name();
            out.put(spec, (ToolExecutionRequest request, Object memoryId) -> {
                String toolName =
                        request != null && request.name() != null && !request.name().isBlank()
                                ? request.name()
                                : specName;
                String rawArgs = request != null ? request.arguments() : "";
                boolean needArgsForSlf4j = logInfo || logDebug;
                String argsForSlf4j = needArgsForSlf4j ? truncateForLog(rawArgs) : null;
                if (logInfo) {
                    log.info(
                            "[{}] tool={} memoryId={} arguments={}",
                            ctx,
                            toolName,
                            memoryId,
                            argsForSlf4j);
                } else if (logDebug) {
                    log.debug(
                            "[{}] tool={} memoryId={} arguments={}",
                            ctx,
                            toolName,
                            memoryId,
                            argsForSlf4j);
                }
                String result = inner.execute(request, memoryId);
                String resultStr = result == null ? "" : result;
                String resultForSlf4jDebug = logDebug ? truncateForLog(resultStr) : null;
                if (logDebug) {
                    log.debug("[{}] tool={} memoryId={} result={}", ctx, toolName, memoryId, resultForSlf4jDebug);
                }
                if (flowListener != null) {
                    String argsForListener = needArgsForSlf4j ? argsForSlf4j : truncateForLog(rawArgs);
                    String resultForListener = logDebug ? resultForSlf4jDebug : truncateForLog(resultStr);
                    flowListener.onToolInvocation(ctx, toolName, memoryId, argsForListener, resultForListener);
                }
                return result;
            });
        }
        return out;
    }

    public static String truncateForLog(String argumentsJson) {
        if (argumentsJson == null) {
            return "";
        }
        if (argumentsJson.length() <= MAX_ARGUMENTS_LOG_CHARS) {
            return argumentsJson;
        }
        return argumentsJson.substring(0, MAX_ARGUMENTS_LOG_CHARS)
                + "... (truncated, total "
                + argumentsJson.length()
                + " chars)";
    }
}
