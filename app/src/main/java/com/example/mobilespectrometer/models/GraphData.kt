package com.example.mobilespectrometer.models

data class GraphData(
    val timestamp: Long = 0L,
    val intensityValues: List<Float> = listOf(),
    val wavelengthValues: List<Float> = listOf()
)