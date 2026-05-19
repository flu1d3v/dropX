package com.fluid.dropx.ui.configure

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fluid.dropx.model.NetworkResult
import com.fluid.dropx.ui.components.ChipButton
import com.fluid.dropx.ui.components.SurfaceCard
import com.fluid.dropx.ui.theme.*
import com.fluid.dropx.utils.QRGenerator

@Composable
fun ConfigureScreen(
    netData    : NetworkResult,
    port       : Int,
    running    : Boolean,
    shareUrl   : String?,
    selectedIp : String?,
    effectiveIp: String?,
    onSelectIp : (String) -> Unit,
    onStartStop: () -> Unit,
    onRefresh  : () -> Unit,
    onShowQr   : () -> Unit,
) {
    val context = LocalContext.current
    val paramsAlpha by animateFloatAsState(
        targetValue = if (running) 0.4f else 1.0f,
        animationSpec = tween(300),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundCanvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── APP HEADER ───────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(BrandCharcoal),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("dropX", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !running) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = if (running) TextMuted else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── LIVE RUNNING BANNER ───────────────────────────────────────────────
        if (running) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StatusSuccessSurf)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(StatusSuccess))
                Text(
                    "Server is running — stop it to change settings",
                    fontSize = 12.sp, color = StatusSuccess, fontWeight = FontWeight.Medium
                )
            }
        }

        // ── NETWORK INTERFACE SELECTION MATRIX ────────────────────────────────
        SurfaceCard(modifier = Modifier.alpha(paramsAlpha)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundCanvas)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Network",    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted, modifier = Modifier.weight(1.2f))
                    Text("IP Address", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted, modifier = Modifier.weight(2f))
                    Text("Use",        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                }
                HorizontalDivider(color = BorderHairline)

                val rows = listOf(
                    "Hotspot" to netData.hotspotIp,
                    "WiFi"    to netData.wifiIp,
                    "Other"   to netData.unknownIp,
                )
                val autoIp = netData.hotspotIp ?: netData.wifiIp ?: netData.unknownIp

                rows.forEachIndexed { idx, (label, ip) ->
                    val available = ip != null
                    val chosen = when {
                        ip == null    -> false
                        selectedIp != null -> selectedIp == ip
                        else          -> ip == autoIp
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (available && !running)
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onSelectIp(ip!!) }
                                else Modifier
                            )
                            .background(if (chosen) BrandContainer else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (available) TextPrimary else TextMuted,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            ip ?: "Not Reachable",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (chosen) BrandCharcoal else if (available) TextSecondary else StatusAlert,
                            modifier = Modifier.weight(2f)
                        )
                        Box(modifier = Modifier.width(44.dp).wrapContentWidth(Alignment.End)) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (chosen) BrandCharcoal else Color.Transparent)
                                    .border(
                                        width = if (chosen) 0.dp else 2.dp,
                                        color = if (available) BorderHairline else TextMuted.copy(.25f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (chosen) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                                }
                            }
                        }
                    }
                    if (idx < rows.lastIndex) HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                }
            }
        }

        // ── PORT TRACKER ROW ──────────────────────────────────────────────────
        SurfaceCard(modifier = Modifier.alpha(paramsAlpha)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Router, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Port", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (port > 0) port.toString() else "50505",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
        }

        // ── CONTEXTUAL EXPORT LINK PANEL ──────────────────────────────────────
        if (running && shareUrl != null) {
            SurfaceCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Link, null, tint = BrandCharcoal, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Text(
                        shareUrl,
                        fontSize = 11.sp, color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipButton(Icons.Outlined.ContentCopy, "Copy") {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("dropX URL", shareUrl))
                        }
                        ChipButton(Icons.Outlined.QrCode2, "QR Code", onShowQr)
                    }
                }
            }
        }

        // ── MASTER POWER ENGINE TOGGLE BUTTON ─────────────────────────────────
        val btnColor by animateColorAsState(
            targetValue = if (running) StatusAlert else BrandCharcoal,
            animationSpec = tween(300),
            label = "btn"
        )
        Button(
            onClick = onStartStop,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = btnColor)
        ) {
            AnimatedContent(
                targetState = running,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label = "btnText"
            ) { isRunning ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunning) "Stop Server" else "Start Server", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ── ENGINE RUNNING STATUS LIGHT ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotColor by animateColorAsState(if (running) Color(0xFF188038) else TextMuted, tween(400), label = "dot")
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(6.dp))
            Text(
                if (running && port > 0) "Running on port $port" else "Server stopped",
                fontSize = 12.sp, color = TextSecondary
            )
        }
    }
}

@Composable
fun QrDialog(url: String, onDismiss: () -> Unit) {
    val qr: Bitmap? = remember(url) { QRGenerator.generate(url, 512) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = SurfacePrimary, tonalElevation = 0.dp) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("Scan to Open", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(), contentDescription = "QR Code",
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(10.dp))
                    )
                }
                Text(
                    url, fontSize = 11.sp, color = TextSecondary, textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onDismiss) { Text("Close", color = BrandCharcoal) }
            }
        }
    }
}