package dev.gorokhov.smoothcaret

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SmoothCaretSettingsPanel(private val settings: SmoothCaretSettings) {
    private val panel = JPanel(GridBagLayout())
    private val components = mutableListOf<JComponent>()

    private val blinkingStyleComboBox = ComboBox(SmoothCaretSettings.BlinkingStyle.entries.toTypedArray()).apply {
        selectedItem = settings.blinkingStyle
        addActionListener {
            settings.blinkingStyle = selectedItem as SmoothCaretSettings.BlinkingStyle
        }
        toolTipText = "Select the type of cursor animation"

        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is SmoothCaretSettings.BlinkingStyle) {
                    text = when (value) {
                        SmoothCaretSettings.BlinkingStyle.BLINK -> "Blink"
                        SmoothCaretSettings.BlinkingStyle.SMOOTH -> "Smooth"
                        SmoothCaretSettings.BlinkingStyle.PHASE -> "Phase"
                        SmoothCaretSettings.BlinkingStyle.EXPAND -> "Expand"
                        SmoothCaretSettings.BlinkingStyle.SOLID -> "Solid"
                    }
                }
                return component
            }
        }
    }

    private val adaptiveSpeedCheckbox = JCheckBox("Adaptive speed").apply {
        isSelected = settings.adaptiveSpeed
        addActionListener { settings.adaptiveSpeed = isSelected }
        toolTipText = "Automatically adjust animation speed based on typing speed"
    }

    private val sliderComponents = mutableListOf<Pair<JSlider, JBTextField>>()

    init {
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        var y = 0

        addComponent(
            createDescriptionPanel(
                "Smooth Caret Settings", "Configure the behavior and appearance of the smooth caret animation."
            ), y++
        )

        addComponent(createGroupPanel("Animation Settings") {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(JLabel("Animation style: "))
                add(blinkingStyleComboBox)
            })

            add(
                createLabeledSlider(
                    "Animation speed",
                    "Controls how fast the cursor animation happens (lower = faster)",
                    200,
                    2000,
                    settings.blinkInterval,
                    isPercentage = false
                ) { settings.blinkInterval = it })

            add(
                createLabeledSlider(
                    "Animation smoothness",
                    "Controls how smooth the caret movement is (lower = smoother)",
                    5,
                    30,
                    (settings.smoothness * 100).toInt()
                ) { settings.smoothness = it / 100f })

            add(
                createLabeledSlider(
                    "Catch-up speed",
                    "How quickly the caret catches up during normal typing",
                    30,
                    80,
                    (settings.catchupSpeed * 100).toInt()
                ) { settings.catchupSpeed = it / 100f })

            add(
                createLabeledSlider(
                    "Max catch-up speed",
                    "Maximum speed for catching up during fast typing",
                    50,
                    100,
                    (settings.maxCatchupSpeed * 100).toInt()
                ) { settings.maxCatchupSpeed = it / 100f })

            add(adaptiveSpeedCheckbox)
        }, y++)

        addComponent(createResetPanel(), y++)

        addComponent(JPanel().apply {
            preferredSize = Dimension(1, 20)
        }, y++)
    }

    private fun createResetPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Reset to Defaults").apply {
                addActionListener {
                    resetToDefaults()
                }
            })
        }
    }

    private fun resetToDefaults() {
        val result = JOptionPane.showConfirmDialog(
            panel,
            "Are you sure you want to reset all settings to their default values?",
            "Reset Settings",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            settings.resetToDefaults()

            blinkingStyleComboBox.selectedItem = settings.blinkingStyle
            adaptiveSpeedCheckbox.isSelected = settings.adaptiveSpeed

            sliderComponents.forEach { pair ->
                val (slider, textField) = pair
                when (slider.toolTipText) {
                    "Controls how smooth the caret movement is (lower = smoother)" -> {
                        slider.value = (settings.smoothness * 100).toInt()
                        textField.text = slider.value.toString()
                    }

                    "How quickly the caret catches up during normal typing" -> {
                        slider.value = (settings.catchupSpeed * 100).toInt()
                        textField.text = slider.value.toString()
                    }

                    "Maximum speed for catching up during fast typing" -> {
                        slider.value = (settings.maxCatchupSpeed * 100).toInt()
                        textField.text = slider.value.toString()
                    }

                    "Controls how fast the cursor animation happens (lower = faster)" -> {
                        slider.value = settings.blinkInterval
                        textField.text = slider.value.toString()
                    }
                }
            }
        }
    }

    private fun createDescriptionPanel(title: String, description: String): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
            add(JLabel(title).apply {
                font = font.deriveFont(Font.BOLD, font.size + 2f)
            }, BorderLayout.NORTH)
            add(JLabel("<html><body width='300px'>$description</body></html>").apply {
                border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
            }, BorderLayout.CENTER)
        }
    }

    private fun createGroupPanel(title: String, init: JPanel.() -> Unit): JPanel {
        return JPanel(GridLayout(0, 1, 0, 5)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.LIGHT_GRAY), title, TitledBorder.LEFT, TitledBorder.TOP
                ), BorderFactory.createEmptyBorder(10, 10, 10, 10)
            )
            init()
        }
    }

    private fun createLabeledSlider(
        label: String,
        tooltip: String,
        min: Int,
        max: Int,
        initial: Int,
        isPercentage: Boolean = true,
        onChange: (Int) -> Unit
    ): JPanel {
        return JPanel(GridBagLayout()).apply {
            val unitLabel = JLabel(if (isPercentage) "%" else "ms")
            val textField = JBTextField(initial.toString()).apply {
                preferredSize = Dimension(60, preferredSize.height)
                toolTipText = "Enter value directly"
            }

            val slider = JSlider(min, max, initial).apply {
                toolTipText = tooltip
                addChangeListener {
                    if (!textField.hasFocus()) {
                        onChange(value)
                        textField.text = value.toString()
                    }
                }
            }

            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateFromTextField()
                override fun removeUpdate(e: DocumentEvent?) = updateFromTextField()
                override fun changedUpdate(e: DocumentEvent?) = updateFromTextField()

                private fun updateFromTextField() {
                    try {
                        val value = textField.text.toInt().coerceIn(min, max)

                        if (value != slider.value) {
                            slider.value = value
                            onChange(value)
                        }
                    } catch (e: NumberFormatException) {
                    }
                }
            })

            sliderComponents.add(Pair(slider, textField))

            add(JLabel(label).apply {
                toolTipText = tooltip
            }, GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                gridwidth = 3
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
            })

            add(slider, GridBagConstraints().apply {
                gridx = 0
                gridy = 1
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
            })

            add(textField, GridBagConstraints().apply {
                gridx = 1
                gridy = 1
                insets = JBUI.insetsLeft(5)
            })

            add(unitLabel, GridBagConstraints().apply {
                gridx = 2
                gridy = 1
                insets = JBUI.insetsLeft(5)
            })
        }
    }

    private fun addComponent(component: JComponent, y: Int) {
        components.add(component)
        panel.add(component, GridBagConstraints().apply {
            gridx = 0
            gridy = y
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = JBUI.insetsBottom(5)
        })
    }

    fun getPanel(): JPanel = panel

    fun isModified(): Boolean = true

    fun apply() {
    }
}