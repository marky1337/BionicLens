package com.example.bioniclens.selfie_segmentation

import android.graphics.*
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.example.bioniclens.utils.GraphicOverlay
import com.google.mlkit.vision.segmentation.SegmentationMask
import java.nio.ByteBuffer

/** Draw the mask from SegmentationResult in preview.  */
class SelfieSegmentationGraphic(overlay: GraphicOverlay, segmentationMask: SegmentationMask, private val useBackgroundImg: Boolean,
                                private val backgroundImgIdx: Int) :
        GraphicOverlay.Graphic(overlay) {
    private val mask: ByteBuffer
    private val maskWidth: Int
    private val maskHeight: Int
    private val isRawSizeMaskEnabled: Boolean
    private val scaleX: Float
    private val scaleY: Float
    private var backgroundBitmap: Bitmap? = null
    private var lastBackground: Int? = -1
    /** Draws the segmented background on the supplied canvas.  */
    override fun draw(canvas: Canvas) {
        val bitmap: Bitmap

        if (lastBackground != backgroundImgIdx) {
            if (backgroundBitmap != null) {
                backgroundBitmap!!.recycle()
                backgroundBitmap = null
            }
        }

        if (!useBackgroundImg) {
            bitmap = Bitmap.createBitmap(
                    maskColorsFromByteBuffer(mask), maskWidth, maskHeight, Bitmap.Config.ARGB_8888
            )
        }
        else
        {
            if (lastBackground != backgroundImgIdx) {
                val inputImgStream = applicationContext.assets.open("segmentationBackground$backgroundImgIdx.jpg")
                backgroundBitmap = BitmapFactory.decodeStream(inputImgStream, null, null)!!
                backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap!!, maskWidth, maskHeight, true)
                inputImgStream.close()
            }

            bitmap = processBackground(backgroundBitmap!!, mask)
        }
        if (isRawSizeMaskEnabled) {
            val matrix = Matrix(getTransformationMatrix())
            matrix.preScale(scaleX, scaleY)
            canvas.drawBitmap(bitmap, matrix, null)
        } else {
            canvas.drawBitmap(bitmap, getTransformationMatrix(), null)
        }
        bitmap.recycle()
        // Reset byteBuffer pointer to beginning, so that the mask can be redrawn if screen is refreshed
        mask.rewind()
    }

    /** Sets the background transparency. */
    private fun processBackground(bitmap: Bitmap, maskBuffer: ByteBuffer): Bitmap
    {
        val bitmapPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(bitmapPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in 0 until maskWidth * maskHeight) {
            val backgroundLikelihood = 1 - maskBuffer.float
            if (backgroundLikelihood <= 0.2)
            {
                bitmapPixels[i] = ColorUtils.setAlphaComponent(bitmapPixels[i], 0)
            }
            else if (backgroundLikelihood <= 0.9)
            {
                val alpha = (182.9 * backgroundLikelihood - 36.6 + 0.5).toInt()
                bitmapPixels[i] = ColorUtils.setAlphaComponent(bitmapPixels[i], alpha)
            }
        }

        return Bitmap.createBitmap(bitmapPixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
    }
    /** Converts byteBuffer floats to ColorInt array that can be used as a mask.  */
    @ColorInt
    private fun maskColorsFromByteBuffer(byteBuffer: ByteBuffer): IntArray {
        @ColorInt val colors =
                IntArray(maskWidth * maskHeight)
        for (i in 0 until maskWidth * maskHeight) {
            val backgroundLikelihood = 1 - byteBuffer.float
            if (backgroundLikelihood > 0.9) {
                colors[i] = Color.argb(128, 255, 0, 255)
            } else if (backgroundLikelihood > 0.2) {
                // Linear interpolation to make sure when backgroundLikelihood is 0.2, the alpha is 0 and
                // when backgroundLikelihood is 0.9, the alpha is 128.
                // +0.5 to round the float value to the nearest int.
                val alpha = (182.9 * backgroundLikelihood - 36.6 + 0.5).toInt()
                colors[i] = Color.argb(alpha, 255, 0, 255)
            }
        }
        return colors
    }

    init {
        mask = segmentationMask.buffer
        maskWidth = segmentationMask.width
        maskHeight = segmentationMask.height
        isRawSizeMaskEnabled =
                maskWidth != overlay.getImageWidth() || maskHeight != overlay.getImageHeight()
        scaleX = overlay.getImageWidth() * 1f / maskWidth
        scaleY = overlay.getImageHeight() * 1f / maskHeight
    }
}