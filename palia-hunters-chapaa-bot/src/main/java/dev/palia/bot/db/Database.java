package dev.palia.bot.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private final String jdbcUrl;

    public Database(String path) {
        ensureParentDirectory(path);
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    private void ensureParentDirectory(String path) {
        try {
            Path dbPath = Path.of(path).toAbsolutePath();
            Path parent = dbPath.getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create database directory for " + path, e);
        }
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    public void initialize() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    guild_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    display_name TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    challenge_pending INTEGER NOT NULL DEFAULT 0,
                    proof_pending INTEGER NOT NULL DEFAULT 0,
                    thread_id INTEGER,
                    winner INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (guild_id, user_id)
                )
                """);
            addColumnIfMissing(c, "players", "proof_pending", "INTEGER NOT NULL DEFAULT 0");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS completed_spaces (
                    guild_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    space INTEGER NOT NULL,
                    completed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (guild_id, user_id, space),
                    FOREIGN KEY (guild_id, user_id) REFERENCES players(guild_id, user_id) ON DELETE CASCADE
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS proof_submissions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    guild_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    space INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    reviewer_user_id INTEGER,
                    reviewer_note TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    reviewed_at TEXT,
                    FOREIGN KEY (guild_id, user_id) REFERENCES players(guild_id, user_id) ON DELETE CASCADE
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS proofs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    submission_id INTEGER,
                    guild_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    space INTEGER NOT NULL,
                    file_name TEXT NOT NULL,
                    content_type TEXT,
                    url TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (submission_id) REFERENCES proof_submissions(id) ON DELETE CASCADE,
                    FOREIGN KEY (guild_id, user_id) REFERENCES players(guild_id, user_id) ON DELETE CASCADE
                )
                """);
            addColumnIfMissing(c, "proofs", "submission_id", "INTEGER");
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize SQLite database", e);
        }
    }

    private void addColumnIfMissing(Connection c, String table, String column, String definition) throws SQLException {
        boolean exists = false;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }
}
