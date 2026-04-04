package com.srk.myutils.task;

import java.util.List;

public record Task(int id, String title, String status, String priority, String dueAt, String notes, String createdAt, String updatedAt, List<String> tags) {

    public String toJson() {
        String tagsJson = tags.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
        return "{\"id\":%d,\"title\":\"%s\",\"status\":\"%s\",\"priority\":\"%s\",\"dueAt\":%s,\"notes\":%s,\"createdAt\":\"%s\",\"updatedAt\":\"%s\",\"tags\":%s}"
                .formatted(id, esc(title), status, priority,
                        dueAt == null ? "null" : "\"" + dueAt + "\"",
                        notes == null ? "null" : "\"" + esc(notes) + "\"",
                        createdAt, updatedAt, tagsJson);
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
