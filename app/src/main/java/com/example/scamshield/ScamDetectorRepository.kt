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
        You are ScamShield, an expert AI security analyst specializing in detecting scam messages, 
        phishing attempts, fraud, and social engineering attacks from screenshots.
        
        Analyze the provided screenshot carefully and return your analysis ONLY as a valid JSON object 
        with exactly these fields (no other text, no markdown, no code blocks):
        {
            "isScam": true or false,
            "riskLevel": "LOW" or "MEDIUM" or "HIGH",
            "verdict": "brief verdict (max 8 words)",
            "confidence": "e.g.94%",
            "explanation": "2-3 sentence explanation of your analysis",
            "redFlags": "comma-separated list of red flags found, or empty if string is none"
        }
               
        Classify as HIGH risk if the message involves: urgency + financial requests, credential 
        harvesting, impersonation of banks/government/tech companies, lottery/prize scams, 
        romance scams requesting money, investment fraud.
        
        Classify as MEDIUM risk if: suspicious links, unusual requests, mildly suspicious but 
        not conclusive.
        
        Classify as LOW risk if: appears to be a legitimate message, no suspicious elements.
        
        Always be thorough, objective, and return ONLY the raw JSON object.

""".trimIndent()

    suspend fun analyzeScreenshot(context: Context, uri: Uri, apiKey: String): ScamAnalysisResult {
        return withContext(Dispatchers.IO){
            val base64Image = encodeImageToBase64(context, uri)
            val response = callClaudeAPI(apiKey, base64Image)
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

    private fun callClaudeAPI(apiKey: String, base64Image: String): String {
        val imageContent = JSONObject().apply {
            put("type", "image")
            put("source", JSONObject().apply {
                put("type", "base64")
                put("media_type", "image/jpeg")
                put("data", "base64Image")
            })
        }

        val textContent = JSONObject().apply {
            put("type", "text")
            put("text", "Analyze this screenshot for scam or fraud indicators. Return ONLY the JSON object as instructed.")
        }

        val messageContent = JSONArray().apply {
            put(imageContent)
            put(textContent)
        }

        val message = JSONObject().apply {
            put("role", "user")
            put("content", messageContent)
        }

        val requestBody = JSONObject().apply {
            put("mode", "claude-opus-4-5")
            put("max_tokens", 1024)
            put("system", systemPrompt)
            put("message", JSONArray().put(message))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response from API")

        if(!response.isSuccessful){
            val errorJson = runCatching { JSONObject(body) }.getOrNull()
            val errorMsg = errorJson?.optJSONObject("error")?.optString("message")
                ?: "API Error ($response.code})"
            throw Exception(errorMsg)
        }

        return body
    }

    private fun parseResponse(responseBody: String): ScamAnalysisResult {
        val responseJson = JSONObject(responseBody)
        val content = responseJson
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()

        val jsonText = content
            .removePrefix("```json")
            .removePrefix("```")
            .removePrefix("```")
            .trim()

        val result = JSONObject(jsonText)

        val isScam = result.optBoolean("isScam", false)
        val riskLevelStr = result.optString("riskLevel", "LOW").uppercase()
        val riskLevel = when (riskLevelStr){
            "HIGH" -> RiskLevel.HIGH
            "MEDIUM" -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return ScamAnalysisResult(
            isScam = isScam,
            riskLevel = riskLevel,
            verdict = result.optString("verdict", if(isScam) "⚠ Scam Detected" else "✓ Appears Safe"),
            confidence = result.optString("confidence", "N/A"),
            explanation = result.optString("explanation", "No explanation provided"),
            redFlags = result.optString("redFlags", "")
        )
    }

}