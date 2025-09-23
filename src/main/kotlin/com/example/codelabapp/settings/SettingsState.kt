package com.example.codelabapp.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(name = "CodelabSettingsState", storages = [Storage("codelab-settings.xml")])
class SettingsState : PersistentStateComponent<SettingsState.State> {
    data class State(
        var textValue: String = "",
        var apiKey: String = "",
        var modelApiUrl: String = "https://api.z.ai/api/paas/v4/chat/completions",
        var modelName: String = "glm-4.7-flash"
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): SettingsState = service()
    }
}
