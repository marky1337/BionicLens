package com.example.bioniclens.selfie_segmentation

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
import com.example.bioniclens.agegenderrecognition.AgeGenRecognitionActivity
import com.example.bioniclens.facedetection.FaceDetectionActivity
import com.example.bioniclens.objectrecognition.ObjRecognitionActivity
import com.example.bioniclens.textrecognition.TextRecognitionActivity
import com.example.bioniclens.utils.GraphicOverlay
import com.example.bioniclens.utils.InferenceInfoGraphic
import com.google.mlkit.vision.camera.CameraSourceConfig
import com.google.mlkit.vision.camera.CameraXSource
import com.google.mlkit.vision.camera.DetectionTaskCallback
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.Segmentation

class SelfieSegmentationActivity : AppCompatActivity(){

    private var previewView: PreviewView? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var isImageFlipped: Boolean = false
    private var segmentationTaskCallback: DetectionTaskCallback<SegmentationMask>? = null
    private var lensFacing: Int = CameraSourceConfig.CAMERA_FACING_BACK
    private var cameraSource: CameraXSource? = null
    private var selfieSegmentationOptions: SelfieSegmenterOptions? = null
    private var selfieSegmentationOptionsBuilder:SelfieSegmenterOptions.Builder? = null
    private var segmenter : Segmenter? = null
    private var targetResolution: Size? = null


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
        setContentView(R.layout.activity_segmentation)

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
        segmentationTaskCallback = DetectionTaskCallback<SegmentationMask> { segmentationTask ->
            segmentationTask
                    .addOnSuccessListener { result -> onSegmentationTaskSuccess(result) }
                    .addOnFailureListener { e -> onSegmentationTaskFailure(e) }
        }

        selfieSegmentationOptionsBuilder = SelfieSegmenterOptions.Builder()

        if(isStreamMode){
            selfieSegmentationOptionsBuilder!!.setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        }
        else{
            selfieSegmentationOptionsBuilder!!.setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        }
        selfieSegmentationOptions = selfieSegmentationOptionsBuilder!!.build()

        // Use-case buttons START
        val objectRecognition: Button = findViewById<Button>(R.id.obj_recognition)
        val faceDetection: Button = findViewById<Button>(R.id.face_detection)
        val textRecognition: Button = findViewById<Button>(R.id.text_recognition)
        objectRecognition.setVisibility(View.INVISIBLE)
        faceDetection.setVisibility(View.INVISIBLE)
        textRecognition.setVisibility(View.INVISIBLE)

        val netButton: ImageButton = findViewById(R.id.netButton)
        netButton.setOnClickListener {
            if(objectRecognition.isVisible){
                objectRecognition.setVisibility(View.INVISIBLE)
                faceDetection.setVisibility(View.INVISIBLE)
                textRecognition.setVisibility(View.INVISIBLE)
            }
            else{
                objectRecognition.setVisibility(View.VISIBLE)
                faceDetection.setVisibility(View.VISIBLE)
                textRecognition.setVisibility(View.VISIBLE)
            }
        }

        objectRecognition.setOnClickListener {
            val intent = Intent(this, ObjRecognitionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, faceDetection, objectRecognition)
        }

        faceDetection.setOnClickListener {
            val intent = Intent(this, FaceDetectionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, faceDetection, objectRecognition)
        }

        textRecognition.setOnClickListener {
            val intent = Intent(this,TextRecognitionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(textRecognition, faceDetection, objectRecognition)
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

    private fun startSwitchCamera() {
        if (cameraSource != null) {
            cameraSource!!.close()
        }
        segmenter = Segmentation.getClient(selfieSegmentationOptions!!)


        val builder: CameraSourceConfig.Builder = CameraSourceConfig.Builder(
                getApplicationContext(), segmenter!!, segmentationTaskCallback!!
        ).setFacing(lensFacing)

        // Change camera from one switch to another
        switchCameraFacing()

        targetResolution = Size(previewView!!.layoutParams.width, previewView!!.layoutParams.height)
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

    private fun onSegmentationTaskSuccess(mask:SegmentationMask) {
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

        Log.v(SelfieSegmentationActivity.TAG, "Face detection successful!")
        // Draw the NN info

        graphicOverlay!!.add(SelfieSegmentationGraphic(graphicOverlay!!, mask))
        graphicOverlay!!.add(InferenceInfoGraphic(graphicOverlay!!))
        graphicOverlay!!.postInvalidate()
    }

    private fun onSegmentationTaskFailure(e: Exception) {
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
        private const val TAG = "SelfieSegmentationUnit"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val CAMERA_PREVIEW_WIDTH = 1080
        private const val CAMERA_PREVIEW_HEIGHT = 1920
        private const val isStreamMode = true
    }
}