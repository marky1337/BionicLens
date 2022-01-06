package com.example.bioniclens.objectrecognition

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
import com.example.bioniclens.R
import com.example.bioniclens.facedetection.FaceDetectionActivity
import com.example.bioniclens.selfie_segmentation.SelfieSegmentationActivity
import com.example.bioniclens.textrecognition.TextRecognitionActivity
import com.example.bioniclens.utils.GraphicOverlay
import com.example.bioniclens.utils.InferenceInfoGraphic
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.camera.CameraSourceConfig
import com.google.mlkit.vision.camera.CameraXSource
import com.google.mlkit.vision.camera.DetectionTaskCallback
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions

class ObjRecognitionActivity : AppCompatActivity(){
    private var previewView: PreviewView? = null
    private var cameraSource: CameraXSource? = null
    private var localModel: LocalModel? = null
    private var customObjectDetectorOptions: CustomObjectDetectorOptions? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var lensFacing: Int = CameraSourceConfig.CAMERA_FACING_BACK
    private var targetResolution: Size? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var objectDetector: ObjectDetector? = null
    private var detectionTaskCallback: DetectionTaskCallback<List<DetectedObject>>? = null
    private var detectionModeSingleImage = false
    private var trackMultipleObjects = MULTIPLE_OBJECTS_AT_ONCE
    private var classifyObjects = CLASSIFICATION_ENABLED
    private var labelCount = MAX_OBJECT_LABELS
    private var objDetOptionsBuilder: CustomObjectDetectorOptions.Builder? = null
    private var isImageFlipped: Boolean = false

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

    // When resuming the app, restart the camera
    override fun onResume()
    {
        super.onResume()
        if (allPermissionsGranted()) {
            // Small workaround to prevent switching cameras when we resume the app
            if (lensFacing == CameraSourceConfig.CAMERA_FACING_BACK)
            {
                lensFacing = CameraSourceConfig.CAMERA_FACING_FRONT
            }
            else
            {
                lensFacing = CameraSourceConfig.CAMERA_FACING_BACK
            }
            startSwitchCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_obj_recognition)

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

        localModel = LocalModel.Builder().setAssetFilePath(MODEL_PATH).build()
        objDetOptionsBuilder = CustomObjectDetectorOptions.Builder(localModel)

        // Set the desired Object Detection options
        objDetOptionsBuilder!!
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .setClassificationConfidenceThreshold(MIN_CONFIDENCE)
                .setMaxPerObjectLabelCount(MAX_OBJECT_LABELS)

        if (MULTIPLE_OBJECTS_AT_ONCE)
        {
            objDetOptionsBuilder!!.enableMultipleObjects()
        }

        if (CLASSIFICATION_ENABLED)
        {
            objDetOptionsBuilder!!.enableClassification()
        }

        customObjectDetectorOptions = objDetOptionsBuilder!!.build()

        // Set the callbacks for a successful inference and for a failed inference
        detectionTaskCallback =
        DetectionTaskCallback<List<DetectedObject>> { detectionTask ->
            detectionTask
                    .addOnSuccessListener { results -> onDetectionTaskSuccess(results) }
                    .addOnFailureListener { e -> onDetectionTaskFailure(e) }
        }

        // Use-case buttons START
        val textRecognition: Button = findViewById(R.id.text_recognition)
        val faceDetection: Button = findViewById(R.id.face_detection)
        val selfieSegmentation: Button = findViewById(R.id.selfie_segmentation)
        val detectionModeButton: Button = findViewById(R.id.detection_mode)
        val objectTrackButton: Button = findViewById(R.id.object_track)
        val classifyObjectsButton: Button = findViewById(R.id.classify_objects)
        val increaseLabelsButton: Button = findViewById(R.id.more_labels)
        val decreaseLabelsButton: Button = findViewById(R.id.less_labels)
        val settingsButton: Button = findViewById(R.id.settingsButton)

        textRecognition.setVisibility(View.INVISIBLE)
        faceDetection.setVisibility(View.INVISIBLE)
        selfieSegmentation.setVisibility(View.INVISIBLE)

        detectionModeButton.setVisibility(View.INVISIBLE)
        objectTrackButton.setVisibility(View.INVISIBLE)
        classifyObjectsButton.setVisibility(View.INVISIBLE)
        increaseLabelsButton.setVisibility(View.INVISIBLE)
        decreaseLabelsButton.setVisibility(View.INVISIBLE)

        val netButton: ImageButton = findViewById(R.id.netButton)
        netButton.setOnClickListener {
            if(textRecognition.isVisible){
                textRecognition.setVisibility(View.INVISIBLE)
                faceDetection.setVisibility(View.INVISIBLE)
                selfieSegmentation.setVisibility(View.INVISIBLE)
            }
            else{
                textRecognition.setVisibility(View.VISIBLE)
                faceDetection.setVisibility(View.VISIBLE)
                selfieSegmentation.setVisibility(View.VISIBLE)
            }

            detectionModeButton.setVisibility(View.INVISIBLE)
            objectTrackButton.setVisibility(View.INVISIBLE)
            classifyObjectsButton.setVisibility(View.INVISIBLE)
            increaseLabelsButton.setVisibility(View.INVISIBLE)
            decreaseLabelsButton.setVisibility(View.INVISIBLE)
        }

        settingsButton.setOnClickListener {
            if (detectionModeButton.isVisible)
            {
                detectionModeButton.setVisibility(View.INVISIBLE)
                objectTrackButton.setVisibility(View.INVISIBLE)
                classifyObjectsButton.setVisibility(View.INVISIBLE)
                increaseLabelsButton.setVisibility(View.INVISIBLE)
                decreaseLabelsButton.setVisibility(View.INVISIBLE)
            }
            else
            {
                detectionModeButton.setVisibility(View.VISIBLE)
                objectTrackButton.setVisibility(View.VISIBLE)
                classifyObjectsButton.setVisibility(View.VISIBLE)
                increaseLabelsButton.setVisibility(View.VISIBLE)
                decreaseLabelsButton.setVisibility(View.VISIBLE)
            }

            textRecognition.setVisibility(View.INVISIBLE)
            faceDetection.setVisibility(View.INVISIBLE)
            selfieSegmentation.setVisibility(View.INVISIBLE)
        }

        detectionModeButton.setOnClickListener {
            if (detectionModeSingleImage == true)
            {
                objDetOptionsBuilder!!.setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                detectionModeButton.text = "Single Image Mode"
                detectionModeSingleImage = false
            }
            else
            {
                objDetOptionsBuilder!!.setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                detectionModeButton.text = "Streaming Mode"
                detectionModeSingleImage = true
            }

            detectionModeButton.setVisibility(View.INVISIBLE)
            objectTrackButton.setVisibility(View.INVISIBLE)
            classifyObjectsButton.setVisibility(View.INVISIBLE)
            increaseLabelsButton.setVisibility(View.INVISIBLE)
            decreaseLabelsButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            customObjectDetectorOptions = objDetOptionsBuilder!!.build()
            startSwitchCamera()
        }

        objectTrackButton.setOnClickListener {
            if (trackMultipleObjects == true)
            {
                objDetOptionsBuilder = CustomObjectDetectorOptions.Builder(localModel)
                objDetOptionsBuilder!!.setClassificationConfidenceThreshold(MIN_CONFIDENCE)
                objDetOptionsBuilder!!.setMaxPerObjectLabelCount(labelCount)
                if (detectionModeSingleImage == true) objDetOptionsBuilder!!.setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                else objDetOptionsBuilder!!.setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                if (classifyObjects) objDetOptionsBuilder!!.enableClassification()
                objectTrackButton.text = "Multiple Object Detection"
                trackMultipleObjects = false
            }
            else
            {
                objDetOptionsBuilder!!.enableMultipleObjects()
                objectTrackButton.text = "Single Object Detection"
                trackMultipleObjects = true
            }

            detectionModeButton.setVisibility(View.INVISIBLE)
            objectTrackButton.setVisibility(View.INVISIBLE)
            classifyObjectsButton.setVisibility(View.INVISIBLE)
            increaseLabelsButton.setVisibility(View.INVISIBLE)
            decreaseLabelsButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            customObjectDetectorOptions = objDetOptionsBuilder!!.build()
            startSwitchCamera()
        }

        classifyObjectsButton.setOnClickListener {
            if (classifyObjects == true)
            {
                objDetOptionsBuilder = CustomObjectDetectorOptions.Builder(localModel)
                objDetOptionsBuilder!!.setClassificationConfidenceThreshold(MIN_CONFIDENCE)
                objDetOptionsBuilder!!.setMaxPerObjectLabelCount(labelCount)
                if (detectionModeSingleImage == true) objDetOptionsBuilder!!.setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                else objDetOptionsBuilder!!.setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                if (trackMultipleObjects) objDetOptionsBuilder!!.enableMultipleObjects()
                classifyObjectsButton.text = "Turn ON Classification"
                classifyObjects = false
            }
            else
            {
                objDetOptionsBuilder!!.enableClassification()
                classifyObjectsButton.text = "Turn OFF Classification"
                classifyObjects = true
            }

            detectionModeButton.setVisibility(View.INVISIBLE)
            objectTrackButton.setVisibility(View.INVISIBLE)
            classifyObjectsButton.setVisibility(View.INVISIBLE)
            increaseLabelsButton.setVisibility(View.INVISIBLE)
            decreaseLabelsButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            customObjectDetectorOptions = objDetOptionsBuilder!!.build()
            startSwitchCamera()
        }

        increaseLabelsButton.setOnClickListener {
            var switchNeeded: Boolean
            labelCount++
            if (labelCount > 5) {
                labelCount = 5
                switchNeeded = false
            }
            else
            {
                switchNeeded = true
            }
            objDetOptionsBuilder!!.setMaxPerObjectLabelCount(labelCount)

            detectionModeButton.setVisibility(View.INVISIBLE)
            objectTrackButton.setVisibility(View.INVISIBLE)
            classifyObjectsButton.setVisibility(View.INVISIBLE)
            increaseLabelsButton.setVisibility(View.INVISIBLE)
            decreaseLabelsButton.setVisibility(View.INVISIBLE)

            if (switchNeeded) {
                switchCameraFacing()
                customObjectDetectorOptions = objDetOptionsBuilder!!.build()
                startSwitchCamera()
            }
        }

        decreaseLabelsButton.setOnClickListener {
            var switchNeeded: Boolean
            labelCount--
            if (labelCount < 1) {
                labelCount = 1
                switchNeeded = false
            }
            else
            {
                switchNeeded = true
            }
            objDetOptionsBuilder!!.setMaxPerObjectLabelCount(labelCount)

            detectionModeButton.setVisibility(View.INVISIBLE)
            objectTrackButton.setVisibility(View.INVISIBLE)
            classifyObjectsButton.setVisibility(View.INVISIBLE)
            increaseLabelsButton.setVisibility(View.INVISIBLE)
            decreaseLabelsButton.setVisibility(View.INVISIBLE)

            if (switchNeeded) {
                switchCameraFacing()
                customObjectDetectorOptions = objDetOptionsBuilder!!.build()
                startSwitchCamera()
            }
        }

        textRecognition.setOnClickListener {
            val intent = Intent(this, TextRecognitionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, faceDetection, selfieSegmentation)
        }

        faceDetection.setOnClickListener {
            val intent = Intent(this, FaceDetectionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, faceDetection, selfieSegmentation)
        }

        selfieSegmentation.setOnClickListener {
            val intent = Intent(this, SelfieSegmentationActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, faceDetection, selfieSegmentation)
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

    private fun onDetectionTaskSuccess(results: List<DetectedObject>) {
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
        Log.v(TAG, "Number of object been detected: " + results.size)
        // Draw the NN info
        for (`object` in results) {
            graphicOverlay!!.add(
                ObjectGraphic(
                    graphicOverlay!!,
                    `object`,
                    !detectionModeSingleImage
                )
            )
        }
        graphicOverlay!!.add(InferenceInfoGraphic(graphicOverlay!!))
        graphicOverlay!!.postInvalidate()
    }

    private fun onDetectionTaskFailure(e: Exception) {
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
        if (cameraSource != null)
        {
            cameraSource!!.close()
        }

        objectDetector = ObjectDetection.getClient(customObjectDetectorOptions!!)

        val builder: CameraSourceConfig.Builder = CameraSourceConfig.Builder(
            getApplicationContext(), objectDetector!!, detectionTaskCallback
        ).setFacing(lensFacing)

        // Change camera from one switch to another
        switchCameraFacing()

        targetResolution = Size(previewView!!.layoutParams.width, previewView!!.layoutParams.height)
        if (targetResolution != null) {
            if (isPortraitMode)
                builder.setRequestedPreviewSize(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT)
            else
                builder.setRequestedPreviewSize(CAMERA_PREVIEW_HEIGHT, CAMERA_PREVIEW_WIDTH)
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
        private get() = (
                getApplicationContext().getResources().getConfiguration().orientation
                        !== Configuration.ORIENTATION_LANDSCAPE
                )

    companion object {
        private const val TAG = "ObjectRecognitionUnit"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MODEL_PATH = "lite-model_object_detection_mobile_object_labeler_v1_1.tflite"
        private const val MAX_OBJECT_LABELS = 1
        private const val MIN_CONFIDENCE = 0.7f
        private const val CAMERA_PREVIEW_WIDTH = 1080
        private const val CAMERA_PREVIEW_HEIGHT = 1920
        private const val MULTIPLE_OBJECTS_AT_ONCE = true
        private const val CLASSIFICATION_ENABLED = true
    }
}
