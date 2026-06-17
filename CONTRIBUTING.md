# Contributing to Algsoch

Thank you for considering contributing to Algsoch! We welcome bug fixes, new features, documentation improvements, and anything else that makes Algsoch better.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Code Style](#code-style)
- [Reporting Bugs](#reporting-bugs)
- [Feature Requests](#feature-requests)

## Code of Conduct

By participating in this project, you agree to maintain a respectful, inclusive, and harassment-free environment for everyone.

## Getting Started

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/YOUR_USERNAME/algsoch.git
   cd algsoch
   ```
3. Add upstream remote:
   ```bash
   git remote add upstream https://github.com/FiscalMindset/algsoch.git
   ```

## Development Setup

### Prerequisites

| Tool | Version |
|------|---------|
| **Android Studio** | Hedgehog (2023.1.1) or later |
| **Minimum SDK** | API 26 (Android 8.0) |
| **Target SDK** | API 35 (Android 15) |
| **Kotlin** | 2.0.21 or later |
| **Java** | 17 |
| **Gradle** | 8.x |

### Build & Run

```bash
# Open in Android Studio
File → Open → Select the project folder

# Sync Gradle
# Android Studio will prompt to sync - click "Sync Now"

# Run the app
# Connect a device or start an emulator
# Click the Run button (▶️) in Android Studio
```

## How to Contribute

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

Use a descriptive branch name:
- `feature/vision-mode` — new feature
- `fix/download-retry` — bug fix
- `docs/readme-update` — documentation

### 2. Make Your Changes

- Follow the [Code Style](#code-style) guidelines
- Keep changes focused and atomic
- Write meaningful commit messages

### 3. Commit Your Changes

```bash
git add .
git commit -m "Add your feature description"
```

**Commit message format:**
- Use present tense ("Add feature" not "Added feature")
- Use imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit first line to 72 characters
- Reference issues and pull requests where applicable

### 4. Push to Your Fork

```bash
git push origin feature/your-feature-name
```

### 5. Create a Pull Request

- Go to the [Algsoch repository](https://github.com/FiscalMindset/algsoch)
- Click **New Pull Request**
- Select your fork and branch
- Fill in the PR template with:
  - What your change does
  - How to test it
  - Any related issues

## Pull Request Process

1. **Ensure your code builds** — Run `./gradlew assembleDebug` before submitting
2. **Update documentation** — If your change requires README or docs updates
3. **Keep PRs small** — Focus on one feature or fix per PR
4. **Respond to feedback** — Be open to code review suggestions
5. **Squash commits** — If requested, squash into logical commits
6. **Wait for review** — We'll review as quickly as possible

### PR Review Checklist

- [ ] Code compiles without errors
- [ ] No new warnings introduced
- [ ] Follows existing code patterns
- [ ] Documentation updated if needed
- [ ] Changes are backward compatible

## Code Style

### Kotlin

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use **4-space indentation**
- Follow naming conventions:
  - `camelCase` for functions and variables
  - `PascalCase` for classes and objects
  - `UPPER_SNAKE_CASE` for constants
- Add meaningful type annotations
- Use `val` over `var` where possible

### XML

- Use **2-space indentation**
- Keep layouts flat where possible
- Use `dp` for dimensions, `sp` for text sizes

### Jetpack Compose

- Follow [Compose API guidelines](https://developer.android.com/jetpack/compose/api-guidelines)
- Extract reusable composables to separate functions
- Use `remember` and `derivedStateOf` judiciously
- Keep previews for UI components

## Reporting Bugs

When reporting a bug, please include:

1. **Description** — What happened vs what you expected
2. **Steps to Reproduce** — Minimal steps to trigger the bug
3. **Environment** — Android version, device model, app version
4. **Screenshots** — If applicable
5. **Logs** — Any relevant error logs from Logcat

Open a [bug report](https://github.com/FiscalMindset/algsoch/issues/new?labels=bug).

## Feature Requests

Feature requests are welcome! When suggesting a feature:

1. **Describe the problem** — What can't you do currently?
2. **Proposed solution** — How should it work?
3. **Alternative approaches** — Any other ways to solve it?
4. **Use case** — How would this help you or others?

Open a [feature request](https://github.com/FiscalMindset/algsoch/issues/new?labels=enhancement).

## Project Structure

```
app/src/main/
├── java/com/algsoch/
│   ├── MainActivity.kt
│   ├── data/
│   │   ├── models/
│   │   │   ├── Message.kt
│   │   │   └── UserPreferences.kt
│   │   └── repository/
│   ├── domain/
│   │   ├── ai/
│   │   │   ├── PromptBuilder.kt
│   │   │   └── ResponseParser.kt
│   │   └── models/
│   │       └── StructuredResponse.kt
│   ├── services/
│   │   ├── ModelService.kt
│   │   └── AIInferenceService.kt
│   └── ui/
│       ├── screens/
│       │   ├── HomeScreen.kt
│       │   ├── ChatScreen.kt
│       │   └── ModeSelectionScreen.kt
│       └── theme/
│           └── AlgsochTheme.kt
└── resources/
    └── drawable/
```

## Need Help?

- Open a [discussion](https://github.com/FiscalMindset/algsoch/discussions)
- Email: npdimagine@gmail.com

---

**Thank you for helping make Algsoch better!** 🚀
