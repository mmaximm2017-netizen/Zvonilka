package ru.zvonilka.prototype

import android.content.Context
import android.content.res.Configuration

object ThemeSettings {
    fun mode(context: Context): String = context.getSharedPreferences("settings",0).getString("theme","system") ?: "system"
    fun set(context: Context, mode: String) {
        require(mode in setOf("system","light","dark"))
        context.getSharedPreferences("settings",0).edit().putString("theme",mode).apply()
    }
    fun wrap(context: Context): Context {
        val night=when(mode(context)) { "light" -> Configuration.UI_MODE_NIGHT_NO; "dark" -> Configuration.UI_MODE_NIGHT_YES; else -> return context }
        val config=Configuration(context.resources.configuration)
        config.uiMode=(config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return context.createConfigurationContext(config)
    }
}
