package com.example.codelabapp.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.application.ApplicationManager
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextField

class SettingsConfigurable : Configurable {
    private val textField = JTextField()
    private val apiUrlField = JTextField()
    private val modelField = JTextField()
    private val apiKeyField = JPasswordField()
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Codelab Settings"

    override fun createComponent(): JComponent {
        if (panel == null) {
            val root = JPanel(GridLayout(4, 2, 8, 8))
            root.add(JLabel("Value:"))
            root.add(textField)
            root.add(JLabel("Model API URL:"))
            root.add(apiUrlField)
            root.add(JLabel("Model Name:"))
            root.add(modelField)
            root.add(JLabel("API Key:"))
            root.add(apiKeyField)
            panel = root
        }
        return panel as JPanel
    }

    override fun isModified(): Boolean {
        val state = SettingsState.getInstance().state
        return textField.text != state.textValue ||
            apiUrlField.text != state.modelApiUrl ||
            modelField.text != state.modelName ||
            String(apiKeyField.password) != state.apiKey
    }

    override fun apply() {
        val state = SettingsState.getInstance().state
        state.textValue = textField.text
        state.modelApiUrl = apiUrlField.text.trim()
        state.modelName = modelField.text.trim()
        state.apiKey = String(apiKeyField.password)

        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(SettingsTopic.TOPIC)
            .settingsChanged()
    }

    override fun reset() {
        val state = SettingsState.getInstance().state
        textField.text = state.textValue
        apiUrlField.text = state.modelApiUrl
        modelField.text = state.modelName
        apiKeyField.text = state.apiKey
    }
}
