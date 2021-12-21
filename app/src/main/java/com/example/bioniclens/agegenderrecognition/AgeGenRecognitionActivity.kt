package com.example.bioniclens.agegenderrecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.bioniclens.R
import com.example.bioniclens.objectrecognition.ObjRecognitionActivity
import com.example.bioniclens.facedetection.FaceDetectionActivity
import com.example.bioniclens.textrecognition.TextRecognitionActivity
import kotlinx.android.synthetic.main.activity_obj_recognition.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AgeGenRecognitionActivity : AppCompatActivity() {
}