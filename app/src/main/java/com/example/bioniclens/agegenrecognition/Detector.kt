package com.example.bioniclens.agegenrecognition

import android.graphics.Bitmap
import android.graphics.RectF

interface Detector {
    fun recognizeImage(bitmap: Bitmap?): String?
    fun enableStatLogging(debug: Boolean)
    val statString: String?

    fun close()
    fun setNumThreads(numThreads: Int)
    fun setUseNNAPI(isChecked: Boolean)

    class Recognition(
            val id: String?,
            val title: String?,
            val confidence: Float?,
            private var location: RectF?) {

        fun getLocation(): RectF {
            return RectF(location)
        }

        fun setLocation(location: RectF?) {
            this.location = location
        }

        override fun toString(): String {
            var resultString = ""
            if (id != null) {
                resultString += "[$id] "
            }
            if (title != null) {
                resultString += "$title "
            }
            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f)
            }
            if (location != null) {
                resultString += location.toString() + " "
            }
            return resultString.trim { it <= ' ' }
        }
    }
}