package com.example.a22i1066_b_socially

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.a22i1066_b_socially.network.CreateHighlightRequest
import com.example.a22i1066_b_socially.network.RetrofitClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlinx.coroutines.launch

class AddHighlightActivity : AppCompatActivity() {

    private lateinit var titleInput: EditText
    private lateinit var dateText: TextView
    private lateinit var selectDateBtn: Button
    private lateinit var selectImagesBtn: Button
    private lateinit var uploadBtn: Button

    private val selectedImages = mutableListOf<Uri>()
    private var selectedDate: Date? = null

    private val client = OkHttpClient()

    private val CLOUDINARY_URL = "https://api.cloudinary.com/v1_1/dihbswob7/image/upload"
    private val UPLOAD_PRESET = "mobile_unsigned_preset"

    private val TAG = "AddHighlightActivity"

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            selectedImages.clear()
            selectedImages.addAll(uris)
            Toast.makeText(this, "${uris.size} images selected", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Selected ${uris.size} images")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_highlight)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        titleInput = findViewById(R.id.titleInput)
        dateText = findViewById(R.id.dateText)
        selectDateBtn = findViewById(R.id.selectDateBtn)
        selectImagesBtn = findViewById(R.id.selectImagesBtn)
        uploadBtn = findViewById(R.id.uploadBtn)
    }

    private fun setupListeners() {
        selectDateBtn.setOnClickListener { showDatePicker() }
        selectImagesBtn.setOnClickListener { imagePickerLauncher.launch("image/*") }
        uploadBtn.setOnClickListener { uploadHighlight() }
        findViewById<TextView>(R.id.cancelBtn).setOnClickListener { finish() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                selectedDate = calendar.time
                dateText.text = android.text.format.DateFormat.format("MMM dd, yyyy", selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun uploadHighlight() {
        val title = titleInput.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDate == null) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Please select at least one image", Toast.LENGTH_SHORT).show()
            return
        }

        uploadBtn.isEnabled = false
        uploadBtn.text = "Uploading..."
        Log.d(TAG, "Starting upload for ${selectedImages.size} images")

        uploadImagesToCloudinary { imageUrls ->
            if (imageUrls.isNotEmpty()) {
                saveHighlightToBackend(title, imageUrls)
            } else {
                Toast.makeText(this, "Failed to upload images", Toast.LENGTH_SHORT).show()
                uploadBtn.isEnabled = true
                uploadBtn.text = "Upload Highlight"
            }
        }
    }

    private fun uploadImagesToCloudinary(onComplete: (List<String>) -> Unit) {
        val uploadedUrls = mutableListOf<String>()
        var completedUploads = 0
        val totalImages = selectedImages.size

        selectedImages.forEachIndexed { index, uri ->
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for image $index")
                completedUploads++
                if (completedUploads == totalImages) {
                    onComplete(uploadedUrls)
                }
                return@forEachIndexed
            }

            val bytes = inputStream.readBytes()
            inputStream.close()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "highlight_${System.currentTimeMillis()}_$index.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .build()

            val request = Request.Builder()
                .url(CLOUDINARY_URL)
                .post(requestBody)
                .build()

            Log.d(TAG, "Uploading image ${index + 1}/$totalImages")

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Upload failed for image $index", e)
                    runOnUiThread {
                        Toast.makeText(this@AddHighlightActivity,
                            "Upload failed for image ${index + 1}: ${e.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                    completedUploads++
                    if (completedUploads == totalImages) {
                        runOnUiThread { onComplete(uploadedUrls) }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            Log.e(TAG, "Upload failed for image $index: ${it.code}")
                            runOnUiThread {
                                Toast.makeText(this@AddHighlightActivity,
                                    "Upload failed for image ${index + 1}",
                                    Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val body = it.body?.string()
                            val json = JSONObject(body ?: "{}")
                            val url = json.optString("secure_url", "")
                            if (url.isNotEmpty()) {
                                uploadedUrls.add(url)
                                Log.d(TAG, "Image ${index + 1} uploaded successfully: $url")
                            }
                        }
                        completedUploads++
                        if (completedUploads == totalImages) {
                            runOnUiThread { onComplete(uploadedUrls) }
                        }
                    }
                }
            })
        }
    }

    private fun saveHighlightToBackend(title: String, imageUrls: List<String>) {
        val sessionManager = SessionManager(this)
        val token = "Bearer ${sessionManager.getAuthToken()}"
        val dateTimestamp = selectedDate!!.time / 1000 // Convert to seconds

        Log.d(TAG, "Saving highlight to backend: $title with ${imageUrls.size} images")

        lifecycleScope.launch {
            try {
                val request = CreateHighlightRequest(
                    title = title,
                    imageUrls = imageUrls,
                    date = dateTimestamp
                )

                val response = RetrofitClient.instance.createHighlight(token, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "Highlight saved successfully")
                    runOnUiThread {
                        Toast.makeText(this@AddHighlightActivity, "Highlight added!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    val error = response.body()?.error ?: "Unknown error"
                    Log.e(TAG, "Failed to save highlight: $error")
                    runOnUiThread {
                        Toast.makeText(this@AddHighlightActivity, "Failed to add highlight: $error", Toast.LENGTH_SHORT).show()
                        uploadBtn.isEnabled = true
                        uploadBtn.text = "Upload Highlight"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving highlight", e)
                runOnUiThread {
                    Toast.makeText(this@AddHighlightActivity, "Failed to add highlight: ${e.message}", Toast.LENGTH_SHORT).show()
                    uploadBtn.isEnabled = true
                    uploadBtn.text = "Upload Highlight"
                }
            }
        }
    }
}
