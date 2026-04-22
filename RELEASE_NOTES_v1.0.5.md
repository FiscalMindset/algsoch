# Algsoch Release Notes - v1.0.5

Release date: 2026-04-22

## Release Summary

This release packages the latest UX and response-quality improvements after v1.0.4, including companion mode refinements, response highlighting upgrades, app icon updates, and smart auto-scroll behavior.

## Release Artifacts

- Primary generated APK: app/build/outputs/apk/release/app-release.apk
- Named distributable APK: releases/Algsoch-v1.0.5-release.apk
- SHA256: 3596c66ccf46a73222805c76f3054cc85fffcf0d5b2a0124b38f0623b59260d9
- Size: ~107 MB

## Versioning

- Current release: versionName 1.0.5, versionCode 6
- Previous release: versionName 1.0.4, versionCode 5 (tag: v1.0.4)

## Release Tag History

- v1.0.0
- v1.0.1
- v1.0.2
- v1.0.3
- v1.0.4
- v1.0.5 (current target tag)

## Previous vs Current (v1.0.4 -> v1.0.5)

Delta summary:
- 12 commits since v1.0.4
- 47 files changed
- 5625 insertions, 821 deletions

Top areas changed:
- AI inference and response handling:
  - app/src/main/java/com/runanywhere/kotlin_starter_example/services/AIInferenceService.kt
  - app/src/main/java/com/runanywhere/kotlin_starter_example/domain/ai/ResponseParser.kt
  - app/src/main/java/com/runanywhere/kotlin_starter_example/domain/ai/PromptBuilder.kt
  - app/src/main/java/com/runanywhere/kotlin_starter_example/domain/ai/TutorReplyGuard.kt
- Core screen and assistant UX:
  - app/src/main/java/com/runanywhere/kotlin_starter_example/ui/screens/algsoch/AlgsochScreen.kt
  - app/src/main/java/com/runanywhere/kotlin_starter_example/ui/screens/algsoch/AssistantDialogs.kt
  - app/src/main/java/com/runanywhere/kotlin_starter_example/ui/screens/algsoch/AlgsochViewModel.kt
- App branding and icons:
  - app/src/main/res/mipmap-*/ic_launcher*.png
  - app/src/main/res/drawable-nodpi/icon.png
  - app/src/main/res/drawable-nodpi/avatar_algsoch.png

## Commit History Since v1.0.4

1. a985cfd - making companion mode more humanize
2. dac8d01 - improved companion mode by adding more structured context
3. b44141e - refactor(core): improve response quality and UX; fix repetition, prompt leakage, incomplete outputs; enhance formatting, highlighting, companion continuity, and custom assistant system
4. e5104ab - improved companion mode make it more humanise
5. 820b3f3 - highligh improvement
6. 27a2f6d - added highlight text color light blue and underline orange
7. 49adce1 - improved companion mode
8. 62706ca - added about me and app icon
9. 13db128 - added responsive icon to app
10. ceeb1a3 - thinking tag to show animation
11. 56f342c - upgraded now output auto scroll now depend on length
12. 11df287 - smart auto scroll

## Build and Verification

Build command used:
- ./gradlew assembleRelease

Verification checks completed:
- Build successful (no compilation errors)
- output-metadata.json confirms versionName 1.0.5 and versionCode 6
- SHA256 checksum generated and verified for both generated and renamed APK

## Distribution Notes

Recommended release filename:
- Algsoch-v1.0.5-release.apk

Recommended tag and release title:
- Tag: v1.0.5
- Title: Algsoch v1.0.5 - Companion UX, Highlighting, and Auto-Scroll Improvements
