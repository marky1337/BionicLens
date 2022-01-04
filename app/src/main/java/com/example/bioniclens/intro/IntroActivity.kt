package com.example.bioniclens.intro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.example.bioniclens.MainActivity
import com.example.bioniclens.R
import com.google.android.material.tabs.TabLayout


class IntroActivity : AppCompatActivity() {
  //  private var screenPager: ViewPager? = null
    var introViewPagerAdapter: IntroViewPagerAdapter? = null
    var tabIndicator: TabLayout? = null
    var btnNext: Button? = null
    var position = 0
    var btnGetStarted: Button? = null
    var btnAnim: Animation? = null
    var tvSkip: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (restorePrefData()) {
            val mainActivity = Intent(applicationContext, MainActivity::class.java)
            startActivity(mainActivity)
            finish()
        }

        setContentView(R.layout.activity_intro)

        btnNext = findViewById(R.id.btn_next)
        btnGetStarted = findViewById(R.id.btn_get_started)
        btnAnim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.button_animation)
        tvSkip = findViewById(R.id.tv_skip)

        // fill list screen
        val mList: MutableList<ScreenItem> = ArrayList<ScreenItem>()
        mList.add(
                ScreenItem(
                        "BionicLens walkthrough",
                        "",
                        R.drawable.ic_icon
                )
        )
        mList.add(
                ScreenItem(
                        "Choose activity",
                        "You have the option to choose between 4 Machine Learning use cases:\n" +
                                "     ★ Object Recognition\n" +
                                "     ★ Face Detection\n" +
                                "     ★ Text Recognition\n" +
                                "     ★ Selfie Segmentation\n",
                        R.drawable.net_default
                )
        )
        mList.add(
                ScreenItem(
                        "Switch camera",
                        "★ Switch to front/back camera",
                        R.drawable.switch_camera
                )
        )
        mList.add(
                ScreenItem(
                        "Settings",
                        "★ Customize activity application to your preferences",
                        R.drawable.settings_cog
                )
        )
        mList.add(
                ScreenItem(
                        "Object Recognition",
                        "★ Start camera, and you can do identify, live, different categories of objects.\n"+
                                "★ You can do object detection with either front and back camera.\n"+
                                "★ Application can recognize up to 80 different categories of objects.\n",
                        R.drawable.intro_obj_activity
                )
        )
        mList.add(
                ScreenItem(
                        "Face detection",
                        "  ★ Start camera, and you cand detect faces from real-time frames from your camera.\n"+
                                   "★ You can identify key facial features and contours of detected faces.\n" +
                                   "★ You can customize face detection by the settings button with different options.\n",
                        R.drawable.intro_face_activity
                )
        )
        mList.add(
                ScreenItem(
                        "Text recognition",
                        "★ Real-time text recognition in any Latin-based characters set",
                        R.drawable.ic_text_recognition
                )
        )
        mList.add(
                ScreenItem(
                        "Selfie segmentation",
                        "★ Real-time selfie segmentation.\n"+
                                   "★ Separate the background from users within a scene.\n",
                        R.drawable.intro_selfie_segmentation
                )
        )


        val screenPager = findViewById<ViewPager>(R.id.screen_viewpager)
        introViewPagerAdapter = IntroViewPagerAdapter(this, mList)
        screenPager.adapter = introViewPagerAdapter
        tabIndicator = findViewById(R.id.tab_indicator)
        tabIndicator!!.setupWithViewPager(screenPager)

        btnNext!!.setOnClickListener {
            position = screenPager.getCurrentItem()
            if (position < mList.size) {
                position++
                screenPager.setCurrentItem(position)
            }
            if (position == mList.size - 1) {
                loaddLastScreen()
            }
        }
        tabIndicator!!.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.getPosition() === mList.size - 1) {
                    loaddLastScreen()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })

        btnGetStarted!!.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            savePrefsData()
            finish()
        }

        tvSkip!!.setOnClickListener { screenPager.setCurrentItem(mList.size) }
    }

    private fun loaddLastScreen() {
        btnNext!!.visibility = View.INVISIBLE
        btnGetStarted!!.visibility = View.VISIBLE
        tvSkip!!.visibility = View.INVISIBLE
        tabIndicator!!.visibility = View.INVISIBLE
        btnGetStarted!!.animation = btnAnim
    }

    private fun savePrefsData() {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean("isIntroOpnend", true)
        editor.commit()
    }

    private fun restorePrefData(): Boolean {
        val pref = applicationContext.getSharedPreferences("myPrefs", MODE_PRIVATE)
        return pref.getBoolean("isIntroOpnend", false)
    }
}

