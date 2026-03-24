# Algsoch - Complete Explanation 🧠

## Overview

**Algsoch** is an advanced AI-powered study companion Android application that brings cutting-edge AI capabilities directly to your device. It's built using **RunAnywhere SDK** to enable 100% offline AI inference, ensuring your data stays completely private while providing instant, intelligent responses.

**Version:** 1.0.0  
**Platform:** Android 10+  
**Size:** ~45 MB (lightweight)  
**License:** Open Source

---

## What Problem Does Algsoch Solve?

Traditional study assistants and tutoring apps have several limitations:
- ❌ Require constant internet connection
- ❌ Upload sensitive data to cloud servers
- ❌ Slow responses due to network latency
- ❌ Limited to rigid question-answer formats
- ❌ Expensive subscriptions

**Algsoch solves all of these:**
- ✅ 100% offline - works anywhere, anytime
- ✅ Zero cloud uploads - complete privacy
- ✅ Sub-second responses - instant help
- ✅ Adaptive learning modes - learn your way
- ✅ Completely free - no hidden costs

---

## Key Features

### 🎓 7 Adaptive Learning Modes

Algsoch adapts to your learning style through 7 different response modes:

| Mode | Use Case | Example |
|------|----------|---------|
| **Direct** 💬 | Quick, concise answers | "What is photosynthesis?" → Direct answer |
| **Answer** ✓ | Focused, well-structured responses | "Explain this concept" → Structured answer |
| **Explain** 📖 | Deep, educational breakdowns | "Help me understand calculus" → Step-by-step explanation |
| **Notes** 📝 | Study notes in bullet format | "Summarize for exam" → Formatted study notes |
| **Direction** 🧭 | Problem-solving guidance | "How do I solve this?" → Approach guidance |
| **Creative** 💡 | Analogies and real-world examples | "Make it relatable" → Creative explanations |
| **Theory** 🔬 | Advanced conceptual deep-dives | "Theoretical aspects" → In-depth theory |

### 📸 Image Upload & Analysis
- Upload photos of handwritten notes
- Capture diagrams, equations, charts
- Get instant explanations via SmolVLM (Vision AI)
- Works completely offline

### 💾 Local Chat History
- All conversations stored on your device
- Review past learning sessions anytime
- Search through your study history
- Completely encrypted and private

### ⚡ Lightning Fast Responses
- Sub-second latency
- No network delays
- Instant model inference
- Smooth user experience

### 🔒 100% Private & Secure
- All AI runs on your device
- Zero data uploads to servers
- No tracking or analytics
- Your conversations are yours alone

---

## Technical Architecture

### 🏗️ Project Structure

```
app/src/main/
├── java/com/algsoch/
│   ├── MainActivity.kt                    # App entry point
│   ├── data/
│   │   ├── models/
│   │   │   ├── Message.kt                # Chat message entity
│   │   │   ├── ChatSession.kt            # Chat session data
│   │   │   └── UserPreferences.kt        # User settings
│   │   └── repository/
│   │       ├── ChatRepository.kt         # Chat data management
│   │       └── ModelRepository.kt        # Model management
│   ├── domain/
│   │   ├── ai/
│   │   │   ├── PromptBuilder.kt         # Builds prompts for 7 modes
│   │   │   ├── ResponseParser.kt        # Parses AI responses
│   │   │   └── SystemPrompts.kt         # 7 system prompt templates
│   │   └── models/
│   │       ├── LearningMode.kt          # Mode enum
│   │       └── StructuredResponse.kt    # Response structure
│   ├── services/
│   │   ├── ModelService.kt              # Model registration & management
│   │   ├── AIInferenceService.kt        # AI inference engine
│   │   ├── ModelDownloadManager.kt      # Model download handling
│   │   └── AIChatService.kt             # Chat operations
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── HomeScreen.kt            # Mode selection
│   │   │   ├── ChatScreen.kt            # Chat interface
│   │   │   └── SettingsScreen.kt        # User settings
│   │   ├── components/
│   │   │   ├── MessageBubble.kt         # Chat message display
│   │   │   ├── ModeSelector.kt          # Mode selection UI
│   │   │   └── ModelLoadingScreen.kt    # Model download UI
│   │   └── theme/
│   │       └── AlgsochTheme.kt          # Material 3 theming
│   └── viewmodel/
│       └── AlgsochViewModel.kt          # MVVM state management
└── resources/
    ├── values/
    │   ├── strings.xml                  # String resources
    │   └── colors.xml                   # Color definitions
    └── drawable/                        # App icons & assets
```

### 🛠️ Technical Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **UI Framework** | Jetpack Compose | Modern declarative UI |
| **Design System** | Material Design 3 | Material 3 components |
| **Navigation** | Navigation Compose | Screen navigation |
| **State Management** | ViewModel + StateFlow | Lifecycle-aware state |
| **Async Operations** | Coroutines + Flow | Async tasks & streams |
| **Local Database** | JSON File Storage | Chat history storage |
| **AI Engine** | RunAnywhere SDK | On-device inference |
| **Language** | Kotlin | Modern, safe code |

### 🤖 AI Models Used

| Model | Purpose | Size | Framework |
|-------|---------|------|-----------|
| **SmolLM2-360M** | Text generation (chat) | ~300 MB | llama.cpp |
| **SmolVLM-256M** | Image understanding | ~200 MB | ONNX |
| **Whisper** | Speech recognition | ~140 MB | ONNX (optional) |

All models are:
- ✅ Quantized for mobile (Q8_0 precision)
- ✅ Optimized for Snapdragon processors
- ✅ Downloaded on-demand to save storage
- ✅ Cached locally for quick access

---

## How Algsoch Works

### Step 1: Initialize RunAnywhere SDK
When the app launches, it initializes the RunAnywhere SDK, which manages AI models and inference:

```
App Start → RunAnywhere Init → Model Registry → Ready for Chat
```

### Step 2: Download AI Models
On first run, users download AI models to their device (~250-300 MB):

```
User Tap Download → Download starts (shows progress) → Model cached locally → Ready
```

### Step 3: User Selects Learning Mode
User chooses from 7 different learning modes based on their needs:

```
Home Screen → Select Mode (7 options) → Mode selected → Go to Chat
```

### Step 4: User Sends Message
User types question, optionally uploads image, and sends:

```
Type Question → Optional: Upload Image → Send → Message stored locally
```

### Step 5: AI Inference on Device
RunAnywhere SDK runs inference using selected mode's system prompt:

```
Prompt Builder (adds system prompt for mode) → SmolLM2/SmolVLM → Inference
```

### Step 6: Response Processing & Display
Response is processed, formatted, and displayed in chat:

```
Raw Response → ResponseParser → Format for UI → Display in Chat → Save History
```

### Step 7: Complete Chat Loop
Conversation continues with full context, all locally stored:

```
New User Message → Include Chat History → AI Inference → Display Response → Loop
```

---

## RunAnywhere SDK Integration

### Why RunAnywhere SDK?

RunAnywhere SDK is the backbone of Algsoch's on-device AI capabilities:

- **Model Management**: Handles downloading, caching, and updating models
- **Inference Engine**: Executes models efficiently on mobile hardware
- **Multiple Frameworks**: Supports llama.cpp, ONNX, and more
- **Privacy**: All processing happens on your device, zero network calls
- **Performance**: Optimized models run at sub-second latency

### SDK Integration Points

1. **Model Registration**: Models are registered at app startup
2. **Download Management**: SDK handles model downloads with progress tracking
3. **Inference Calling**: Simple API to run inference with custom prompts
4. **Error Handling**: Automatic retry and fallback mechanisms

---

## Installation & Usage Guide

### 📥 Installation (5 Steps)

#### Step 1: Download APK
- Go to: https://github.com/FiscalMindset/algsoch/releases
- Download the APK file (~45 MB)

#### Step 2: Enable Unknown Sources
On your Android device:
1. Open **Settings**
2. Go to **Apps & Notifications** → **Advanced**
3. Select **Install unknown apps**
4. Choose your file manager and **Enable**

#### Step 3: Install the App
1. Open **Downloads** folder
2. Tap the APK file
3. Follow installation prompts
4. Wait for installation to complete (~1 minute)

#### Step 4: Download AI Models ⚠️ IMPORTANT
1. Launch Algsoch
2. Tap **"Load"** to download models (~250 MB)
3. Wait for download (2-3 minutes)
4. Models are now cached for offline use

#### Step 5: Start Learning
1. Select your preferred **Learning Mode**
2. Ask any question
3. Enjoy instant offline responses!

### 🎯 Usage Tips

- **Pick Your Mode**: Different modes for different needs (direct, explain, notes, etc.)
- **Upload Images**: Click upload to add diagrams, notes, or equations
- **Review History**: Swipe left to see chat history (all stored locally)
- **No Internet Needed**: Everything works completely offline
- **Settings**: Customize response tone, language, and more

---

## Device Requirements

### Minimum Requirements
- **OS**: Android 10 or higher
- **Storage**: 500 MB free space
- **RAM**: 1.5 GB minimum
- **Processor**: ARM64

### Recommended Requirements
- **OS**: Android 12+
- **Storage**: 1 GB free space
- **RAM**: 4 GB+
- **Processor**: Snapdragon 765+
- **Network**: Fast WiFi for initial model download

### What Gets Downloaded
- SmolLM2 (text model): ~300 MB
- SmolVLM (vision model): ~200 MB
- **Total**: ~500 MB (downloaded once, cached)

---

## Key Differentiators

### vs. ChatGPT/Cloud AI
| Feature | Algsoch | ChatGPT |
|---------|---------|---------|
| Offline | ✅ 100% | ❌ Requires internet |
| Privacy | ✅ On-device | ⚠️ Uploaded to servers |
| Cost | ✅ Free | ⚠️ Paid subscription |
| Speed | ✅ Sub-second | ⚠️ Network latency |
| Uses Data | ✅ No tracking | ❌ Data collection |

### vs. Traditional Tutoring Apps
| Feature | Algsoch | Traditional Apps |
|---------|---------|-----------------|
| Offline | ✅ 100% | ❌ Cloud-dependent |
| Learning Modes | ✅ 7 modes | ⚠️ Limited options |
| Real-time | ✅ Instant | ⚠️ Scheduled only |
| Privacy | ✅ Complete | ⚠️ Data shared |
| Cost | ✅ Free forever | ❌ Monthly fees |

---

## Understanding the 7 Learning Modes

### 1️⃣ Direct Mode 💬
**Best for:** Quick reference, simple answers  
**Response type:** Concise, straight to the point  
**Example:**
```
User: "What is photosynthesis?"
AI: "Photosynthesis is the process by which plants convert light into 
chemical energy using chlorophyll."
```

### 2️⃣ Answer Mode ✓
**Best for:** Well-structured, focused responses  
**Response type:** Clear, organized information  
**Example:**
```
User: "Explain the water cycle"
AI: 
• Evaporation: Water from surface evaporates
• Condensation: Water vapor condenses into clouds
• Precipitation: Water falls as rain/snow
• Collection: Water collects in oceans/lakes
```

### 3️⃣ Explain Mode 📖
**Best for:** Deep understanding, detailed breakdowns  
**Response type:** Comprehensive, educational  
**Example:**
```
User: "Help me understand calculus"
AI: "Calculus is a branch of mathematics that studies change...
[Full detailed explanation with examples]"
```

### 4️⃣ Notes Mode 📝
**Best for:** Study preparation, exam review  
**Response type:** Formatted study notes  
**Example:**
```
User: "Summarize evolution for my exam"
AI:
📌 KEY POINTS:
  • Darwin's theory based on natural selection
  • Evolution happens over millions of years
  • Adaptation is key to survival
  
🎓 REMEMBER:
  • Common ancestors across species
  • Variation drives natural selection
```

### 5️⃣ Direction Mode 🧭
**Best for:** Problem-solving, learning approach  
**Response type:** Guidance, not just answers  
**Example:**
```
User: "How do I solve this quadratic equation?"
AI: "Here's the approach to follow:
1. Identify a, b, c coefficients
2. Use quadratic formula: x = -b ± √(b²-4ac) / 2a
3. Substitute your values
4. Simplify to get two solutions"
```

### 6️⃣ Creative Mode 💡
**Best for:** Better understanding, memorable learning  
**Response type:** Analogies, analogies & real-world examples  
**Example:**
```
User: "Explain how a computer processor works"
AI: "Think of a CPU like a chef in a kitchen. The recipes 
are instructions (code), ingredients are data... [creative analogy]"
```

### 7️⃣ Theory Mode 🔬
**Best for:** Advanced topics, deep conceptual understanding  
**Response type:** In-depth theory and principles  
**Example:**
```
User: "Explain quantum mechanics"
AI: "Quantum mechanics is built on several foundational principles:
1. Wave-particle duality
2. Uncertainty principle
3. Superposition...
[Advanced theoretical explanation]"
```

---

## Privacy & Security

### 🔒 What Stays Local

Everything about your usage stays on your device:
- ✅ All chat messages and history
- ✅ User preferences and settings
- ✅ Downloaded AI models
- ✅ Uploaded images and documents
- ✅ Learning pattern data

### 📡 What We Don't Do

Algsoch **NEVER**:
- ❌ Sends data to any server
- ❌ Collects usage analytics
- ❌ Tracks your conversations
- ❌ Stores your location
- ❌ Requires account/login
- ❌ Shows advertisements

### 🛡️ Data Encryption

- Chat history encrypted locally using AES-256
- Model files verified with SHA-256 checksums
- All on-device data stored in app's private storage
- Accessible only by Algsoch application

---

## Project Statistics

| Metric | Value |
|--------|-------|
| **Total Code** | 5,000+ lines |
| **Kotlin Files** | 50+ files |
| **UI Components** | 30+ Compose components |
| **Learning Modes** | 7 different modes |
| **AI Models** | 3 models (LLM, Vision, Speech) |
| **App Size** | ~45 MB |
| **Model Size** | ~500 MB (downloaded) |
| **Supported Devices** | Android 10+ |

---

## Development & Contribution

### Tech Stack Summary
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Clean Architecture
- **State Management**: ViewModel + StateFlow
- **Async**: Coroutines + Flow
- **AI**: RunAnywhere SDK v0.16.0-test.39

### Open Source
Algsoch is completely open-source and free. The complete source code is available at:
```
https://github.com/FiscalMindset/algsoch
```

### For Developers
Want to contribute or build upon Algsoch?
- Clone: `git clone https://github.com/FiscalMindset/algsoch.git`
- Build: Run in Android Studio (minimum SDK 29)
- Deploy: Build release APK or sideload debug APK
- Extend: Add new learning modes, improve UI, optimize performance

---

## FAQ

### Q: Does it really work offline?
**A:** Yes, 100% offline. All AI models run on your device using RunAnywhere SDK. No internet required.

### Q: How much storage do I need?
**A:** App requires ~500 MB for models. Chat history uses minimal storage (compressed text).

### Q: Is my data private?
**A:** Completely. All conversations stay on your device. We don't have servers or collect data.

### Q: Why is it free?
**A:** Education should be accessible to everyone. Algsoch is free and open-source forever.

### Q: Can I use it without downloading models?
**A:** No, models must be downloaded first (~250-300 MB). This happens only once.

### Q: Does it work on all Android devices?
**A:** Works on Android 10+. Requires at least 1.5 GB RAM. Better performance on 4GB+ RAM.

### Q: Can I delete my chat history?
**A:** Yes, everything is local. You can clear chats anytime - they're stored on your device only.

### Q: Is there a web version or iOS app?
**A:** Currently Android only. iOS version may come in future releases.

### Q: Can I use multiple accounts?
**A:** All chats are stored in the app. There's no account system — just local storage.

---

## Getting Started

### Quick Links
- 📥 **Download**: https://github.com/FiscalMindset/algsoch/releases
- 📖 **Documentation**: https://github.com/FiscalMindset/algsoch/blob/main/README.md
- 🐛 **Report Issues**: https://github.com/FiscalMindset/algsoch/issues
- 💬 **Discuss**: https://github.com/FiscalMindset/algsoch/discussions

### Creator
**Vicky Kumar (@algsoch)**  
AI Engineer & Creator  
Passionate about building privacy-first, intelligent learning tools

---

## License & Attribution

**Algsoch** is open-source and released under a permissive license.  
**Powered by**: [RunAnywhere SDK](https://www.runanywhere.ai/)

---

Made with ❤️ for learners everywhere.  
Learn Smarter. Not Harder. 🧠

**Version 1.0.0** | March 2026
