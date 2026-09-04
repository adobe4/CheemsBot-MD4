package com.vinplay.m3u.task

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vinplay.m3u.R
import com.vinplay.m3u.data.repository.ChannelFilter
import com.vinplay.m3u.data.repository.ChannelRepository
import com.vinplay.m3u.data.repository.ImportManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Foreground service that runs long imports and link-testing so they survive the app being
 * backgrounded or closed, showing an ongoing notification with live progress. All actual work
 * is delegated to the shared singleton [ImportManager] / [ChannelRepository], so any UI that is
 * still on screen keeps updating from the same sources.
 */
@AndroidEntryPoint
class TaskService : Service() {

    @Inject lateinit var importManager: ImportManager
    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var healthChecker: com.vinplay.m3u.player.PlaybackHealthChecker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForegroundCompat(buildProgress("Working…", null, 0, 0, indeterminate = true))
        active.incrementAndGet()

        when (intent?.action) {
            ACTION_IMPORT -> {
                val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, 0L)
                val links = intent.getStringArrayListExtra(EXTRA_LINKS) ?: arrayListOf()
                runImport(playlistId, links, startId)
            }
            ACTION_TEST -> runTest(intent.getLongExtra(EXTRA_PLAYLIST_ID, 0L), filterFrom(intent), intent.getBooleanExtra(EXTRA_GLOBAL, false), startId)
            ACTION_PLAYBACK_TEST -> runPlaybackTest(intent.getLongExtra(EXTRA_PLAYLIST_ID, 0L), filterFrom(intent), intent.getBooleanExtra(EXTRA_GLOBAL, false), startId)
            else -> finish(startId)
        }
        return START_NOT_STICKY
    }

    private fun runImport(playlistId: Long, links: List<String>, startId: Int) {
        if (links.isEmpty()) { finish(startId); return }
        scope.launch {
            var total = 0
            var failures = 0
            var firstError: String? = null
            links.forEachIndexed { index, link ->
                val note = if (links.size > 1) "Source ${index + 1}/${links.size}" else null
                runCatching {
                    importManager.importFromUrl(playlistId, link) { imported ->
                        postProgress(buildProgress("Importing channels…", note, total + imported, 0, indeterminate = true))
                    }
                }.onSuccess { total += it }.onFailure {
                    failures++
                    if (firstError == null) firstError = it.message
                }
            }
            val summary = buildString {
                append("Imported ").append("%,d".format(total)).append(" channels")
                if (failures > 0) {
                    append(" · $failures link(s) failed")
                    // Surface why, otherwise a rejected/expired IPTV line just looks like "0 channels".
                    firstError?.let { append(": ").append(it.take(200)) }
                }
            }
            notifyDone(if (total == 0 && failures > 0) "Import failed" else "Import complete", summary)
            finish(startId)
        }
    }

    private fun filterFrom(intent: Intent) = ChannelFilter(
        query = intent.getStringExtra(EXTRA_QUERY) ?: "",
        group = intent.getStringExtra(EXTRA_GROUP),
        kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { enumValueOf<com.vinplay.m3u.data.model.ChannelKind>(it) }.getOrNull() }
    )

    private fun runTest(playlistId: Long, filter: ChannelFilter, global: Boolean, startId: Int) {
        scope.launch {
            val progressJob = launch {
                channelRepository.testProgress.collect { p ->
                    if (p.running && p.total > 0) {
                        postProgress(buildProgress("Testing links…", "${p.done}/${p.total}", p.done, p.total, indeterminate = false))
                    }
                }
            }
            if (global) channelRepository.testGlobalFiltered(filter) else channelRepository.testFiltered(playlistId, filter)
            progressJob.cancel()
            val done = channelRepository.testProgress.value
            notifyDone("Link test complete", "Tested ${done.total} channels")
            finish(startId)
        }
    }

    private fun runPlaybackTest(playlistId: Long, filter: ChannelFilter, global: Boolean, startId: Int) {
        scope.launch {
            val progressJob = launch {
                channelRepository.testProgress.collect { p ->
                    if (p.running && p.total > 0) {
                        postProgress(buildProgress("Deep testing (playback)…", "${p.done}/${p.total}", p.done, p.total, indeterminate = false))
                    }
                }
            }
            val check: suspend (com.vinplay.m3u.data.local.entity.ChannelEntity) -> com.vinplay.m3u.data.model.TestStatus =
                { ch -> healthChecker.check(ch.url, ch.userAgent, ch.referrer) }
            if (global) channelRepository.playbackTestGlobal(filter, check)
            else channelRepository.playbackTestFiltered(playlistId, filter, check)
            progressJob.cancel()
            val done = channelRepository.testProgress.value
            notifyDone("Deep test complete", "Checked ${done.total} channels")
            finish(startId)
        }
    }

    private fun finish(startId: Int) {
        // Only tear the service down once every queued command has completed.
        if (active.decrementAndGet() <= 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            stopSelf(startId)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---- Notifications ----

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Background tasks", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildProgress(title: String, text: String?, done: Int, total: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text ?: if (done > 0) "%,d done".format(done) else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), done, indeterminate)
            .build()

    private fun postProgress(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun notifyDone(title: String, text: String) {
        val done = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(DONE_NOTIFICATION_ID + active.get(), done)
    }

    companion object {
        const val ACTION_IMPORT = "com.vinplay.m3u.action.IMPORT"
        const val ACTION_TEST = "com.vinplay.m3u.action.TEST"
        const val ACTION_PLAYBACK_TEST = "com.vinplay.m3u.action.PLAYBACK_TEST"
        private const val EXTRA_PLAYLIST_ID = "playlistId"
        private const val EXTRA_LINKS = "links"
        private const val EXTRA_QUERY = "query"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_GLOBAL = "global"

        private const val CHANNEL_ID = "vinplay_tasks"
        private const val NOTIFICATION_ID = 1001
        private const val DONE_NOTIFICATION_ID = 2000

        fun startImport(context: Context, playlistId: Long, links: List<String>) {
            val intent = Intent(context, TaskService::class.java).apply {
                action = ACTION_IMPORT
                putExtra(EXTRA_PLAYLIST_ID, playlistId)
                putStringArrayListExtra(EXTRA_LINKS, ArrayList(links))
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun startTest(context: Context, playlistId: Long, filter: ChannelFilter) =
            start(context, ACTION_TEST, playlistId, filter, global = false)

        fun startTestGlobal(context: Context, filter: ChannelFilter) =
            start(context, ACTION_TEST, 0L, filter, global = true)

        fun startPlaybackTest(context: Context, playlistId: Long, filter: ChannelFilter) =
            start(context, ACTION_PLAYBACK_TEST, playlistId, filter, global = false)

        fun startPlaybackTestGlobal(context: Context, filter: ChannelFilter) =
            start(context, ACTION_PLAYBACK_TEST, 0L, filter, global = true)

        private fun start(context: Context, action: String, playlistId: Long, filter: ChannelFilter, global: Boolean) {
            val intent = Intent(context, TaskService::class.java).apply {
                this.action = action
                putExtra(EXTRA_PLAYLIST_ID, playlistId)
                putExtra(EXTRA_QUERY, filter.query)
                putExtra(EXTRA_GROUP, filter.group)
                putExtra(EXTRA_KIND, filter.kindName)
                putExtra(EXTRA_GLOBAL, global)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
