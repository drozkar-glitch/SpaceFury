package cz.mares.spacefury

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential

private fun Context.unwrapActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
actual fun rememberCredentialBridge(): CredentialBridge {
    val context = LocalContext.current
    return remember(context) {
        object : CredentialBridge {
            override val isSupported: Boolean
                get() = context.unwrapActivity() != null

            override suspend fun saveCredentials(username: String, secret: String) {
                val activity = context.unwrapActivity() ?: return
                if (username.isBlank() || secret.isBlank()) return
                try {
                    val manager = CredentialManager.create(activity)
                    val request = CreatePasswordRequest(id = username.trim(), password = secret)
                    manager.createCredential(activity, request)
                } catch (_: Throwable) {
                }
            }

            override suspend fun loadCredentials(): Pair<String, String>? {
                val activity = context.unwrapActivity() ?: return null
                return try {
                    val manager = CredentialManager.create(activity)
                    val request = GetCredentialRequest(listOf(GetPasswordOption()))
                    val result = manager.getCredential(activity, request)
                    val cred = result.credential
                    if (cred is PasswordCredential) {
                        cred.id to cred.password
                    } else {
                        null
                    }
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }
}
