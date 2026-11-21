package cz.mares.spacefury

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataWithBytes
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.posix.memcpy
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
private class IosNetStorage : PlatformNetStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String, default: String): String {
        return defaults.stringForKey(key) ?: default
    }

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun removeKey(key: String) {
        defaults.removeObjectForKey(key)
    }

    override suspend fun httpRequest(method: String, url: String, jsonBody: String?): HttpResult {
        val nsUrl = NSURL.URLWithString(url) ?: return HttpResult(0, "")
        return suspendCancellableCoroutine { cont ->
            val request = NSMutableURLRequest.requestWithURL(nsUrl)
            request.setHTTPMethod(method)
            request.setValue("application/json", forHTTPHeaderField = "Accept")
            if (jsonBody != null) {
                request.setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
                val bytes = jsonBody.encodeToByteArray()
                if (bytes.isNotEmpty()) {
                    val data = bytes.usePinned { pinned ->
                        NSData.dataWithBytes(pinned.addressOf(0), length = bytes.size.convert())
                    }
                    request.setHTTPBody(data)
                }
            }

            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                if (!cont.isActive) return@dataTaskWithRequest
                if (error != null) {
                    cont.resume(HttpResult(0, ""))
                    return@dataTaskWithRequest
                }
                val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 200
                val text = data?.toUtf8String() ?: ""
                cont.resume(HttpResult(status, text))
            }
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    private fun NSData.toUtf8String(): String {
        val len = length.toInt()
        if (len <= 0) return ""
        val out = ByteArray(len)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
        return out.decodeToString()
    }
}

@Composable
actual fun rememberPlatformNetStorage(): PlatformNetStorage {
    return remember { IosNetStorage() }
}
