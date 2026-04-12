/*
 * MIT License
 *
 * Copyright (c) 2026 iappyx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

// Renders IconConfig JSON into launcher PNGs at all five mipmap densities.

package com.iappyx.container

import android.graphics.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.abs

object IconGenerator {

    private val COLORS = intArrayOf(
        0xFF1565C0.toInt(), 0xFF6A1B9A.toInt(), 0xFF00838F.toInt(),
        0xFFC62828.toInt(), 0xFF2E7D32.toInt(), 0xFFEF6C00.toInt(),
        0xFF283593.toInt(), 0xFF4E342E.toInt(), 0xFF00695C.toInt(),
        0xFFAD1457.toInt(),
    )

    fun generateFromConfig(
        configJson: String?,
        appLabel: String,
        sizePx: Int,
    ): ByteArray {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Parse config or create default
        var bgColor: Int
        var bgGradient: IntArray? = null
        var elements: List<ElementData>

        if (configJson != null && configJson.isNotBlank()) {
            try {
                val json = JSONObject(configJson)
                bgColor = json.optInt("bgColor", defaultColorFor(appLabel))
                val gradArr = json.optJSONArray("bgGradient")
                if (gradArr != null && gradArr.length() >= 2) {
                    bgGradient = intArrayOf(gradArr.optInt(0), gradArr.optInt(1))
                }
                val arr = json.optJSONArray("elements")
                elements = if (arr != null) {
                    (0 until arr.length()).map { i ->
                        val el = arr.getJSONObject(i)
                        ElementData(
                            content = el.optString("content", ""),
                            isText = el.optBoolean("isText", false),
                            isImage = el.optBoolean("isImage", false),
                            scale = el.optDouble("scale", 1.0),
                            offsetX = el.optDouble("offsetX", 0.0),
                            offsetY = el.optDouble("offsetY", 0.0),
                            color = el.optInt("color", 0xFFEAEAEA.toInt()),
                            rotation = el.optDouble("rotation", 0.0),
                            opacity = el.optDouble("opacity", 1.0),
                            shadowPreset = if (el.has("shadow") && !el.has("shadowPreset"))
                                (if (el.optBoolean("shadow", false)) "drop" else "none")
                            else el.optString("shadowPreset", "none"),
                            shadowBlur = el.optDouble("shadowBlur", 8.0),
                            shadowOffsetX = el.optDouble("shadowOffsetX", 2.0),
                            shadowOffsetY = el.optDouble("shadowOffsetY", 3.0),
                            shadowColor = el.optInt("shadowColor", 0xFF000000.toInt()),
                            shadowOpacity = el.optDouble("shadowOpacity", 0.5),
                            shadowSpread = el.optDouble("shadowSpread", 0.0),
                            filter = el.optString("filter", "none"),
                        )
                    }
                } else emptyList()
            } catch (e: Exception) {
                bgColor = defaultColorFor(appLabel)
                val letter = appLabel.firstOrNull()?.uppercase() ?: "A"
                elements = listOf(ElementData(content = letter))
            }
        } else {
            bgColor = defaultColorFor(appLabel)
            val letter = appLabel.firstOrNull()?.uppercase() ?: "A"
            elements = listOf(ElementData(content = letter))
        }

        // Draw full square background — the launcher applies its own shape mask
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        if (bgGradient != null) {
            bgPaint.shader = LinearGradient(0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                bgGradient[0], bgGradient[1], Shader.TileMode.CLAMP)
        } else {
            bgPaint.color = bgColor
        }
        canvas.drawRect(rect, bgPaint)

        // Clip to canvas bounds so zoomed elements don't overflow
        canvas.clipRect(rect)

        // Draw each element
        for (el in elements) {
            if (el.content.isBlank()) continue

            val cx = sizePx / 2f + (el.offsetX * sizePx).toFloat()
            val cy = sizePx / 2f + (el.offsetY * sizePx).toFloat()

            canvas.save()
            canvas.rotate(el.rotation.toFloat(), cx, cy)

            // Shadow pass — draw offset version first
            val shadow = resolveShadow(el, sizePx)
            if (shadow != null) {
                canvas.save()
                canvas.translate(shadow.offsetX, shadow.offsetY)
                drawElement(canvas, el, cx, cy, sizePx, isShadow = true, shadowAlpha = shadow.alpha, shadowColor = shadow.color, shadowBlur = shadow.blur)
                canvas.restore()
            }
            // Main element
            drawElement(canvas, el, cx, cy, sizePx, isShadow = false)

            canvas.restore()
        }

        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        return baos.toByteArray()
    }

    private data class ShadowInfo(val offsetX: Float, val offsetY: Float, val blur: Float, val color: Int, val alpha: Int)

    private fun resolveShadow(el: ElementData, sizePx: Int): ShadowInfo? {
        val s = sizePx.toFloat()
        return when (el.shadowPreset) {
            "subtle" -> ShadowInfo(s * 0.01f, s * 0.01f, s * 0.03f, Color.BLACK, 77)
            "drop" -> ShadowInfo(s * 0.015f, s * 0.02f, s * 0.06f, Color.BLACK, 128)
            "hard" -> ShadowInfo(s * 0.02f, s * 0.02f, s * 0.008f, Color.BLACK, 179)
            "glow" -> ShadowInfo(0f, 0f, s * 0.08f, if (el.isText) el.color else 0xFF4FC3F7.toInt(), 153)
            "neon" -> ShadowInfo(0f, 0f, s * 0.1f, 0xFF4FC3F7.toInt(), 204)
            "custom" -> ShadowInfo(
                el.shadowOffsetX.toFloat() * s / 100f,
                el.shadowOffsetY.toFloat() * s / 100f,
                el.shadowBlur.toFloat() * s / 100f,
                el.shadowColor,
                (el.shadowOpacity * 255).toInt()
            )
            else -> null
        }
    }

    private fun drawElement(canvas: Canvas, el: ElementData, cx: Float, cy: Float, sizePx: Int, isShadow: Boolean, shadowAlpha: Int = 100, shadowColor: Int = Color.BLACK, shadowBlur: Float = 0f) {
        val alpha = if (isShadow) shadowAlpha else (el.opacity * 255).toInt()
        val cf = if (!isShadow) colorFilterFor(el.filter) else null

        if (el.isImage) {
            try {
                val imgBytes = android.util.Base64.decode(el.content, android.util.Base64.DEFAULT)
                val imgBmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                if (imgBmp != null) {
                    val imgSize = sizePx * 0.6f * el.scale.toFloat()
                    val srcSize = minOf(imgBmp.width, imgBmp.height)
                    val srcLeft = (imgBmp.width - srcSize) / 2
                    val srcTop = (imgBmp.height - srcSize) / 2
                    val srcRect = Rect(srcLeft, srcTop, srcLeft + srcSize, srcTop + srcSize)
                    val dstRect = RectF(cx - imgSize / 2f, cy - imgSize / 2f, cx + imgSize / 2f, cy + imgSize / 2f)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        this.alpha = alpha
                        if (isShadow) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                            setScale(Color.red(shadowColor) / 255f, Color.green(shadowColor) / 255f, Color.blue(shadowColor) / 255f, alpha / 255f)
                        })
                        else if (cf != null) colorFilter = cf
                    }
                    canvas.drawBitmap(imgBmp, srcRect, dstRect, paint)
                    imgBmp.recycle()
                }
            } catch (_: Exception) {}
        } else {
            val textSize = if (el.isText) sizePx * 0.25f * el.scale.toFloat()
            else sizePx * 0.55f * el.scale.toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = textSize
                textAlign = Paint.Align.CENTER
                this.alpha = alpha
                if (isShadow) {
                    color = shadowColor
                    this.alpha = shadowAlpha
                    if (shadowBlur > 0) maskFilter = android.graphics.BlurMaskFilter(shadowBlur, android.graphics.BlurMaskFilter.Blur.NORMAL)
                } else if (el.isText) {
                    color = el.color
                }
                if (el.isText) typeface = Typeface.DEFAULT_BOLD
                if (!isShadow && cf != null) colorFilter = cf
            }
            val bounds = Rect()
            paint.getTextBounds(el.content, 0, el.content.length, bounds)
            val textY = cy - (bounds.top + bounds.bottom) / 2f
            canvas.drawText(el.content, cx, textY, paint)
        }
    }

    fun generateAllFromConfig(
        configJson: String?,
        appLabel: String,
    ): Map<String, ByteArray> {
        val densities = mapOf(
            "res/mipmap-mdpi-v4/ic_launcher.png" to 48,
            "res/mipmap-hdpi-v4/ic_launcher.png" to 72,
            "res/mipmap-xhdpi-v4/ic_launcher.png" to 96,
            "res/mipmap-xxhdpi-v4/ic_launcher.png" to 144,
            "res/mipmap-xxxhdpi-v4/ic_launcher.png" to 192,
        )
        return densities.mapValues { (_, size) ->
            generateFromConfig(configJson, appLabel, size)
        }
    }

    private fun defaultColorFor(s: String): Int {
        val key = s.firstOrNull()?.uppercase() ?: "A"
        return COLORS[abs(key.hashCode()) % COLORS.size]
    }

    private data class ElementData(
        val content: String = "",
        val isText: Boolean = false,
        val isImage: Boolean = false,
        val scale: Double = 1.0,
        val offsetX: Double = 0.0,
        val offsetY: Double = 0.0,
        val color: Int = 0xFFEAEAEA.toInt(),
        val rotation: Double = 0.0,
        val opacity: Double = 1.0,
        val shadowPreset: String = "none",
        val shadowBlur: Double = 8.0,
        val shadowOffsetX: Double = 2.0,
        val shadowOffsetY: Double = 3.0,
        val shadowColor: Int = 0xFF000000.toInt(),
        val shadowOpacity: Double = 0.5,
        val shadowSpread: Double = 0.0,
        val filter: String = "none",
    )

    private fun colorFilterFor(filter: String): ColorMatrixColorFilter? {
        val m = when (filter) {
            "grayscale" -> floatArrayOf(0.2126f,0.7152f,0.0722f,0f,0f, 0.2126f,0.7152f,0.0722f,0f,0f, 0.2126f,0.7152f,0.0722f,0f,0f, 0f,0f,0f,1f,0f)
            "sepia" -> floatArrayOf(0.393f,0.769f,0.189f,0f,0f, 0.349f,0.686f,0.168f,0f,0f, 0.272f,0.534f,0.131f,0f,0f, 0f,0f,0f,1f,0f)
            "invert" -> floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)
            "vibrant" -> floatArrayOf(1.5f,-0.25f,-0.25f,0f,0f, -0.25f,1.5f,-0.25f,0f,0f, -0.25f,-0.25f,1.5f,0f,0f, 0f,0f,0f,1f,0f)
            "warm" -> floatArrayOf(1.2f,0.1f,0f,0f,10f, 0f,1.05f,0f,0f,5f, 0f,0f,0.8f,0f,-10f, 0f,0f,0f,1f,0f)
            "cool" -> floatArrayOf(0.85f,0f,0f,0f,-10f, 0f,1f,0.1f,0f,0f, 0f,0.1f,1.2f,0f,15f, 0f,0f,0f,1f,0f)
            "contrast" -> floatArrayOf(1.5f,0f,0f,0f,-40f, 0f,1.5f,0f,0f,-40f, 0f,0f,1.5f,0f,-40f, 0f,0f,0f,1f,0f)
            "fade" -> floatArrayOf(0.8f,0.1f,0.1f,0f,30f, 0.1f,0.8f,0.1f,0f,30f, 0.1f,0.1f,0.8f,0f,30f, 0f,0f,0f,1f,0f)
            "duotone" -> floatArrayOf(0.3f,0.6f,0.1f,0f,20f, 0.1f,0.3f,0.6f,0f,10f, 0.5f,0.3f,0.2f,0f,40f, 0f,0f,0f,1f,0f)
            "nightshift" -> floatArrayOf(1.1f,0.1f,0f,0f,20f, 0f,0.9f,0.1f,0f,10f, 0f,0f,0.6f,0f,-20f, 0f,0f,0f,1f,0f)
            "posterize" -> floatArrayOf(2f,-0.5f,-0.5f,0f,-20f, -0.5f,2f,-0.5f,0f,-20f, -0.5f,-0.5f,2f,0f,-20f, 0f,0f,0f,1f,0f)
            else -> return null
        }
        return ColorMatrixColorFilter(ColorMatrix(m))
    }
}
