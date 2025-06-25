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

class SmoothCaretRenderer(private val settings: SmoothCaretSettings) : CustomHighlighterRenderer {
    private val caretPositions = mutableMapOf<Int, CaretPosition>()
    private var timer: Timer? = null
    private var lastEditor: Editor? = null

    private var cachedRefreshRate: Int = -1

    private var isCaretVisible = true
    private var blinkTimer: Timer? = null
    private var lastMoveTime = System.currentTimeMillis()
    private val resumeBlinkDelay = 1000

    private data class CaretPosition(
        var currentX: Double = 0.0, var currentY: Double = 0.0, var targetX: Double = 0.0, var targetY: Double = 0.0
    )


    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        if (!settings.isEnabled) return

        if (!editor.contentComponent.hasFocus()) return

        // Reset position if editor changed
        if (lastEditor != editor) {
            lastEditor = editor
            resetAllPositions(editor)
            setupBlinkTimer()
        }

        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val allCarets = editor.caretModel.allCarets

        ensureTimerStarted(editor)

        var anyMoving = false
        allCarets.forEachIndexed { index, caret ->
            val point = editor.visualPositionToXY(caret.visualPosition)
            val caretPos = caretPositions.getOrPut(index) { CaretPosition() }

            // Reset position if there's a large jump
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
            isCaretVisible = true
        }

        val timeSinceLastMove = System.currentTimeMillis() - lastMoveTime
        val shouldBlink = timeSinceLastMove > resumeBlinkDelay

        g2d.color = editor.colorsScheme.defaultForeground
        val caretHeight = editor.lineHeight - (settings.caretHeightMargins * 2)

        allCarets.forEachIndexed { index, _ ->
            val caretPos = caretPositions[index] ?: return@forEachIndexed

            // Only draw if we have valid positions
            if (caretPos.currentX.isFinite() && caretPos.currentY.isFinite() && (!shouldBlink || isCaretVisible || !settings.isBlinking)) {
                when (settings.caretStyle) {
                    SmoothCaretSettings.CaretStyle.BLOCK -> {
                        g2d.fillRect(
                            caretPos.currentX.toInt(),
                            caretPos.currentY.toInt() + settings.caretHeightMargins,
                            settings.caretWidth,
                            caretHeight
                        )
                    }

                    SmoothCaretSettings.CaretStyle.LINE -> {
                        g2d.fillRect(
                            caretPos.currentX.toInt(),
                            caretPos.currentY.toInt() + settings.caretHeightMargins,
                            settings.caretWidth,
                            caretHeight
                        )
                    }

                    SmoothCaretSettings.CaretStyle.UNDERSCORE -> {
                        g2d.fillRect(
                            caretPos.currentX.toInt(),
                            caretPos.currentY.toInt() + caretHeight - 2,
                            settings.caretWidth * 2,
                            2
                        )
                    }
                }
            }
        }

        val currentCaretCount = allCarets.size
        caretPositions.keys.retainAll { it < currentCaretCount }
    }

    private fun setupBlinkTimer() {
        blinkTimer?.stop()
        blinkTimer = Timer(settings.blinkInterval) {
            if (lastEditor?.isDisposed == false) {
                isCaretVisible = !isCaretVisible
                lastEditor?.contentComponent?.repaint()
            } else {
                blinkTimer?.stop()
                blinkTimer = null
            }
        }
        blinkTimer?.start()
    }

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
        isCaretVisible = true
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
            // Prioritize the refresh rate of the primary display
            val mainDisplay = gd[0]
            val mode = mainDisplay.displayMode
            if (mode.refreshRate > 0) {
                refreshRate = mode.refreshRate
            }
        }

        // Limit refresh rate to a reasonable range
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

                            // Adaptive speed based on distance to avoid falling behind
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
                            // Simple constant speed animation
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
