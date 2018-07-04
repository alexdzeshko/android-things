package com.epam.opencv.detector.face.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import com.epam.opencv.R
import com.epam.opencv.detector.face.DetectionBasedTracker
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FaceDetectionActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private val TAG = "OCVSample::Activity"

    private val FACE_RECT_COLOR = Scalar(0.0, 255.0, 0.0, 255.0)
    private val JAVA_DETECTOR = 0
    private val NATIVE_DETECTOR = 1

    private var mItemFace50: MenuItem? = null
    private var mItemFace40: MenuItem? = null
    private var mItemFace30: MenuItem? = null
    private var mItemFace20: MenuItem? = null
    private var mItemType: MenuItem? = null

    private var mRgba: Mat? = null
    private var mGray: Mat? = null
    private var mCascadeFile: File? = null
    private var mJavaDetector: CascadeClassifier? = null
    private var mNativeDetector: DetectionBasedTracker? = null

    private var mDetectorType = JAVA_DETECTOR
    private var mDetectorName: Array<String> = arrayOf("Java", "Native (tracking)")

    private var mRelativeFaceSize = 0.2f
    private var mAbsoluteFaceSize = 0

    private var mOpenCvCameraView: CameraBridgeViewBase? = null

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("opencv-detector")

                    try {
                        // load cascade file from application resources
                        val cascadeStream = resources.openRawResource(R.raw.lbpcascade_frontalface)
                        val cascadeDir = getDir("cascade", Context.MODE_PRIVATE)
                        mCascadeFile = File(cascadeDir, "lbpcascade_frontalface.xml")
                        val os = FileOutputStream(mCascadeFile)

                        cascadeStream.use {cascade ->
                            os.use {fileStream ->
                                cascade.copyTo(fileStream, 4096)
                            }
                        }

                        mJavaDetector = CascadeClassifier(mCascadeFile?.absolutePath)
                        if (mJavaDetector?.empty() == true) {
                            Log.e(TAG, "Failed to load cascade classifier")
                            mJavaDetector = null
                        } else {
                            Log.i(TAG, "Loaded cascade classifier from ${mCascadeFile?.absolutePath}")
                        }
                        mNativeDetector = DetectionBasedTracker(mCascadeFile?.absolutePath, 0)

                        cascadeDir.delete()

                    } catch (e: IOException) {
                        Log.e(FaceDetectionActivity::class.java.simpleName, "onManagerConnected", e)
                    }

                    mOpenCvCameraView?.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.face_recognition_activity)

        mOpenCvCameraView = findViewById(R.id.cameraView)
        mOpenCvCameraView?.visibility = CameraBridgeViewBase.VISIBLE
        mOpenCvCameraView?.setCvCameraViewListener(this)

        findViewById<View>(R.id.btnBack).setOnClickListener {
//            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    public override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    public override fun onPause() {
        super.onPause()
        mOpenCvCameraView?.disableView()
    }

    public override fun onDestroy() {
        super.onDestroy()
        mOpenCvCameraView?.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        mGray = Mat()
        mRgba = Mat()
    }

    override fun onCameraViewStopped() {
        mGray?.release()
        mRgba?.release()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat? {

        mRgba = inputFrame.rgba()
        mGray = inputFrame.gray()

        if (mAbsoluteFaceSize == 0) {
            val height = mGray?.rows()
            if (height != null) {
                if (Math.round(height * mRelativeFaceSize) > 0) {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize)
                }
            }
            mNativeDetector?.setMinFaceSize(mAbsoluteFaceSize)
        }

        val faces = MatOfRect()

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                mJavaDetector?.detectMultiScale(
                        mGray,
                        faces,
                        1.1,
                        2,
                        2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        Size(mAbsoluteFaceSize.toDouble(), mAbsoluteFaceSize.toDouble()),
                        Size()
                )
        } else if (mDetectorType == NATIVE_DETECTOR) {
            if (mNativeDetector != null)
                mNativeDetector?.detect(mGray, faces)
        } else {
            Log.e(TAG, "Detection method is not selected!")
        }

        val facesArray = faces.toArray()
        for (i in facesArray.indices) {
            val face = facesArray[i]
            mRgba?.let { Imgproc.rectangle(it, face.tl(), face.br(), FACE_RECT_COLOR, 3) }

            Log.d(FaceDetectionActivity::class.java.simpleName, "onCameraFrame ${face}")
        }
        return mRgba
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.i(TAG, "called onCreateOptionsMenu")
        mItemFace50 = menu.add("Face size 50%")
        mItemFace40 = menu.add("Face size 40%")
        mItemFace30 = menu.add("Face size 30%")
        mItemFace20 = menu.add("Face size 20%")
        mItemType = menu.add(mDetectorName[mDetectorType])
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.i(TAG, "called onOptionsItemSelected; selected item: $item")
        if (item === mItemFace50)
            setMinFaceSize(0.5f)
        else if (item === mItemFace40)
            setMinFaceSize(0.4f)
        else if (item === mItemFace30)
            setMinFaceSize(0.3f)
        else if (item === mItemFace20)
            setMinFaceSize(0.2f)
        else if (item === mItemType) {
            val tmpDetectorType = (mDetectorType + 1) % mDetectorName.size
            item.title = mDetectorName[tmpDetectorType]
            setDetectorType(tmpDetectorType)
        }
        return true
    }

    private fun setMinFaceSize(faceSize: Float) {
        mRelativeFaceSize = faceSize
        mAbsoluteFaceSize = 0
    }

    private fun setDetectorType(type: Int) {
        if (mDetectorType != type) {
            mDetectorType = type

            if (type == NATIVE_DETECTOR) {
                Log.i(TAG, "Detection Based Tracker enabled")
                mNativeDetector?.start()
            } else {
                Log.i(TAG, "Cascade detector enabled")
                mNativeDetector?.stop()
            }
        }
    }
}