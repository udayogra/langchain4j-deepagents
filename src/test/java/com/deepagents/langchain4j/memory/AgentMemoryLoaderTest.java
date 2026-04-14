package com.deepagents.langchain4j.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMemoryLoaderTest {

    @Test
    void buildPromptSection_emptySources_returnsEmpty() throws Exception {
        assertEquals("", AgentMemoryLoader.buildPromptSection(Path.of("/tmp/ws"), List.of()));
        assertEquals("", AgentMemoryLoader.buildPromptSection(Path.of("/tmp/ws"), null));
    }

    @Test
    void formatMemoryContents_missingFiles_yieldsPlaceholder(@TempDir Path ws) throws Exception {
        String s = AgentMemoryLoader.formatMemoryContents(ws, List.of(Path.of("nope.md")));
        assertEquals("(No memory loaded)", s);
    }

    @Test
    void formatMemoryContents_ordersAndJoinsSections(@TempDir Path ws) throws Exception {
        Files.writeString(ws.resolve("a.md"), "alpha", StandardCharsets.UTF_8);
        Files.createDirectories(ws.resolve("sub"));
        Files.writeString(ws.resolve("sub/b.md"), "beta", StandardCharsets.UTF_8);
        String formatted =
                AgentMemoryLoader.formatMemoryContents(
                        ws, List.of(Path.of("a.md"), Path.of("sub/b.md")));
        assertTrue(formatted.startsWith("a.md\nalpha"));
        assertTrue(formatted.contains("\n\nsub/b.md\nbeta"));
    }

    @Test
    void buildPromptSection_wrapsWithAgentMemoryTags(@TempDir Path ws) throws Exception {
        Files.writeString(ws.resolve("AGENTS.md"), "# Hi", StandardCharsets.UTF_8);
        String section = AgentMemoryLoader.buildPromptSection(ws, List.of(Path.of("AGENTS.md")));
        assertTrue(section.contains("<agent_memory>"));
        assertTrue(section.contains("</agent_memory>"));
        assertTrue(section.contains("<memory_guidelines>"));
        assertTrue(section.contains("AGENTS.md\n# Hi"));
    }

    @Test
    void resolveUnderWorkspace_rejectsEscape(@TempDir Path ws) throws Exception {
        Path root = ws.toAbsolutePath().normalize();
        Path outside = root.getParent().resolve("outside-agents-" + System.nanoTime() + ".md");
        Files.writeString(outside, "x", StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> AgentMemoryLoader.resolveUnderWorkspace(root, outside.toAbsolutePath().normalize()));
    }

    @Test
    void displayPathForModel_usesPosixSlashes(@TempDir Path ws) throws Exception {
        Path f = ws.resolve("d").resolve("f.md");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "x", StandardCharsets.UTF_8);
        String d = AgentMemoryLoader.displayPathForModel(ws.toAbsolutePath().normalize(), f.toAbsolutePath().normalize());
        assertEquals("d/f.md", d.replace('\\', '/'));
    }
}
