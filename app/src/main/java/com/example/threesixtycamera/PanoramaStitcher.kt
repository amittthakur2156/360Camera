package com.example.threesixtycamera

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Builds a 2:1 cylindrical/equirectangular-style panorama from frames
 * captured at known relative yaw angles.
 */
data class PanoFrame(
    val bitmap: Bitmap,
    val angleDeg: Float
)

object PanoramaStitcher {

    private const val OUTPUT_WIDTH = 2048
    private const val OUTPUT_HEIGHT = 1024
    private const val CAMERA_FOV_DEG = 70.0
    private const val FEATHER_DEG = 8.0

    class StitchException(message: String) : Exception(message)

    fun stitch(frames: List<PanoFrame>): Bitmap {
        val valid = frames
            .filter { !it.bitmap.isRecycled && it.bitmap.width > 20 && it.bitmap.height > 20 }
            .sortedBy { normalize(it.angleDeg) }

        if (valid.size < 8) {
            throw StitchException(
                "Only ${valid.size} frames captured. Rotate slowly and complete the full 360°."
            )
        }

        val sourceW = valid.minOf { it.bitmap.width }
        val sourceH = valid.minOf { it.bitmap.height }

        val f = sourceW / (2.0 * tan(Math.toRadians(CAMERA_FOV_DEG / 2.0)))
        val cx = sourceW / 2.0
        val halfFov = CAMERA_FOV_DEG / 2.0

        val top = (sourceH * 0.06).toInt().coerceIn(0, sourceH - 2)
        val bottom = (sourceH * 0.94).toInt().coerceIn(top + 1, sourceH - 1)

        // Scale each frame once to the common source size.
        val prepared = valid.map { frame ->
            val scaled = if (frame.bitmap.width == sourceW && frame.bitmap.height == sourceH) {
                frame.bitmap
            } else {
                Bitmap.createScaledBitmap(frame.bitmap, sourceW, sourceH, true)
            }
            val pixels = IntArray(sourceW * sourceH)
            scaled.getPixels(pixels, 0, sourceW, 0, 0, sourceW, sourceH)
            if (scaled !== frame.bitmap) scaled.recycle()
            frame to pixels
        }

        val output = Bitmap.createBitmap(
            OUTPUT_WIDTH,
            OUTPUT_HEIGHT,
            Bitmap.Config.ARGB_8888
        )

        val row = IntArray(OUTPUT_WIDTH)
        val sumR = DoubleArray(OUTPUT_WIDTH)
        val sumG = DoubleArray(OUTPUT_WIDTH)
        val sumB = DoubleArray(OUTPUT_WIDTH)
        val sumW = DoubleArray(OUTPUT_WIDTH)

        // Render row-by-row to keep memory low on phones.
        for (oy in 0 until OUTPUT_HEIGHT) {
            java.util.Arrays.fill(sumR, 0.0)
            java.util.Arrays.fill(sumG, 0.0)
            java.util.Arrays.fill(sumB, 0.0)
            java.util.Arrays.fill(sumW, 0.0)

            val v = oy.toDouble() / (OUTPUT_HEIGHT - 1)
            val sourceY = top + v * (bottom - top - 1)
            val sy = sourceY.toInt().coerceIn(top, bottom - 2)
            val fy = sourceY - sy

            for (ox in 0 until OUTPUT_WIDTH) {
                val worldAngle = ox.toDouble() / OUTPUT_WIDTH * 360.0

                for ((frame, pixels) in prepared) {
                    val delta = shortestDelta(
                        worldAngle,
                        normalize(frame.angleDeg).toDouble()
                    )

                    if (abs(delta) > halfFov) continue

                    val sourceX = cx + f * tan(Math.toRadians(delta))
                    if (sourceX < 0.0 || sourceX >= sourceW - 1.0) continue

                    val sx = sourceX.toInt().coerceIn(0, sourceW - 2)
                    val fx = sourceX - sx

                    val edge = if (abs(delta) > halfFov - FEATHER_DEG) {
                        (halfFov - abs(delta)) / FEATHER_DEG
                    } else {
                        1.0
                    }.coerceIn(0.0, 1.0)

                    val p00 = pixels[sy * sourceW + sx]
                    val p10 = pixels[sy * sourceW + sx + 1]
                    val p01 = pixels[(sy + 1) * sourceW + sx]
                    val p11 = pixels[(sy + 1) * sourceW + sx + 1]

                    val rTop = Color.red(p00) * (1.0 - fx) + Color.red(p10) * fx
                    val gTop = Color.green(p00) * (1.0 - fx) + Color.green(p10) * fx
                    val bTop = Color.blue(p00) * (1.0 - fx) + Color.blue(p10) * fx

                    val rBottom = Color.red(p01) * (1.0 - fx) + Color.red(p11) * fx
                    val gBottom = Color.green(p01) * (1.0 - fx) + Color.green(p11) * fx
                    val bBottom = Color.blue(p01) * (1.0 - fx) + Color.blue(p11) * fx

                    sumR[ox] += (rTop * (1.0 - fy) + rBottom * fy) * edge
                    sumG[ox] += (gTop * (1.0 - fy) + gBottom * fy) * edge
                    sumB[ox] += (bTop * (1.0 - fy) + bBottom * fy) * edge
                    sumW[ox] += edge
                }
            }

            for (x in 0 until OUTPUT_WIDTH) {
                if (sumW[x] > 0.001) {
                    row[x] = Color.rgb(
                        (sumR[x] / sumW[x]).toInt().coerceIn(0, 255),
                        (sumG[x] / sumW[x]).toInt().coerceIn(0, 255),
                        (sumB[x] / sumW[x]).toInt().coerceIn(0, 255)
                    )
                } else {
                    row[x] = Color.BLACK
                }
            }

            output.setPixels(row, 0, OUTPUT_WIDTH, 0, oy, OUTPUT_WIDTH, 1)
        }

        return output
    }

    private fun shortestDelta(a: Double, b: Double): Double {
        var d = a - b
        while (d > 180.0) d -= 360.0
        while (d < -180.0) d += 360.0
        return d
    }

    private fun normalize(angle: Float): Float {
        var a = angle % 360f
        if (a < 0f) a += 360f
        return a
    }
}
