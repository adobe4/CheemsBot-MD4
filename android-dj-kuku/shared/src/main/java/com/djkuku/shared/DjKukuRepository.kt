package com.djkuku.shared

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class DjKukuRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    fun songs() = db.collection("songs").orderBy("createdAt", Query.Direction.DESCENDING)
    fun stories() = db.collection("stories").orderBy("createdAt", Query.Direction.DESCENDING)

    suspend fun uploadSong(title: String, artist: String, localMp3: Uri): String {
        val id = db.collection("songs").document().id
        val ref = storage.reference.child("songs/$id.mp3")
        ref.putFile(localMp3).await()
        val url = ref.downloadUrl.await().toString()
        db.collection("songs").document(id).set(Song(id, title, artist, url)).await()
        return id
    }

    suspend fun saveStory(story: Story) {
        val id = story.id.ifBlank { db.collection("stories").document().id }
        db.collection("stories").document(id).set(story.copy(id = id)).await()
    }

    suspend fun deleteSong(id: String) = db.collection("songs").document(id).delete().await()
    suspend fun deleteStory(id: String) = db.collection("stories").document(id).delete().await()
}
