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
import com.example.bioniclens.R
import com.example.bioniclens.RecognizedTextBlockGraphic
import com.example.bioniclens.RecognizedTextGraphic
import com.example.bioniclens.TranslatedTextGraphic
import com.example.bioniclens.facedetection.FaceDetectionActivity
import com.example.bioniclens.objectrecognition.ObjRecognitionActivity
import com.example.bioniclens.selfie_segmentation.SelfieSegmentationActivity
import com.example.bioniclens.utils.GraphicOverlay
import com.example.bioniclens.utils.InferenceInfoGraphic
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.camera.CameraSourceConfig
import com.google.mlkit.vision.camera.CameraXSource
import com.google.mlkit.vision.camera.DetectionTaskCallback
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class TextRecognitionActivity : AppCompatActivity(){
    enum class ChosenLanguage {
        LATIN, CHINESE, JAPANESE, KOREAN, DEVANAGARI
    }

    enum class TranslationLanguage {
        OFF, ENGLISH, ROMANIAN
    }

    private var previewView: PreviewView? = null
    private var cameraSource: CameraXSource? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var lensFacing: Int = CameraSourceConfig.CAMERA_FACING_BACK
    private var targetResolution: Size? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var textRecTaskCallback: DetectionTaskCallback<Text>? = null
    private var textRecognizer: TextRecognizer? = null
    private var isImageFlipped: Boolean = false
    private var chosenLanguage = ChosenLanguage.LATIN
    private var translationLanguage = TranslationLanguage.OFF
    private val optionsBuilder = TranslatorOptions.Builder()
    private var numberOfTextBlocksTranslated = AtomicInteger(0)
    private var totalBlocks = 0
    private var translationInProgress = false
    private var waitFrames = 0

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
            switchCameraFacing()
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

        // Use-case buttons START
        val objectRecognition: Button = findViewById<Button>(R.id.obj_recognition)
        val selfieSegmentation: Button = findViewById<Button>(R.id.selfie_segmentation)
        val faceDetection: Button = findViewById<Button>(R.id.face_detection)
        val latinOnlyButton: Button = findViewById<Button>(R.id.latin_only)
        val chineseAndLatinButton = findViewById<Button>(R.id.chinese_and_latin)
        val japaneseAndLatinButton = findViewById<Button>(R.id.japanese_and_latin)
        val koreanAndLatinButton = findViewById<Button>(R.id.korean_and_latin)
        val devanagariAndLatinButton = findViewById<Button>(R.id.devanagari_and_latin)
        val translateToEnglishButton = findViewById<Button>(R.id.translate_to_english)
        val translateToRomanianButton = findViewById<Button>(R.id.translate_to_romanian)
        val settingsButton = findViewById<Button>(R.id.settingsButton)
        objectRecognition.setVisibility(View.INVISIBLE)
        selfieSegmentation.setVisibility(View.INVISIBLE)
        faceDetection.setVisibility(View.INVISIBLE)
        latinOnlyButton.setVisibility(View.INVISIBLE)
        chineseAndLatinButton.setVisibility(View.INVISIBLE)
        japaneseAndLatinButton.setVisibility(View.INVISIBLE)
        koreanAndLatinButton.setVisibility(View.INVISIBLE)
        devanagariAndLatinButton.setVisibility(View.INVISIBLE)
        translateToEnglishButton.setVisibility(View.INVISIBLE)
        translateToRomanianButton.setVisibility(View.INVISIBLE)

        val netButton: ImageButton = findViewById(R.id.netButton)
        netButton.setOnClickListener {
            if(objectRecognition.isVisible){
                objectRecognition.setVisibility(View.INVISIBLE)
                selfieSegmentation.setVisibility(View.INVISIBLE)
                faceDetection.setVisibility(View.INVISIBLE)
            }
            else{
                objectRecognition.setVisibility(View.VISIBLE)
                selfieSegmentation.setVisibility(View.VISIBLE)
                faceDetection.setVisibility(View.VISIBLE)
            }

            latinOnlyButton.setVisibility(View.INVISIBLE)
            chineseAndLatinButton.setVisibility(View.INVISIBLE)
            japaneseAndLatinButton.setVisibility(View.INVISIBLE)
            koreanAndLatinButton.setVisibility(View.INVISIBLE)
            devanagariAndLatinButton.setVisibility(View.INVISIBLE)
            translateToEnglishButton.setVisibility(View.INVISIBLE)
            translateToRomanianButton.setVisibility(View.INVISIBLE)
        }

        settingsButton.setOnClickListener {
            if (latinOnlyButton.isVisible)
            {
                latinOnlyButton.setVisibility(View.INVISIBLE)
                chineseAndLatinButton.setVisibility(View.INVISIBLE)
                japaneseAndLatinButton.setVisibility(View.INVISIBLE)
                koreanAndLatinButton.setVisibility(View.INVISIBLE)
                devanagariAndLatinButton.setVisibility(View.INVISIBLE)
                translateToEnglishButton.setVisibility(View.INVISIBLE)
                translateToRomanianButton.setVisibility(View.INVISIBLE)
            }
            else
            {
                latinOnlyButton.setVisibility(View.VISIBLE)
                chineseAndLatinButton.setVisibility(View.VISIBLE)
                japaneseAndLatinButton.setVisibility(View.VISIBLE)
                koreanAndLatinButton.setVisibility(View.VISIBLE)
                devanagariAndLatinButton.setVisibility(View.VISIBLE)
                translateToEnglishButton.setVisibility(View.VISIBLE)
                translateToRomanianButton.setVisibility(View.VISIBLE)
            }

            objectRecognition.setVisibility(View.INVISIBLE)
            selfieSegmentation.setVisibility(View.INVISIBLE)
            faceDetection.setVisibility(View.INVISIBLE)
        }

        objectRecognition.setOnClickListener {
            val intent = Intent(this, ObjRecognitionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(objectRecognition, faceDetection, selfieSegmentation)
        }

        selfieSegmentation.setOnClickListener {
            val intent = Intent(this, SelfieSegmentationActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(objectRecognition, faceDetection, selfieSegmentation)
        }

        faceDetection.setOnClickListener {
            val intent = Intent(this, FaceDetectionActivity::class.java)
            startActivity(intent)
            makeButtonsInvisible(objectRecognition, faceDetection, selfieSegmentation)
        }

        latinOnlyButton.setOnClickListener {
            chosenLanguage = ChosenLanguage.LATIN

            latinOnlyButton.setVisibility(View.INVISIBLE)
            chineseAndLatinButton.setVisibility(View.INVISIBLE)
            japaneseAndLatinButton.setVisibility(View.INVISIBLE)
            koreanAndLatinButton.setVisibility(View.INVISIBLE)
            devanagariAndLatinButton.setVisibility(View.INVISIBLE)
            translateToEnglishButton.setVisibility(View.INVISIBLE)
            translateToRomanianButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        chineseAndLatinButton.setOnClickListener {
            chosenLanguage = ChosenLanguage.CHINESE

            latinOnlyButton.setVisibility(View.INVISIBLE)
            chineseAndLatinButton.setVisibility(View.INVISIBLE)
            japaneseAndLatinButton.setVisibility(View.INVISIBLE)
            koreanAndLatinButton.setVisibility(View.INVISIBLE)
            devanagariAndLatinButton.setVisibility(View.INVISIBLE)
            translateToEnglishButton.setVisibility(View.INVISIBLE)
            translateToRomanianButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        japaneseAndLatinButton.setOnClickListener {
            chosenLanguage = ChosenLanguage.JAPANESE

            latinOnlyButton.setVisibility(View.INVISIBLE)
            chineseAndLatinButton.setVisibility(View.INVISIBLE)
            japaneseAndLatinButton.setVisibility(View.INVISIBLE)
            koreanAndLatinButton.setVisibility(View.INVISIBLE)
            devanagariAndLatinButton.setVisibility(View.INVISIBLE)
            translateToEnglishButton.setVisibility(View.INVISIBLE)
            translateToRomanianButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        koreanAndLatinButton.setOnClickListener {
            chosenLanguage = ChosenLanguage.KOREAN

            latinOnlyButton.setVisibility(View.INVISIBLE)
            chineseAndLatinButton.setVisibility(View.INVISIBLE)
            japaneseAndLatinButton.setVisibility(View.INVISIBLE)
            koreanAndLatinButton.setVisibility(View.INVISIBLE)
            devanagariAndLatinButton.setVisibility(View.INVISIBLE)
            translateToEnglishButton.setVisibility(View.INVISIBLE)
            translateToRomanianButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        devanagariAndLatinButton.setOnClickListener {
            chosenLanguage = ChosenLanguage.DEVANAGARI

            latinOnlyButton.setVisibility(View.INVISIBLE)
            chineseAndLatinButton.setVisibility(View.INVISIBLE)
            japaneseAndLatinButton.setVisibility(View.INVISIBLE)
            koreanAndLatinButton.setVisibility(View.INVISIBLE)
            devanagariAndLatinButton.setVisibility(View.INVISIBLE)
            translateToEnglishButton.setVisibility(View.INVISIBLE)
            translateToRomanianButton.setVisibility(View.INVISIBLE)

            switchCameraFacing()
            startSwitchCamera()
        }

        translateToEnglishButton.setOnClickListener {
            latinOnlyButton.setVisibility(View.INVISIBLE)
            chineseAndLatinButton.setVisibility(View.INVISIBLE)
            japaneseAndLatinButton.setVisibility(View.INVISIBLE)
            koreanAndLatinButton.setVisibility(View.INVISIBLE)
            devanagariAndLatinButton.setVisibility(View.INVISIBLE)
            translateToEnglishButton.setVisibility(View.INVISIBLE)
            translateToRomanianButton.setVisibility(View.INVISIBLE)

            if (translationLanguage == TranslationLanguage.ENGLISH)
            {
                translationLanguage = TranslationLanguage.OFF
                translateToEnglishButton.text = "Translate to English"
            }
            else
            {
                translationLanguage = TranslationLanguage.ENGLISH
                translateToEnglishButton.text = "Turn OFF Translation"
                translateToRomanianButton.text = "Translate to Romanian"
            }
            translationInProgress = false
            waitFrames = 0
        }

        translateToRomanianButton.setOnClickListener {
            latinOnlyButton.setVisibility(View.INVISIBLE)
            chineseAndLatinButton.setVisibility(View.INVISIBLE)
            japaneseAndLatinButton.setVisibility(View.INVISIBLE)
            koreanAndLatinButton.setVisibility(View.INVISIBLE)
            devanagariAndLatinButton.setVisibility(View.INVISIBLE)
            translateToEnglishButton.setVisibility(View.INVISIBLE)
            translateToRomanianButton.setVisibility(View.INVISIBLE)

            if (translationLanguage == TranslationLanguage.ROMANIAN)
            {
                translationLanguage = TranslationLanguage.OFF
                translateToRomanianButton.text = "Translate to Romanian"
            }
            else
            {
                translationLanguage = TranslationLanguage.ROMANIAN
                translateToEnglishButton.text = "Translate to English"
                translateToRomanianButton.text = "Turn OFF Translation"
            }

            translationInProgress = false
            waitFrames = 0
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

    private fun checkNumberOfTranslatedBlocks()
    {
        numberOfTextBlocksTranslated.incrementAndGet()
        if (numberOfTextBlocksTranslated.get() == totalBlocks)
        {
            graphicOverlay!!.postInvalidate()
            translationInProgress = false
            waitFrames = WAIT_FRAMES_AFTER_TRANSLATION_TO_CLEAR
        }
    }

    private fun translateTextBlock(textBlock: Text.TextBlock)
    {
        val translateToRomanian = (translationLanguage == TranslationLanguage.ROMANIAN)

        val languageIdentifier = LanguageIdentification.getClient()

        languageIdentifier.identifyLanguage(textBlock.text)
            .addOnSuccessListener { languageCode ->
                if (languageCode == "und")
                {
                    Log.i(TAG, "Can't identify language.")
                    graphicOverlay!!.add(RecognizedTextBlockGraphic(graphicOverlay!!, textBlock))
                    checkNumberOfTranslatedBlocks()
                }
                else
                {
                    val locale = Locale(languageCode)
                    val language = locale.displayLanguage.capitalize()
                    Log.i(TAG, "Language: $languageCode ($language)")

                    val sourceLanguage = TranslateLanguage.fromLanguageTag(languageCode)

                    if (sourceLanguage == null)
                    {
                        graphicOverlay!!.add(RecognizedTextBlockGraphic(graphicOverlay!!, textBlock))
                        checkNumberOfTranslatedBlocks()
                    }
                    else if ((sourceLanguage == "ro" && translateToRomanian) || (sourceLanguage == "en" && !translateToRomanian))
                    {
                        graphicOverlay!!.add(RecognizedTextBlockGraphic(graphicOverlay!!, textBlock))
                        checkNumberOfTranslatedBlocks()
                    }
                    else {
                        optionsBuilder.setSourceLanguage(sourceLanguage)

                        if (translateToRomanian) {
                            optionsBuilder.setTargetLanguage(TranslateLanguage.ROMANIAN)
                        } else {
                            optionsBuilder.setTargetLanguage(TranslateLanguage.ENGLISH)
                        }

                        val options = optionsBuilder.build()

                        val translator = Translation.getClient(options)

                        var conditions = DownloadConditions.Builder()
                            .build()

                        translator.downloadModelIfNeeded(conditions)
                            .addOnSuccessListener {
                                translator.translate(textBlock.text)
                                    .addOnSuccessListener { translatedText ->
                                        val charLimit = 35
                                        var lastSplitIdx = 0
                                        var lineList = mutableListOf<String>()
                                        for (i in translatedText.indices)
                                        {
                                            if (translatedText[i] == ' ')
                                            {
                                                if (i >= lastSplitIdx + charLimit)
                                                {
                                                    lineList.add(translatedText.subSequence(lastSplitIdx, i) as String)
                                                    lastSplitIdx = i
                                                }
                                            }
                                        }

                                        lineList.add(translatedText.subSequence(lastSplitIdx, translatedText.length) as String)

                                        Log.d(TAG, "Translated text successfully!")
                                        graphicOverlay!!.add(TranslatedTextGraphic(graphicOverlay!!, textBlock, lineList))
                                        checkNumberOfTranslatedBlocks()
                                    }
                                    .addOnFailureListener { exception ->
                                        exception.message?.let { Log.e(TAG, it) }
                                        graphicOverlay!!.add(RecognizedTextBlockGraphic(graphicOverlay!!, textBlock))
                                        checkNumberOfTranslatedBlocks()
                                    }
                            }
                            .addOnFailureListener { exception ->
                                exception.message?.let { Log.e(TAG, it) }
                                graphicOverlay!!.add(RecognizedTextBlockGraphic(graphicOverlay!!, textBlock))
                                checkNumberOfTranslatedBlocks()
                            }
                    }
                }
            }
            .addOnFailureListener {
                Log.d(TAG, "Language identification failed!")
                graphicOverlay!!.add(RecognizedTextBlockGraphic(graphicOverlay!!, textBlock))
                checkNumberOfTranslatedBlocks()
            }
    }

    private fun onTextRecognitionTaskSuccess(result: Text) {
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

        Log.v(TAG, "Text recognition successful!")
        // Draw the NN info

        if (translationLanguage != TranslationLanguage.OFF)
        {
            Log.d(TAG, "Translation in progress: " + translationInProgress)
            Log.d(TAG, "Total Blocks: $totalBlocks")
            Log.d(TAG, "waitFrames: $waitFrames")
            Log.d(TAG, "Number of translated blocks: $numberOfTextBlocksTranslated")
            if (waitFrames > 0) waitFrames--
            else {
                if (!translationInProgress)
                {
                    graphicOverlay!!.clear()
                }
                if (!translationInProgress && result.textBlocks.size > 0) {
                    totalBlocks = result.textBlocks.size
                    translationInProgress = true
                    numberOfTextBlocksTranslated.set(0)
                    for (i in result.textBlocks.indices) {
                        translateTextBlock(result.textBlocks[i])
                    }
                }
            }
        }
        else
        {
            graphicOverlay!!.clear()
            graphicOverlay!!.add(RecognizedTextGraphic(graphicOverlay!!, result, true))
            graphicOverlay!!.postInvalidate()
        }
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

        if (chosenLanguage == ChosenLanguage.LATIN)
        {
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        else if (chosenLanguage == ChosenLanguage.CHINESE)
        {
            textRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }
        else if (chosenLanguage == ChosenLanguage.JAPANESE)
        {
            textRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        }
        else if (chosenLanguage == ChosenLanguage.KOREAN)
        {
            textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
        else if (chosenLanguage == ChosenLanguage.DEVANAGARI)
        {
            textRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }

        val builder: CameraSourceConfig.Builder = CameraSourceConfig.Builder(
                getApplicationContext(), textRecognizer!!, textRecTaskCallback
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
        private const val TAG = "TextRecognitionUnit"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val TEXT_RECOGNIZER_OPTIONS = TextRecognizerOptions.DEFAULT_OPTIONS
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val CAMERA_PREVIEW_WIDTH = 1080
        private const val CAMERA_PREVIEW_HEIGHT = 1920
        private const val WAIT_FRAMES_AFTER_TRANSLATION_TO_CLEAR = 5 // adjust this to change the time the translated text remains on-screen
    }
}
