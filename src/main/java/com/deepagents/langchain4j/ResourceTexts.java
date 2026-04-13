package com.deepagents.langchain4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads bundled prompt and tool description text from {@code /agent-prompts/} on the classpath (aligned with upstream
 * deepagents / langgraph4j wording; e.g. {@code base_agent_prompt.txt} from {@code langchain-ai/deepagents} {@code graph.py}).
 */
public final class ResourceTexts {

    private static final String RESOURCE_ROOT = "/agent-prompts/";

    private ResourceTexts() {}

    public static String load(String filename) {
        String path = RESOURCE_ROOT + filename;
        try (InputStream in = ResourceTexts.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
