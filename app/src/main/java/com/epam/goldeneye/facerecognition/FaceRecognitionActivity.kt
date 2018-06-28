package com.epam.goldeneye.facerecognition

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import com.epam.goldeneye.R
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat

class FaceRecognitionActivity : Activity() {

    private lateinit var cameraView: CameraBridgeViewBase

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i("Tag", "OpenCV loaded successfully")
                    cameraView.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.face_recognition_activity)

        cameraView = findViewById(R.id.cameraView)

        cameraView.visibility = SurfaceView.VISIBLE

        cameraView.setCvCameraViewListener(object : CameraBridgeViewBase.CvCameraViewListener2 {

            override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat? {
                Log.d(FaceRecognitionActivity::class.java.simpleName, "onCameraFrame() called with: inputFrame = [$inputFrame]")
                return inputFrame?.rgba()
            }

            override fun onCameraViewStarted(width: Int, height: Int) {
                Log.d(FaceRecognitionActivity::class.java.simpleName, "width = [$width], height = [$height]")
            }

            override fun onCameraViewStopped() {
                Log.d(FaceRecognitionActivity::class.java.simpleName, "onCameraViewStopped() called")
            }

        })
    }

    public override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    public override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d("TAG", "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback)
        } else {
            Log.d("TAG", "OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        cameraView.disableView()
    }
}