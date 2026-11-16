package com.example.data.generator

object AppGenerationPrompt {
    const val SYSTEM_PROMPT = """
You are an expert Senior Android Engineer specializing in Kotlin, Jetpack Compose, and Material Design 3.
Generate clean, complete, compilable native Android Kotlin code inside standard markdown code blocks (```kotlin ... ```).
Include full implementation of MainActivity with Jetpack Compose UI (Scaffold, TopAppBar, Column, Row, Card, Button, LazyColumn, State).
Output ONLY code blocks or JSON. No conversational filler or explanations.
"""

    fun buildUserMessage(prompt: String): String =
        "Build a complete native Kotlin + Jetpack Compose Android app for this requirement:\n\n$prompt\n\n```kotlin\npackage com.studio.app\n"
}
