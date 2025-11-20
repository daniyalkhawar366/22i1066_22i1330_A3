package com.example.a22i1066_b_socially.offline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

object PicassoImageLoader {

    private const val TAG = "PicassoImageLoader"
    private const val CACHE_SIZE = 100 * 1024 * 1024L // 100 MB cache

    private lateinit var picasso: Picasso

    fun initialize(context: Context) {
        if (::picasso.isInitialized) {
            return
        }

        try {
            // Create cache directory
            val cacheDir = File(context.cacheDir, "picasso-cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Create OkHttp client with cache
            val okHttpClient = OkHttpClient.Builder()
                .cache(Cache(cacheDir, CACHE_SIZE))
                .build()

            // Create Picasso instance with caching enabled
            picasso = Picasso.Builder(context)
                .downloader(com.squareup.picasso.OkHttp3Downloader(okHttpClient))
                .indicatorsEnabled(false) // Set to true for debugging
                .loggingEnabled(false) // Set to true for debugging
                .build()

            Picasso.setSingletonInstance(picasso)

            Log.d(TAG, "Picasso initialized with ${CACHE_SIZE / (1024 * 1024)} MB cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Picasso", e)
            // Fallback to default Picasso if initialization fails
            picasso = Picasso.get()
        }
    }

    fun get(): Picasso {
        if (!::picasso.isInitialized) {
            throw IllegalStateException("PicassoImageLoader must be initialized first")
        }
        return picasso
    }

    /**
     * Load image with offline support
     * Automatically uses cached version if available
     */
    fun loadImage(
        imageView: ImageView,
        url: String?,
        placeholderResId: Int = 0,
        errorResId: Int = 0,
        circular: Boolean = false
    ) {
        if (url.isNullOrBlank()) {
            if (placeholderResId != 0) {
                imageView.setImageResource(placeholderResId)
            }
            return
        }

        try {
            val requestCreator = get().load(url)

            if (placeholderResId != 0) {
                requestCreator.placeholder(placeholderResId)
            }

            if (errorResId != 0) {
                requestCreator.error(errorResId)
            }

            if (circular) {
                requestCreator.transform(CircleTransformation())
            }

            requestCreator
                .networkPolicy(com.squareup.picasso.NetworkPolicy.OFFLINE)
                .into(imageView, object : com.squareup.picasso.Callback {
                    override fun onSuccess() {
                        // Image loaded from cache
                        Log.d(TAG, "Image loaded from cache: $url")
                    }

                    override fun onError(e: Exception?) {
                        // Cache miss, try loading from network
                        Log.d(TAG, "Cache miss, loading from network: $url")
                        val networkRequest = get().load(url)

                        if (placeholderResId != 0) {
                            networkRequest.placeholder(placeholderResId)
                        }

                        if (errorResId != 0) {
                            networkRequest.error(errorResId)
                        }

                        if (circular) {
                            networkRequest.transform(CircleTransformation())
                        }

                        networkRequest.into(imageView, object : com.squareup.picasso.Callback {
                            override fun onSuccess() {
                                Log.d(TAG, "Image loaded from network: $url")
                            }

                            override fun onError(e: Exception?) {
                                Log.e(TAG, "Error loading image: $url", e)
                            }
                        })
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image", e)
            if (errorResId != 0) {
                imageView.setImageResource(errorResId)
            }
        }
    }

    /**
     * Load image from local file path
     */
    fun loadLocalImage(
        imageView: ImageView,
        filePath: String,
        placeholderResId: Int = 0,
        errorResId: Int = 0,
        circular: Boolean = false
    ) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                if (errorResId != 0) {
                    imageView.setImageResource(errorResId)
                }
                return
            }

            val requestCreator = get().load(file)

            if (placeholderResId != 0) {
                requestCreator.placeholder(placeholderResId)
            }

            if (errorResId != 0) {
                requestCreator.error(errorResId)
            }

            if (circular) {
                requestCreator.transform(CircleTransformation())
            }

            requestCreator.into(imageView)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local image", e)
            if (errorResId != 0) {
                imageView.setImageResource(errorResId)
            }
        }
    }

    /**
     * Prefetch image for offline use
     */
    fun prefetchImage(url: String) {
        try {
            if (url.isNotBlank()) {
                get().load(url).fetch()
                Log.d(TAG, "Prefetching image: $url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error prefetching image", e)
        }
    }

    /**
     * Prefetch multiple images
     */
    fun prefetchImages(urls: List<String>) {
        urls.forEach { url ->
            prefetchImage(url)
        }
    }

    /**
     * Get bitmap from URL with callback
     */
    fun getBitmap(url: String, onSuccess: (Bitmap) -> Unit, onError: () -> Unit) {
        try {
            get().load(url).into(object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                    bitmap?.let { onSuccess(it) } ?: onError()
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    Log.e(TAG, "Error loading bitmap", e)
                    onError()
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                    // Optional: Show loading state
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bitmap", e)
            onError()
        }
    }

    /**
     * Clear cache
     */
    fun clearCache() {
        try {
            get().invalidate("")
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
}

/**
 * Circle transformation for profile pictures
 */
class CircleTransformation : com.squareup.picasso.Transformation {
    override fun transform(source: Bitmap): Bitmap {
        val size = Math.min(source.width, source.height)

        val x = (source.width - size) / 2
        val y = (source.height - size) / 2

        val squaredBitmap = Bitmap.createBitmap(source, x, y, size, size)
        if (squaredBitmap != source) {
            source.recycle()
        }

        val bitmap = Bitmap.createBitmap(size, size, source.config ?: Bitmap.Config.ARGB_8888)

        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        val shader = android.graphics.BitmapShader(
            squaredBitmap,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.isAntiAlias = true

        val r = size / 2f
        canvas.drawCircle(r, r, r, paint)

        squaredBitmap.recycle()
        return bitmap
    }

    override fun key(): String = "circle"
}

