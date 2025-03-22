package dev.gorokhov.smoothcaret

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Level.APP)
@State(
    name = "SmoothCaretSettings",
    storages = [Storage("smooth-caret.xml")]
)
class SmoothCaretService : PersistentStateComponent<SmoothCaretSettings> {
    private val settings = SmoothCaretSettings()

    override fun getState(): SmoothCaretSettings = settings

    override fun loadState(state: SmoothCaretSettings) {
        settings.loadState(state)
    }

    fun getSettings(): SmoothCaretSettings = settings
}