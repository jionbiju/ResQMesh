package com.example.resqmesh.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HardwareManager(private val context: Context) : SensorEventListener {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var cameraId: String? = null
    private var isFlashOn = false

    private val _azimuth = MutableStateFlow(0f)
    val azimuth = _azimuth.asStateFlow()

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    init {
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startCompass() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopCompass() {
        sensorManager.unregisterListener(this)
    }

    // Low-pass filter constant (0.1 = heavy smoothing, 1.0 = no smoothing)
    private val alpha = 0.15f

    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = lowPass(event.values.clone(), gravity)
        }
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = lowPass(event.values.clone(), geomagnetic)
        }

        val g = gravity
        val m = geomagnetic
        if (g != null && m != null) {
            val R = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, g, m)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                val degree = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val smoothedDegree = (degree + 360) % 360

                // Only update if change is significant to avoid tiny jitters
                if (Math.abs(_azimuth.value - smoothedDegree) > 0.8f) {
                    _azimuth.value = smoothedDegree
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun toggleFlashlight() {
        cameraId?.let { id ->
            try {
                isFlashOn = !isFlashOn
                cameraManager.setTorchMode(id, isFlashOn)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var isWhistlePlaying = false
    private var whistleThread: Thread? = null

    fun playWhistle() {
        if (isWhistlePlaying) {
            stopWhistle()
            return
        }

        isWhistlePlaying = true
        whistleThread = Thread {
            val sampleRate = 44100
            val numSamples = sampleRate / 2 // 0.5s whistle burst
            val sample = DoubleArray(numSamples)
            val generatedSnd = ByteArray(2 * numSamples)

            // Synthesize 2800 Hz distress whistle sine wave
            val freqOfTone = 2800.0
            for (i in 0 until numSamples) {
                sample[i] = Math.sin(2.0 * Math.PI * i.toDouble() / (sampleRate / freqOfTone))
            }

            var idx = 0
            for (dVal in sample) {
                val valShort = (dVal * 32767).toInt().toShort()
                generatedSnd[idx++] = (valShort.toInt() and 0x00ff).toByte()
                generatedSnd[idx++] = (valShort.toInt() and 0xff00 ushr 8).toByte()
            }

            try {
                @Suppress("DEPRECATION")
                val minBufferSize = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )

                @Suppress("DEPRECATION")
                val audioTrack = android.media.AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize.coerceAtLeast(generatedSnd.size),
                    android.media.AudioTrack.MODE_STREAM
                )

                audioTrack.play()

                // Loop 3 short blasts pattern (Universal SOS Distress Whistle)
                while (isWhistlePlaying) {
                    for (blast in 0 until 3) {
                        if (!isWhistlePlaying) break
                        audioTrack.write(generatedSnd, 0, generatedSnd.size)
                        Thread.sleep(150)
                    }
                    if (isWhistlePlaying) {
                        Thread.sleep(600) // Pause between 3-blast groups
                    }
                }

                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        whistleThread?.start()
    }

    fun stopWhistle() {
        isWhistlePlaying = false
        whistleThread?.interrupt()
        whistleThread = null
    }

    fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}