package dev.gorokhov.smoothcaret

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Color

class SmoothCaretEditorFactoryListener : EditorFactoryListener {
    private val settings = service<SmoothCaretService>().getSettings()
    private val highlighters = mutableMapOf<Editor, RangeHighlighter>()

    override fun editorCreated(event: EditorFactoryEvent) {
        setupSmoothCaret(event.editor)
    }

    private fun setupSmoothCaret(editor: Editor) {
        if (!settings.isEnabled) return

        if (shouldSkipEditor(editor)) return

        editor.settings.apply {
            isBlinkCaret = false
            isBlockCursor = false
            isRightMarginShown = false
            lineCursorWidth = 0
            if (settings.replaceDefaultCaret) {
                isCaretRowShown = false
            }
        }

        editor.colorsScheme.setColor(EditorColors.CARET_COLOR, Color(0, 0, 0, 0))

        val markupModel = editor.markupModel
        val docLength = editor.document.textLength
        val highlighter = markupModel.addRangeHighlighter(
            0, docLength, HighlighterLayer.LAST + 1, null, HighlighterTargetArea.EXACT_RANGE
        )
        highlighters[editor] = highlighter

        val renderer = SmoothCaretRenderer(settings)
        highlighter.customRenderer = renderer

        val caretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (settings.isEnabled && editor.contentComponent.isShowing) {
                    editor.contentComponent.repaint()
                }
            }
        }

        val documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val newLength = editor.document.textLength
                val currentHighlighter = highlighters[editor]

                if (currentHighlighter != null && newLength > 0) {
                    val startOffset = currentHighlighter.startOffset
                    val endOffset = currentHighlighter.endOffset

                    if (startOffset == 0 && endOffset != newLength) {
                        try {
                            currentHighlighter.gutterIconRenderer = null
                            val editorMarkupModel = editor.markupModel
                            editorMarkupModel.removeHighlighter(currentHighlighter)

                            val newHighlighter = editorMarkupModel.addRangeHighlighter(
                                0, newLength, HighlighterLayer.LAST + 1, null, HighlighterTargetArea.EXACT_RANGE
                            )
                            newHighlighter.customRenderer = currentHighlighter.customRenderer
                            highlighters[editor] = newHighlighter
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }

        editor.document.addDocumentListener(documentListener)
        editor.caretModel.addCaretListener(caretListener)
        editor.putUserData(CARET_LISTENER_KEY, caretListener)
        editor.putUserData(DOCUMENT_LISTENER_KEY, documentListener)
    }

    private fun shouldSkipEditor(editor: Editor): Boolean {
        val virtualFile = editor.document.let { document ->
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(document)
        }

        virtualFile?.let { file ->
            val fileType = file.fileType
            val fileName = file.name
            val filePath = file.path

            if (fileType.name.contains("Console", ignoreCase = true) || fileType.name.contains(
                    "Terminal", ignoreCase = true
                ) || fileName.contains("Console", ignoreCase = true) || fileName.contains(
                    "Terminal", ignoreCase = true
                ) || filePath.contains("console://", ignoreCase = true) || filePath.contains(
                    "terminal://", ignoreCase = true
                )
            ) {
                return true
            }
        }

        try {
            val editorKind = editor.editorKind
            if (editorKind == EditorKind.CONSOLE) {
                return true
            }
        } catch (e: Exception) {
        }

        return false
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor

        event.editor.colorsScheme.setColor(EditorColors.CARET_COLOR, null)

        editor.getUserData(DOCUMENT_LISTENER_KEY)?.let { listener ->
            editor.document.removeDocumentListener(listener)
        }

        editor.getUserData(CARET_LISTENER_KEY)?.let { listener ->
            editor.caretModel.removeCaretListener(listener)
        }

        highlighters.remove(editor)?.let { highlighter ->
            editor.markupModel.removeHighlighter(highlighter)
        }

        editor.colorsScheme.setColor(EditorColors.CARET_COLOR, null)
    }
}

private val CARET_LISTENER_KEY = com.intellij.openapi.util.Key<CaretListener>("SMOOTH_CARET_LISTENER")
private val DOCUMENT_LISTENER_KEY = com.intellij.openapi.util.Key<DocumentListener>("SMOOTH_CARET_DOCUMENT_LISTENER")
