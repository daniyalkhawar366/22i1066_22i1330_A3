// kotlin
package com.example.a22i1066_b_socially

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import androidx.camera.view.PreviewView

class VideoCallActivity : AppCompatActivity() {

    private var previewView: PreviewView? = null
    private var selfPreviewView: PreviewView? = null

    private lateinit var endBtn: ImageView
    private lateinit var muteButton: ImageView
    private lateinit var cameraToggle: ImageView
    private lateinit var switchCameraButton: ImageView

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null

    private var currentSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var isCameraEnabled = true
    private var isMutedLocal = false

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val ok = perms[Manifest.permission.CAMERA] == true && perms[Manifest.permission.RECORD_AUDIO] == true
        if (ok) startCameraPreview()
        else Toast.makeText(this, "Camera & audio permissions required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        previewView = findViewById(R.id.previewView)
        selfPreviewView = findViewById(R.id.selfPreview)

        endBtn = findViewById(R.id.endVideoCall)
        muteButton = findViewById(R.id.muteButton)
        cameraToggle = findViewById(R.id.cameraToggle)
        switchCameraButton = findViewById(R.id.switchCamera)

        endBtn.setOnClickListener {
            finish()
        }

        muteButton.setOnClickListener {
            isMutedLocal = !isMutedLocal
            CallSession.setMuted(isMutedLocal)
            val tint = if (isMutedLocal) android.R.color.darker_gray else android.R.color.white
            muteButton.setColorFilter(ContextCompat.getColor(this, tint))
            Toast.makeText(this, if (isMutedLocal) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
        }

        cameraToggle.setOnClickListener {
            isCameraEnabled = !isCameraEnabled
            updateCameraEnabledState()
            val msg = if (isCameraEnabled) "Camera enabled" else "Camera disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        switchCameraButton.setOnClickListener {
            currentSelector = if (currentSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            bindCameraUseCases()
            Toast.makeText(this, "Switched camera", Toast.LENGTH_SHORT).show()
        }

        // mark session active for this video call (chat id may be passed via intent)
        val chatId = intent.getStringExtra("CHAT_ID")
        CallSession.start(chatId.takeIf { !it.isNullOrBlank() }, "video")

        // If caller sent an END_CALL request in the intent, finish immediately
        if (intent.getBooleanExtra("END_CALL", false)) {
            finish()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        } else {
            requestPerms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("END_CALL", false)) {
            finish()
        }
    }

    private fun startCameraPreview() {
        val pv = previewView ?: return
        selfPreviewView = findViewById(R.id.selfPreview)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val executor: Executor = ContextCompat.getMainExecutor(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Toast.makeText(this, "Camera start failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, executor)
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val pv = previewView ?: return
        val spv = selfPreviewView

        if (!isCameraEnabled) {
            provider.unbindAll()
            pv.visibility = View.GONE
            spv?.visibility = View.GONE
            return
        }

        try {
            val mainPreview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
            val smallPreview = spv?.let { Preview.Builder().build().also { pr -> pr.setSurfaceProvider(it.surfaceProvider) } }

            provider.unbindAll()
            if (smallPreview != null) {
                provider.bindToLifecycle(this, currentSelector, mainPreview, smallPreview)
                spv.visibility = View.VISIBLE
            } else {
                provider.bindToLifecycle(this, currentSelector, mainPreview)
            }
            pv.visibility = View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to bind camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateCameraEnabledState() {
        bindCameraUseCases()
        val iconTint = if (isCameraEnabled) android.R.color.white else android.R.color.darker_gray
        cameraToggle.setColorFilter(ContextCompat.getColor(this, iconTint))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        CallSession.end()
    }
}
