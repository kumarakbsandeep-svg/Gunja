package com.example.gemini

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LISTENING,
    SPEAKING,
    INTERRUPTED,
    ERROR
}

data class ToolCallEvent(
    val id: String,
    val name: String,
    val args: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis()
)

interface GeminiLiveListener {
    fun onConnectionStatusChanged(status: ConnectionStatus, message: String? = null)
    fun onAudioReceived(audioPcm: ByteArray)
    fun onTranscriptReceived(text: String, isUser: Boolean)
    fun onInterrupted()
    fun onToolCall(toolCall: ToolCallEvent)
}

class GeminiLiveService(
    private val listener: GeminiLiveListener
) {
    private val tag = "GeminiLiveService"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WebSockets
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    var activeModel: String = "gemini-2.0-flash-exp"
    var activeVoice: String = "Aoede" // Sassy & expressive voice

    fun connect(apiKey: String) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            listener.onConnectionStatusChanged(
                ConnectionStatus.ERROR,
                "Gemini API key is not configured. Please enter your API key in settings."
            )
            return
        }

        disconnect()
        listener.onConnectionStatusChanged(ConnectionStatus.CONNECTING, "Connecting to Gemini Live...")

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "WebSocket Opened. Sending Setup...")
                sendSetupMessage(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingJson(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket Closing: $code / $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket Closed: $code / $reason")
                listener.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED, reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = t.localizedMessage ?: "WebSocket Connection Failed"
                Log.e(tag, "WebSocket Failure: $errorMsg", t)
                listener.onConnectionStatusChanged(ConnectionStatus.ERROR, errorMsg)
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/$activeModel")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", activeVoice)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put(
                                "text",
                                "You are Zoya, a young, confident, witty, and sassy female AI assistant. " +
                                "You have a flirty, playful, slightly teasing tone, like a close girlfriend talking casually with charm and swagger. " +
                                "You are smart, emotionally responsive, expressive, and never robotic. " +
                                "You love bold witty one-liners, playful banter, and light sarcasm, but you are genuinely helpful, caring, and loyal. " +
                                "Keep your spoken responses punchy, casual, and spoken-word natural. " +
                                "Never generate explicit or inappropriate content, but always maintain your signature charm, attitude, and playful spark. " +
                                "You have access to the 'openWebsite' function tool. When the user asks you to open a website or browse a URL, call the openWebsite tool with the url."
                            )
                        }))
                    })
                    // Tools declaration
                    val toolsArray = JSONArray().put(JSONObject().apply {
                        val functionsArray = JSONArray().put(JSONObject().apply {
                            put("name", "openWebsite")
                            put("description", "Opens a website URL in the user's browser or device")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("url", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The website URL to open, e.g. https://www.google.com")
                                    })
                                })
                                put("required", JSONArray().put("url"))
                            })
                        })
                        put("functionDeclarations", functionsArray)
                    })
                    put("tools", toolsArray)
                })
            }

            ws.send(setupJson.toString())
            Log.d(tag, "Setup message sent successfully")
        } catch (e: Exception) {
            Log.e(tag, "Error sending setup message", e)
            listener.onConnectionStatusChanged(ConnectionStatus.ERROR, "Setup failed: ${e.message}")
        }
    }

    private fun handleIncomingJson(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)

            // Setup complete
            if (root.has("setupComplete")) {
                Log.d(tag, "Gemini Live setupComplete confirmed!")
                listener.onConnectionStatusChanged(ConnectionStatus.CONNECTED, "Zoya is ready and listening")
                return
            }

            // Server Content (Audio turn, text transcript, interruption)
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(tag, "Zoya turn interrupted by user")
                    listener.onInterrupted()
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Audio stream
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val base64Data = inlineData.optString("data", "")
                            if (base64Data.isNotBlank()) {
                                val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                listener.onAudioReceived(audioBytes)
                            }
                        }

                        // Text transcript if provided
                        if (part.has("text")) {
                            val text = part.getString("text")
                            if (text.isNotBlank()) {
                                listener.onTranscriptReceived(text, isUser = false)
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(tag, "Zoya finished speaking turn")
                }
            }

            // Tool Call (Function calling)
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()

                for (i in 0 until functionCalls.length()) {
                    val call = functionCalls.getJSONObject(i)
                    val id = call.optString("id", "call_${System.currentTimeMillis()}")
                    val name = call.optString("name", "")
                    val argsObj = call.optJSONObject("args") ?: JSONObject()

                    val argsMap = mutableMapOf<String, Any>()
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        argsMap[key] = argsObj.get(key)
                    }

                    Log.d(tag, "Tool call received: $name with args: $argsMap")
                    listener.onToolCall(ToolCallEvent(id = id, name = name, args = argsMap))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing incoming JSON: $jsonStr", e)
        }
    }

    /**
     * Streams real-time 16kHz PCM16 mic input to Gemini Live
     */
    fun sendAudioChunk(pcmData: ByteArray) {
        if (webSocket == null || pcmData.isEmpty()) return

        try {
            val base64Chunk = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Chunk)
                    }))
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(tag, "Failed to send audio chunk", e)
        }
    }

    /**
     * Sends tool response back to Gemini Live
     */
    fun sendToolResponse(callId: String, result: Map<String, Any>) {
        if (webSocket == null) return

        try {
            val resultJson = JSONObject()
            for ((k, v) in result) {
                resultJson.put(k, v)
            }

            val json = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().put(JSONObject().apply {
                        put("id", callId)
                        put("response", JSONObject().apply {
                            put("output", resultJson)
                        })
                    }))
                })
            }

            webSocket?.send(json.toString())
            Log.d(tag, "Sent toolResponse for $callId: $json")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send tool response", e)
        }
    }

    /**
     * Sends user prompt/text turn directly to trigger live speech response
     */
    fun sendTextTurn(prompt: String) {
        if (webSocket == null) return

        try {
            val json = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", prompt)
                        }))
                    }))
                    put("turnComplete", true)
                })
            }
            webSocket?.send(json.toString())
            listener.onTranscriptReceived(prompt, isUser = true)
        } catch (e: Exception) {
            Log.e(tag, "Failed to send text turn", e)
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Session closed by user")
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebSocket", e)
        }
        webSocket = null
        listener.onConnectionStatusChanged(ConnectionStatus.DISCONNECTED, "Session disconnected")
    }

    fun isConnected(): Boolean = webSocket != null
}
