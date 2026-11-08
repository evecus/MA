package com.ma.manager.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import com.ma.manager.ui.theme.MAMotion
import com.ma.manager.ui.theme.MAAlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ma.manager.data.FileItem
import com.ma.manager.util.ApkInfo
import com.ma.manager.util.ApkInfoUtil

/**
 * MT 管理器风格「安装 APK 前二次确认」。
 *
 * MT 2.26.11 在长按菜单点「安装」后不会立刻安装，而是先展示
 * 应用名 / 包名 / 版本 / 签名摘要，并做「验证签名和版本号」预检：
 * 签名不一致、降级安装、SDK 不兼容时直接阻止安装（fail-fast）。
 *
 * 分包（.apks/.xapk/.apkm）无法按单 APK 解析，info 为 null，
 * 此时只展示文件名并交给系统安装器，不做预检。
 */
@Composable
fun InstallConfirmDialog(
    item: FileItem,
    info: ApkInfo?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val parsed = info
    val warning = ApkInfoUtil.installWarning(parsed)
    val isInstalled = parsed?.isInstalled == true

    MAAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isInstalled) "是否覆盖安装？" else "安装 APK") },
        text = {
            Column(Modifier.animateContentSize(tween(MAMotion.Medium, easing = MAMotion.Standard))) {
                if (parsed == null) {
                    Text(
                        text = "${item.name}\n将使用系统安装器安装该安装包。",
                        fontSize = 13.sp
                    )
                } else {
                    ConfirmRow("名称", parsed.label)
                    ConfirmRow("包名", parsed.packageName)
                    ConfirmRow("版本", parsed.versionDisplay)
                    ConfirmRow("大小", FileItem.formatSize(parsed.sizeBytes))
                    if (parsed.isInstalled) {
                        ConfirmRow(
                            "已安装版本",
                            (parsed.installedVersionName ?: "?") +
                                (parsed.installedVersionCode?.let { " ($it)" } ?: "")
                        )
                    }
                    if (parsed.signatureSha256.isNotEmpty()) {
                        ConfirmRow("签名 SHA-256", parsed.signatureSha256.take(32) + "…")
                    }
                    if (warning != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = warning.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = warning == null) {
                Text(if (isInstalled) "覆盖安装" else "安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
