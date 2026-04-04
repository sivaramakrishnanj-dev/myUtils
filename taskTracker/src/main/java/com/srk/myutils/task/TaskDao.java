package com.srk.myutils.task;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TaskDao {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final DbHelper db;

    public TaskDao(DbHelper db) { this.db = db; }

    public Task add(String title, String dueAt, List<String> tags, String priority, String notes) throws SQLException {
        String now = LocalDateTime.now().format(FMT);
        String prio = priority != null ? priority : "MEDIUM";
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO tasks(title,status,priority,due_at,notes,created_at,updated_at) VALUES(?,'TODO',?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, title);
                ps.setString(2, prio);
                ps.setString(3, dueAt);
                ps.setString(4, notes);
                ps.setString(5, now);
                ps.setString(6, now);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                int id = keys.getInt(1);
                insertTags(conn, id, tags);
                conn.commit();
                return new Task(id, title, "TODO", prio, dueAt, notes, now, now, tags);
            } catch (SQLException e) { conn.rollback(); throw e; }
        }
    }

    public Task get(int id) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM tasks WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            return mapTask(conn, rs);
        }
    }

    public List<Task> list(String status, String tag, String dueBefore, String dueAfter, String priority) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT t.* FROM tasks t");
        List<Object> params = new ArrayList<>();
        if (tag != null) sql.append(" JOIN task_tags tt ON t.id=tt.task_id");
        List<String> where = new ArrayList<>();
        if (status != null)    { where.add("t.status=?");       params.add(status); }
        if (tag != null)       { where.add("tt.tag=?");         params.add(tag); }
        if (dueBefore != null) { where.add("t.due_at<=?");      params.add(dueBefore); }
        if (dueAfter != null)  { where.add("t.due_at>=?");      params.add(dueAfter); }
        if (priority != null)  { where.add("t.priority=?");     params.add(priority); }
        if (!where.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", where));
        sql.append(" ORDER BY t.due_at ASC NULLS LAST, t.id ASC");

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setString(i + 1, params.get(i).toString());
            ResultSet rs = ps.executeQuery();
            List<Task> tasks = new ArrayList<>();
            while (rs.next()) tasks.add(mapTask(conn, rs));
            return tasks;
        }
    }

    public Task update(int id, String title, String status, String dueAt, List<String> tags, String priority, String notes) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String now = LocalDateTime.now().format(FMT);
                List<String> sets = new ArrayList<>();
                List<Object> params = new ArrayList<>();
                if (title != null)    { sets.add("title=?");    params.add(title); }
                if (status != null)   { sets.add("status=?");   params.add(status); }
                if (dueAt != null)    { sets.add("due_at=?");   params.add(dueAt); }
                if (priority != null) { sets.add("priority=?"); params.add(priority); }
                if (notes != null)    { sets.add("notes=?");    params.add(notes); }
                if (!sets.isEmpty()) {
                    sets.add("updated_at=?"); params.add(now);
                    params.add(id);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE tasks SET " + String.join(",", sets) + " WHERE id=?")) {
                        for (int i = 0; i < params.size(); i++) ps.setString(i + 1, params.get(i).toString());
                        if (ps.executeUpdate() == 0) { conn.rollback(); return null; }
                    }
                }
                if (tags != null) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM task_tags WHERE task_id=?")) {
                        ps.setInt(1, id); ps.executeUpdate();
                    }
                    insertTags(conn, id, tags);
                    if (sets.isEmpty()) {
                        try (PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET updated_at=? WHERE id=?")) {
                            ps.setString(1, now); ps.setInt(2, id); ps.executeUpdate();
                        }
                    }
                }
                conn.commit();
                return get(id);
            } catch (SQLException e) { conn.rollback(); throw e; }
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.createStatement().execute("PRAGMA foreign_keys = ON");
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id=?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    private void insertTags(Connection conn, int taskId, List<String> tags) throws SQLException {
        if (tags == null || tags.isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO task_tags(task_id,tag) VALUES(?,?)")) {
            for (String tag : tags) { ps.setInt(1, taskId); ps.setString(2, tag.trim()); ps.addBatch(); }
            ps.executeBatch();
        }
    }

    private Task mapTask(Connection conn, ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        List<String> tags = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT tag FROM task_tags WHERE task_id=?")) {
            ps.setInt(1, id);
            ResultSet trs = ps.executeQuery();
            while (trs.next()) tags.add(trs.getString("tag"));
        }
        return new Task(id, rs.getString("title"), rs.getString("status"), rs.getString("priority"),
                rs.getString("due_at"), rs.getString("notes"), rs.getString("created_at"), rs.getString("updated_at"), tags);
    }
}
