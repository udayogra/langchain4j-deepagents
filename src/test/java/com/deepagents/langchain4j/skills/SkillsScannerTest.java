package com.deepagents.langchain4j.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillsScannerTest {

    @Test
    void scan_findsSkillUnderRoot(@TempDir Path workspace) throws Exception {
        Path skills = workspace.resolve("skills");
        Path one = skills.resolve("alpha");
        Files.createDirectories(one);
        Files.writeString(
                one.resolve("SKILL.md"),
                """
                ---
                name: alpha
                description: A skill.
                ---

                # Alpha
                """);

        List<SkillMetadata> list = SkillsScanner.scan(workspace, List.of(skills));
        assertEquals(1, list.size());
        assertEquals("alpha", list.get(0).name());
        assertTrue(list.get(0).readRelativePath().replace('\\', '/').endsWith("skills/alpha/SKILL.md"));
    }

    @Test
    void laterRootOverridesSameName(@TempDir Path workspace) throws Exception {
        Path a = workspace.resolve("a");
        Path b = workspace.resolve("b");
        Path s1 = a.resolve("dup");
        Path s2 = b.resolve("dup");
        Files.createDirectories(s1);
        Files.createDirectories(s2);
        Files.writeString(
                s1.resolve("SKILL.md"),
                """
                ---
                name: dup
                description: first
                ---
                """);
        Files.writeString(
                s2.resolve("SKILL.md"),
                """
                ---
                name: dup
                description: second
                ---
                """);

        List<SkillMetadata> list = SkillsScanner.scan(workspace, List.of(a, b));
        assertEquals(1, list.size());
        assertEquals("second", list.get(0).description());
    }

    @Test
    void skillRootOutsideWorkspace_throws() {
        Path ws = Path.of("/tmp/ws-skills-test-unique");
        assertThrows(IllegalArgumentException.class, () -> SkillsScanner.scan(ws, List.of(Path.of("/etc"))));
    }
}
