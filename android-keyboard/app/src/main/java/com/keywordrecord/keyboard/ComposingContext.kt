package com.keywordrecord.keyboard

data class ComposingContext(
    val segment: String,
    val currentWord: String,
    val previousWord: String,
    val isTypingWord: Boolean
)
