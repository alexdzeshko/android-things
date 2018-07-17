package com.epam.goldeneye.facerecognition

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.AsyncTask.execute
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.epam.goldeneye.R
import com.example.android.camera2basic.CameraActivity
import com.tzutalin.dlib.Constants
import com.tzutalin.dlib.FaceDet
import com.tzutalin.dlib.VisionDetRet
import com.tzutalin.dlibtest.DetectedItem
import com.tzutalin.dlibtest.FileUtils
import kotlinx.android.synthetic.main.face_recognition_activity.*
import java.io.File

class FaceRecognitionActivity : Activity() {

    private var faceDetector: FaceDet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.face_recognition_activity)

        btnBack.setOnClickListener { startActivity(Intent(this, CameraActivity::class.java)) }

        val file: File = intent.getSerializableExtra(EXTRA_IMAGE_FILE) as File

        execute {
            beginRecognition(file)
        }
    }

    private fun beginRecognition(file: File) {
        Log.d(FaceRecognitionActivity::class.java.simpleName, "beginRecognition() called with: file = [$file] ${Thread.currentThread()}")
        prepareModel()
        if (faceDetector == null) {
            faceDetector = FaceDet(Constants.getFaceShapeModelPath())
        }

        val imgPath = file.absolutePath
        Log.d(TAG, "Image path: $imgPath")
        val faceList = faceDetector?.detect(readBitmapFromFile(imgPath))
        if (faceList != null && faceList.size > 0) {
            val detectedItem = DetectedItem(drawRect(imgPath, faceList, Color.GREEN), "Face det")
            runOnUiThread {
                displayDetectedImage(detectedItem)
            }
        } else {
            runOnUiThread {
                Toast.makeText(applicationContext, "No face found", Toast.LENGTH_SHORT).show()
                displayImage(file)
            }
        }
    }

    private fun displayImage(file: File) {
        recognitionImageView.setImageURI(Uri.fromFile(file))
    }

    private fun displayDetectedImage(detectedItem: DetectedItem) {
        recognitionImageView.setImageDrawable(detectedItem.image)
    }

    private fun prepareModel() {
        val targetPath = Constants.getFaceShapeModelPath()
        if (!File(targetPath).exists()) {
            Log.d(FaceRecognitionActivity::class.java.simpleName, "prepareModel from $targetPath")
            FileUtils.copyFileFromRawToOthers(applicationContext, R.raw.shape_predictor_5_face_landmarks, targetPath)
        }
    }

    private fun drawRect(path: String, results: List<VisionDetRet>, color: Int): BitmapDrawable {
        var bm = readBitmapFromFile(path)
        var bitmapConfig: Bitmap.Config? = bm.config
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = Bitmap.Config.ARGB_8888
        }
        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bm = bm.copy(bitmapConfig, true)
        val width = bm.width
        val height = bm.height
        // By ratio scale
        val aspectRatio = bm.width / bm.height.toFloat()

        val MAX_SIZE = 512
        var newHeight = MAX_SIZE
        var resizeRatio = 1f
        newHeight = Math.round(MAX_SIZE / aspectRatio)
        if (bm.width > MAX_SIZE && bm.height > MAX_SIZE) {
            Log.d(TAG, "Resize Bitmap")
            bm = getResizedBitmap(bm, MAX_SIZE, newHeight)
            resizeRatio = bm.width.toFloat() / width.toFloat()
            Log.d(TAG, "resizeRatio $resizeRatio")
        }

        // Create canvas to draw
        val canvas = Canvas(bm)
        val paint = Paint()
        paint.color = color
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        // Loop result list
        for (ret in results) {
            val bounds = Rect()
            bounds.left = (ret.left * resizeRatio).toInt()
            bounds.top = (ret.top * resizeRatio).toInt()
            bounds.right = (ret.right * resizeRatio).toInt()
            bounds.bottom = (ret.bottom * resizeRatio).toInt()
            canvas.drawRect(bounds, paint)
            // Get landmark
            val landmarks = ret.faceLandmarks
            for (point in landmarks) {
                val pointX = (point.x * resizeRatio).toInt()
                val pointY = (point.y * resizeRatio).toInt()
                canvas.drawCircle(pointX.toFloat(), pointY.toFloat(), 2f, paint)
            }
        }

        return BitmapDrawable(resources, bm)
    }

    private fun readBitmapFromFile(path: String): Bitmap {
        val options = BitmapFactory.Options()
        options.inSampleSize = 1
        return BitmapFactory.decodeFile(path, options)
    }

    private fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bm, newWidth, newHeight, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        faceDetector?.release()
    }
    companion object {
        private const val EXTRA_IMAGE_FILE = "ImageFile"

        @JvmStatic
        fun start(context: Context, file: File) {
            val intent = Intent(context, FaceRecognitionActivity::class.java)
            intent.putExtra(EXTRA_IMAGE_FILE, file)
            context.startActivity(intent)
        }
    }
}
