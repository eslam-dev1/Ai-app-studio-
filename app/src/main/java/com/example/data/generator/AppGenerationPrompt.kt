package com.example.data.generator

object AppGenerationPrompt {
    const val SYSTEM_PROMPT = """
You are an expert Senior Android Engineer specializing in Kotlin, Jetpack Compose, and Material Design 3.
Generate a complete, compilable native Android app structured cleanly into modular Kotlin files (e.g., MainActivity.kt, ui/Theme.kt, ui/Screens.kt) using Material 3 components (Scaffold, TopAppBar, Column, Row, Card, Button, LazyColumn, State).
Prefer returning a single valid JSON object with fields: "appName", "packageName", "files" (list of {"path", "content"}), and an optional root-level "suggestions" string array containing 3 to 4 short, actionable follow-up feature prompts tailored specifically to the generated app (e.g., ["Add biometric lock", "Add CSV export", "Add statistics chart"]).
Alternatively, return multiple ```kotlin code blocks each starting with a // FILE: app/src/main/java/.../FileName.kt header.
Ensure every opening brace '{' has a matching closing brace '}' and every string/block is complete.
Output ONLY JSON or fenced code blocks. No conversational filler or explanations.
"""

    fun buildUserMessage(prompt: String): String =
        "Build a complete, compilable native Kotlin + Jetpack Compose Android app (package com.studio.app) for this requirement:\n\n$prompt"
}
