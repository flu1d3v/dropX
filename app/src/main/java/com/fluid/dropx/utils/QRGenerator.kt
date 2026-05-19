package com.fluid.dropx.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.graphics.toColorInt

object QRGenerator {
    fun generate(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)

            val dark = "#2C2C2E".toColorInt()
            val light = Color.WHITE

            for (x in 0 until size) {
                for (y in 0 until size) {
                    val color = if (bitMatrix[x, y]) dark else light
                    bitmap[x, y] = color
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}

