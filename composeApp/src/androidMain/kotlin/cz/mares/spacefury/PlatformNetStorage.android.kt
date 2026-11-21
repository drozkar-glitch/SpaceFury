package cz.mares.spacefury

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private class AndroidNetStorage(context: Context) : PlatformNetStorage {
    private val prefs = context.applicationContext.getSharedPreferences("spacefury_prefs", Context.MODE_PRIVATE)

    override fun getString(key: String, default: String): String {
        return prefs.getString(key, default) ?: default
    }

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun removeKey(key: String) {
        prefs.edit().remove(key).apply()
    }

    override suspend fun httpRequest(method: String, url: String, jsonBody: String?): HttpResult {
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                    if (jsonBody != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { os ->
                            os.write(jsonBody.encodeToByteArray())
                        }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                HttpResult(code, text)
            } catch (_: Throwable) {
                HttpResult(0, "")
            } finally {
                conn?.disconnect()
            }
        }
    }
}

@Composable
actual fun rememberPlatformNetStorage(): PlatformNetStorage {
    val context = LocalContext.current
    return remember(context) { AndroidNetStorage(context) }
}
