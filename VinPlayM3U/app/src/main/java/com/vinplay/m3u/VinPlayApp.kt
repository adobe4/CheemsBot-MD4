package com.vinplay.m3u

import android.app.Application
import com.vinplay.m3u.data.local.SearchIndexer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Application entry point. Hilt generates the dependency graph rooted here. */
@HiltAndroidApp
class VinPlayApp : Application() {

    @Inject lateinit var searchIndexer: SearchIndexer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Builds the full-text search index the first time a pre-existing library is opened.
        // Runs off the main thread; search falls back to the old scan until it completes.
        scope.launch { searchIndexer.ensureBuilt() }
    }
}
