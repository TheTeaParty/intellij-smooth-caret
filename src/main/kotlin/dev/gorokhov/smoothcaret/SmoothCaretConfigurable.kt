package dev.gorokhov.smoothcaret

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class SmoothCaretConfigurable : Configurable {
    private var settingsPanel: SmoothCaretSettingsPanel? = null
    private val settings = service<SmoothCaretService>().getSettings()

    override fun createComponent(): JComponent {
        settingsPanel = SmoothCaretSettingsPanel(settings)
        return settingsPanel!!.getPanel()
    }

    override fun isModified(): Boolean {
        return settingsPanel?.isModified() ?: false
    }

    override fun apply() {
        settingsPanel?.apply()
    }

    override fun getDisplayName(): String = "Smooth Caret"

    override fun disposeUIResources() {
        settingsPanel = null
    }
}