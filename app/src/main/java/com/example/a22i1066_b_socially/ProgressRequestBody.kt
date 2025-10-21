package com.example.a22i1066_b_socially

import android.util.Log
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

class ProgressRequestBody(
    private val data: ByteArray,
    private val contentType: String,
    private val progressCallback: (percent: Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = data.size.toLong()

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val total = data.size.toLong()
        if (total == 0L) {
            progressCallback(100)
            return
        }

        progressCallback(0)

        var uploaded: Long = 0
        val chunk = 8192 // Larger chunk size
        var offset = 0

        try {
            while (offset < data.size) {
                val bytesToWrite = kotlin.math.min(chunk, data.size - offset)
                sink.write(data, offset, bytesToWrite)

                offset += bytesToWrite
                uploaded += bytesToWrite

                val percent = ((uploaded * 100) / total).toInt()

                // Update progress less frequently (every 10%)
                if (percent % 10 == 0 || uploaded == total) {
                    Log.d("ProgressRequestBody", "upload progress: $percent%")
                    progressCallback(percent)
                }
            }

            // Flush once at the end
            sink.flush()
            progressCallback(100)
        } catch (e: Exception) {
            Log.e("ProgressRequestBody", "writeTo error", e)
            throw e
        }
    }
}
