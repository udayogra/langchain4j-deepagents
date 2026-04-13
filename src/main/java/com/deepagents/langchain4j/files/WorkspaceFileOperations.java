package com.deepagents.langchain4j.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sandboxed workspace file operations (no {@code @Tool}; used by {@link FileToolFactory} tool executors).
 *
 * <p><strong>Read cache:</strong> successful {@link #readFile} results are cached by absolute path and last-modified time
 * so orchestrator + sub-agent (sharing this instance) avoid redundant disk reads until the file changes. {@link
 * #writeFile} and {@link #editFile} invalidate entries for the affected path.
 */
public final class WorkspaceFileOperations {

    private final Path root;

    private record CachedRead(long lastModifiedMillis, String content) {}

    private final ConcurrentHashMap<String, CachedRead> readCache = new ConcurrentHashMap<>();

    public WorkspaceFileOperations(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path resolveSafe(String relativeOrAbsolute) throws IOException {
        Path p = Path.of(relativeOrAbsolute);
        Path resolved = (p.isAbsolute() ? p : root.resolve(p)).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Path escapes workspace: " + relativeOrAbsolute);
        }
        return resolved;
    }

    public String listDir(String path) {
        try {
            Path dir = resolveSafe(path);
            if (!Files.isDirectory(dir)) {
                return "Not a directory: " + path;
            }
            try (Stream<Path> s = Files.list(dir)) {
                return s.map(p -> Files.isDirectory(p) ? p.getFileName() + "/" : p.getFileName().toString())
                        .sorted()
                        .collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    public String readFile(String path) {
        try {
            Path f = resolveSafe(path);
            if (!Files.isRegularFile(f)) {
                return "Not a file: " + path;
            }
            long mtime = Files.getLastModifiedTime(f).toMillis();
            String cacheKey = f.toString();
            CachedRead hit = readCache.get(cacheKey);
            if (hit != null && hit.lastModifiedMillis == mtime) {
                return hit.content;
            }
            byte[] bytes = Files.readAllBytes(f);
            if (bytes.length > 512_000) {
                return "File too large (>512KB); use a smaller path or split.";
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            readCache.put(cacheKey, new CachedRead(mtime, text));
            return text;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    public String writeFile(String path, String content) {
        try {
            Path f = resolveSafe(path);
            Path parent = f.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(f, content == null ? "" : content, StandardCharsets.UTF_8);
            readCache.remove(f.toString());
            return "Wrote " + f;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * String replace edit (deepagents-style). Does not enforce "read once in conversation" — callers should follow
     * {@link com.deepagents.langchain4j.Prompts#EDIT_FILE_DESCRIPTION} in the prompt.
     */
    public String editFile(String path, String oldString, String newString, boolean replaceAll) {
        try {
            Path f = resolveSafe(path);
            if (!Files.isRegularFile(f)) {
                return "Not a file: " + path;
            }
            String content = Files.readString(f, StandardCharsets.UTF_8);
            if (!content.contains(oldString)) {
                return "Error: old_string not found in file (must match exactly, including whitespace).";
            }
            String updated;
            if (replaceAll) {
                updated = content.replace(oldString, newString);
            } else {
                int idx = content.indexOf(oldString);
                int second = content.indexOf(oldString, idx + oldString.length());
                if (second >= 0) {
                    return "Error: old_string is not unique; provide more context or use replace_all=true.";
                }
                updated = content.substring(0, idx) + newString + content.substring(idx + oldString.length());
            }
            Files.writeString(f, updated, StandardCharsets.UTF_8);
            readCache.remove(f.toString());
            return "Updated " + f;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    /** For tests or callers that mutate files outside these APIs. */
    public void invalidateReadCacheForPath(String relativeOrAbsolute) {
        try {
            Path f = resolveSafe(relativeOrAbsolute);
            readCache.remove(f.toString());
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
