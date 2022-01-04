package com.example.bioniclens.facedetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.bioniclens.agegenderrecognition.AgeGenRecognitionActivity
import com.example.bioniclens.R
import com.example.bioniclens.objectrecognition.ObjRecognitionActivity
import com.example.bioniclens.selfie_segmentation.SelfieSegmentationActivity
import com.example.bioniclens.textrecognition.TextRecognitionActivity
import com.example.bioniclens.utils.GraphicOverlay
import com.example.bioniclens.utils.InferenceInfoGraphic
import com.google.mlkit.vision.camera.CameraSourceConfig
import com.google.mlkit.vision.camera.CameraXSource
import com.google.mlkit.vision.camera.DetectionTaskCallback
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceDetectionActivity : AppCompatActivity(){
    private var previewView: PreviewView? = null
    private var cameraSource: CameraXSource? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var lensFacing: Int = CameraSourceConfig.CAMERA_FACING_BACK
    private var targetResolution: Size? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var faceDetTaskCallback: DetectionTaskCallback<List<Face>>? = null
    private var faceDetector: FaceDetector? = null
    private var faceDetectorOptions: FaceDetectorOptions? = null
    private var faceContourEnabled: Boolean = USE_CONTOUR_MODE_ALL
    private var faceLandmarksEnabled: Boolean = USE_LANDMARK_MODE_ALL
    private var faceClassificationEnabled: Boolean = USE_CLASSIFICATION_MODE_ALL
    private var faceTrackingEnabled: Boolean = ENABLE_TRACKING
    private var favorAccuracy: Boolean = USE_PERFORMANCE_MODE_ACCURATE
    private var faceDetectorOptionsBuilder: FaceDetectorOptions.Builder? = null
    private var isImageFlipped: Boolean = false

    // When resuming the app, restart the camera
    private fun switchCameraFacing()
    {
        if (lensFacing == CameraSourceConfig.CAMERA_FACING_BACK)
        {
            lensFacing = CameraSourceConfig.CAMERA_FACING_FRONT
            isImageFlipped = false
        }
        else
        {
            lensFacing = CameraSourceConfig.CAMERA_FACING_BACK
            isImageFlipped = true
        }
    }

    override fun onResume()
    {
        super.onResume()
        if (allPermissionsGranted()) {
            // Small workaround to prevent switching cameras when we resume the app
            switchCameraFacing()
            startSwitchCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_detection)

        // Fetch the preview view
        previewView = findViewById(R.id.viewFinder)
        if (previewView == null) {
            Log.d(TAG, "previewView is null")
        }

        // Fetch the GraphicOverlay view, to which we draw NN info
        graphicOverlay = findViewById(R.id.graphic_overlay)
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null")
        }

        // Set the callbacks for a successful inference and for a failed inference
        faceDetTaskCallback = DetectionTaskCallback<List<Face>> { faceDetTask ->
            faceDetTask
                    .addOnSuccessListener { faces -> onFaceDetectionTaskSuccess(faces) }
                    .addOnFailureListener { e -> onFaceDetectionTaskFailure(e) }
        }

        faceDetectorOptionsBuilder = FaceDetectorOptions.Builder()

        if (USE_PERFORMANCE_MODE_ACCURATE)
            faceDetectorOptionsBuilder!!.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        else
            faceDetectorOptionsBuilder!!.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)

        if (USE_LANDMARK_MODE_ALL)
            faceDetectorOptionsBuilder!!.setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        else
            faceDetectorOptionsBuilder!!.setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)

        if (USE_CONTOUR_MODE_ALL)
            faceDetectorOptionsBuilder!!.setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        else
            faceDetectorOptionsBuilder!!.setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)

        if (USE_CLASSIFICATION_MODE_ALL)
            faceDetectorOptionsBuilder!!.setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        else
            faceDetectorOptionsBuilder!!.setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)

        if (ENABLE_TRACKING)
            faceDetectorOptionsBuilder!!.enableTracking()

        faceDetectorOptions = faceDetectorOptionsBuilder!!.build()

        // Use-case buttons START
        val objectRecognition: Button = findViewById<Button>(R.id.obj_recognition)
        val selfieSegmentation: Button = findViewById<Button>(R.id.selfie_segmentation)
        val textRecognition: Button = findViewById<Button>(R.id.text_recognition)
        val enableFaceContourButton = findViewById<Button>(R.id.enable_face_contour)
        val disableFaceContourButton = findViewById<Button>(R.id.disable_face_contour)
        val enableFaceLandmarksButton = findViewById<Button>(R.id.enable_face_landmarks)
        val disableFaceLandmarksButton = findViewById<Button>(R.id.disable_face_landmarks)
        val enableFaceClassificationButton = findViewById<Button>(R.id.enable_classification)
        val disableFaceClassificationButton = findViewById<Button>(R.id.disable_classification)
        val enableFaceTracking = findViewById<Button>(R.id.enable_face_tracking)
        val disableFaceTracking = findViewById<Button>(R.id.disable_face_tracking)
        val favorAccuracyButton = findViewById<Button>(R.id.favor_accuracy)
        val favorPerformanceButton = findViewById<Button>(R.id.favor_performance)
        objectRecognition.setVisibility(View.INVISIBLE)
        selfieSegmentation.setVisibility(View.INVISIBLE)
        textRecognition.setVisibility(View.INVISIBLE)
        enableFaceContourButton.setVisibility(View.INVISIBLE)
        disableFaceContourButton.setVisibility(View.INVISIBLE)
        enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
        disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
        enableFaceClassificationButton.setVisibility(View.INVISIBLE)
        disableFaceClassificationButton.setVisibility(View.INVISIBLE)
        enableFaceTracking.setVisibility(View.INVISIBLE)
        disableFaceTracking.setVisibility(View.INVISIBLE)
        favorAccuracyButton.setVisibility(View.INVISIBLE)
        favorPerformanceButton.setVisibility(View.INVISIBLE)

        val netButton: ImageButton = findViewById(R.id.netButton)
        netButton.setOnClickListener {
            if(objectRecognition.isVisible){
                objectRecognition.setVisibility(View.INVISIBLE)
                selfieSegmentation.setVisibility(View.INVISIBLE)
                textRecognition.setVisibility(View.INVISIBLE)
            }
            else{
                objectRecognition.setVisibility(View.VISIBLE)
                selfieSegmentation.setVisibility(View.VISIBLE)
                textRecognition.setVisibility(View.VISIBLE)
            }

            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)
        }

        val settingsButton: Button = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener{
            if (faceContourEnabled)
            {
                disableFaceContourButton.setVisibility( if(disableFaceContourButton.isVisible) View.INVISIBLE else View.VISIBLE)
            }
            else
            {
                enableFaceContourButton.setVisibility( if(enableFaceContourButton.isVisible) View.INVISIBLE else View.VISIBLE)
            }

            if (faceLandmarksEnabled)
            {
                disableFaceLandmarksButton.setVisibility( if(disableFaceLandmarksButton.isVisible) View.INVISIBLE else View.VISIBLE)
            }
            else
            {
                enableFaceLandmarksButton.setVisibility( if(enableFaceLandmarksButton.isVisible) View.INVISIBLE else View.VISIBLE)
            }

            if (faceClassificationEnabled)
            {
                disableFaceClassificationButton.setVisibility( if(disableFaceClassificationButton.isVisible) View.INVISIBLE else View.VISIBLE)
            }
            else
            {
                enableFaceClassificationButton.setVisibility( if(enableFaceClassificationButton.isVisible) View.INVISIBLE else View.VISIBLE)
            }

            if (faceTrackingEnabled)
            {
                disableFaceTracking.setVisibility( if(disableFaceTracking.isVisible) View.INVISIBLE else View.VISIBLE)
            }
            else
            {
                enableFaceTracking.setVisibility( if(enableFaceTracking.isVisible) View.INVISIBLE else View.VISIBLE)
            }

            if (favorAccuracy == false)
            {
                favorAccuracyButton.setVisibility( if(favorAccuracyButton.isVisible) View.INVISIBLE else View.VISIBLE)
            }
            else
            {
                favorPerformanceButton.setVisibility( if(favorPerformanceButton.isVisible) View.INVISIBLE else View.VISIBLE)
            }

            objectRecognition.setVisibility(View.INVISIBLE)
            selfieSegmentation.setVisibility(View.INVISIBLE)
            textRecognition.setVisibility(View.INVISIBLE)
        }

        objectRecognition.setOnClickListener {
            val intent = Intent(this, ObjRecognitionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, objectRecognition, selfieSegmentation)
        }
        selfieSegmentation.setOnClickListener {
            val intent = Intent(this, SelfieSegmentationActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, objectRecognition, selfieSegmentation)
        }
        textRecognition.setOnClickListener {
            val intent = Intent(this, TextRecognitionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, objectRecognition, selfieSegmentation)
        }
        disableFaceContourButton.setOnClickListener{
            faceDetectorOptionsBuilder!!.setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            faceContourEnabled = false
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        enableFaceContourButton.setOnClickListener{
            faceDetectorOptionsBuilder!!.setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            faceContourEnabled = true
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        disableFaceLandmarksButton.setOnClickListener{
            faceDetectorOptionsBuilder!!.setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            faceLandmarksEnabled = false
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        enableFaceLandmarksButton.setOnClickListener{
            faceDetectorOptionsBuilder!!.setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            faceLandmarksEnabled = true
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        disableFaceClassificationButton.setOnClickListener{
            faceDetectorOptionsBuilder!!.setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            faceClassificationEnabled = false
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        enableFaceClassificationButton.setOnClickListener{
            faceDetectorOptionsBuilder!!.setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            faceClassificationEnabled = true
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        disableFaceTracking.setOnClickListener{
            faceDetectorOptionsBuilder = FaceDetectorOptions.Builder()
                    .setContourMode(if (faceContourEnabled) FaceDetectorOptions.CONTOUR_MODE_ALL else FaceDetectorOptions.CONTOUR_MODE_NONE)
                    .setPerformanceMode(if (favorAccuracy) FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE else FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(if (faceLandmarksEnabled) FaceDetectorOptions.LANDMARK_MODE_ALL else FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(if (faceClassificationEnabled) FaceDetectorOptions.CLASSIFICATION_MODE_ALL else FaceDetectorOptions.CLASSIFICATION_MODE_NONE)

            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            faceTrackingEnabled = false
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        enableFaceTracking.setOnClickListener{
            faceDetectorOptionsBuilder!!.enableTracking()
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            faceTrackingEnabled = true
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        favorAccuracyButton.setOnClickListener{
            faceDetectorOptionsBuilder!!.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            favorAccuracy = true
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        favorPerformanceButton.setOnClickListener{
            faceDetectorOptionsBuilder!!.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            faceDetectorOptions = faceDetectorOptionsBuilder!!.build()
            faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

            favorAccuracy = false
            disableFaceContourButton.setVisibility(View.INVISIBLE)
            enableFaceContourButton.setVisibility(View.INVISIBLE)
            disableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceLandmarksButton.setVisibility(View.INVISIBLE)
            enableFaceClassificationButton.setVisibility(View.INVISIBLE)
            disableFaceClassificationButton.setVisibility(View.INVISIBLE)
            enableFaceTracking.setVisibility(View.INVISIBLE)
            disableFaceTracking.setVisibility(View.INVISIBLE)
            favorAccuracyButton.setVisibility(View.INVISIBLE)
            favorPerformanceButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }
        // Use-case buttons END

        if (allPermissionsGranted()) {
            startSwitchCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        val switchCameraButton = findViewById<Button>(R.id.switchCameraButton)

        switchCameraButton.setOnClickListener{
            if (allPermissionsGranted())
            {
                startSwitchCamera()
            }
        }
    }

    private fun onFaceDetectionTaskSuccess(faces: List<Face>) {
        graphicOverlay!!.clear()
        if (needUpdateGraphicOverlayImageSourceInfo) {
            if (isPortraitMode) {
                // Swap width and height sizes when in portrait, since it will be rotated by
                // 90 degrees. The camera preview and the image being processed have the same size.
                graphicOverlay!!.setImageSourceInfo(
                    CAMERA_PREVIEW_WIDTH,
                    CAMERA_PREVIEW_HEIGHT,
                    isImageFlipped
                )
            } else {
                graphicOverlay!!.setImageSourceInfo(
                    CAMERA_PREVIEW_HEIGHT,
                    CAMERA_PREVIEW_WIDTH,
                    isImageFlipped
                )
            }
            needUpdateGraphicOverlayImageSourceInfo = false
        }

        Log.v(TAG, "Face detection successful!")
        // Draw the NN info

        for (face in faces) {
            graphicOverlay!!.add(DetectedFaceGraphic(graphicOverlay!!, face))
        }
        graphicOverlay!!.add(InferenceInfoGraphic(graphicOverlay!!))
        graphicOverlay!!.postInvalidate()
    }

    private fun onFaceDetectionTaskFailure(e: Exception) {
        graphicOverlay!!.clear()
        graphicOverlay!!.postInvalidate()

        // Display info about the error
        val error = "Failed to process. Error: " + e.localizedMessage
        Toast.makeText(
                graphicOverlay!!.getContext(),
                """
   $error
   Cause: ${e.cause}
      """.trimIndent(),
                Toast.LENGTH_SHORT
        )
                .show()
        Log.d(TAG, error)
    }

    // Start or switch from the front facing camera and the back facing camera
    private fun startSwitchCamera() {
        if (cameraSource != null) {
            cameraSource!!.close()
        }

        faceDetector = FaceDetection.getClient(faceDetectorOptions!!)

        val builder: CameraSourceConfig.Builder = CameraSourceConfig.Builder(
            getApplicationContext(), faceDetector!!, faceDetTaskCallback!!
        ).setFacing(lensFacing)

        // Change camera from one switch to another
        switchCameraFacing()

        targetResolution =
            Size(previewView!!.layoutParams.width, previewView!!.layoutParams.height)
        if (targetResolution != null) {
            builder.setRequestedPreviewSize(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT)
        }

        cameraSource = CameraXSource(builder.build(), previewView!!)
        needUpdateGraphicOverlayImageSourceInfo = true
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        cameraSource!!.start()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startSwitchCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun makeButtonsInvisible(b1 : Button, b2 : Button, b3 : Button){
        b1.setVisibility(View.INVISIBLE)
        b2.setVisibility(View.INVISIBLE)
        b3.setVisibility(View.INVISIBLE)
    }

    private val isPortraitMode: Boolean
         get() = (
            applicationContext.getResources().getConfiguration().orientation
                    != Configuration.ORIENTATION_LANDSCAPE
            )

    companion object {
        private const val TAG = "FaceDetectionUnit"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val CAMERA_PREVIEW_WIDTH = 1080
        private const val CAMERA_PREVIEW_HEIGHT = 1920
        private const val USE_PERFORMANCE_MODE_ACCURATE = false
        private const val USE_LANDMARK_MODE_ALL = true
        private const val USE_CONTOUR_MODE_ALL = true
        private const val USE_CLASSIFICATION_MODE_ALL = true
        private const val ENABLE_TRACKING = true
    }
}
