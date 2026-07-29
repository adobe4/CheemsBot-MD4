package com.djkuku.admin

import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.djkuku.shared.DjKukuRepository
import com.djkuku.shared.Story
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {
    private var selectedMp3: Uri? = null
    private val pickMp3 = registerForActivityResult(ActivityResultContracts.GetContent()) { selectedMp3 = it }
    private val auth = FirebaseAuth.getInstance()
    private val repo = DjKukuRepository()
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showLogin() }

    private fun showLogin() {
        val email = EditText(this).apply { hint = "Admin email" }
        val pass = EditText(this).apply { hint = "Password" }
        val login = Button(this).apply { text = "Login" }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32); addView(email); addView(pass); addView(login) }
        setContentView(root)
        login.setOnClickListener { auth.signInWithEmailAndPassword(email.text.toString(), pass.text.toString()).addOnSuccessListener { showDashboard() }.addOnFailureListener { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() } }
    }

    private fun showDashboard() {
        val title = EditText(this).apply { hint = "Kichwa cha simulizi" }
        val body = EditText(this).apply { hint = "Andika simulizi"; minLines = 5 }
        val songTitle = EditText(this).apply { hint = "Jina la wimbo" }
        val pickSong = Button(this).apply { text = "Chagua MP3" }
        val uploadSong = Button(this).apply { text = "Post Wimbo MP3" }
        val save = Button(this).apply { text = "Post / Update Simulizi" }
        val deleteId = EditText(this).apply { hint = "ID ya kufuta" }
        val deleteStory = Button(this).apply { text = "Futa Simulizi" }
        val note = TextView(this).apply { text = "Admin anaweza kuongeza MP3, kuongeza/kuhariri simulizi kwa ID, na kufuta maudhui yaliyopo kwenye Firebase moja." }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32); addView(songTitle); addView(pickSong); addView(uploadSong); addView(title); addView(body); addView(save); addView(deleteId); addView(deleteStory); addView(note) }
        setContentView(root)
        pickSong.setOnClickListener { pickMp3.launch("audio/mpeg") }
        uploadSong.setOnClickListener { scope.launch { selectedMp3?.let { repo.uploadSong(songTitle.text.toString(), "DJ Kuku", it); Toast.makeText(this@AdminActivity, "Wimbo umepostiwa", Toast.LENGTH_SHORT).show() } } }
        save.setOnClickListener { scope.launch { repo.saveStory(Story(title = title.text.toString(), body = body.text.toString())); Toast.makeText(this@AdminActivity, "Imehifadhiwa", Toast.LENGTH_SHORT).show() } }
        deleteStory.setOnClickListener { scope.launch { repo.deleteStory(deleteId.text.toString()); Toast.makeText(this@AdminActivity, "Imefutwa", Toast.LENGTH_SHORT).show() } }
    }
}
