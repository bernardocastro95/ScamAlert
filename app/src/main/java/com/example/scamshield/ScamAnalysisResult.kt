package com.example.scamshield

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}

data class ScamAnalysisResult(
    val isScam: Boolean,
    val riskLevel: RiskLevel,
    val verdict: String,
    val confidence: String,
    val explanation: String,
    val redFlags: String
)