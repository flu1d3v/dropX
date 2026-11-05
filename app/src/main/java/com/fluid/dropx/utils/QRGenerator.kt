package com.fluid.dropx.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QRGenerator {
    // Encodes a share URL into a QR code bitmap. All pixels are filled into one IntArray and written
    // with a single setPixels() call, which is far faster than setting ~260,000 pixels one by one.
    fun generate(content: String, size: Int = 512): Bitmap? {
        return try {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)

            val dark = "#2C2C2E".toColorInt()
            val light = Color.WHITE
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                val row = y * size
                for (x in 0 until size) {
                    pixels[row + x] = if (matrix[x, y]) dark else light
                }
            }

            // RGB_565 = 2 bytes per pixel (no transparency needed), half the memory of ARGB_8888
            val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            bitmap
        } catch (e: Exception) {
            null // e.g. text too long for a QR code: don't crash
        }
    }
}