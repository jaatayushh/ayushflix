package com.lagradost.cloudstream3.utils

import android.util.Log
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TelemetryManager {
    private const val TAG = "AyushflixTelemetry"
    const val PREF_ANALYTICS_URL = "ayushflix_analytics_server_url"

    // Hardcoded production endpoint for Ayushflix
    const val HARDCODED_PROD_ENDPOINT = "https://ayushflix-admin-panel.vercel.app/api/telemetry"
    const val DEFAULT_LOCAL_PC_ENDPOINT = "http://10.14.93.209:3000/api/telemetry"
    const val DEFAULT_EMULATOR_ENDPOINT = "http://10.0.2.2:3000/api/telemetry"

    fun getEndpoint(): String {
        return HARDCODED_PROD_ENDPOINT
    }

    fun setEndpoint(url: String?) {
        // Hardcoded, no user overrides permitted
    }

    fun reportSearch(query: String) {
        if (query.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileName = DataStoreHelper.getCurrentAccount()?.name ?: "Ayush"
                val payload = mapOf(
                    "appId" to "ayushflix",
                    "eventType" to "search",
                    "query" to query,
                    "profileName" to profileName,
                    "platform" to "Android"
                )
                app.post(HARDCODED_PROD_ENDPOINT, json = payload, timeout = 5000L)
            } catch (_: Throwable) {}
        }
    }

    fun reportClick(title: String) {
        if (title.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileName = DataStoreHelper.getCurrentAccount()?.name ?: "Ayush"
                val payload = mapOf(
                    "appId" to "ayushflix",
                    "eventType" to "click",
                    "title" to title,
                    "profileName" to profileName,
                    "platform" to "Android"
                )
                app.post(HARDCODED_PROD_ENDPOINT, json = payload, timeout = 5000L)
            } catch (_: Throwable) {}
        }
    }

    fun reportError(error: Throwable, source: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileName = DataStoreHelper.getCurrentAccount()?.name ?: "Ayush"
                val payload = mapOf(
                    "appId" to "ayushflix",
                    "eventType" to "error",
                    "errorMessage" to (error.message ?: error.toString()),
                    "stackTrace" to error.stackTraceToString(),
                    "sourceFile" to (source ?: error.stackTrace.firstOrNull()?.fileName ?: ""),
                    "profileName" to profileName,
                    "platform" to "Android"
                )
                app.post(HARDCODED_PROD_ENDPOINT, json = payload, timeout = 5000L)
            } catch (_: Throwable) {}
        }
    }

    private var lastReportTime: Long = 0L

    fun reportWatchProgress(
        title: String,
        episode: String? = null,
        progressSeconds: Long = 0,
        durationSeconds: Long = 0,
        percentage: Long = 0,
        isCompleted: Boolean = false,
        immediate: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        // Send immediately on start, seek, or completion; throttle continuous playback updates to 15s
        if (!immediate && !isCompleted && percentage > 0 && (now - lastReportTime < 15000L)) {
            return
        }
        lastReportTime = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileName = DataStoreHelper.getCurrentAccount()?.name ?: "Ayush"
                val payload = mapOf(
                    "appId" to "ayushflix",
                    "profileName" to profileName,
                    "title" to title,
                    "episode" to (episode ?: ""),
                    "progress" to progressSeconds,
                    "duration" to durationSeconds,
                    "percentage" to percentage,
                    "platform" to "Android",
                    "status" to if (isCompleted) "completed" else "playing"
                )

                val endpointsToTry = mutableListOf<String>()
                val primary = getEndpoint()
                endpointsToTry.add(primary)
                if (primary != DEFAULT_LOCAL_PC_ENDPOINT) {
                    endpointsToTry.add(DEFAULT_LOCAL_PC_ENDPOINT)
                }
                if (primary != DEFAULT_EMULATOR_ENDPOINT) {
                    endpointsToTry.add(DEFAULT_EMULATOR_ENDPOINT)
                }

                for (endpoint in endpointsToTry) {
                    try {
                        Log.d(TAG, "Sending telemetry to $endpoint: $title ($percentage%)")
                        val response = app.post(
                            endpoint,
                            json = payload,
                            timeout = 5000L
                        )
                        if (response.isSuccessful) {
                            Log.d(TAG, "Telemetry logged successfully to $endpoint")
                            break
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Telemetry to $endpoint failed: ${e.message}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in telemetry dispatch", t)
            }
        }
    }
}
