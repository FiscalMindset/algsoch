package com.runanywhere.kotlin_starter_example.utils

/**
 * Utility functions for formatting tool-calling results for display
 */
object ToolCallingFormatter {
    
    /**
     * Format a tool result for display in the chat
     */
    fun formatToolResult(toolName: String, result: String): String {
        return when (toolName.lowercase()) {
            "get_weather" -> formatWeatherResult(result)
            "get_current_time" -> formatTimeResult(result)
            "calculate" -> formatCalculationResult(result)
            else -> result
        }
    }
    
    private fun formatWeatherResult(result: String): String {
        return try {
            // Parse weather result and format nicely
            buildString {
                append("🌤️ ")
                append(result)
            }
        } catch (e: Exception) {
            result
        }
    }
    
    private fun formatTimeResult(result: String): String {
        return try {
            buildString {
                append("⏰ ")
                append(result)
            }
        } catch (e: Exception) {
            result
        }
    }
    
    private fun formatCalculationResult(result: String): String {
        return try {
            buildString {
                append("🔢 Result: ")
                append(result)
            }
        } catch (e: Exception) {
            result
        }
    }
}

