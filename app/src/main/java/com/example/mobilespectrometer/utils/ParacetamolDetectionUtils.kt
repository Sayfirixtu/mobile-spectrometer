package com.example.mobilespectrometer.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.BufferedReader
import kotlin.math.pow
import kotlin.math.sqrt

fun loadReferenceCSV(context: Context, fileName: String): List<Pair<Float, Float>> {
    val inputStream = context.assets.open(fileName)
    return inputStream.bufferedReader()
        .readLines()
        .mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size >= 2) {
                val wavelength = parts[0].toFloatOrNull()
                val intensity = parts[1].toFloatOrNull()
                if (wavelength != null && intensity != null) {
                    Pair(wavelength, intensity)
                } else null
            } else null
        }
}


fun extractSpectrum(bitmap: Bitmap): Pair<List<Float>, List<Float>> {
    // Replace with your actual image processing logic!
    // For now, this just returns a fake linear spectrum for testing.
    val wavelengths = (400..700 step 5).map { it.toFloat() } // 400nm to 700nm
    val intensities = wavelengths.map { (0..100).random().toFloat() } // Dummy intensities
    return Pair(wavelengths, intensities)
}


fun pearsonCorrelation(x: List<Float>, y: List<Float>): Float {
    require(x.size == y.size) { "Vectors must be of equal length" }

    val meanX = x.average().toFloat()
    val meanY = y.average().toFloat()

    val numerator = x.zip(y).sumByDouble { (xi, yi) -> ((xi - meanX) * (yi - meanY)).toDouble() }.toFloat()

    val denominator = sqrt(
        x.sumOf { (it - meanX).toDouble().pow(2) }.toFloat() *
                y.sumOf { (it - meanY).toDouble().pow(2) }.toFloat()
    )
    return if (denominator != 0f) numerator / denominator else 0f
}




