package com.example.scamshield

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppLanguage(val displayName: String, val code: String) {
    ENGLISH("English", "en"),
    PORTUGUESE_BR("Português (BR)", "pt-BR")
}

class ScamDetectorViewModel : ViewModel() {

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ENGLISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private var apiKey: String = ""
    private val repository = ScamDetectorRepository()

    fun setApiKey(key: String) { apiKey = key }

    fun setLanguage(lang: AppLanguage) { _language.value = lang }

    fun setImage(uri: Uri) {
        _imageUri.value = uri
        _analysisState.value = AnalysisState.Idle
    }

    fun analyze(context: Context) {
        val uri = _imageUri.value ?: return
        if (apiKey.isBlank()) {
            _analysisState.value = AnalysisState.Error(
                if (_language.value == AppLanguage.PORTUGUESE_BR)
                    "Insira sua chave de API nas configurações ⚙ primeiro."
                else
                    "Enter your API key in ⚙ settings first."
            )
            return
        }
        viewModelScope.launch {
            _analysisState.value = AnalysisState.Loading
            try {
                val result = repository.analyzeScreenshot(context, uri, apiKey, _language.value)
                _analysisState.value = AnalysisState.Success(result)
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        _imageUri.value = null
        _analysisState.value = AnalysisState.Idle
    }
}

sealed class AnalysisState {
    object Idle : AnalysisState()
    object Loading : AnalysisState()
    data class Success(val result: ScamAnalysisResult) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}