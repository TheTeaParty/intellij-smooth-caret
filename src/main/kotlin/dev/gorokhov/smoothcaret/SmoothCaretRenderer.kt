package dev.gorokhov.smoothcaret

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.sin

class SmoothCaretRenderer(private val settings: SmoothCaretSettings) : CustomHighlighterRenderer {
    private val caretPositions = mutableMapOf<Int, CaretPosition>()
    private var timer: Timer? = null
    private var lastEditor: Editor? = null

    private var cachedRefreshRate: Int = -1

    private var blinkStartTime = System.currentTimeMillis()
    private var lastMoveTime = System.currentTimeMillis()
    private val resumeBlinkDelay = 100
    private var blinkTimer: Timer? = null

    private data class CaretPosition(
        var currentX: Double = 0.0, var currentY: Double = 0.0, var targetX: Double = 0.0, var targetY: Double = 0.0
    )

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        if (!settings.isEnabled) return

        if (!editor.contentComponent.hasFocus()) return

        if (lastEditor != editor) {
            lastEditor = editor
            resetAllPositions(editor)
            blinkStartTime = System.currentTimeMillis()

            blinkTimer?.stop()
            blinkTimer = null
        }

        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val allCarets = editor.caretModel.allCarets

        ensureTimerStarted(editor)
        ensureBlinkTimerStarted(editor)

        var anyMoving = false
        allCarets.forEachIndexed { index, caret ->
            val point = editor.visualPositionToXY(caret.visualPosition)
            val caretPos = caretPositions.getOrPut(index) { CaretPosition() }

            if (abs(point.x - caretPos.targetX) > 1000 || abs(point.y - caretPos.targetY) > 1000) {
                resetCaretPosition(caretPos, point)
            }

            caretPos.targetX = point.x.toDouble()
            caretPos.targetY = point.y.toDouble()

            val isMoving =
                abs(caretPos.targetX - caretPos.currentX) > 0.01 || abs(caretPos.targetY - caretPos.currentY) > 0.01
            if (isMoving) {
                anyMoving = true
            }
        }

        if (anyMoving) {
            lastMoveTime = System.currentTimeMillis()
            blinkStartTime = System.currentTimeMillis()
        }

        val timeSinceLastMove = System.currentTimeMillis() - lastMoveTime
        val shouldBlink = timeSinceLastMove > resumeBlinkDelay

        val blinkValue = if (settings.blinkingStyle != SmoothCaretSettings.BlinkingStyle.SOLID) {
            if (shouldBlink) {
                val currentTime = System.currentTimeMillis()
                val elapsedSinceBlinkStart = currentTime - blinkStartTime - resumeBlinkDelay
                val timeInCycle = if (elapsedSinceBlinkStart >= 0) {
                    (elapsedSinceBlinkStart % settings.blinkInterval.toLong()).toFloat() / settings.blinkInterval.toFloat()
                } else {
                    0f
                }
                calculateBlinkValue(timeInCycle)
            } else {
                BlinkValue(1.0f, 1.0f)
            }
        } else {
            BlinkValue(1.0f, 1.0f)
        }

        g2d.color = editor.colorsScheme.defaultForeground
        val caretHeight = editor.lineHeight - (settings.caretHeightMargins * 2)

        allCarets.forEachIndexed { index, _ ->
            val caretPos = caretPositions[index] ?: return@forEachIndexed

            if (caretPos.currentX.isFinite() && caretPos.currentY.isFinite() && blinkValue.opacity > 0.01f) {
                val originalComposite = g2d.composite
                if (blinkValue.opacity < 1.0f) {
                    g2d.composite =
                        java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, blinkValue.opacity)
                }
                val caretX = caretPos.currentX.toInt()
                val caretY = caretPos.currentY.toInt()
                val scaledHeight = (caretHeight * blinkValue.scaleY).toInt()
                val yOffset = if (blinkValue.scaleY < 1.0f) {
                    settings.caretHeightMargins + (caretHeight - scaledHeight) / 2
                } else {
                    settings.caretHeightMargins
                }

                when (settings.caretStyle) {
                    SmoothCaretSettings.CaretStyle.BLOCK -> {
                        g2d.fillRect(caretX, caretY + yOffset, settings.caretWidth, scaledHeight)
                    }

                    SmoothCaretSettings.CaretStyle.LINE -> {
                        g2d.fillRect(caretX, caretY + yOffset, settings.caretWidth, scaledHeight)
                    }

                    SmoothCaretSettings.CaretStyle.UNDERSCORE -> {
                        val underscoreY = if (blinkValue.scaleY < 1.0f) {
                            caretY + caretHeight - 2 + (2 - (2 * blinkValue.scaleY).toInt()) / 2
                        } else {
                            caretY + caretHeight - 2
                        }
                        g2d.fillRect(
                            caretX,
                            underscoreY,
                            settings.caretWidth * 2,
                            (2 * blinkValue.scaleY).toInt().coerceAtLeast(1)
                        )
                    }
                }

                g2d.composite = originalComposite
            }
        }

        val currentCaretCount = allCarets.size
        caretPositions.keys.retainAll { it < currentCaretCount }
    }

    private fun calculateBlinkValue(timeInCycle: Float): BlinkValue {
        return when (settings.blinkingStyle) {
            SmoothCaretSettings.BlinkingStyle.BLINK -> {
                when {
                    timeInCycle < 0.49f -> BlinkValue(1.0f, 1.0f)
                    timeInCycle < 0.51f -> BlinkValue(1.0f, 1.0f)
                    else -> BlinkValue(0.0f, 1.0f)
                }
            }

            SmoothCaretSettings.BlinkingStyle.SMOOTH -> {
                when {
                    timeInCycle < 0.3f -> BlinkValue(1.0f, 1.0f)
                    timeInCycle < 0.7f -> {
                        val fadeProgress = (timeInCycle - 0.3f) / 0.4f
                        val opacity = 1.0f - fadeProgress
                        BlinkValue(opacity, 1.0f)
                    }

                    else -> {
                        val fadeProgress = (timeInCycle - 0.7f) / 0.3f
                        val opacity = fadeProgress
                        BlinkValue(opacity, 1.0f)
                    }
                }
            }

            SmoothCaretSettings.BlinkingStyle.PHASE -> {
                when {
                    timeInCycle < 0.15f -> BlinkValue(1.0f, 1.0f)
                    timeInCycle < 0.85f -> {
                        val progress = (timeInCycle - 0.15f) / 0.7f
                        val opacity = 1.0f - sin(progress * Math.PI).toFloat() * 0.8f
                        BlinkValue(opacity, 1.0f)
                    }

                    else -> BlinkValue(1.0f, 1.0f)
                }
            }

            SmoothCaretSettings.BlinkingStyle.EXPAND -> {
                when {
                    timeInCycle < 0.2f -> BlinkValue(1.0f, 1.0f)
                    timeInCycle < 0.8f -> {
                        val progress = (timeInCycle - 0.2f) / 0.6f
                        val scale = 1.0f - sin(progress * Math.PI).toFloat() * 0.5f
                        BlinkValue(1.0f, scale)
                    }

                    else -> BlinkValue(1.0f, 1.0f)
                }
            }

            SmoothCaretSettings.BlinkingStyle.SOLID -> {
                BlinkValue(1.0f, 1.0f)
            }
        }
    }

    private fun ensureBlinkTimerStarted(editor: Editor) {
        if (blinkTimer == null && settings.blinkingStyle != SmoothCaretSettings.BlinkingStyle.SOLID) {
            val refreshRate = getScreenRefreshRate()
            val delay = 1000 / refreshRate

            blinkTimer = Timer(delay) {
                if (!editor.isDisposed) {
                    val timeSinceLastMove = System.currentTimeMillis() - lastMoveTime
                    if (timeSinceLastMove > resumeBlinkDelay) {
                        editor.contentComponent.repaint()
                    }
                } else {
                    blinkTimer?.stop()
                    blinkTimer = null
                }
            }
            blinkTimer?.start()
        }
    }

    private data class BlinkValue(val opacity: Float, val scaleY: Float)

    private fun resetAllPositions(editor: Editor) {
        caretPositions.clear()
        val allCarets = editor.caretModel.allCarets
        allCarets.forEachIndexed { index, caret ->
            val point = editor.visualPositionToXY(caret.visualPosition)
            val caretPos = CaretPosition(
                currentX = point.x.toDouble(),
                currentY = point.y.toDouble(),
                targetX = point.x.toDouble(),
                targetY = point.y.toDouble()
            )
            caretPositions[index] = caretPos
        }
        blinkStartTime = System.currentTimeMillis()
    }

    private fun resetCaretPosition(caretPos: CaretPosition, point: java.awt.Point) {
        caretPos.currentX = point.x.toDouble()
        caretPos.currentY = point.y.toDouble()
        caretPos.targetX = caretPos.currentX
        caretPos.targetY = caretPos.currentY
    }

    private fun getScreenRefreshRate(): Int {
        if (cachedRefreshRate > 0) {
            return cachedRefreshRate
        }

        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val gd = ge.screenDevices
        var refreshRate = 60

        if (gd.isNotEmpty()) {
            val mainDisplay = gd[0]
            val mode = mainDisplay.displayMode
            if (mode.refreshRate > 0) {
                refreshRate = mode.refreshRate
            }
        }

        refreshRate = refreshRate.coerceIn(30, 240)
        cachedRefreshRate = refreshRate

        return refreshRate
    }

    private fun ensureTimerStarted(editor: Editor) {
        if (timer == null) {
            val refreshRate = getScreenRefreshRate()
            val delay = 1000 / refreshRate

            timer = Timer(delay) {
                if (!editor.isDisposed) {
                    var needsRepaint = false

                    caretPositions.values.forEach { caretPos ->
                        val dx = caretPos.targetX - caretPos.currentX
                        val dy = caretPos.targetY - caretPos.currentY

                        if (settings.adaptiveSpeed) {
                            val charWidth =
                                editor.component.getFontMetrics(editor.colorsScheme.getFont(null)).charWidth('m')

                            val speedFactor = when {
                                abs(dx) > charWidth * 2 -> settings.maxCatchupSpeed
                                abs(dx) > charWidth -> settings.catchupSpeed
                                else -> settings.smoothness
                            }

                            if (abs(dx) > 0.01 || abs(dy) > 0.01) {
                                caretPos.currentX += dx * speedFactor
                                caretPos.currentY += dy * speedFactor
                                needsRepaint = true
                            }
                        } else {
                            if (abs(dx) > 0.01 || abs(dy) > 0.01) {
                                caretPos.currentX += dx * settings.smoothness
                                caretPos.currentY += dy * settings.smoothness
                                needsRepaint = true
                            }
                        }
                    }

                    if (needsRepaint) {
                        editor.contentComponent.repaint()
                    }
                } else {
                    timer?.stop()
                    timer = null
                }
            }
            timer?.start()
        }
    }
}
