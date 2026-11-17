package com.frameforge.app.ui

internal fun isValidAspectRatio(value: String): Boolean {
    val parts = value.trim().split(":")
    if (parts.size != 2) return false
    val width = parts[0].trim().toIntOrNull() ?: return false
    val height = parts[1].trim().toIntOrNull() ?: return false
    return width in 1..10000 && height in 1..10000
}
