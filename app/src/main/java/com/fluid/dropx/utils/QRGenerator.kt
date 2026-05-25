package com.fluid.dropx.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.graphics.toColorInt

object QRGenerator {
    // Encodes a raw share URL string into a visual scan grid layout matrix
    fun generate(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            // Generates a boolean 2D data block matrix where true = pixel block, false = space
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)

            // RGB_565 uses only 2 bytes per pixel instead of 4 (ARGB_8888).
            // Since we don't need alpha transparency channels, this cuts the QR image RAM memory overhead right in half.
            val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)

            // Using a clean custom off-black char-gray color palette element instead of default ugly high-contrast pure black
            val dark = "#2C2C2E".toColorInt()
            val light = Color.WHITE

            // Nested processing loop: inspects every coordinate position mapping layer inside the data matrix
            // and physically draws the corresponding pixel color block target directly onto the bitmap canvas
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val color = if (bitMatrix[x, y]) dark else light
                    bitmap[x, y] = color
                }
            }
            bitmap
        } catch (e: Exception) {
            null // Catches structural text length overflows gracefully without crashing the app pipeline
        }
    }
}