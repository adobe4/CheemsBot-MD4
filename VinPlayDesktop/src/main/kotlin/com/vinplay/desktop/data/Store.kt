package com.vinplay.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

data class Playlist(val id: Long, val name: String, val channelCount: Long = 0)

data class Channel(
    val id: Long,
    val playlistId: Long,
    val name: String,
    val url: String,
    val groupTitle: String,
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val kind: ChannelKind,
    val userAgent: String?,
    val referrer: String?,
    val testStatus: TestStatus
)

/** Filter applied to the channel list. [playlistId] null means "every playlist". */
data class Filter(
    val query: String = "",
    val kind: ChannelKind? = null,
    val playlistId: Long? = null,
    val showDeleted: Boolean = false
)

/**
 * SQLite store built for very large libraries (millions of channels).
 *
 * The important part is the FTS5 index: searching with `name LIKE '%x%'` forces a full table scan
 * of every row, which is what makes a huge library unusable. `channels_fts` turns the same search
 * into an index lookup, kept in sync by triggers.
 *
 * Two connections in WAL mode let a long import/test write while the UI keeps reading.
 */
class Store(dbFile: File) {

    private val readConn: Connection = open(dbFile)
    private val writeConn: Connection = open(dbFile)
    private val readLock = Mutex()
    private val writeLock = Mutex()

    private fun open(file: File): Connection {
        file.parentFile?.mkdirs()
        return DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").apply {
            createStatement().use { st ->
                st.execute("PRAGMA journal_mode=WAL")
                st.execute("PRAGMA synchronous=NORMAL")
                st.execute("PRAGMA foreign_keys=ON")
                st.execute("PRAGMA temp_store=MEMORY")
                st.execute("PRAGMA cache_size=-64000") // ~64MB page cache
            }
        }
    }

    suspend fun migrate() = withContext(Dispatchers.IO) {
        writeLock.withLock {
            writeConn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS playlists(
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      name TEXT NOT NULL,
                      createdAt INTEGER NOT NULL,
                      updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS channels(
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      playlistId INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                      name TEXT NOT NULL,
                      url TEXT NOT NULL,
                      groupTitle TEXT NOT NULL DEFAULT '',
                      tvgId TEXT, tvgName TEXT, tvgLogo TEXT,
                      kind TEXT NOT NULL DEFAULT 'UNKNOWN',
                      userAgent TEXT, referrer TEXT,
                      orderIndex INTEGER NOT NULL DEFAULT 0,
                      testStatus TEXT NOT NULL DEFAULT 'UNTESTED',
                      testCode INTEGER, testCheckedAt INTEGER,
                      deletedAt INTEGER
                    )
                    """.trimIndent()
                )
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ch_playlist ON channels(playlistId, deletedAt)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ch_name ON channels(name COLLATE NOCASE)")
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ch_dupe ON channels(name, url)")

                st.executeUpdate(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS channels_fts USING fts5(
                      name, groupTitle,
                      content='channels', content_rowid='id', tokenize='unicode61'
                    )
                    """.trimIndent()
                )
                // Keep the index in sync. UPDATE only re-indexes when searchable text changes, so
                // link-testing (which rewrites testStatus constantly) costs nothing here.
                st.executeUpdate(
                    """
                    CREATE TRIGGER IF NOT EXISTS ch_fts_ai AFTER INSERT ON channels BEGIN
                      INSERT INTO channels_fts(rowid, name, groupTitle) VALUES (new.id, new.name, new.groupTitle);
                    END
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TRIGGER IF NOT EXISTS ch_fts_ad AFTER DELETE ON channels BEGIN
                      INSERT INTO channels_fts(channels_fts, rowid, name, groupTitle)
                      VALUES ('delete', old.id, old.name, old.groupTitle);
                    END
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TRIGGER IF NOT EXISTS ch_fts_au AFTER UPDATE OF name, groupTitle ON channels BEGIN
                      INSERT INTO channels_fts(channels_fts, rowid, name, groupTitle)
                      VALUES ('delete', old.id, old.name, old.groupTitle);
                      INSERT INTO channels_fts(rowid, name, groupTitle) VALUES (new.id, new.name, new.groupTitle);
                    END
                    """.trimIndent()
                )
            }
        }
    }

    // ---- Playlists ----

    suspend fun playlists(): List<Playlist> = read { conn ->
        conn.prepareStatement(
            """
            SELECT p.id, p.name, (
              SELECT COUNT(*) FROM channels c WHERE c.playlistId = p.id AND c.deletedAt IS NULL
            ) AS n
            FROM playlists p ORDER BY p.name COLLATE NOCASE
            """.trimIndent()
        ).use { ps ->
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Playlist(rs.getLong(1), rs.getString(2), rs.getLong(3))) }
            }
        }
    }

    suspend fun createPlaylist(name: String): Long = write { conn ->
        val now = System.currentTimeMillis()
        conn.prepareStatement("INSERT INTO playlists(name, createdAt, updatedAt) VALUES(?,?,?)").use { ps ->
            ps.setString(1, name.trim().ifBlank { "Untitled" })
            ps.setLong(2, now)
            ps.setLong(3, now)
            ps.executeUpdate()
        }
        conn.createStatement().use { st ->
            st.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    suspend fun renamePlaylist(id: Long, name: String) = write { conn ->
        conn.prepareStatement("UPDATE playlists SET name=?, updatedAt=? WHERE id=?").use { ps ->
            ps.setString(1, name.trim())
            ps.setLong(2, System.currentTimeMillis())
            ps.setLong(3, id)
            ps.executeUpdate()
        }
        Unit
    }

    suspend fun deletePlaylist(id: Long) = write { conn ->
        conn.prepareStatement("DELETE FROM playlists WHERE id=?").use { ps ->
            ps.setLong(1, id); ps.executeUpdate()
        }
        Unit
    }

    // ---- Channel reads ----

    /**
     * Builds the WHERE clause shared by paging and counting. When a search term is present the
     * query goes through the FTS index; otherwise it's a plain indexed scan of the channel table.
     */
    private fun buildQuery(filter: Filter, select: String, order: String?, page: String): Pair<String, List<Any?>> {
        val args = mutableListOf<Any?>()
        val sb = StringBuilder()
        val match = ftsMatch(filter.query)
        if (match != null) {
            sb.append("SELECT $select FROM channels_fts JOIN channels c ON c.id = channels_fts.rowid ")
            sb.append("WHERE channels_fts MATCH ? ")
            args += match
        } else {
            sb.append("SELECT $select FROM channels c WHERE 1=1 ")
        }
        sb.append(if (filter.showDeleted) "AND c.deletedAt IS NOT NULL " else "AND c.deletedAt IS NULL ")
        filter.playlistId?.let { sb.append("AND c.playlistId = ? "); args += it }
        filter.kind?.let { sb.append("AND c.kind = ? "); args += it.name }
        if (order != null) {
            // Relevance order for searches (cheap); alphabetical for plain browsing (indexed).
            sb.append(if (match != null) "ORDER BY rank " else "ORDER BY $order ")
        }
        sb.append(page)
        return sb.toString() to args
    }

    suspend fun page(filter: Filter, limit: Int, offset: Int): List<Channel> = read { conn ->
        val (sql, args) = buildQuery(
            filter,
            select = "c.id, c.playlistId, c.name, c.url, c.groupTitle, c.tvgId, c.tvgName, c.tvgLogo, c.kind, c.userAgent, c.referrer, c.testStatus",
            order = "c.name COLLATE NOCASE",
            page = "LIMIT ? OFFSET ?"
        )
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args + listOf(limit, offset))
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toChannel()) }
            }
        }
    }

    suspend fun count(filter: Filter): Long = read { conn ->
        val (sql, args) = buildQuery(filter, select = "COUNT(*)", order = null, page = "")
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    }

    suspend fun idsMatching(filter: Filter, cap: Int = 500_000): List<Long> = read { conn ->
        val (sql, args) = buildQuery(filter, select = "c.id", order = null, page = "LIMIT ?")
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args + listOf(cap))
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong(1)) } }
        }
    }

    // ---- Channel writes ----

    suspend fun insertBatch(playlistId: Long, parsed: List<ParsedChannel>, startOrder: Long) = write { conn ->
        conn.autoCommit = false
        try {
            conn.prepareStatement(
                """
                INSERT INTO channels(playlistId, name, url, groupTitle, tvgId, tvgName, tvgLogo, kind,
                                     userAgent, referrer, orderIndex)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent()
            ).use { ps ->
                var order = startOrder
                for (p in parsed) {
                    ps.setLong(1, playlistId)
                    ps.setString(2, p.name)
                    ps.setString(3, p.url)
                    ps.setString(4, p.groupTitle)
                    ps.setString(5, p.tvgId)
                    ps.setString(6, p.tvgName)
                    ps.setString(7, p.tvgLogo)
                    ps.setString(8, p.kind.name)
                    ps.setString(9, p.userAgent)
                    ps.setString(10, p.referrer)
                    ps.setLong(11, order++)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } catch (t: Throwable) {
            conn.rollback(); throw t
        } finally {
            conn.autoCommit = true
        }
    }

    suspend fun maxOrderIndex(playlistId: Long): Long = read { conn ->
        conn.prepareStatement("SELECT COALESCE(MAX(orderIndex), 0) FROM channels WHERE playlistId=?").use { ps ->
            ps.setLong(1, playlistId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    }

    suspend fun moveToPlaylist(ids: List<Long>, target: Long) = bulk(ids) { conn, chunk ->
        conn.prepareStatement("UPDATE channels SET playlistId=? WHERE id IN ${placeholders(chunk.size)}").use { ps ->
            ps.setLong(1, target)
            chunk.forEachIndexed { i, id -> ps.setLong(i + 2, id) }
            ps.executeUpdate()
        }
    }

    suspend fun softDelete(ids: List<Long>) = bulk(ids) { conn, chunk ->
        conn.prepareStatement("UPDATE channels SET deletedAt=? WHERE id IN ${placeholders(chunk.size)}").use { ps ->
            ps.setLong(1, System.currentTimeMillis())
            chunk.forEachIndexed { i, id -> ps.setLong(i + 2, id) }
            ps.executeUpdate()
        }
    }

    suspend fun restore(ids: List<Long>) = bulk(ids) { conn, chunk ->
        conn.prepareStatement("UPDATE channels SET deletedAt=NULL WHERE id IN ${placeholders(chunk.size)}").use { ps ->
            chunk.forEachIndexed { i, id -> ps.setLong(i + 1, id) }
            ps.executeUpdate()
        }
    }

    suspend fun purge(ids: List<Long>) = bulk(ids) { conn, chunk ->
        conn.prepareStatement("DELETE FROM channels WHERE id IN ${placeholders(chunk.size)}").use { ps ->
            chunk.forEachIndexed { i, id -> ps.setLong(i + 1, id) }
            ps.executeUpdate()
        }
    }

    suspend fun updateChannel(id: Long, name: String, url: String, group: String, logo: String?) = write { conn ->
        conn.prepareStatement("UPDATE channels SET name=?, url=?, groupTitle=?, tvgLogo=? WHERE id=?").use { ps ->
            ps.setString(1, name); ps.setString(2, url); ps.setString(3, group)
            ps.setString(4, logo); ps.setLong(5, id)
            ps.executeUpdate()
        }
        Unit
    }

    suspend fun setTestResult(id: Long, status: TestStatus, code: Int?) = write { conn ->
        conn.prepareStatement("UPDATE channels SET testStatus=?, testCode=?, testCheckedAt=? WHERE id=?").use { ps ->
            ps.setString(1, status.name)
            if (code == null) ps.setNull(2, java.sql.Types.INTEGER) else ps.setInt(2, code)
            ps.setLong(3, System.currentTimeMillis())
            ps.setLong(4, id)
            ps.executeUpdate()
        }
        Unit
    }

    /** Soft-deletes every duplicate (same name + url), keeping the lowest id of each group. */
    suspend fun removeDuplicates(playlistId: Long?): Int = write { conn ->
        val scope = if (playlistId != null) "AND playlistId = $playlistId" else ""
        conn.createStatement().use { st ->
            st.executeUpdate(
                """
                UPDATE channels SET deletedAt = ${System.currentTimeMillis()}
                WHERE deletedAt IS NULL $scope AND id NOT IN (
                  SELECT MIN(id) FROM channels WHERE deletedAt IS NULL $scope GROUP BY name, url
                )
                """.trimIndent()
            )
        }
    }

    suspend fun channelsForExport(filter: Filter, block: (Channel) -> Unit) = read { conn ->
        val (sql, args) = buildQuery(
            filter,
            select = "c.id, c.playlistId, c.name, c.url, c.groupTitle, c.tvgId, c.tvgName, c.tvgLogo, c.kind, c.userAgent, c.referrer, c.testStatus",
            order = "c.name COLLATE NOCASE",
            page = ""
        )
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeQuery().use { rs -> while (rs.next()) block(rs.toChannel()) }
        }
    }

    // ---- helpers ----

    private suspend fun <T> read(body: (Connection) -> T): T =
        withContext(Dispatchers.IO) { readLock.withLock { body(readConn) } }

    private suspend fun <T> write(body: (Connection) -> T): T =
        withContext(Dispatchers.IO) { writeLock.withLock { body(writeConn) } }

    /** Applies an id-list statement in chunks so we never exceed SQLite's parameter limit. */
    private suspend fun bulk(ids: List<Long>, body: (Connection, List<Long>) -> Unit) {
        if (ids.isEmpty()) return
        write { conn ->
            conn.autoCommit = false
            try {
                ids.chunked(500).forEach { body(conn, it) }
                conn.commit()
            } catch (t: Throwable) {
                conn.rollback(); throw t
            } finally {
                conn.autoCommit = true
            }
        }
    }

    private fun placeholders(n: Int) = (1..n).joinToString(",", "(", ")") { "?" }

    private fun bind(ps: java.sql.PreparedStatement, args: List<Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                is Long -> ps.setLong(i + 1, a)
                is Int -> ps.setInt(i + 1, a)
                is String -> ps.setString(i + 1, a)
                null -> ps.setNull(i + 1, java.sql.Types.VARCHAR)
                else -> ps.setString(i + 1, a.toString())
            }
        }
    }

    private fun ResultSet.toChannel() = Channel(
        id = getLong(1),
        playlistId = getLong(2),
        name = getString(3) ?: "",
        url = getString(4) ?: "",
        groupTitle = getString(5) ?: "",
        tvgId = getString(6),
        tvgName = getString(7),
        tvgLogo = getString(8),
        kind = runCatching { ChannelKind.valueOf(getString(9)) }.getOrDefault(ChannelKind.UNKNOWN),
        userAgent = getString(10),
        referrer = getString(11),
        testStatus = runCatching { TestStatus.valueOf(getString(12)) }.getOrDefault(TestStatus.UNTESTED)
    )

    companion object {
        /**
         * Turns free text into an FTS5 MATCH expression: each word becomes a quoted prefix term,
         * so "sky sp" matches "Sky Sports". Quoting makes punctuation safe to type.
         */
        fun ftsMatch(query: String): String? {
            val terms = query.trim().split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .map { it.replace("\"", "\"\"") }
            if (terms.isEmpty()) return null
            return terms.joinToString(" ") { "\"$it\"*" }
        }
    }
}
