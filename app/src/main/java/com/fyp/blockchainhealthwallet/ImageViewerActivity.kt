package com.fyp.blockchainhealthwallet

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var ivFullscreen: ImageView
    private lateinit var btnClose: ImageView
    
    private var matrix = Matrix()
    private var savedMatrix = Matrix()
    
    // Zoom and pan
    private var mode = NONE
    private var start = PointF()
    private var mid = PointF()
    private var oldDist = 1f
    private var minScale = 1f
    private var maxScale = 4f
    private var currentScale = 1f
    
    private lateinit var scaleDetector: ScaleGestureDetector

    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_image_viewer)
            
            // Hide system bars AFTER setContentView
            hideSystemBars()

            ivFullscreen = findViewById(R.id.ivFullscreen)
            btnClose = findViewById(R.id.btnClose)

            // Load image
            val imagePath = intent.getStringExtra("IMAGE_PATH")
            Log.d("ImageViewerActivity", "Loading image from: $imagePath")
            
            if (imagePath != null) {
                try {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    if (bitmap != null) {
                        ivFullscreen.setImageBitmap(bitmap)
                        setupZoomAndPan()
                        Log.d("ImageViewerActivity", "Image loaded successfully")
                    } else {
                        Log.e("ImageViewerActivity", "Bitmap is null")
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("ImageViewerActivity", "Error loading image", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                Log.e("ImageViewerActivity", "Image path is null")
                Toast.makeText(this, "No image path provided", Toast.LENGTH_SHORT).show()
                finish()
            }

            btnClose.setOnClickListener {
                finish()
            }
        } catch (e: Exception) {
            Log.e("ImageViewerActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error opening viewer: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun hideSystemBars() {
        // Make truly fullscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
        
        // Also set window flags
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun setupZoomAndPan() {
        // Wait for layout to get view dimensions
        ivFullscreen.post {
            val drawable = ivFullscreen.drawable ?: return@post
            
            val viewWidth = ivFullscreen.width.toFloat()
            val viewHeight = ivFullscreen.height.toFloat()
            val imageWidth = drawable.intrinsicWidth.toFloat()
            val imageHeight = drawable.intrinsicHeight.toFloat()
            
            // Calculate scale to fit the image to screen (fill width or height)
            val scaleX = viewWidth / imageWidth
            val scaleY = viewHeight / imageHeight
            val scale = max(scaleX, scaleY) // Use max to fill screen (crop if needed)
            
            // Center the image
            val dx = (viewWidth - imageWidth * scale) / 2
            val dy = (viewHeight - imageHeight * scale) / 2
            
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)
            
            ivFullscreen.scaleType = ImageView.ScaleType.MATRIX
            ivFullscreen.imageMatrix = matrix
            
            minScale = min(scaleX, scaleY) // Min scale to fit inside screen
            currentScale = scale
            
            Log.d("ImageViewerActivity", "Image: ${imageWidth}x${imageHeight}, View: ${viewWidth}x${viewHeight}, Scale: $scale")
        }

        scaleDetector = ScaleGestureDetector(this, ScaleListener())

        ivFullscreen.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    start.set(event.x, event.y)
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(mid, event)
                        mode = ZOOM
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(event.x - start.x, event.y - start.y)
                    } else if (mode == ZOOM) {
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            matrix.set(savedMatrix)
                            val scale = newDist / oldDist
                            matrix.postScale(scale, scale, mid.x, mid.y)
                        }
                    }
                }
            }

            ivFullscreen.imageMatrix = matrix
            true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            currentScale *= scaleFactor
            currentScale = max(minScale, min(currentScale, maxScale))
            
            matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            ivFullscreen.imageMatrix = matrix
            return true
        }
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        if (event.pointerCount < 2) return
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }
}
