package com.example.bioniclens


import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cameraButton = findViewById<Button>(R.id.cameraButton)

        cameraButton.setOnClickListener{
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }
}