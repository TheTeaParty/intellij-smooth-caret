package dev.gorokhov.smoothcaret

import java.awt.*
import javax.swing.*
import javax.swing.border.TitledBorder

class SmoothCaretSettingsPanel(private val settings: SmoothCaretSettings) {
    private val panel = JPanel(GridBagLayout())
    private val components = mutableListOf<JComponent>()

    private val blinkingCheckbox = JCheckBox("Enable blinking").apply {
        isSelected = settings.isBlinking
        addActionListener { settings.isBlinking = isSelected }
        toolTipText = "Toggle caret blinking animation"
    }

    private val adaptiveSpeedCheckbox = JCheckBox("Adaptive speed").apply {
        isSelected = settings.adaptiveSpeed
        addActionListener { settings.adaptiveSpeed = isSelected }
        toolTipText = "Automatically adjust animation speed based on typing speed"
    }

    private val sliderComponents = mutableListOf<Pair<JSlider, JLabel>>()


    init {
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        var y = 0

        addComponent(createDescriptionPanel(
            "Smooth Caret Settings",
            "Configure the behavior and appearance of the smooth caret animation."
        ), y++)

        addComponent(createGroupPanel("Basic Settings") {
            add(blinkingCheckbox)
        }, y++)


        // Animation settings
        addComponent(createGroupPanel("Animation Settings") {
            add(createLabeledSlider(
                "Animation smoothness",
                "Controls how smooth the caret movement is (lower = smoother)",
                5, 30,
                (settings.smoothness * 100).toInt()
            ) { settings.smoothness = it / 100f })

            add(Box.createVerticalStrut(10))

            add(createLabeledSlider(
                "Catch-up speed",
                "How quickly the caret catches up during normal typing",
                30, 80,
                (settings.catchupSpeed * 100).toInt()
            ) { settings.catchupSpeed = it / 100f })

            add(Box.createVerticalStrut(10))

            add(createLabeledSlider(
                "Max catch-up speed",
                "Maximum speed for catching up during fast typing",
                50, 100,
                (settings.maxCatchupSpeed * 100).toInt()
            ) { settings.maxCatchupSpeed = it / 100f })

            add(Box.createVerticalStrut(10))

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
        // Show confirmation dialog
        val result = JOptionPane.showConfirmDialog(
            panel,
            "Are you sure you want to reset all settings to their default values?",
            "Reset Settings",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            settings.resetToDefaults()

            // Update UI components
            blinkingCheckbox.isSelected = settings.isBlinking
            adaptiveSpeedCheckbox.isSelected = settings.adaptiveSpeed

            // Update all sliders
            sliderComponents.forEach { (slider, label) ->
                when (slider.toolTipText) {
                    "Animation smoothness" -> {
                        slider.value = (settings.smoothness * 100).toInt()
                        label.text = "${slider.value}%"
                    }
                    "Catch-up speed" -> {
                        slider.value = (settings.catchupSpeed * 100).toInt()
                        label.text = "${slider.value}%"
                    }
                    "Max catch-up speed" -> {
                        slider.value = (settings.maxCatchupSpeed * 100).toInt()
                        label.text = "${slider.value}%"
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
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                    title,
                    TitledBorder.LEFT,
                    TitledBorder.TOP
                ),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
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
        onChange: (Int) -> Unit
    ): JPanel {
        return JPanel(GridBagLayout()).apply {
            val valueLabel = JLabel("$initial%")
            val slider = JSlider(min, max, initial).apply {
                toolTipText = tooltip
                addChangeListener {
                    onChange(value)
                    valueLabel.text = "$value%"
                }
            }

            sliderComponents.add(Pair(slider, valueLabel))

            add(JLabel(label).apply {
                toolTipText = tooltip
            }, GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                gridwidth = 2
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
            })

            add(slider, GridBagConstraints().apply {
                gridx = 0
                gridy = 1
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
            })

            add(valueLabel, GridBagConstraints().apply {
                gridx = 1
                gridy = 1
                insets = Insets(0, 5, 0, 0)
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
            insets = Insets(0, 0, 5, 0)
        })
    }

    fun getPanel(): JPanel = panel

    fun isModified(): Boolean = true

    fun apply() {
        // Changes are applied immediately
    }
}