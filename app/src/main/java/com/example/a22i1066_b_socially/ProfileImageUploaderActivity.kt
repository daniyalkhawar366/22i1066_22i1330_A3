package com.example.a22i1066_b_socially

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileImageUploaderActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var selectedUri: Uri? = null

    private lateinit var imgPreview: ImageView
    private lateinit var btnPick: Button
    private lateinit var btnUpload: Button
    private lateinit var btnCancel: Button
    private lateinit var btnBack: ImageButton

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            imgPreview.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_image_uploader)

        imgPreview = findViewById(R.id.imgPreview)
        btnPick = findViewById(R.id.btnPick)
        btnUpload = findViewById(R.id.btnUpload)
        btnCancel = findViewById(R.id.btnCancel)
        btnBack = findViewById(R.id.btnBack)

        btnPick.setOnClickListener { pickImageLauncher.launch("image/*") }

        btnUpload.setOnClickListener {
            val uid = auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED); finish()
                return@setOnClickListener
            }
            val uri = selectedUri
            if (uri == null) {
                Toast.makeText(this, "Please pick an image first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Upload to Firebase Storage
            val ref = storage.reference.child("profile_pics/$uid.jpg")
            val uploadTask = ref.putFile(uri)
            btnUpload.isEnabled = false
            uploadTask.addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Update Firestore profilePicUrl
                    db.collection("users").document(uid)
                        .update("profilePicUrl", downloadUri.toString())
                        .addOnSuccessListener {
                            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            btnUpload.isEnabled = true
                            Toast.makeText(this, "Failed to save profile URL: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }.addOnFailureListener { e ->
                    btnUpload.isEnabled = true
                    Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener { e ->
                btnUpload.isEnabled = true
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        val cancelAction = {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        btnCancel.setOnClickListener { cancelAction() }
        btnBack.setOnClickListener { cancelAction() }
    }
}
