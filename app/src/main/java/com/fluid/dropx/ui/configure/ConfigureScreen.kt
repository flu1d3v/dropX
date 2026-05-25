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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fluid.dropx.R
import com.fluid.dropx.model.NetworkResult
import com.fluid.dropx.ui.components.ChipButton
import com.fluid.dropx.ui.components.SurfaceCard
import com.fluid.dropx.utils.QRGenerator

@Composable
fun ConfigureScreen(
    netData    : NetworkResult,
    port       : Int,
    running    : Boolean,
    shareUrl   : String?,
    selectedIp : String?,
    onSelectIp : (String) -> Unit,
    onStartStop: () -> Unit,
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.configure_header_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            if (running) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Text(
                        text = stringResource(R.string.banner_server_running),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium
                    )
                }
            }

            SurfaceCard(modifier = Modifier.alpha(paramsAlpha)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.table_header_network),    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1.2f))
                        Text(stringResource(R.string.table_header_ip), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(2f))
                        Text(stringResource(R.string.table_header_use),        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    val rows = listOf(
                        stringResource(R.string.network_label_hotspot) to netData.hotspotIp,
                        stringResource(R.string.network_label_wifi)    to netData.wifiIp,
                        stringResource(R.string.network_label_other)   to netData.unknownIp,
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
                                        ) { onSelectIp(ip) }
                                    else Modifier
                                )
                                .background(if (chosen) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                fontSize = 13.sp,
                                color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.weight(1.2f)
                            )
                            Text(
                                ip ?: stringResource(R.string.network_unreachable),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (chosen) MaterialTheme.colorScheme.primary else if (available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(2f)
                            )
                            Box(modifier = Modifier.width(44.dp).wrapContentWidth(Alignment.End)) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            width = if (chosen) 0.dp else 2.dp,
                                            color = if (available) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(.25f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (chosen) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                                    }
                                }
                            }
                        }
                        if (idx < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                }
            }


            SurfaceCard(modifier = Modifier.alpha(paramsAlpha)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Router, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.parameter_port), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (port > 0) port.toString() else "50505",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }


            if (running && shareUrl != null) {
                SurfaceCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.parameter_share_link), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            shareUrl,
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy)) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("dropX URL", shareUrl))
                            }
                            ChipButton(Icons.Outlined.QrCode2, stringResource(R.string.action_qr_code), onShowQr)
                        }
                    }
                }
            }


            val btnColor by animateColorAsState(
                targetValue = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                        Text(
                            text = if (isRunning) stringResource(R.string.action_stop_server) else stringResource(R.string.action_start_server),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor by animateColorAsState(if (running) Color(0xFF188038) else MaterialTheme.colorScheme.outline, tween(400), label = "dot")
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(6.dp))

                val statusMessage = if (running && port > 0) {
                    stringResource(R.string.status_running, port)
                } else {
                    stringResource(R.string.status_stopped)
                }
                Text(
                    text = statusMessage,
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QrDialog(url: String, onDismiss: () -> Unit) {
    val qr: Bitmap? = remember(url) { QRGenerator.generate(url, 512) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(stringResource(R.string.dialog_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(10.dp))
                    )
                }
                Text(
                    url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}