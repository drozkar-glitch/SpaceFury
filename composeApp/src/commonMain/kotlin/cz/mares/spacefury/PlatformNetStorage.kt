package cz.mares.spacefury

import androidx.compose.runtime.Composable

data class HttpResult(
    val code: Int,
    val body: String
)

interface PlatformNetStorage {
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
    fun removeKey(key: String)
    suspend fun httpRequest(method: String, url: String, jsonBody: String? = null): HttpResult
}

@Composable
expect fun rememberPlatformNetStorage(): PlatformNetStorage
