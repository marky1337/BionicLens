package com.example.bioniclens.textrecognition

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
import com.example.bioniclens.RecognizedTextGraphic
import com.example.bioniclens.objectrecognition.ObjRecognitionActivity
import com.example.bioniclens.utils.GraphicOverlay
import com.example.bioniclens.utils.InferenceInfoGraphic
import com.google.mlkit.vision.camera.CameraSourceConfig
import com.google.mlkit.vision.camera.CameraXSource
import com.google.mlkit.vision.camera.DetectionTaskCallback
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class TextRecognitionActivity : AppCompatActivity(){
    private var previewView: PreviewView? = null
    private var cameraSource: CameraXSource? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var lensFacing: Int = CameraSourceConfig.CAMERA_FACING_BACK
    private var targetResolution: Size? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var textRecTaskCallback: DetectionTaskCallback<Text>? = null
    private var textRecognizer: TextRecognizer? = null

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
        setContentView(R.layout.activity_text_recognition)

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
        textRecTaskCallback = DetectionTaskCallback<Text> { textRecTask ->
            textRecTask
                    .addOnSuccessListener { result -> onTextRecognitionTaskSuccess(result) }
                    .addOnFailureListener { e -> onTextRecognitionTaskFailure(e) }
        }

        val objectRecognition: Button = findViewById<Button>(R.id.obj_recognition)
        val ageRecognition: Button = findViewById<Button>(R.id.age_recognition)
        objectRecognition.setVisibility(View.INVISIBLE)
        ageRecognition.setVisibility(View.INVISIBLE)

        val netButton: ImageButton = findViewById(R.id.netButton)
        netButton.setOnClickListener {
            if(objectRecognition.isVisible){
                objectRecognition.setVisibility(View.INVISIBLE)
                ageRecognition.setVisibility(View.INVISIBLE)
            }
            else{
                objectRecognition.setVisibility(View.VISIBLE)
                ageRecognition.setVisibility(View.VISIBLE)
            }
        }

        objectRecognition.setOnClickListener {
            val intent = Intent(this, ObjRecognitionActivity::class.java)
            startActivity(intent)
        }
        ageRecognition.setOnClickListener {
            val intent = Intent(this, AgeGenRecognitionActivity::class.java)
            startActivity(intent)
        }

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

    private fun onTextRecognitionTaskSuccess(result: Text) {
        graphicOverlay!!.clear()
        if (needUpdateGraphicOverlayImageSourceInfo) {
            val size: Size = cameraSource!!.getPreviewSize()!!
            if (size != null) {
                Log.d(TAG, "preview width: " + size.width)
                Log.d(TAG, "preview height: " + size.height)
                val isImageFlipped =
                        cameraSource!!.getCameraFacing() == CameraSourceConfig.CAMERA_FACING_FRONT
                if (isPortraitMode) {
                    // Swap width and height sizes when in portrait, since it will be rotated by
                    // 90 degrees. The camera preview and the image being processed have the same size.
                    graphicOverlay!!.setImageSourceInfo(size.height, size.width, isImageFlipped)
                } else {
                    graphicOverlay!!.setImageSourceInfo(size.width, size.height, isImageFlipped)
                }
                needUpdateGraphicOverlayImageSourceInfo = false
            } else {
                Log.d(TAG, "previewsize is null")
            }
        }

        Log.v(TAG, "Text recognition successful!")
        // Draw the NN info
        graphicOverlay!!.add(RecognizedTextGraphic(graphicOverlay!!, result, true))
        graphicOverlay!!.add(InferenceInfoGraphic(graphicOverlay!!))
        graphicOverlay!!.postInvalidate()
    }

    private fun onTextRecognitionTaskFailure(e: Exception) {
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

        textRecognizer = TextRecognition.getClient(TEXT_RECOGNIZER_OPTIONS)

        val builder: CameraSourceConfig.Builder = CameraSourceConfig.Builder(
                getApplicationContext(), textRecognizer!!, textRecTaskCallback
        ).setFacing(lensFacing)

        // Change camera from one switch to another
        if (lensFacing == CameraSourceConfig.CAMERA_FACING_BACK)
        {
            lensFacing = CameraSourceConfig.CAMERA_FACING_FRONT
        }
        else
        {
            lensFacing = CameraSourceConfig.CAMERA_FACING_BACK
        }

        targetResolution = Size(previewView!!.layoutParams.width, previewView!!.layoutParams.height)
        if (targetResolution != null) {
            builder.setRequestedPreviewSize(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT)
        }

        cameraSource = CameraXSource(builder.build(), previewView!!)
        needUpdateGraphicOverlayImageSourceInfo = true
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

    private val isPortraitMode: Boolean
        private get() = (
                getApplicationContext().getResources().getConfiguration().orientation
                        !== Configuration.ORIENTATION_LANDSCAPE
                )

    companion object {
        private const val TAG = "TextRecognitionUnit"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val TEXT_RECOGNIZER_OPTIONS = TextRecognizerOptions.DEFAULT_OPTIONS
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val CAMERA_PREVIEW_WIDTH = 1080
        private const val CAMERA_PREVIEW_HEIGHT = 1920
    }
}
