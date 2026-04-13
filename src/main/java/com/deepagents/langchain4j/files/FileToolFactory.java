package com.deepagents.langchain4j.files;

import com.deepagents.langchain4j.ResourceTexts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workspace file tools with descriptions ported from langgraph4j-deepagents / Python deepagents where available.
 */
public final class FileToolFactory {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String READ_WORKSPACE_NOTE =
            """

            ---
            Reminder: the only JSON parameter name for this tool is **path** (workspace-relative unless allowed above).""";

    private static final String EDIT_WORKSPACE_NOTE =
            """

            ---
            This Java harness: path is relative to the workspace root (or absolute within the sandbox). \
            The tool does not enforce "read_file before edit" in code—follow the policy above in spirit.""";

    private FileToolFactory() {}

    public static Map<ToolSpecification, ToolExecutor> build(WorkspaceFileOperations ops) {
        Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();

        String listDesc = ResourceTexts.load("list_dir_description.txt");
        map.put(
                ToolSpecification.builder()
                        .name("list_dir")
                        .description(listDesc)
                        .parameters(
                                JsonObjectSchema.builder()
                                        .addStringProperty(
                                                "path",
                                                "Directory path relative to workspace (e.g. . or subdir)")
                                        .required("path")
                                        .build())
                        .build(),
                (ToolExecutionRequest request, Object memoryId) -> {
                    try {
                        JsonNode n = JSON.readTree(request.arguments());
                        return ops.listDir(n.path("path").asText("."));
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                });

        String readDesc = ResourceTexts.load("read_file_description.txt") + READ_WORKSPACE_NOTE;
        map.put(
                ToolSpecification.builder()
                        .name("read_file")
                        .description(readDesc)
                        .parameters(
                                JsonObjectSchema.builder()
                                        .addStringProperty(
                                                "path",
                                                "Required. Workspace-relative file path (JSON key must be \"path\", not file_path).")
                                        .required("path")
                                        .build())
                        .build(),
                (ToolExecutionRequest request, Object memoryId) -> {
                    try {
                        JsonNode n = JSON.readTree(request.arguments());
                        String p = n.path("path").asText("");
                        if (p.isBlank() && n.has("file_path")) {
                            return "Error: this tool's JSON schema uses the key \"path\", not \"file_path\". "
                                    + "Retry with {\"path\": \"...\"}.";
                        }
                        return ops.readFile(p);
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                });

        String writeDesc = ResourceTexts.load("write_file_description.txt");
        map.put(
                ToolSpecification.builder()
                        .name("write_file")
                        .description(writeDesc)
                        .parameters(
                                JsonObjectSchema.builder()
                                        .addStringProperty("path", "File path relative to workspace")
                                        .addStringProperty("content", "Full file contents")
                                        .required("path", "content")
                                        .build())
                        .build(),
                (ToolExecutionRequest request, Object memoryId) -> {
                    try {
                        JsonNode n = JSON.readTree(request.arguments());
                        String path = n.path("path").asText("");
                        String content = n.path("content").asText("");
                        if (path.isBlank()) {
                            return "Error: path must not be empty";
                        }
                        return ops.writeFile(path, content);
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                });

        String editDesc = ResourceTexts.load("edit_file_description.txt") + EDIT_WORKSPACE_NOTE;
        map.put(
                ToolSpecification.builder()
                        .name("edit_file")
                        .description(editDesc)
                        .parameters(
                                JsonObjectSchema.builder()
                                        .addStringProperty("path", "File path relative to workspace")
                                        .addStringProperty(
                                                "old_string", "Exact substring to replace (must be unique unless replace_all)")
                                        .addStringProperty("new_string", "Replacement text")
                                        .addBooleanProperty(
                                                "replace_all",
                                                "If true, replace every occurrence of old_string")
                                        .required("path", "old_string", "new_string")
                                        .build())
                        .build(),
                (ToolExecutionRequest request, Object memoryId) -> {
                    try {
                        JsonNode n = JSON.readTree(request.arguments());
                        String path = n.path("path").asText("");
                        String oldS = n.path("old_string").asText("");
                        String newS = n.path("new_string").asText("");
                        boolean replaceAll = n.path("replace_all").asBoolean(false);
                        if (path.isBlank() || oldS.isEmpty()) {
                            return "Error: path and old_string are required";
                        }
                        return ops.editFile(path, oldS, newS, replaceAll);
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                });

        return map;
    }
}
