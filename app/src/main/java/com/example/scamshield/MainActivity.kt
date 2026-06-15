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
    val context   = LocalContext.current
    val imageUri  by viewModel.imageUri.collectAsStateWithLifecycle()
    val state     by viewModel.analysisState.collectAsStateWithLifecycle()
    var showApiKey by remember { mutableStateOf(false) }
    var savedKey   by remember { mutableStateOf("") }


    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.setImage(it) } }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) imagePicker.launch("image/*") }

    fun pickImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(context, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            imagePicker.launch("image/*")
        } else {
            permissionLauncher.launch(permission)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BgPrimary) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Spacer(Modifier.height(16.dp))


            Header(onSettingsClick = { showApiKey = !showApiKey })
            Spacer(Modifier.height(8.dp))


            AnimatedVisibility(visible = savedKey.isNotEmpty() && !showApiKey) {
                Text(
                    "✓ API Key saved",
                    color = Color(0xFF34D399),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }


            AnimatedVisibility(visible = showApiKey) {
                ApiKeyCard(
                    currentKey = savedKey,
                    onSave = { key ->
                        savedKey = key
                        viewModel.setApiKey(key)
                        showApiKey = false
                    }
                )
            }
            Spacer(Modifier.height(16.dp))


            ScreenshotCard(
                imageUri    = imageUri,
                onPickClick = { pickImage() }
            )
            Spacer(Modifier.height(16.dp))


            AnimatedVisibility(
                visible = imageUri != null && state !is AnalysisState.Loading
            ) {
                AnalyzeButton(onClick = { viewModel.analyze(context) })
            }

            AnimatedVisibility(visible = state is AnalysisState.Loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = AccentBlue,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Scanning for scam patterns…",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val currentState = state

            val successResult = (currentState as? AnalysisState.Success)?.result
            val errorMessage = (currentState as? AnalysisState.Error)?.message ?: ""
            AnimatedVisibility(
                visible = currentState is AnalysisState.Success,
                enter   = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit    = slideOutVertically() + fadeOut()
            ) {
                if (successResult != null) {
                    ResultCard(
                        riskLevel   = successResult.riskLevel.name,
                        verdict     = successResult.verdict,
                        confidence  = successResult.confidence,
                        explanation = successResult.explanation,
                        redFlags    = successResult.redFlags
                    )
                }
            }


            AnimatedVisibility(visible = currentState is AnalysisState.Error) {
                ErrorCard(message = errorMessage)
            }


            AnimatedVisibility(
                visible = state is AnalysisState.Success || state is AnalysisState.Error
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape  = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Divider),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text("Scan Another Screenshot", fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun Header(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ScamShield",
                fontSize    = 28.sp,
                fontWeight  = FontWeight.Bold,
                color       = TextPrimary
            )
            Text(
                "AI-Powered Scam Detection",
                fontSize = 13.sp,
                color    = TextSecondary
            )
        }
        IconButton(
            onClick  = onSettingsClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CardBg)
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
fun ApiKeyCard(currentKey: String, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(currentKey) }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🔑  Anthropic API Key",
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                fontSize   = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Get your key at console.anthropic.com",
                color    = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = key,
                onValueChange = { key = it },
                placeholder   = { Text("sk-ant-...", color = TextHint) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Save Key", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ScreenshotCard(imageUri: Uri?, onPickClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Screenshot",
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                fontSize   = 16.sp
            )
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
                        Text(
                            "Tap to select screenshot",
                            color    = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "JPG, PNG supported",
                            color    = TextHint,
                            fontSize = 12.sp
                        )
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
                        Text("TAP TO CHANGE", color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick  = onPickClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape  = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, AccentBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
            ) {
                Text("📁  Choose Screenshot", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun AnalyzeButton(onClick: () -> Unit) {
    Button(
        onClick         = onClick,
        modifier        = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape           = RoundedCornerShape(16.dp),
        colors          = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding  = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(AccentBlue, Color(0xFF7C3AED))
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Analyze Screenshot",
                color      = Color.White,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ResultCard(
    riskLevel:   String,
    verdict:     String,
    confidence:  String,
    explanation: String,
    redFlags:    String
) {
    val (bgColor, textColor, iconRes) = when (riskLevel) {
        "HIGH"   -> Triple(RiskingHighBg, RiskingHighText, R.drawable.ic_danger)
        "MEDIUM" -> Triple(RiskingMediumBg, RiskingMediumText,  R.drawable.ic_warning)
        else     -> Triple(RiskingLowBg,  RiskingLowText,  R.drawable.ic_safe)
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
                    Text(
                        verdict,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 17.sp,
                        color      = textColor
                    )
                    Text(
                        "Confidence: $confidence",
                        fontSize = 13.sp,
                        color    = textColor.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(12.dp))

            Text(
                "Analysis",
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                color      = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(explanation, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)

            if (redFlags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "🚩  Red Flags",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(redFlags, fontSize = 14.sp, color = TextPrimary, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter           = painterResource(R.drawable.ic_error),
                contentDescription = "Error",
                tint              = Color.Unspecified,
                modifier          = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Analysis Failed",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = TextPrimary
                )
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