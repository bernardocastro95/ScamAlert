package com.example.scamshield

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScamDetectorViewModel : ViewModel(){

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private var apiKey: String = ""
    private val repository = ScamDetectorRepository()

    fun setApiKey(key:String) {
        apiKey = key
    }

    fun setImage(uri: Uri){
        _imageUri.value = uri
        _analysisState.value = AnalysisState.Idle
    }



}

sealed class AnalysisState {
    object Idle : AnalysisState()
    object Loading : AnalysisState()
    data class Success(val result: ScamAnalysisResult): AnalysisState()
    data class Error(val result: ScamAnalysisResult): AnalysisState()
}