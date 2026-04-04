package com.srk.myutils.task;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DbHelper {

    private final String dbPath;

    public DbHelper(String dbPath) {
        this.dbPath = dbPath;
        new File(dbPath).getParentFile().mkdirs();
        initSchema();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    title      TEXT NOT NULL,
                    status     TEXT NOT NULL DEFAULT 'TODO',
                    priority   TEXT NOT NULL DEFAULT 'MEDIUM',
                    due_at     TEXT,
                    notes      TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )""");
            // migrate existing DBs
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN notes TEXT"); } catch (SQLException ignored) {}
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS task_tags (
                    task_id INTEGER NOT NULL,
                    tag     TEXT NOT NULL,
                    PRIMARY KEY (task_id, tag),
                    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
                )""");
            stmt.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init DB: " + e.getMessage(), e);
        }
    }
}
