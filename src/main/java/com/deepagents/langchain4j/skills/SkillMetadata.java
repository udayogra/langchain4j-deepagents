package com.deepagents.langchain4j.skills;

/**
 * One discovered skill: short catalog fields plus the workspace-relative path the model should pass to {@code read_file}.
 *
 * @param name catalog name (from frontmatter or directory name)
 * @param description one-line summary for the system prompt
 * @param readRelativePath path relative to workspace root, POSIX-style (e.g. {@code skills/research/SKILL.md})
 */
public record SkillMetadata(String name, String description, String readRelativePath) {}
