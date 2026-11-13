package com.jarvis.assistant.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client for the Model Context Protocol (MCP) and Antigravity Agent Engine.
 * Supports discovery (tools/list), execution (tools/call), and custom MCP server endpoints.
 */
class McpClient(
    private val endpointUrl: String = DEFAULT_ENDPOINT,
    private val authToken: String? = null
) {

    companion object {
        private const val TAG = "McpClient"
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8080/mcp"
        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

        data class McpTool(
            val name: String,
            val description: String,
            val parameters: JSONObject?
        )

        data class McpResult(
            val success: Boolean,
            val output: String,
            val isError: Boolean = false
        )
    }

    private val requestId = AtomicInteger(1)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Tests connectivity to the MCP / Antigravity server.
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", "SiyaAI-Antigravity/5.1")

            if (!authToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $authToken")
            }

            val request = reqBuilder.get().build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 405 || response.code == 400
            }
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed for $endpointUrl: ${e.message}")
            false
        }
    }

    /**
     * Fetches available tools from the MCP server via `tools/list`.
     */
    suspend fun listTools(): List<McpTool> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", requestId.getAndIncrement())
            put("method", "tools/list")
            put("params", JSONObject())
        }

        val responseJson = sendRpcRequest(payload) ?: return@withContext emptyList()
        val toolsList = mutableListOf<McpTool>()

        try {
            val result = responseJson.optJSONObject("result")
            val toolsArray = result?.optJSONArray("tools") ?: JSONArray()
            for (i in 0 until toolsArray.length()) {
                val toolObj = toolsArray.getJSONObject(i)
                toolsList.add(
                    McpTool(
                        name = toolObj.optString("name", "tool_$i"),
                        description = toolObj.optString("description", ""),
                        parameters = toolObj.optJSONObject("inputSchema")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tools/list: ${e.message}", e)
        }

        toolsList
    }

    /**
     * Executes an MCP tool via `tools/call`.
     */
    suspend fun callTool(name: String, arguments: JSONObject): McpResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", requestId.getAndIncrement())
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", name)
                put("arguments", arguments)
            })
        }

        val responseJson = sendRpcRequest(payload)
            ?: return@withContext McpResult(false, "No response from Antigravity/MCP endpoint at $endpointUrl", isError = true)

        try {
            if (responseJson.has("error")) {
                val err = responseJson.getJSONObject("error")
                return@withContext McpResult(
                    success = false,
                    output = err.optString("message", "MCP execution error"),
                    isError = true
                )
            }

            val result = responseJson.optJSONObject("result")
            val contentArray = result?.optJSONArray("content")
            val outputBuilder = StringBuilder()

            if (contentArray != null) {
                for (i in 0 until contentArray.length()) {
                    val item = contentArray.getJSONObject(i)
                    if (item.optString("type") == "text") {
                        outputBuilder.append(item.optString("text", ""))
                    }
                }
            } else {
                outputBuilder.append(result?.toString() ?: "Execution complete")
            }

            McpResult(
                success = true,
                output = outputBuilder.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tools/call response: ${e.message}", e)
            McpResult(false, "Error parsing response: ${e.message}", isError = true)
        }
    }

    /**
     * Generates code or media using Antigravity Agent tools via MCP.
     */
    suspend fun generateWithAntigravity(prompt: String, taskType: String = "code"): McpResult {
        val args = JSONObject().apply {
            put("prompt", prompt)
            put("task", taskType)
        }
        // Try calling antigravity agent tool
        return callTool("antigravity_agent_generate", args)
    }

    private fun sendRpcRequest(bodyJson: JSONObject): JSONObject? {
        val mediaType = JSON_MEDIA_TYPE.toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "SiyaAI-Antigravity/5.1")

        if (!authToken.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $authToken")
        }

        return try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: return null
                if (!response.isSuccessful) {
                    Log.w(TAG, "MCP RPC failed with HTTP ${response.code}: $bodyStr")
                    return null
                }
                JSONObject(bodyStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during MCP RPC to $endpointUrl: ${e.message}")
            null
        }
    }
}
