package com.deepagents.langchain4j.skills;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads optional YAML frontmatter from {@code SKILL.md} ({@code ---} … {@code ---}) without an extra YAML dependency.
 * Supports single-line {@code name:} and {@code description:} (Deep Agents skill spec).
 */
final class SkillFrontmatterParser {

    private static final int HEAD_BYTES = 64_000;
    private static final Pattern NAME_LINE = Pattern.compile("^name:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern DESC_LINE = Pattern.compile("^description:\\s*(.+)$", Pattern.MULTILINE);

    private SkillFrontmatterParser() {}

    static SkillMetadata parse(Path skillMdPath, String directoryName, String pathForRead) throws IOException {
        String head = readHeadUtf8(skillMdPath, HEAD_BYTES);
        String name = sanitizeName(directoryName);
        String description = "";

        String fm = extractFrontmatter(head);
        if (fm != null) {
            Matcher nm = NAME_LINE.matcher(fm);
            if (nm.find()) {
                String v = stripQuotes(nm.group(1).trim());
                if (!v.isBlank()) {
                    name = v;
                }
            }
            Matcher dm = DESC_LINE.matcher(fm);
            if (dm.find()) {
                description = stripQuotes(dm.group(1).trim());
            }
        }
        if (description.isBlank()) {
            description = "Full instructions in " + pathForRead;
        }
        return new SkillMetadata(name, description, pathForRead);
    }

    private static String readHeadUtf8(Path path, int maxBytes) throws IOException {
        byte[] all = Files.readAllBytes(path);
        int n = Math.min(all.length, maxBytes);
        return new String(all, 0, n, StandardCharsets.UTF_8);
    }

    /**
     * Returns text between first opening {@code ---} line and the next line that is exactly {@code ---}, or {@code null}.
     */
    static String extractFrontmatter(String content) {
        if (content == null || !content.startsWith("---")) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new StringReader(content))) {
            String opening = br.readLine();
            if (opening == null || !opening.strip().equals("---")) {
                return null;
            }
            StringBuilder fm = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.strip().equals("---")) {
                    return fm.toString().trim();
                }
                if (!fm.isEmpty()) {
                    fm.append('\n');
                }
                fm.append(line);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return null;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static String sanitizeName(String directoryName) {
        String s = directoryName == null ? "skill" : directoryName.trim();
        return s.isEmpty() ? "skill" : s;
    }
}
