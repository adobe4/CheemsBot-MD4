package com.vinplay.m3u.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vinplay.m3u.data.model.ChannelKind
import com.vinplay.m3u.data.model.TestStatus

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_channel_playlist_order", value = ["playlistId", "orderIndex"]),
        Index(name = "idx_channel_playlist_deleted", value = ["playlistId", "deletedAt"]),
        Index(value = ["groupTitle"])
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val url: String,
    val groupTitle: String = "",
    val tvgId: String? = null,
    val tvgLogo: String? = null,
    val tvgName: String? = null,
    val kind: ChannelKind = ChannelKind.UNKNOWN,
    val orderIndex: Long = 0,
    val testStatus: TestStatus = TestStatus.UNTESTED,
    val testStatusCode: Int? = null,
    val testCheckedAt: Long? = null,
    val deletedAt: Long? = null
)
