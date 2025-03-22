package dev.gorokhov.smoothcaret

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "SmoothCaretSettings",
    storages = [Storage("smooth-caret.xml")]
)
class SmoothCaretSettings : PersistentStateComponent<SmoothCaretSettings> {

    enum class CaretStyle { BLOCK, LINE, UNDERSCORE }

    var isEnabled: Boolean = true
    var replaceDefaultCaret: Boolean = true
    var caretWidth: Int = 2
    var caretStyle: CaretStyle = CaretStyle.LINE
    var caretColor: String = "CARET_COLOR"

    var isBlinking: Boolean = true
    var blinkInterval: Int = 500

    var caretHeightMargins: Int = 2

    var smoothness: Float = 0.15f
    var catchupSpeed: Float = 0.5f
    var maxCatchupSpeed: Float = 0.8f
    var smoothingEnabled: Boolean = true
    var adaptiveSpeed: Boolean = true

    fun resetToDefaults() {
        isEnabled = true
        isBlinking = true
        blinkInterval = 500
        caretStyle = CaretStyle.LINE
        caretWidth = 2
        caretHeightMargins = 2
        smoothness = 0.15f
        catchupSpeed = 0.5f
        maxCatchupSpeed = 0.8f
        smoothingEnabled = true
        adaptiveSpeed = true
    }

    override fun getState(): SmoothCaretSettings = this

    override fun loadState(state: SmoothCaretSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

