package com.example.a22i1066_b_socially

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

class MakePostActivity : AppCompatActivity() {

    private lateinit var closebtn: TextView
    private lateinit var nextbtn: TextView
    private lateinit var mainSelectedPhoto: ImageView
    private lateinit var photoGrid: RecyclerView
    private lateinit var selectMultipleBtn: View

    private val galleryImages = mutableListOf<GalleryImage>()
    private val selectedImages = mutableListOf<String>()
    private lateinit var adapter: GalleryAdapter
    private var isMultiSelectMode = false

    private val TAG = "MakePostActivity"
    private val auth = FirebaseAuth.getInstance()
    private lateinit var sessionManager: SessionManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadGalleryImages()
        } else {
            Toast.makeText(this, "Permission denied. Cannot load photos.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.post)

        sessionManager = SessionManager(this)

        // Check session instead of Firebase Auth
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Not signed in. Please log in.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val uid = sessionManager.getUserId()
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Invalid session. Please log in again.", Toast.LENGTH_SHORT).show()
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        closebtn = findViewById(R.id.closebutton)
        nextbtn = findViewById(R.id.nextbtn)
        mainSelectedPhoto = findViewById(R.id.mainSelectedPhoto)
        photoGrid = findViewById(R.id.photoGridRecycler)
        selectMultipleBtn = findViewById(R.id.photoControls)

        setupRecyclerView()

        closebtn.setOnClickListener {
            finish()
        }

        nextbtn.setOnClickListener {
            if (selectedImages.isEmpty()) {
                Toast.makeText(this, "Please select at least one image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, AddPostDetailsActivity::class.java)
            intent.putStringArrayListExtra("selectedImages", ArrayList(selectedImages))
            startActivity(intent)
        }

        selectMultipleBtn.setOnClickListener {
            isMultiSelectMode = !isMultiSelectMode
            if (!isMultiSelectMode) {
                // Switching back to single select - keep only first selected
                val firstSelected = selectedImages.firstOrNull()
                selectedImages.clear()
                if (firstSelected != null) {
                    selectedImages.add(firstSelected)
                }
            }
            updateGallerySelections()
            Toast.makeText(
                this,
                if (isMultiSelectMode) "Multi-select enabled" else "Single select enabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        checkPermissionAndLoadImages()
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(galleryImages) { image, position ->
            toggleImageSelection(image, position)
        }
        photoGrid.layoutManager = GridLayoutManager(this, 3)
        photoGrid.adapter = adapter
    }

    private fun checkPermissionAndLoadImages() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                loadGalleryImages()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun loadGalleryImages() {
        galleryImages.clear()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA
        )

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val data = it.getString(dataColumn)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                galleryImages.add(GalleryImage(uri.toString(), false))
            }
        }

        adapter.notifyDataSetChanged()

        if (galleryImages.isNotEmpty()) {
            selectFirstImage()
        }
    }

    private fun selectFirstImage() {
        if (galleryImages.isNotEmpty()) {
            val firstImage = galleryImages[0]
            selectedImages.add(firstImage.uri)
            firstImage.isSelected = true
            Glide.with(this).load(firstImage.uri).centerCrop().into(mainSelectedPhoto)
            adapter.notifyItemChanged(0)
        }
    }

    private fun toggleImageSelection(image: GalleryImage, position: Int) {
        if (!isMultiSelectMode) {
            // Single select mode
            galleryImages.forEach { it.isSelected = false }
            selectedImages.clear()

            image.isSelected = true
            selectedImages.add(image.uri)
            Glide.with(this).load(image.uri).centerCrop().into(mainSelectedPhoto)
            adapter.notifyDataSetChanged()
        } else {
            // Multi select mode
            if (image.isSelected) {
                image.isSelected = false
                selectedImages.remove(image.uri)
            } else {
                if (selectedImages.size >= 10) {
                    Toast.makeText(this, "Maximum 10 images allowed", Toast.LENGTH_SHORT).show()
                    return
                }
                image.isSelected = true
                selectedImages.add(image.uri)
            }

            updateGallerySelections()

            // Update main photo to show first selected
            if (selectedImages.isNotEmpty()) {
                Glide.with(this).load(selectedImages[0]).centerCrop().into(mainSelectedPhoto)
            }
        }

        Log.d(TAG, "Selected images count: ${selectedImages.size}")
    }

    private fun updateGallerySelections() {
        galleryImages.forEachIndexed { index, image ->
            image.isSelected = selectedImages.contains(image.uri)
        }
        adapter.notifyDataSetChanged()

        if (selectedImages.isNotEmpty()) {
            Glide.with(this).load(selectedImages[0]).centerCrop().into(mainSelectedPhoto)
        }
    }
}
