package dev.palia.bot.db;

import dev.palia.bot.game.PlayerState;
import dev.palia.bot.game.Proof;
import dev.palia.bot.game.ProofSubmission;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerRepository {
    private final Database database;

    public PlayerRepository(Database database) {
        this.database = database;
    }

    public PlayerState join(long guildId, long userId, String displayName, int startSpace) {
        try (Connection c = database.connect()) {
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO players(guild_id,user_id,display_name,position,challenge_pending,proof_pending,winner)
                VALUES(?,?,?,?,0,0,0)
                ON CONFLICT(guild_id,user_id) DO UPDATE SET display_name=excluded.display_name
                """)) {
                ps.setLong(1, guildId);
                ps.setLong(2, userId);
                ps.setString(3, displayName);
                ps.setInt(4, startSpace);
                ps.executeUpdate();
            }
            return find(guildId, userId).orElseThrow();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to join game", e);
        }
    }

    public Optional<PlayerState> find(long guildId, long userId) {
        try (Connection c = database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM players WHERE guild_id=? AND user_id=?")) {
            ps.setLong(1, guildId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load player", e);
        }
    }

    public List<PlayerState> findAll(long guildId) {
        List<PlayerState> list = new ArrayList<>();
        try (Connection c = database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM players WHERE guild_id=? ORDER BY position DESC, display_name ASC")) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapPlayer(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load leaderboard", e);
        }
    }

    private PlayerState mapPlayer(ResultSet rs) throws SQLException {
        Long threadId = rs.getObject("thread_id") == null ? null : rs.getLong("thread_id");
        return new PlayerState(
                rs.getLong("guild_id"), rs.getLong("user_id"), rs.getString("display_name"),
                rs.getInt("position"), rs.getBoolean("challenge_pending"), rs.getBoolean("proof_pending"),
                threadId, rs.getBoolean("winner")
        );
    }

    public void setThread(long guildId, long userId, long threadId) {
        updateLong("UPDATE players SET thread_id=? WHERE guild_id=? AND user_id=?", threadId, guildId, userId);
    }

    public void updatePosition(long guildId, long userId, int position, boolean pending) {
        try (Connection c = database.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE players SET position=?, challenge_pending=?, proof_pending=0 WHERE guild_id=? AND user_id=?")) {
            ps.setInt(1, position);
            ps.setBoolean(2, pending);
            ps.setLong(3, guildId);
            ps.setLong(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update player position", e);
        }
    }

    public boolean isCompleted(long guildId, long userId, int space) {
        try (Connection c = database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM completed_spaces WHERE guild_id=? AND user_id=? AND space=?")) {
            ps.setLong(1, guildId); ps.setLong(2, userId); ps.setInt(3, space);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to check completed space", e);
        }
    }

    public long submitProof(long guildId, long userId, int space, List<Proof> proofs) {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                long submissionId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO proof_submissions(guild_id,user_id,space,status) VALUES(?,?,?,'PENDING')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, guildId); ps.setLong(2, userId); ps.setInt(3, space); ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No proof submission ID returned");
                        submissionId = keys.getLong(1);
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO proofs(submission_id,guild_id,user_id,space,file_name,content_type,url) VALUES(?,?,?,?,?,?,?)")) {
                    for (Proof proof : proofs) {
                        ps.setLong(1, submissionId); ps.setLong(2, guildId); ps.setLong(3, userId); ps.setInt(4, space);
                        ps.setString(5, proof.fileName()); ps.setString(6, proof.contentType()); ps.setString(7, proof.url());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE players SET proof_pending=1 WHERE guild_id=? AND user_id=?")) {
                    ps.setLong(1, guildId); ps.setLong(2, userId); ps.executeUpdate();
                }
                c.commit();
                return submissionId;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to submit proof", e);
        }
    }

    public Optional<ProofSubmission> findSubmission(long guildId, long submissionId) {
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM proof_submissions WHERE guild_id=? AND id=?")) {
            ps.setLong(1, guildId); ps.setLong(2, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Long reviewer = rs.getObject("reviewer_user_id") == null ? null : rs.getLong("reviewer_user_id");
                return Optional.of(new ProofSubmission(
                        rs.getLong("id"), rs.getLong("guild_id"), rs.getLong("user_id"), rs.getInt("space"),
                        rs.getString("status"), rs.getString("reviewer_note"), reviewer,
                        rs.getString("created_at"), rs.getString("reviewed_at")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load proof submission", e);
        }
    }

    public Optional<ProofSubmission> latestSubmission(long guildId, long userId) {
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM proof_submissions WHERE guild_id=? AND user_id=? ORDER BY id DESC LIMIT 1")) {
            ps.setLong(1, guildId); ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Long reviewer = rs.getObject("reviewer_user_id") == null ? null : rs.getLong("reviewer_user_id");
                return Optional.of(new ProofSubmission(
                        rs.getLong("id"), rs.getLong("guild_id"), rs.getLong("user_id"), rs.getInt("space"),
                        rs.getString("status"), rs.getString("reviewer_note"), reviewer,
                        rs.getString("created_at"), rs.getString("reviewed_at")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load proof status", e);
        }
    }

    public List<Proof> proofsForSubmission(long submissionId) {
        List<Proof> result = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT file_name,content_type,url FROM proofs WHERE submission_id=? ORDER BY id")) {
            ps.setLong(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new Proof(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load proof images", e);
        }
    }

    public void approveSubmission(long guildId, long submissionId, long reviewerUserId, String note, boolean winner) {
        reviewSubmission(guildId, submissionId, reviewerUserId, note, true, winner);
    }

    public void rejectSubmission(long guildId, long submissionId, long reviewerUserId, String note) {
        reviewSubmission(guildId, submissionId, reviewerUserId, note, false, false);
    }

    private void reviewSubmission(long guildId, long submissionId, long reviewerUserId, String note,
                                  boolean approved, boolean winner) {
        ProofSubmission submission = findSubmission(guildId, submissionId)
                .orElseThrow(() -> new IllegalStateException("Proof submission #" + submissionId + " was not found."));
        if (!"PENDING".equals(submission.status())) {
            throw new IllegalStateException("Proof submission #" + submissionId + " has already been reviewed.");
        }

        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                String status = approved ? "APPROVED" : "REJECTED";
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE proof_submissions SET status=?,reviewer_user_id=?,reviewer_note=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND guild_id=?")) {
                    ps.setString(1, status); ps.setLong(2, reviewerUserId); ps.setString(3, note);
                    ps.setLong(4, submissionId); ps.setLong(5, guildId); ps.executeUpdate();
                }

                if (approved) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT OR IGNORE INTO completed_spaces(guild_id,user_id,space) VALUES(?,?,?)")) {
                        ps.setLong(1, guildId); ps.setLong(2, submission.userId()); ps.setInt(3, submission.space()); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE players SET challenge_pending=0,proof_pending=0,winner=? WHERE guild_id=? AND user_id=?")) {
                        ps.setBoolean(1, winner); ps.setLong(2, guildId); ps.setLong(3, submission.userId()); ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE players SET proof_pending=0 WHERE guild_id=? AND user_id=?")) {
                        ps.setLong(1, guildId); ps.setLong(2, submission.userId()); ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to review proof submission", e);
        }
    }

    public List<Integer> completedSpaces(long guildId, long userId) {
        List<Integer> spaces = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT space FROM completed_spaces WHERE guild_id=? AND user_id=? ORDER BY space")) {
            ps.setLong(1, guildId); ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) spaces.add(rs.getInt(1)); }
            return spaces;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load completed spaces", e);
        }
    }

    public void resetGuild(long guildId) {
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM players WHERE guild_id=?")) {
            ps.setLong(1, guildId); ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to reset game", e);
        }
    }

    private void updateLong(String sql, long a, long b, long c) {
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, a); ps.setLong(2, b); ps.setLong(3, c); ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Database update failed", e);
        }
    }
}
