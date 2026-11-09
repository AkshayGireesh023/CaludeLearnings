package com.kharcha.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kharcha.app.ingest.PaymentNotificationListener

data class PermissionStatus(val sms: Boolean, val notificationAccess: Boolean, val postNotifications: Boolean)

fun permissionStatus(context: Context) = PermissionStatus(
    sms = granted(context, Manifest.permission.READ_SMS) && granted(context, Manifest.permission.RECEIVE_SMS),
    notificationAccess = PaymentNotificationListener.isEnabled(context),
    postNotifications = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS),
)

private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

val smsPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

fun notificationAccessIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

/** Re-checks permissions every time the app comes back to the foreground (e.g. from system settings). */
@Composable
fun rememberPermissionStatus(): PermissionStatus {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(permissionStatus(context)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) status = permissionStatus(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return status
}
