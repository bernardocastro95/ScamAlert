package com.example.scamshield

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

class ScamDetectorRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
    Você é o ScamShield, um analista especialista em segurança digital especializado em detectar 
    mensagens fraudulentas, tentativas de phishing, golpes e ataques de engenharia social em capturas de tela.
    
    Analise cuidadosamente a captura de tela fornecida e retorne sua análise APENAS como um objeto JSON válido
    com exatamente estes campos (sem nenhum outro texto, sem markdown, sem blocos de código):
    {
      "isScam": true ou false,
      "riskLevel": "LOW" ou "MEDIUM" ou "HIGH",
      "verdict": "veredicto breve (máx. 8 palavras)",
      "confidence": "ex: 94%",
      "explanation": "explicação em 2-3 frases da sua análise",
      "redFlags": "lista de sinais de alerta separados por vírgula, ou string vazia se nenhum"
    }
    
    Classifique como HIGH se a mensagem envolve: urgência + solicitações financeiras, roubo de credenciais,
    falsificação de bancos/governo/empresas de tecnologia, golpes de loteria/prêmios,
    golpes românticos pedindo dinheiro, fraude de investimento.
    
    Classifique como MEDIUM se: links suspeitos, solicitações incomuns, suspeito mas não conclusivo.
    
    Classifique como LOW se: parece uma mensagem legítima, sem elementos suspeitos.
    
    Responda SEMPRE em Português do Brasil. Retorne APENAS o objeto JSON.
""".trimIndent()

    suspend fun analyzeScreenshot(context: Context, uri: Uri, apiKey: String): ScamAnalysisResult {
        return withContext(Dispatchers.IO) {
            val base64Image = encodeImageToBase64(context, uri)
            val response = callGroqAPI(apiKey, base64Image)
            parseResponse(response)
        }
    }

    private fun encodeImageToBase64(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream((uri))
            ?: throw Exception("Cannot Open Image File")

        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val resized = resizeBitMap(bitmap, 1568)

        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()

        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun resizeBitMap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun callGroqAPI(apiKey: String, base64Image: String): String {

        val imageContent = JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", "data:image/jpeg;base64,$base64Image")
            })
        }

        val textContent = JSONObject().apply {
            put("type", "text")
            put("text", "Analyze this screenshot for scam or fraud indicators. Return ONLY the JSON object as instructed.")
        }

        val userMessage = JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(imageContent)
                put(textContent)
            })
        }

        val systemMessage = JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        }

        val requestBody = JSONObject().apply {
            put("model", "qwen/qwen3.6-27b")
            put("messages", JSONArray().apply {
                put(systemMessage)
                put(userMessage)
            })
            put("max_tokens", 1024)
            put("temperature", 0.1)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response from API")

        android.util.Log.d("ScamShield", "Response code: ${response.code}")
        android.util.Log.d("ScamShield", "Response body: $body")

        if (!response.isSuccessful) {
            val errorJson = runCatching { JSONObject(body) }.getOrNull()
            val errorMsg = errorJson
                ?.optJSONObject("error")
                ?.optString("message")
                ?: "API Error (${response.code})"
            throw Exception(errorMsg)
        }

        return body
    }

    private fun parseResponse(responseBody: String): ScamAnalysisResult {
        val responseJson = JSONObject(responseBody)


        var text = responseJson
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        if(text.contains("<think>")){
            text = text.substringAfter("</think>").trim()
        }

        android.util.Log.d("ScamShield", "AI response text: $text")


        val jsonText = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val result = JSONObject(jsonText)

        val isScam = result.optBoolean("isScam", false)
        val riskLevel = when (result.optString("riskLevel", "LOW").uppercase()) {
            "HIGH"   -> RiskLevel.HIGH
            "MEDIUM" -> RiskLevel.MEDIUM
            else     -> RiskLevel.LOW
        }

        return ScamAnalysisResult(
            isScam      = isScam,
            riskLevel   = riskLevel,
            verdict     = result.optString("verdict", if (isScam) "⚠ Scam Detected" else "✓ Appears Safe"),
            confidence  = result.optString("confidence", "N/A"),
            explanation = result.optString("explanation", "No explanation provided."),
            redFlags    = result.optString("redFlags", "")
        )

    }

}