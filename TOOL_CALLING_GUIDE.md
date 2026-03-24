# Tool-Calling Integration Guide

## Overview
This document describes the tool-calling integration implemented in the Algsoch app using the RunAnywhere SDK.

## What Was Implemented

### 1. **ToolRegistry Service** (`ToolRegistry.kt`)
Central registry for all available tools in the RunAnywhere SDK.

**Available Tools:**
- `get_weather` - Gets current weather for a location using Open-Meteo API
- `get_current_time` - Gets current date, time, and timezone information  
- `calculate` - Performs mathematical calculations (+, -, *, /, parentheses)

**Key Methods:**
```kotlin
// Get all available tools
ToolRegistry.getAvailableTools(): List<ToolDefinitionMetadata>

// Register all tools
ToolRegistry.registerAllTools()

// Get tool by ID
ToolRegistry.getToolMetadata(toolId: String)

// Get tools by category
ToolRegistry.getToolsByCategory(category: String)
```

### 2. **Enhanced Custom Mode Creation**
Users can now create custom modes that leverage tool-calling capabilities.

**Features:**
- Select multiple tools for a custom mode
- Search and filter tools by name/description
- View tool parameters and categories
- Save custom modes with selected tools

**Sample Pre-Built Modes:**
1. **Smart Assistant** - Access to all tools (weather, time, calculator)
2. **Calculator** - Specialized math mode
3. **Weather Expert** - Weather-focused analysis

### 3. **Tool-Calling Integration in Chat**
When a custom mode with enabled tools is selected:
- Messages are sent with `RunAnywhereToolCalling.generateWithTools()`
- Tools are automatically executed and results displayed
- Seamless integration with existing chat interface

**Flow:**
```
User Message 
  → Check if custom mode has enabled tools
  → If yes: Use RunAnywhereToolCalling.generateWithTools()
  → If no: Use standard AIInferenceService.generateAnswer()
  → Display response with tool results
```

### 4. **Enhanced UI Components**

#### CustomModeOption
- Display mode name and description
- Show enabled tools with build icon
- Visual indicator for selected mode

#### ToolItem
- Tool name and category badge
- Description and parameter information
- Visual selection feedback

### 5. **Supporting Services**

**ToolCallingFormatter** (`ToolCallingFormatter.kt`)
- Formats tool results for display
- Adds emojis and context
- Weather: 🌤️, Time: ⏰, Calculator: 🔢

## Usage Examples

### Creating a Custom Mode in UI
1. Open Mode Selector (bottom sheet)
2. Click "Create Custom Mode"
3. Enter name and description
4. Click "Selected Tools"
5. Choose tools (e.g., "Weather", "Calculator")
6. Click "Save"

### Using a Custom Mode
1. Select custom mode from Mode Selector
2. Type a message that requires tool usage
3. App automatically calls appropriate tools
4. Results displayed with the AI response

### Example Queries
- "What's the weather in Tokyo?" → Uses get_weather tool
- "Calculate 25 * 4 + 10" → Uses calculate tool
- "What time is it and what's the weather here?" → Uses both tools

## Architecture

```
AlgsochScreen
├── ToolRegistry (manages available tools)
├── AlgsochViewModel (orchestrates tool-calling)
│   └── RunAnywhereToolCalling (SDK integration)
├── CustomModeCreationDialog
│   ├── ToolSelectionDialog
│   └── ToolItem
├── ModeSelectorBottomSheet
│   └── CustomModeOption (displays tools)
└── InputBar (triggers messages)
```

## Key Files
- `services/ToolRegistry.kt` - Tool management
- `utils/ToolCallingFormatter.kt` - Result formatting
- `data/store/CustomModeStore.kt` - Custom mode persistence
- `ui/screens/algsoch/AlgsochScreen.kt` - UI integration
- `ui/screens/algsoch/AlgsochViewModel.kt` - Logic & tool-calling

## SDK Integration Points
1. `ToolRegistry.registerAllTools()` - Registers tools with SDK
2. `RunAnywhereToolCalling.registerTool()` - Individual tool registration
3. `RunAnywhereToolCalling.generateWithTools()` - Generate with tools enabled

## Future Enhancements
1. Persistent custom mode storage (database)
2. Custom tool registration by users
3. Vision mode integration into chat
4. Tool usage analytics
5. Advanced tool filtering and search
6. Tool execution timeout handling

