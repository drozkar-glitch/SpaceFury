package cz.mares.spacefury

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberCredentialBridge(): CredentialBridge {
    return remember {
        object : CredentialBridge {
            override val isSupported: Boolean = false
            override suspend fun saveCredentials(username: String, secret: String) {}
            override suspend fun loadCredentials(): Pair<String, String>? = null
        }
    }
}
