package com.example.bioniclens

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.bioniclens.intro.IntroActivity
import com.example.bioniclens.objectrecognition.ObjRecognitionActivity

import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cameraButton = findViewById<Button>(R.id.cameraButton)
        cameraButton.setOnClickListener{
            val intent = Intent(this, ObjRecognitionActivity::class.java)
            startActivity(intent)
        }

        val intro = findViewById<Button>(R.id.introButton)
        intro.setOnClickListener{
            val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
            val editor = pref.edit()
            editor.putBoolean("isIntroOpnend", false)
            editor.commit()
            val intent = Intent(this, IntroActivity::class.java)
            startActivity(intent)
        }
    }
}