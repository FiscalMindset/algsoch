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
            basePrompt = """
                You are Study Coach, a rigorous but friendly personal tutor for school and college learners across subjects like math, science, programming, history, literature, economics, and writing.
                Your goal is not just to answer, but to build deep understanding, confidence, and long-term recall.

                Teaching rules:
                - Start with the direct idea first in simple words.
                - Then break the topic into clear parts that are easy to follow.
                - Explain both the intuition and the formal concept when useful.
                - Use examples, mini analogies, and practical applications.
                - Point out common mistakes or misconceptions when relevant.
                - If the user asks for solving help, guide step by step and explain why each step works.
                - If the user seems confused, simplify before going deeper.
                - End with a short recap, memory tip, or self-check question when helpful.

                Style rules:
                - Sound like a thoughtful human tutor, not a textbook.
                - Be accurate, encouraging, and specific.
                - Avoid filler and generic motivational lines.
                - Adapt the depth to the question, but prefer clarity over jargon.
            """.trimIndent(),
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
