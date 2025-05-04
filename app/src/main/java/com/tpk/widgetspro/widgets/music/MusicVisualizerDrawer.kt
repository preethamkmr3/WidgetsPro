package com.tpk.widgetspro.widgets.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import com.tpk.widgetspro.R
import com.tpk.widgetspro.utils.CommonUtils
import kotlin.math.abs
import kotlin.math.ceil

class MusicVisualizerDrawer(private val context: Context) {

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bytes: ByteArray? = null
    private var isPaused = false
    private var isActive = false
    private var visualizerBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    internal var visualizerWidth: Int = 200
    internal var visualizerHeight: Int = 50

    private val columns = 3
    private val rows = 15
    private var dotRadius = 2f
    private var spacingHorizontal = 0f
    private var spacingVertical = 0f
    private val greyDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.transparent)
        strokeWidth = 1f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.FILL
    }

    init {
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.ROUND
        updateColor()
        calculateGridParams()
    }

    private fun calculateGridParams() {
        if (visualizerWidth > 0 && visualizerHeight > 0) {
            spacingHorizontal = if (columns > 1) visualizerWidth.toFloat() / columns else visualizerWidth.toFloat()
            spacingVertical = if (rows > 1) visualizerHeight.toFloat() / rows else visualizerHeight.toFloat()
            dotRadius = (spacingHorizontal / 3f).coerceAtLeast(1f)
            paint.strokeWidth = dotRadius * 1.5f
            greyDotPaint.strokeWidth = dotRadius * 1.5f
        }
    }

    fun updateDimensions(width: Int, height: Int) {
        if (width > 0 && height > 0 && (width != visualizerWidth || height != visualizerHeight || visualizerBitmap == null || visualizerBitmap!!.isRecycled)) {
            visualizerWidth = width
            visualizerHeight = height
            visualizerBitmap?.recycle()
            try {
                visualizerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                canvas = Canvas(visualizerBitmap!!)
                calculateGridParams()
                visualizerBitmap?.eraseColor(Color.TRANSPARENT)
            } catch (e: Exception) {
                visualizerBitmap = null
                canvas = null
            }
        } else if (visualizerBitmap == null || visualizerBitmap!!.isRecycled) {
            if (visualizerWidth > 0 && visualizerHeight > 0) {
                try {
                    visualizerBitmap = Bitmap.createBitmap(visualizerWidth, visualizerHeight, Bitmap.Config.ARGB_8888)
                    canvas = Canvas(visualizerBitmap!!)
                    calculateGridParams()
                    visualizerBitmap?.eraseColor(Color.TRANSPARENT)
                } catch (e: Exception) {
                    visualizerBitmap = null
                    canvas = null
                }
            }
        }
    }

    fun updateColor() {
        paint.color = CommonUtils.getAccentColor(context)
        greyDotPaint.color = ContextCompat.getColor(context, R.color.transparent)
    }

    fun linkToGlobalOutput() {
        setPlayerId(0)
    }

    fun setPlayerId(sessionId: Int) {
        val targetSessionId = 0

        if (visualizer != null && currentSessionId == targetSessionId) {
            return
        }

        release()
        currentSessionId = targetSessionId

        try {
            visualizer = Visualizer(targetSessionId).apply {
                enabled = false
                val range = Visualizer.getCaptureSizeRange()
                captureSize = range?.getOrNull(1)?.coerceAtLeast(1024) ?: 1024
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED

                val captureRate = Visualizer.getMaxCaptureRate() / 2
                val status = setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        viz: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform != null && isActive) {
                            val processedWaveform = ByteArray(waveform.size) { i ->
                                (waveform[i].toInt() - 128).toByte()
                            }
                            bytes = processedWaveform
                            drawVisualization()
                        }
                    }

                    override fun onFftDataCapture(
                        viz: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) { }
                }, captureRate, true, false)

                if (status != Visualizer.SUCCESS) {
                    release()
                    return@setPlayerId
                }
            }
            isPaused = true
            isActive = false
        } catch (e: Exception) {
            release()
        }
    }

    fun pause() {
        if (!isPaused) {
            isPaused = true
            isActive = false
            try {
                visualizer?.enabled = false
            } catch (e: Exception) {

            }
        }
    }

    fun resume() {
        if (isPaused) {
            isPaused = false
            isActive = true
            try {
                if (visualizer == null) {
                    linkToGlobalOutput()
                    if (visualizer == null) return
                }
                visualizer?.enabled = true
            } catch (e: Exception) {
                isActive = false
            }
        }
    }


    fun release() {
        try {
            visualizer?.apply {
                enabled = false
                setDataCaptureListener(null, 0, false, false)
                release()
            }
        } catch (e: Exception) {

        } finally {
            visualizer = null
            currentSessionId = -1
            bytes = null
            visualizerBitmap?.recycle()
            visualizerBitmap = null
            canvas = null
            isPaused = true
            isActive = false
        }
    }

    private fun drawVisualization() {
        val currentBytes = bytes ?: return
        val localBitmap = visualizerBitmap ?: return
        val localCanvas = canvas ?: return

        if (visualizerWidth <= 0 || visualizerHeight <= 0 || localBitmap.isRecycled) {
            updateDimensions(visualizerWidth, visualizerHeight)
            if (visualizerBitmap == null || visualizerBitmap!!.isRecycled) return
        }

        localBitmap.eraseColor(Color.TRANSPARENT)

        val centerLineY = visualizerHeight / 2f
        val div = currentBytes.size.toFloat() / columns

        for (i in 0 until columns) {
            val bytePosition = ceil(i * div).toInt().coerceIn(0, currentBytes.size - 1)
            val amplitude = currentBytes[bytePosition].toFloat()
            val normalizedAmplitude = (amplitude / 128.0f) * (rows / 2f)
            val dotsCount = abs(normalizedAmplitude).toInt()
            val x = i * spacingHorizontal + spacingHorizontal / 2f

            for (row in 0 until dotsCount) {
                val yUp = centerLineY - (row + 0.5f) * spacingVertical
                if (yUp >= 0 && yUp <= visualizerHeight) {
                    localCanvas.drawCircle(x, yUp, dotRadius, paint)
                }
                val yDown = centerLineY + (row + 0.5f) * spacingVertical
                if (yDown >= 0 && yDown <= visualizerHeight) {
                    localCanvas.drawCircle(x, yDown, dotRadius, paint)
                }
            }
        }
    }

    fun getVisualizerBitmap(): Bitmap? {
        if (visualizerBitmap == null || visualizerBitmap!!.isRecycled) {
            updateDimensions(visualizerWidth, visualizerHeight)
        }
        return visualizerBitmap
    }
}