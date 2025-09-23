package com.example.codelabapp.settings

import com.intellij.util.messages.Topic

fun interface SettingsChangedListener {
    fun settingsChanged()
}

object SettingsTopic {
    val TOPIC: Topic<SettingsChangedListener> =
        Topic.create("CodelabSettingsChanged", SettingsChangedListener::class.java)
}
