package com.lexipopup.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class ParallaxOffset(val x: Float, val y: Float)

/**
 * Provides parallax tilt data from the accelerometer.
 * Max tilt is capped at ±1 degree visual shift for a subtle premium feel.
 */
object SensorHelper {

    private const val MAX_PARALLAX_DP = 6f  // max pixel shift in dp
    private const val GRAVITY = 9.81f
    private const val ALPHA = 0.1f           // low-pass filter coefficient

    fun parallaxFlow(context: Context): Flow<ParallaxOffset> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: run { close(); return@callbackFlow }

        var smoothX = 0f
        var smoothY = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                // Low-pass filter to reduce noise
                smoothX = ALPHA * event.values[0] + (1 - ALPHA) * smoothX
                smoothY = ALPHA * event.values[1] + (1 - ALPHA) * smoothY

                // Normalize to [-1, 1] based on gravity
                val normX = (smoothX / GRAVITY).coerceIn(-1f, 1f)
                val normY = (smoothY / GRAVITY).coerceIn(-1f, 1f)

                // Scale to max parallax offset
                trySend(ParallaxOffset(normX * MAX_PARALLAX_DP, normY * MAX_PARALLAX_DP))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
