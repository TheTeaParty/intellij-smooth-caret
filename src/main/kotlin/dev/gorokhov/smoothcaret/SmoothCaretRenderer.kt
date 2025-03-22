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
    private var currentX: Double = 0.0
    private var currentY: Double = 0.0
    private var targetX: Double = 0.0
    private var targetY: Double = 0.0
    private var timer: Timer? = null
    private var lastEditor: Editor? = null

    private var cachedRefreshRate: Int = -1

    private var isCaretVisible = true
    private var blinkTimer: Timer? = null
    private var lastMoveTime = System.currentTimeMillis()
    private val resumeBlinkDelay = 1000


    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        if (!settings.isEnabled) return

        // Reset position if editor changed
        if (lastEditor != editor) {
            lastEditor = editor
            resetPosition(editor)
            setupBlinkTimer()
        }

        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val point = editor.caretModel.visualPosition.let { editor.visualPositionToXY(it) }

        // Reset position if there's a large jump
        if (abs(point.x - targetX) > 1000 || abs(point.y - targetY) > 1000) {
            resetPosition(editor)
        }

        ensureTimerStarted(editor)

        targetX = point.x.toDouble()
        targetY = point.y.toDouble()

        g2d.color = editor.colorsScheme.defaultForeground

        val caretHeight = editor.lineHeight - (settings.caretHeightMargins * 2)

        val isMoving = Math.abs(targetX - currentX) > 0.01 || Math.abs(targetY - currentY) > 0.01

        if (isMoving) {
            lastMoveTime = System.currentTimeMillis()
            isCaretVisible = true
        }

        val timeSinceLastMove = System.currentTimeMillis() - lastMoveTime
        val shouldBlink = timeSinceLastMove > resumeBlinkDelay

        // Only draw if we have valid positions
        if (currentX.isFinite() && currentY.isFinite() && (!shouldBlink || isCaretVisible || !settings.isBlinking)) {
            when (settings.caretStyle) {
                SmoothCaretSettings.CaretStyle.BLOCK -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt() + settings.caretHeightMargins,
                        settings.caretWidth,
                        caretHeight
                    )
                }

                SmoothCaretSettings.CaretStyle.LINE -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt() + settings.caretHeightMargins,
                        settings.caretWidth,
                        caretHeight
                    )
                }

                SmoothCaretSettings.CaretStyle.UNDERSCORE -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt() + caretHeight - 2,
                        settings.caretWidth * 2,
                        2
                    )
                }
            }
        }
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

    private fun resetPosition(editor: Editor) {
        val point = editor.caretModel.visualPosition.let { editor.visualPositionToXY(it) }
        currentX = point.x.toDouble()
        currentY = point.y.toDouble()
        targetX = currentX
        targetY = currentY
        isCaretVisible = true
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
                    val dx = targetX - currentX
                    val dy = targetY - currentY

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
                            currentX += dx * speedFactor
                            currentY += dy * speedFactor
                            editor.contentComponent.repaint()
                        }
                    } else {
                        // Simple constant speed animation
                        currentX += dx * settings.smoothness
                        currentY += dy * settings.smoothness
                    }

                    editor.contentComponent.repaint()
                } else {
                    timer?.stop()
                    timer = null
                }
            }
            timer?.start()
        }
    }
}
