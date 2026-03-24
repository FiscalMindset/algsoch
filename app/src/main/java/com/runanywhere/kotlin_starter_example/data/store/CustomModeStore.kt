package com.runanywhere.kotlin_starter_example.data.store

import android.content.Context
import com.runanywhere.kotlin_starter_example.data.models.custom.CustomMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent store for user-defined custom modes.
 */
object CustomModeStore {
    private const val PREFS_NAME = "algsoch_custom_modes"
    private const val KEY_MODES_JSON = "custom_modes_json"

    private var initialized = false
    private var appContext: Context? = null

    private val builtInModes = listOf(
        CustomMode(
            id = "study_coach",
            name = "Study Coach",
            description = "Advanced tutoring for all subjects with deep explanations",
            basePrompt = "You are an expert study coach and tutor for high school and college students across ALL subjects: Mathematics, Physics, Chemistry, Biology, History, Literature, Computer Science, Economics, Philosophy, Art History, and Languages. Your mission is to provide COMPREHENSIVE, DETAILED, and ADVANCED explanations that help students understand concepts deeply, not just memorize. Always: (1) Start with a clear definition/overview, (2) Provide historical context or real-world applications, (3) Explain underlying principles and mechanisms, (4) Give concrete examples and case studies, (5) Connect to related concepts, (6) Address common misconceptions, (7) Suggest revision strategies and study tips. Make responses suitable for 1200+ students of varying levels. Be thorough, academic, and educational.",
            enabledTools = listOf("summarize_text", "create_quiz")
        )
    )

    private val customModes = mutableListOf<CustomMode>()

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        loadPersistedModes()
        initialized = true
    }

    fun addMode(mode: CustomMode, context: Context? = null) {
        if (context != null) initialize(context)
        customModes.removeAll { it.id == mode.id }
        customModes.add(mode)
        persistModes()
    }

    fun getModes(): List<CustomMode> {
        val customOnly = customModes.filterNot { custom -> builtInModes.any { it.id == custom.id } }
        return builtInModes + customOnly
    }

    fun getModeById(id: String): CustomMode? = getModes().find { it.id == id }

    fun removeMode(id: String) {
        if (builtInModes.any { it.id == id }) return
        customModes.removeAll { it.id == id }
        persistModes()
    }

    private fun loadPersistedModes() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MODES_JSON, "[]") ?: "[]"
        customModes.clear()

        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val enabledToolsArray = obj.optJSONArray("enabledTools") ?: JSONArray()
                val enabledTools = mutableListOf<String>()
                for (j in 0 until enabledToolsArray.length()) {
                    enabledTools.add(enabledToolsArray.optString(j))
                }

                customModes.add(
                    CustomMode(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        description = obj.optString("description"),
                        basePrompt = obj.optString("basePrompt"),
                        enabledTools = enabledTools
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore corrupt state and continue with built-ins only.
        }
    }

    private fun persistModes() {
        val context = appContext ?: return
        val builtInIds = builtInModes.map { it.id }.toSet()
        val persistable = customModes.filterNot { it.id in builtInIds }

        val arr = JSONArray()
        persistable.forEach { mode ->
            arr.put(
                JSONObject().apply {
                    put("id", mode.id)
                    put("name", mode.name)
                    put("description", mode.description)
                    put("basePrompt", mode.basePrompt)
                    put("enabledTools", JSONArray(mode.enabledTools))
                }
            )
        }

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODES_JSON, arr.toString())
            .apply()
    }
}

