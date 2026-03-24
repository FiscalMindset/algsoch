package com.runanywhere.kotlin_starter_example.services

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Central registry for all available tools in Algsoch.
 * This handles tool definitions for the UI and mapping to SDK tools.
 */
object ToolRegistry {
    
    data class ToolDefinition(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val icon: ImageVector,
        val parametersDescription: String
    )
    
    /**
     * Get list of all available tools for UI display
     */
    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                id = "get_weather",
                name = "Weather",
                description = "Get current weather conditions for any city",
                category = "Information",
                icon = Icons.Rounded.WbSunny,
                parametersDescription = "city: String"
            ),
            ToolDefinition(
                id = "calculate",
                name = "Calculator",
                description = "Perform complex math and unit conversions",
                category = "Utility",
                icon = Icons.Rounded.Calculate,
                parametersDescription = "expression: String"
            ),
            ToolDefinition(
                id = "get_time",
                name = "World Time",
                description = "Get current date and time for any timezone",
                category = "Information",
                icon = Icons.Rounded.Schedule,
                parametersDescription = "timezone: String (Optional)"
            ),
            ToolDefinition(
                id = "web_search",
                name = "Web Search",
                description = "Search the internet for real-time information",
                category = "Knowledge",
                icon = Icons.Rounded.Language,
                parametersDescription = "query: String"
            )
        )
    }
    
    fun getToolById(id: String): ToolDefinition? = getAvailableTools().find { it.id == id }
}
