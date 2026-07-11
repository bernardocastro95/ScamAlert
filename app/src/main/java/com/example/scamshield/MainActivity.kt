package com.example.scamshield

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.scamshield.ui.theme.ScamShieldTheme
import com.example.scamshield.AnalysisState

private val BgPrimary = Color(0xFF0D0F14)
private val CardBg = Color(0xFF1A1D26)
private val Divider = Color(0xFF2A2D3A)
private val TextPrimary = Color(0xFFF0F2FF)
private val TextSecondary = Color(0xFF8892AA)
private val TextHint = Color(0xFF4A5068)
private val AccentBlue = Color(0xFF4F8EF7)
private val RiskingHighBg = Color(0xFF2D1515)
private val RiskingHighText = Color(0xFFF87171)
private val RiskingMediumBg = Color(0xFF2D2010)
private val RiskingMediumText = Color(0xFFFB923C)
private val RiskingLowBg = Color(0xFF0F2D1E)
private val RiskingLowText = Color(0xFF34D399)

enum class PreviewMode {EMPTY, IMAGE_SELECTED, RESULT_HIGH, RESULT_LOW}

private fun uiString(language: AppLanguage, key: String): String {
    val strings = mapOf(
        AppLanguage.ENGLISH to mapOf(
            "app_subtitle"      to "AI-Powered Scam Detection",
            "api_key_title"     to "🔑  Anthropic API Key",
            "api_key_subtitle"  to "Get your key at console.groq.com",
            "api_key_hint"      to "Insert your groq key here",
            "api_key_save"      to "Save Key",
            "api_key_saved"     to "✓ API Key saved",
            "screenshot_title"  to "Screenshot",
            "upload_hint"       to "Tap to select screenshot",
            "upload_sub"        to "JPG, PNG supported",
            "choose_btn"        to "📁  Choose Screenshot",
            "analyze_btn"       to "Analyze Screenshot",
            "analyzing"         to "Scanning for scam patterns…",
            "analysis_title"    to "Analysis",
            "red_flags"         to "🚩  Red Flags",
            "confidence"        to "Confidence",
            "scan_another"      to "Scan Another Screenshot",
            "failed_title"      to "Analysis Failed",
            "language_label"    to "🌐  Language",
            "tap_to_change"     to "TAP TO CHANGE",
            "settings"          to "Settings"
        ),
        AppLanguage.PORTUGUESE_BR to mapOf(
            "app_subtitle"      to "Detecção de Golpes com IA",
            "api_key_title"     to "🔑  Chave de API",
            "api_key_subtitle"  to "Obtenha sua chave em console.groq.com",
            "api_key_hint"      to "Insira sua chave groq aqui",
            "api_key_save"      to "Salvar Chave",
            "api_key_saved"     to "✓ Chave de API salva",
            "screenshot_title"  to "Captura de Tela",
            "upload_hint"       to "Toque para selecionar a captura",
            "upload_sub"        to "JPG, PNG suportados",
            "choose_btn"        to "📁  Escolher Captura de Tela",
            "analyze_btn"       to "Analisar Captura de Tela",
            "analyzing"         to "Verificando padrões de golpe…",
            "analysis_title"    to "Análise",
            "red_flags"         to "🚩  Sinais de Alerta",
            "confidence"        to "Confiança",
            "scan_another"      to "Analisar Outra Captura",
            "failed_title"      to "Análise Falhou",
            "language_label"    to "🌐  Idioma",
            "tap_to_change"     to "TOQUE PARA MUDAR",
            "settings"          to "Configurações"
        )
    )
    return strings[language]?.get(key) ?: key
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScamShieldTheme {
                ScamShieldScreen()
            }
        }
    }
}

@Composable
fun ScamShieldScreen(viewModel: ScamDetectorViewModel = viewModel()) {
    val context      = LocalContext.current
    val imageUri     by viewModel.imageUri.collectAsStateWithLifecycle()
    val state        by viewModel.analysisState.collectAsStateWithLifecycle()
    val language     by viewModel.language.collectAsStateWithLifecycle()  // ← add this
    var showApiKey   by remember { mutableStateOf(false) }
    var savedKey     by remember { mutableStateOf("") }

    val s = { key: String -> uiString(language, key) }  // shorthand

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.setImage(it) } }

    fun pickImage() { imagePicker.launch("image/*") }

    Surface(modifier = Modifier.fillMaxSize(), color = BgPrimary) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Spacer(Modifier.height(16.dp))

            Header(
                subtitle   = s("app_subtitle"),
                onSettingsClick = { showApiKey = !showApiKey }
            )
            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(visible = savedKey.isNotEmpty() && !showApiKey) {
                Text(
                    s("api_key_saved"),
                    color    = Color(0xFF34D399),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            AnimatedVisibility(visible = showApiKey) {
                SettingsCard(
                    currentKey = savedKey,
                    language   = language,
                    strings    = { key -> s(key) },
                    onSave     = { key ->
                        savedKey = key
                        viewModel.setApiKey(key)
                        showApiKey = false
                    },
                    onLanguageChange = { lang -> viewModel.setLanguage(lang) }
                )
            }
            Spacer(Modifier.height(16.dp))

            ScreenshotCard(
                imageUri      = imageUri,
                screenshotTitle = s("screenshot_title"),
                uploadHint    = s("upload_hint"),
                uploadSub     = s("upload_sub"),
                chooseBtn     = s("choose_btn"),
                tapToChange   = s("tap_to_change"),
                onPickClick   = { pickImage() }
            )
            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = imageUri != null && state !is AnalysisState.Loading
            ) {
                AnalyzeButton(
                    label   = s("analyze_btn"),
                    onClick = { viewModel.analyze(context) }
                )
            }

            AnimatedVisibility(visible = state is AnalysisState.Loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(s("analyzing"), color = TextSecondary, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            val currentState  = state
            val successResult = (currentState as? AnalysisState.Success)?.result
            val errorMessage  = (currentState as? AnalysisState.Error)?.message ?: ""

            AnimatedVisibility(
                visible = currentState is AnalysisState.Success,
                enter   = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit    = slideOutVertically() + fadeOut()
            ) {
                if (successResult != null) {
                    ResultCard(
                        riskLevel      = successResult.riskLevel.name,
                        verdict        = successResult.verdict,
                        confidence     = "${s("confidence")}: ${successResult.confidence}",
                        explanation    = successResult.explanation,
                        redFlags       = successResult.redFlags,
                        analysisLabel  = s("analysis_title"),
                        redFlagsLabel  = s("red_flags")
                    )
                }
            }

            AnimatedVisibility(visible = currentState is AnalysisState.Error) {
                ErrorCard(title = s("failed_title"), message = errorMessage)
            }

            AnimatedVisibility(
                visible = currentState is AnalysisState.Success || currentState is AnalysisState.Error
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick  = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(1.dp, Divider),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text(s("scan_another"), fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}


@Composable
fun Header(subtitle: String, onSettingsClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("ScamShield", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        IconButton(
            onClick  = onSettingsClick,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(CardBg)
        ) {
            Icon(
                painter           = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                tint              = TextSecondary,
                modifier          = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsCard(
    currentKey: String,
    language: AppLanguage,
    strings: (String) -> String,
    onSave: (String) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit
) {
    var key by remember { mutableStateOf(currentKey) }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // API Key section
            Text(strings("api_key_title"), fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(strings("api_key_subtitle"), color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = key,
                onValueChange = { key = it },
                placeholder   = { Text(strings("api_key_hint"), color = TextHint) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentBlue,
                    unfocusedBorderColor = Divider,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = AccentBlue
                )
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick  = { if (key.isNotBlank()) onSave(key) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(strings("api_key_save"), color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))

            // Divider
            HorizontalDivider(color = Divider)

            Spacer(Modifier.height(16.dp))

            // Language section
            Text(strings("language_label"), fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    val selected = language == lang
                    OutlinedButton(
                        onClick  = { onLanguageChange(lang) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) AccentBlue else Divider
                        ),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) AccentBlue.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor   = if (selected) AccentBlue else TextSecondary
                        )
                    ) {
                        Text(lang.displayName, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenshotCard(
    imageUri: Uri?,
    screenshotTitle: String,
    uploadHint: String,
    uploadSub: String,
    chooseBtn: String,
    tapToChange: String,
    onPickClick: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(screenshotTitle, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            if (imageUri == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF12141C))
                        .border(1.5.dp, Divider, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter           = painterResource(R.drawable.ic_upload),
                            contentDescription = "Upload",
                            tint              = AccentBlue,
                            modifier          = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(uploadHint, color = TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(uploadSub, color = TextHint, fontSize = 12.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model              = imageUri,
                        contentDescription = "Selected screenshot",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(tapToChange, color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick  = onPickClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.5.dp, AccentBlue),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
            ) {
                Text(chooseBtn, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun AnalyzeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick        = onClick,
        modifier       = Modifier.fillMaxWidth().height(56.dp),
        shape          = RoundedCornerShape(16.dp),
        colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(AccentBlue, Color(0xFF7C3AED))),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ResultCard(
    riskLevel:     String,
    verdict:       String,
    confidence:    String,
    explanation:   String,
    redFlags:      String,
    analysisLabel: String,
    redFlagsLabel: String
) {
    val (bgColor, textColor, iconRes) = when (riskLevel) {
        "HIGH"   -> Triple(RiskingHighBg,   RiskingHighText,   R.drawable.ic_danger)
        "MEDIUM" -> Triple(RiskingMediumBg, RiskingMediumText, R.drawable.ic_warning)
        else     -> Triple(RiskingLowBg,    RiskingLowText,    R.drawable.ic_safe)
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter           = painterResource(iconRes),
                    contentDescription = "Risk level",
                    tint              = Color.Unspecified,
                    modifier          = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(verdict, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = textColor)
                    Text(confidence, fontSize = 13.sp, color = textColor.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(12.dp))
            Text(analysisLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(explanation, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
            if (redFlags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(redFlagsLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(redFlags, fontSize = 14.sp, color = TextPrimary, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
fun ErrorCard(title: String, message: String) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter           = painterResource(R.drawable.ic_error),
                contentDescription = "Error",
                tint              = Color.Unspecified,
                modifier          = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(message, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F14)
@Composable
fun ScamShieldPreview() {
    ScamShieldTheme { ScamShieldScreen() }
}