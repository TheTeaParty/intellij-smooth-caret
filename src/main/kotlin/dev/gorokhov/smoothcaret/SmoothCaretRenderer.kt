package dev.gorokhov.smoothcaret

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Timer

class SmoothCaretRenderer(private val settings: SmoothCaretSettings) : CustomHighlighterRenderer {
    private var currentX: Double = 0.0
    private var currentY: Double = 0.0
    private var targetX: Double = 0.0
    private var targetY: Double = 0.0
    private var timer: Timer? = null
    private var lastEditor: Editor? = null
    private var isCaretVisible = true
    private var blinkTimer: Timer? = null

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

        ensureTimerStarted(editor)

        targetX = point.x.toDouble()
        targetY = point.y.toDouble()

        g2d.color = editor.colorsScheme.defaultForeground

        val lineHeight = editor.lineHeight

        // Only draw if we have valid positions
        if (currentX.isFinite() && currentY.isFinite() && (isCaretVisible || !settings.isBlinking)) {
            when (settings.caretStyle) {
                SmoothCaretSettings.CaretStyle.BLOCK -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt(),
                        settings.caretWidth,
                        lineHeight
                    )
                }

                SmoothCaretSettings.CaretStyle.LINE -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt(),
                        settings.caretWidth,
                        lineHeight
                    )
                }

                SmoothCaretSettings.CaretStyle.UNDERSCORE -> {
                    g2d.fillRect(
                        currentX.toInt(),
                        currentY.toInt() + lineHeight - 2,
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

    private fun ensureTimerStarted(editor: Editor) {
        if (timer == null) {
            timer = Timer(16) { // ~60 FPS
                if (!editor.isDisposed) {
                    val dx = targetX - currentX
                    val dy = targetY - currentY
                    if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01) {
                        currentX += dx * 0.3
                        currentY += dy * 0.3
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
