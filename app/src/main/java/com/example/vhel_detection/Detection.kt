package com.example.vhel_detection

data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
    val score: Float
)
