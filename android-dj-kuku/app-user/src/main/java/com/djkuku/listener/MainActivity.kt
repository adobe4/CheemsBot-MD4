package com.djkuku.listener

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.djkuku.shared.DjKukuRepository

class MainActivity : AppCompatActivity() {
    private val repo = DjKukuRepository()
    private lateinit var list: LinearLayout
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 40, 28, 20); setBackgroundColor(0xFF121212.toInt()) }
        root.addView(TextView(this).apply { text = "DJ Kuku"; textSize = 30f; setTextColor(0xFFFFB300.toInt()); gravity = Gravity.CENTER; alpha = 0f; animate().alpha(1f).setDuration(700).start() })
        val scroll = ScrollView(this); list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; scroll.addView(list); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        bindSongs(); bindStories()
    }

    private fun bindSongs() = repo.songs().addSnapshotListener { snap, _ ->
        snap?.documents?.forEach { doc ->
            val title = doc.getString("title") ?: return@forEach
            val audioUrl = doc.getString("audioUrl") ?: return@forEach
            addCard("🎵 $title", "Sikiliza au pakua MP3", audioUrl)
        }
    }

    private fun bindStories() = repo.stories().addSnapshotListener { snap, _ ->
        snap?.documents?.forEach { doc ->
            val title = doc.getString("title") ?: return@forEach
            val body = doc.getString("body").orEmpty()
            val audioUrl = doc.getString("audioUrl").orEmpty()
            addCard("📖 $title", body.ifBlank { "Simulizi ya sauti" }, audioUrl)
        }
    }

    private fun addCard(title: String, subtitle: String, audioUrl: String) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20); alpha = 0f }
        card.addView(TextView(this).apply { text = title; textSize = 20f; setTextColor(0xFFFFFFFF.toInt()) })
        card.addView(TextView(this).apply { text = subtitle; setTextColor(0xFFBDBDBD.toInt()) })
        if (audioUrl.isNotBlank()) card.addView(Button(this).apply { text = "Play / Download"; setOnClickListener { play(audioUrl); download(audioUrl, title) } })
        list.addView(card); card.animate().alpha(1f).translationYBy(-8f).setDuration(350).start()
    }

    private fun play(url: String) { player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.play() }
    private fun download(url: String, title: String) = (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(DownloadManager.Request(Uri.parse(url)).setTitle(title).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED))
    override fun onDestroy() { player.release(); super.onDestroy() }
}
