package com.vinplay.m3u.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills the `channels_fts` index the first time a library that predates it is opened.
 *
 * The migration creates the index empty so app start stays instant; rebuilding a multi-million-row
 * library takes a while, so it happens here in the background. Until it finishes, searches fall
 * back to the old (slow but correct) LIKE scan — [isReady] is what the repository checks.
 */
@Singleton
class SearchIndexer @Inject constructor(private val db: AppDatabase) {

    @Volatile
    var isReady: Boolean = false
        private set

    private val mutex = Mutex()

    suspend fun ensureBuilt() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isReady) return@withLock
            runCatching {
                val sdb = db.openHelper.writableDatabase
                // `_segdir` is the FTS shadow table holding index segments: empty means the index
                // has never been populated. Checking the fts table directly wouldn't work, because
                // an external-content table reads rows straight from `channels`.
                val indexed = sdb.query("SELECT EXISTS(SELECT 1 FROM channels_fts_segdir)").use { c ->
                    c.moveToFirst() && c.getInt(0) == 1
                }
                val hasChannels = sdb.query("SELECT EXISTS(SELECT 1 FROM channels)").use { c ->
                    c.moveToFirst() && c.getInt(0) == 1
                }
                if (!indexed && hasChannels) {
                    sdb.execSQL("INSERT INTO ${AppDatabase.FTS_TABLE}(${AppDatabase.FTS_TABLE}) VALUES('rebuild')")
                }
                isReady = true
            }.onFailure {
                // Index unavailable for some reason — searches keep working via the LIKE fallback.
                isReady = false
            }
            Unit
        }
    }
}
