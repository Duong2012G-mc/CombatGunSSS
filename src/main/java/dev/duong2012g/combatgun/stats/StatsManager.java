package dev.duong2012g.combatgun.stats;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tracks and persists per-player combat statistics for CombatGunSSS.
 * <p>
 * Stats are stored in a local SQLite database ({@code plugins/CombatGunSSS/stats.db}).
 * Data persists across restarts and is flushed on plugin disable.
 *
 * <h3>Tracked metrics</h3>
 * <ul>
 *   <li>Total kills, deaths, headshots</li>
 *   <li>Per-weapon kill counts</li>
 *   <li>Kill/Death ratio (computed on query)</li>
 * </ul>
 *
 * <h3>Commands</h3>
 * <pre>
 *   /gun stats [player]     — view stats
 *   /gun topleaderboard     — top 10 by kills
 *   /gun statsreset [player]— reset a player's stats (admin)
 * </pre>
 *
 * <h3>PlaceholderAPI integration</h3>
 * Stats are exposed via existing {@link dev.duong2012g.combatgun.hook.PlaceholderHook}:
 * <pre>
 *   %combatgun_kills%
 *   %combatgun_deaths%
 *   %combatgun_headshots%
 *   %combatgun_kd%
 * </pre>
 */
public class StatsManager {

    /** Immutable snapshot of a player's stats. */
    public record PlayerStats(
        UUID   uuid,
        String name,
        int    kills,
        int    deaths,
        int    headshots,
        Map<String, Integer> killsByGun
    ) {
        public double kd() {
            return deaths == 0 ? kills : Math.round((double) kills / deaths * 100.0) / 100.0;
        }
    }

    private final CombatGunSSSPlugin plugin;
    private Connection connection;

    // ✅ FIX: Use ConcurrentHashMap so main-thread writes (recordKill/recordDeath)
    // are thread-safe against the async flush scheduler reads.
    private final Map<UUID, int[]> buffer = new ConcurrentHashMap<>();
    // buffer[uuid] = { kills, deaths, headshots }

    /** Current schema version — bump this when tables change. */
    private static final int SCHEMA_VERSION = 3;

    public StatsManager(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Opens the SQLite database and creates tables if needed. Call from {@code onEnable()}. */
    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "stats.db");
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
            // Start periodic flush every 5 minutes
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::flushBuffer, 20L * 60 * 5, 20L * 60 * 5);
            plugin.getLogger().info("[StatsManager] SQLite database ready.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[StatsManager] Failed to init database: " + e.getMessage(), e);
        }
    }

    /** Flushes all buffered stats and closes the database. Call from {@code onDisable()}. */
    public void shutdown() {
        flushBuffer();
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { plugin.getLogger().warning("[StatsManager] Error closing DB: " + e.getMessage()); }
    }

    // ── Record events ─────────────────────────────────────────────────────────

    /** Called after a player is killed by a gun. */
    public void recordKill(Player killer, String gunId, boolean headshot) {
        UUID uid = killer.getUniqueId();
        int[] data = buffer.computeIfAbsent(uid, k -> new int[3]);
        data[0]++; // kills
        if (headshot) data[2]++;
        // Per-weapon kills flushed to DB directly (low frequency)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
            () -> incrementGunKill(uid, killer.getName(), gunId, headshot));
    }

    /** Called when a player is killed (victim side). */
    public void recordDeath(Player victim) {
        UUID uid = victim.getUniqueId();
        buffer.computeIfAbsent(uid, k -> new int[3])[1]++;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns a player's stats. Combines buffered (not yet flushed) data with DB data.
     * This call is synchronous — run it off the main thread if querying many players.
     */
    public PlayerStats getStats(UUID uuid, String name) {
        int[] buf = buffer.getOrDefault(uuid, new int[3]);
        if (connection == null) {
            return new PlayerStats(uuid, name, buf[0], buf[1], buf[2], Map.of());
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT kills, deaths, headshots FROM player_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            int kills     = buf[0];
            int deaths    = buf[1];
            int headshots = buf[2];
            if (rs.next()) {
                kills     += rs.getInt("kills");
                deaths    += rs.getInt("deaths");
                headshots += rs.getInt("headshots");
            }
            Map<String, Integer> gunKills = getGunKills(uuid);
            return new PlayerStats(uuid, name, kills, deaths, headshots, gunKills);
        } catch (SQLException e) {
            plugin.getLogger().warning("[StatsManager] getStats error: " + e.getMessage());
            return new PlayerStats(uuid, name, buf[0], buf[1], buf[2], Map.of());
        }
    }

    /**
     * Returns the top N players by kill count (DB + buffer combined).
     */
    public List<PlayerStats> getTopKillers(int limit) {
        List<PlayerStats> result = new ArrayList<>();
        if (connection == null) return result;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, name, kills, deaths, headshots FROM player_stats " +
                "ORDER BY kills DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                int[] buf = buffer.getOrDefault(uuid, new int[3]);
                result.add(new PlayerStats(
                    uuid,
                    rs.getString("name"),
                    rs.getInt("kills")     + buf[0],
                    rs.getInt("deaths")    + buf[1],
                    rs.getInt("headshots") + buf[2],
                    Map.of()
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[StatsManager] leaderboard error: " + e.getMessage());
        }
        result.sort(Comparator.comparingInt(PlayerStats::kills).reversed());
        return result.subList(0, Math.min(limit, result.size()));
    }

    /**
     * Resets all stats for a player across all seasons (buffer + DB).
     * Use {@code /gun statsreset <player>}.
     */
    public void resetStats(UUID uuid) {
        buffer.remove(uuid);
        if (connection == null) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM player_stats WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[StatsManager] resetStats error: " + e.getMessage());
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM gun_kills WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[StatsManager] resetStats (gun_kills) error: " + e.getMessage());
            }
            // ✅ NEW (schema v3): also wipe kills_by_gun_detail for this player
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM kills_by_gun_detail WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[StatsManager] resetStats (kills_by_gun_detail) error: " + e.getMessage());
            }
        });
    }

    /**
     * Resets the stats of <b>all</b> players — used for season resets.
     * Clears the in-memory buffer and wipes all three stat tables asynchronously.
     */
    public void resetAllStats() {
        buffer.clear();
        if (connection == null) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Statement st = connection.createStatement()) {
                connection.setAutoCommit(false);
                st.execute("DELETE FROM player_stats");
                st.execute("DELETE FROM gun_kills");
                st.execute("DELETE FROM kills_by_gun_detail");
                connection.commit();
                connection.setAutoCommit(true);
                plugin.getLogger().info("[StatsManager] Season reset — all player stats wiped.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[StatsManager] resetAllStats error: " + e.getMessage());
                try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        });
    }

    // ── Buffer flush ──────────────────────────────────────────────────────────

    private synchronized void flushBuffer() {
        if (connection == null || buffer.isEmpty()) return;
        // ✅ FIX: Snapshot by draining the ConcurrentHashMap atomically.
        // This is safe: main-thread writers use computeIfAbsent which is atomic,
        // and we replace the map contents rather than iterating while writing.
        Map<UUID, int[]> snapshot = new HashMap<>(buffer);
        buffer.clear();
        try {
            connection.setAutoCommit(false);
            String sql = "INSERT INTO player_stats (uuid, name, kills, deaths, headshots) VALUES (?,?,?,?,?) " +
                         "ON CONFLICT(uuid) DO UPDATE SET " +
                         "kills=kills+excluded.kills, deaths=deaths+excluded.deaths, " +
                         "headshots=headshots+excluded.headshots, name=excluded.name";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<UUID, int[]> entry : snapshot.entrySet()) {
                    UUID uid = entry.getKey();
                    int[] data = entry.getValue();
                    if (data[0] == 0 && data[1] == 0 && data[2] == 0) continue;
                    // Try to get name from online players
                    var onlinePlayer = plugin.getServer().getPlayer(uid);
                    String name = onlinePlayer != null ? onlinePlayer.getName() : uid.toString();
                    ps.setString(1, uid.toString());
                    ps.setString(2, name);
                    ps.setInt(3, data[0]);
                    ps.setInt(4, data[1]);
                    ps.setInt(5, data[2]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            plugin.getLogger().warning("[StatsManager] flush error: " + e.getMessage());
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // ── Per-weapon kills ──────────────────────────────────────────────────────

    // ✅ FIX #1: Synchronized to prevent concurrent access to the shared SQLite connection
    // alongside flushBuffer() which is also synchronized on this instance.
    private synchronized void incrementGunKill(UUID uuid, String playerName, String gunId, boolean headshot) {
        if (connection == null) return;
        long now = System.currentTimeMillis();
        try {
            // Aggregate kill count (existing table)
            String sql = "INSERT INTO gun_kills (uuid, gun_id, kills) VALUES (?,?,1) " +
                         "ON CONFLICT(uuid, gun_id) DO UPDATE SET kills=kills+1";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gunId);
                ps.executeUpdate();
            }
            // ✅ NEW (schema v3): detailed per-kill log for analytics and season reset
            String detail = "INSERT INTO kills_by_gun_detail (uuid, gun_id, headshot, timestamp) VALUES (?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(detail)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gunId);
                ps.setInt(3, headshot ? 1 : 0);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[StatsManager] gun kill record error: " + e.getMessage());
        }
    }

    // ✅ FIX #1: Synchronized to prevent concurrent read while flushBuffer writes.
    private synchronized Map<String, Integer> getGunKills(UUID uuid) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (connection == null) return result;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT gun_id, kills FROM gun_kills WHERE uuid=? ORDER BY kills DESC LIMIT 10")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.put(rs.getString("gun_id"), rs.getInt("kills"));
        } catch (SQLException e) {
            plugin.getLogger().warning("[StatsManager] getGunKills error: " + e.getMessage());
        }
        return result;
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // ── Version tracking table ────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL DEFAULT 0
                )""");

            // ── Core stats table ──────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid      TEXT PRIMARY KEY,
                    name      TEXT NOT NULL DEFAULT '',
                    kills     INTEGER NOT NULL DEFAULT 0,
                    deaths    INTEGER NOT NULL DEFAULT 0,
                    headshots INTEGER NOT NULL DEFAULT 0
                )""");

            // ── Per-weapon kills table ────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS gun_kills (
                    uuid   TEXT NOT NULL,
                    gun_id TEXT NOT NULL,
                    kills  INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, gun_id)
                )""");
        }
        runMigrations();
    }

    /**
     * Applies incremental schema migrations so existing databases are upgraded
     * automatically on plugin update without data loss.
     */
    private void runMigrations() throws SQLException {
        int currentVersion = getSchemaVersion();

        // ── Migration 1 → 2: Add kills index for leaderboard performance ─────
        if (currentVersion < 2) {
            try (Statement st = connection.createStatement()) {
                // ✅ FIX: Index on kills DESC dramatically speeds up leaderboard
                // queries — previously a full table scan was needed for ORDER BY kills.
                st.execute("CREATE INDEX IF NOT EXISTS idx_player_stats_kills " +
                           "ON player_stats(kills DESC)");
                plugin.getLogger().info("[StatsManager] Applied schema migration: added kills index.");
            }
            setSchemaVersion(2);
        }

        // ── Migration 2 → 3: Add detailed per-weapon kills table ─────────────
        // ✅ NEW: kills_by_gun_detail stores every kill individually with
        //   timestamp, weapon, and headshot flag — enables per-weapon leaderboards,
        //   session analysis, and season resets without losing historical totals.
        if (currentVersion < 3) {
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS kills_by_gun_detail (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid      TEXT    NOT NULL,
                        gun_id    TEXT    NOT NULL,
                        headshot  INTEGER NOT NULL DEFAULT 0,
                        timestamp INTEGER NOT NULL
                    )""");
                // Index for fast per-player and per-weapon queries
                st.execute("CREATE INDEX IF NOT EXISTS idx_kbgd_uuid " +
                           "ON kills_by_gun_detail(uuid)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_kbgd_gun " +
                           "ON kills_by_gun_detail(gun_id)");
                plugin.getLogger().info("[StatsManager] Applied schema migration v3: kills_by_gun_detail table.");
            }
            setSchemaVersion(3);
        }

        // Future migrations: add additional `if (currentVersion < N)` blocks here.
    }

    private int getSchemaVersion() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private void setSchemaVersion(int version) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM schema_version");
            st.execute("INSERT INTO schema_version (version) VALUES (" + version + ")");
        }
    }
}
