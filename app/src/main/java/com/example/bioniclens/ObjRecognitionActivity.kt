package com.example.bioniclens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.activity_obj_recognition.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ObjRecognitionActivity : AppCompatActivity(){
    private lateinit var cameraExecutor: ExecutorService;
    private lateinit var cameraSelector: CameraSelector;
    private lateinit var preview: Preview;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_obj_recognition)

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
            Toast.makeText(this, "Already there!", Toast.LENGTH_SHORT).show()
        }
        ageRecognition.setOnClickListener {
            val intent = Intent(this, AgeGenRecognitionActivity::class.java)
            startActivity(intent)
        }


        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        val switchCameraButton = findViewById<Button>(R.id.switchCameraButton)

        switchCameraButton.setOnClickListener{
            if (allPermissionsGranted())
            {
                switchCamera()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera()
    {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
            {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            }
            else
            {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
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
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}