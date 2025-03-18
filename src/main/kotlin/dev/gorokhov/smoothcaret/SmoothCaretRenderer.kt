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

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        if (!settings.isEnabled) return

        // Reset position if editor changed
        if (lastEditor != editor) {
            lastEditor = editor
            resetPosition(editor)
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

        val metrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(null))
        val height = metrics.height

        // Only draw if we have valid positions
        if (currentX.isFinite() && currentY.isFinite()) {
            when (settings.caretStyle) {
                SmoothCaretSettings.CaretStyle.BLOCK -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt() + settings.caretHeightMargins,
                        settings.caretWidth,
                        height
                    )
                }
                SmoothCaretSettings.CaretStyle.LINE -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt() + settings.caretHeightMargins,
                        settings.caretWidth,
                        height
                    )
                }
                SmoothCaretSettings.CaretStyle.UNDERSCORE -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt() + height - 2,
                        settings.caretWidth * 2,
                        2
                    )
                }
            }
        }
    }

    private fun resetPosition(editor: Editor) {
        val point = editor.caretModel.visualPosition.let { editor.visualPositionToXY(it) }
        currentX = point.x.toDouble()
        currentY = point.y.toDouble()
        targetX = currentX
        targetY = currentY
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
            
            // Adjust the animation coefficient according to the refresh rate to make the animation feel consistent on high and low refresh screens
            val baseSpeed = 0.3 // Base speed coefficient
            val speedFactor = 60.0 / refreshRate // Adjustment factor relative to 60Hz
            val adjustedSpeed = baseSpeed * speedFactor.coerceIn(0.5, 1.5) // Limit adjustment range
            
            timer = Timer(delay) {
                if (!editor.isDisposed) {
                    val dx = targetX - currentX
                    val dy = targetY - currentY
                    if (abs(dx) > 0.01 || abs(dy) > 0.01) {
                        currentX += dx * adjustedSpeed
                        currentY += dy * adjustedSpeed
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