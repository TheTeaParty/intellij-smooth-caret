package dev.gorokhov.smoothcaret

class SmoothCaretSettings {
    enum class CaretStyle { BLOCK, LINE, UNDERSCORE }

    var isEnabled: Boolean = true
    var replaceDefaultCaret: Boolean = true
    var caretWidth: Int = 2
    var caretStyle: CaretStyle = CaretStyle.LINE
    var caretColor: String = "CARET_COLOR"
    var isBlinking: Boolean = true
    var blinkInterval: Int = 500
}
