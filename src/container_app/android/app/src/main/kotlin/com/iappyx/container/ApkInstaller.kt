package com.iappyx.container

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iappyxOS APK Installer
 *
 * Installs APKs using Android's PackageInstaller API.
 * No root required. Shows the standard Android install dialog.
 *
 * This is the same API used by:
 * - Google Play Store
 * - Firefox (web app shortcuts)
 * - Any app that installs other APKs legitimately
 */
class ApkInstaller(private val context: Context) {

    companion object {
        private const val TAG = "iappyxOS"
        private const val ACTION_INSTALL_RESULT = "com.iappyx.container.INSTALL_RESULT"
    }

    /**
     * Install an APK. Shows the system install dialog.
     * Suspends until the user taps Install or Cancel.
     *
     * Requires: android.permission.REQUEST_INSTALL_PACKAGES
     * The user must also enable "Install unknown apps" for the container app.
     */
    suspend fun install(apkFile: File) = suspendCancellableCoroutine { cont ->
        val packageInstaller = context.packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppLabel("iappyxOS Generated App")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        // Write APK bytes to session
        try {
            apkFile.inputStream().use { input ->
                session.openWrite("package", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
        } catch (e: Exception) {
            session.close()
            throw e
        }

        // Register result receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                context.unregisterReceiver(this)

                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        Log.i(TAG, "Install success")
                        cont.resume(Unit)
                    }

                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        // Show the system confirm dialog
                        // The coroutine stays suspended until user acts
                        val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        if (confirmIntent != null) {
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(confirmIntent)
                        }
                        // Re-register for the final result after user action
                        // (Android will send STATUS_SUCCESS or STATUS_FAILURE after)
                        context.registerReceiver(this, IntentFilter(ACTION_INSTALL_RESULT),
                            Context.RECEIVER_NOT_EXPORTED)
                    }

                    else -> {
                        Log.e(TAG, "Install failed (status $status): $message")
                        cont.resumeWithException(
                            InstallException("Install failed: $message (status $status)")
                        )
                    }
                }
            }
        }

        val intent = Intent(ACTION_INSTALL_RESULT).apply {
            setPackage(context.packageName)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_INSTALL_RESULT),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(ACTION_INSTALL_RESULT))
        }

        session.commit(pendingIntent.intentSender)
        session.close()

        cont.invokeOnCancellation {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    class InstallException(message: String) : Exception(message)
}
