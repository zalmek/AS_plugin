package com.example.codelabapp.toolwindow

import com.example.codelabapp.settings.SettingsState
import com.example.codelabapp.settings.SettingsChangedListener
import com.example.codelabapp.settings.SettingsTopic
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultCaret
import javax.swing.text.View
import kotlin.math.ceil

class ValueToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val statusLabel = JLabel()
        val promptArea = JTextArea(1, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.ALWAYS_UPDATE
        }
        val outputArea = JEditorPane("text/html", "").apply {
            isEditable = false
            (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.ALWAYS_UPDATE
        }
        val buttonBlue = Color(0x1E, 0x88, 0xE5)
        val buttonBlueDim = Color(0x90, 0xAF, 0xCF)
        val sendButton = JButton("Send").apply {
            isOpaque = false
            isContentAreaFilled = true
            background = buttonBlue
            foreground = Color.WHITE
            isFocusPainted = false
            border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
            preferredSize = Dimension(0, 30)
            minimumSize = Dimension(0, 30)
            maximumSize = Dimension(Int.MAX_VALUE, 30)
        }
        val markdownParser = Parser.builder().build()
        val markdownRenderer = HtmlRenderer.builder().escapeHtml(true).build()

        val promptScrollPane = JScrollPane(promptArea).apply {
            minimumSize = Dimension(120, promptArea.getFontMetrics(promptArea.font).height + 12)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        val outputScrollPane = JScrollPane(outputArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        val inputPanel = JPanel(BorderLayout(8, 8)).apply {
            add(JLabel("Prompt:"), BorderLayout.NORTH)
            add(promptScrollPane, BorderLayout.CENTER)
            add(sendButton, BorderLayout.SOUTH)
        }

        val panel = JPanel(BorderLayout(8, 8)).apply {
            add(statusLabel, BorderLayout.NORTH)
            add(outputScrollPane, BorderLayout.CENTER)
            add(inputPanel, BorderLayout.SOUTH)
        }

        val minPromptRows = 1
        val maxPromptRows = 10

        fun updatePromptHeight() {
            val width = promptScrollPane.viewport.width
                .minus(promptArea.insets.left + promptArea.insets.right)
                .coerceAtLeast(1)
            val rootView = promptArea.ui.getRootView(promptArea)
            rootView.setSize(width.toFloat(), Int.MAX_VALUE.toFloat())

            val rowHeight = promptArea.getFontMetrics(promptArea.font).height.coerceAtLeast(1)
            val preferredHeight = ceil(rootView.getPreferredSpan(View.Y_AXIS).toDouble()).toInt()
            val rows = (preferredHeight / rowHeight)
                .coerceIn(minPromptRows, maxPromptRows)

            if (promptArea.rows != rows) {
                promptArea.rows = rows
                promptArea.revalidate()
                promptScrollPane.revalidate()
                inputPanel.revalidate()
            }
        }

        fun setOutputMarkdown(text: String) {
            val document = markdownParser.parse(text)
            val htmlBody = markdownRenderer.render(document)
            outputArea.text = """
                <html>
                  <body style="font-family: -apple-system, Segoe UI, sans-serif; font-size: 12px; margin: 10px;">
                    $htmlBody
                  </body>
                </html>
            """.trimIndent()
            outputArea.caretPosition = 0
        }

        promptArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updatePromptHeight()
            override fun removeUpdate(e: DocumentEvent?) = updatePromptHeight()
            override fun changedUpdate(e: DocumentEvent?) = updatePromptHeight()
        })

        promptScrollPane.viewport.addChangeListener {
            updatePromptHeight()
        }

        fun setSendButtonLoading(isLoading: Boolean) {
            sendButton.isEnabled = !isLoading
            sendButton.background = if (isLoading) buttonBlueDim else buttonBlue
            sendButton.text = if (isLoading) "Sending..." else "Send"
        }

        fun refreshStatus() {
            val settings = SettingsState.getInstance().state
            statusLabel.text = when {
                settings.modelApiUrl.isBlank() -> "Model API URL is not set in Settings"
                settings.apiKey.isBlank() -> "API Key is not set in Settings"
                else -> "Model API URL and API Key configured"
            }
        }

        project.messageBus.connect(toolWindow.contentManager).subscribe(
            SettingsTopic.TOPIC,
            object : SettingsChangedListener {
                override fun settingsChanged() {
                    refreshStatus()
                }
            }
        )

        sendButton.addActionListener {
            val prompt = promptArea.text.trim()
            val settings = SettingsState.getInstance().state
            val apiUrl = settings.modelApiUrl.trim()
            val apiKey = settings.apiKey.trim()
            val modelName = settings.modelName.trim()
            if (apiUrl.isEmpty()) {
                setOutputMarkdown("Model API URL is empty. Set it in Settings.")
                return@addActionListener
            }
            if (apiKey.isEmpty()) {
                setOutputMarkdown("API Key is empty. Set it in Settings.")
                return@addActionListener
            }
            if (modelName.isEmpty()) {
                setOutputMarkdown("Model Name is empty. Set it in Settings.")
                return@addActionListener
            }
            if (prompt.isEmpty()) {
                setOutputMarkdown("Prompt is empty.")
                return@addActionListener
            }

            setSendButtonLoading(true)
            setOutputMarkdown("Requesting...")

            ApplicationManager.getApplication().executeOnPooledThread {
                val responseText = requestModel(apiUrl, apiKey, modelName, prompt)
                SwingUtilities.invokeLater {
                    setOutputMarkdown(responseText)
                    setSendButtonLoading(false)
                }
            }
        }

        refreshStatus()
        updatePromptHeight()

        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    private fun requestModel(apiUrl: String, apiKey: String, modelName: String, prompt: String): String {
        return try {
            val payload = """
                {
                  "model": "${escapeJson(modelName)}",
                  "messages": [
                    {"role":"user","content":"${escapeJson(prompt)}"}
                  ]
                }
            """.trimIndent()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()

            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                "Request failed: HTTP ${response.statusCode()}\n${response.body()}"
            } else {
                extractAssistantText(response.body())
            }
        } catch (e: Exception) {
            "Request error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun extractAssistantText(body: String): String {
        return try {
            val root = JsonParser.parseString(body)
            val message = root.asJsonObject
                .getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")

            if (message == null) {
                body
            } else {
                val reasoning = flattenContent(message.get("reasoning_content"))
                val content = flattenContent(message.get("content"))
                when {
                    reasoning.isNotBlank() && content.isNotBlank() -> "Reasoning:\n$reasoning\n\nAnswer:\n$content"
                    content.isNotBlank() -> content
                    reasoning.isNotBlank() -> reasoning
                    else -> body
                }
            }
        } catch (_: Exception) {
            body
        }
    }

    private fun flattenContent(element: JsonElement?): String {
        if (element == null || element.isJsonNull) {
            return ""
        }
        return when {
            element.isJsonPrimitive -> element.asString
            element.isJsonArray -> element.asJsonArray
                .mapNotNull { item ->
                    when {
                        item.isJsonPrimitive -> item.asString
                        item.isJsonObject -> item.asJsonObject.get("text")?.asString
                        else -> null
                    }
                }
                .joinToString("\n")
            else -> ""
        }.trim()
    }

    private fun escapeJson(text: String): String {
        return buildString(text.length + 16) {
            for (ch in text) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }
}
