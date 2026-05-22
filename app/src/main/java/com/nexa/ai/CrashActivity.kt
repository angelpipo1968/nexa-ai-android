package com.nexa.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.ui.theme.NexaTheme

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorLog = intent.getStringExtra("crash_log") ?: "No log available"
        val exceptionName = intent.getStringExtra("exception_name") ?: "Unknown"
        val errorMessage = intent.getStringExtra("error_message") ?: ""

        setContent {
            NexaTheme(themeMode = com.nexa.ai.viewmodel.ThemeMode.DARK) {
                CrashScreen(
                    exceptionName = exceptionName,
                    errorMessage = errorMessage,
                    fullLog = errorLog,
                    onRestart = {
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun CrashScreen(
    exceptionName: String,
    errorMessage: String,
    fullLog: String,
    onRestart: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF050508)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Error icon
            Box(
                modifier = Modifier.size(64.dp)
                    .background(
                        Color(0xFFFF4D6A).copy(alpha = 0.1f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("💥", fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "NEXA PRO encountered an error",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "The error was saved automatically.\nCopy the error and send it so it can be fixed.",
                fontSize = 13.sp,
                color = Color(0xFF6B6B80),
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error summary card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFF4D6A).copy(alpha = 0.06f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp, Color(0xFFFF4D6A).copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        exceptionName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF4D6A).copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            errorMessage,
                            fontSize = 12.sp,
                            color = Color(0xFFFF4D6A).copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Full log card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0D0D12),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp, Color(0xFF1E1E2A)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "STACK TRACE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexaAccent.copy(alpha = 0.5f),
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        fullLog.take(3000),
                        fontSize = 10.sp,
                        color = Color(0xFF6B6B80),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Copy button
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("NEXA Crash Log", fullLog)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Error copied ✓", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NexaAccent)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("COPY ERROR", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restart button
            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp), tint = NexaAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RESTART APP", color = NexaAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}
