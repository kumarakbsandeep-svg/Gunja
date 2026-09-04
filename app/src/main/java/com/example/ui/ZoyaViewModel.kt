package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.AudioPlayer
import com.example.audio.AudioRecorder
import com.example.gemini.ConnectionStatus
import com.example.gemini.GeminiLiveListener
import com.example.gemini.GeminiLiveService
import com.example.gemini.ToolCallEvent
import com.example.gemini.ZoyaPersonality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class TranscriptItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ZoyaUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusMessage: String = "Tap the glowing orb or mic to connect with Zoya",
    val isMicActive: Boolean = false,
    val isSpeaking: Boolean = false,
    val userAmplitude: Float = 0f,
    val zoyaAmplitude: Float = 0f,
    val apiKey: String = "",
    val transcripts: List<TranscriptItem> = emptyList(),
    val lastToolEvent: ToolCallEvent? = null,
    val activeVoice: String = "Aoede",
    val hasAudioPermission: Boolean = false,
    val isDemoFallbackActive: Boolean = false
)

class ZoyaViewModel(application: Application) : AndroidViewModel(application), GeminiLiveListener, TextToSpeech.OnInitListener {

    private val tag = "ZoyaViewModel"

    private val _uiState = MutableStateFlow(ZoyaUiState())
    val uiState: StateFlow<ZoyaUiState> = _uiState.asStateFlow()

    private val _browserIntentEvent = MutableSharedFlow<String>()
    val browserIntentEvent: SharedFlow<String> = _browserIntentEvent.asSharedFlow()

    private var geminiService: GeminiLiveService = GeminiLiveService(this)
    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        // Initialize API key from BuildConfig if available
        val configKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        val initialKey = if (configKey != "MY_GEMINI_API_KEY") configKey else ""
        _uiState.update { it.copy(apiKey = initialKey) }

        // Setup AudioPlayer
        audioPlayer = AudioPlayer(
            sampleRate = 24000,
            onAmplitudeChanged = { amp ->
                _uiState.update { it.copy(zoyaAmplitude = amp) }
            },
            onPlaybackStateChanged = { playing ->
                _uiState.update {
                    it.copy(
                        isSpeaking = playing,
                        connectionStatus = if (playing) ConnectionStatus.SPEAKING else if (it.isMicActive) ConnectionStatus.LISTENING else it.connectionStatus
                    )
                }
            }
        )
        audioPlayer?.startPlaybackLoop(viewModelScope)

        // Setup AudioRecorder
        audioRecorder = AudioRecorder(
            onAudioChunk = { chunk ->
                if (_uiState.value.isMicActive && geminiService.isConnected()) {
                    geminiService.sendAudioChunk(chunk)
                }
            },
            onAmplitudeChanged = { amp ->
                _uiState.update { it.copy(userAmplitude = amp) }
            }
        )

        // Initialize Android TextToSpeech as companion / fallback audio engine
        try {
            tts = TextToSpeech(application, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(1.2f) // Youthful and sassy pitch
            tts?.setSpeechRate(1.05f) // Energetic pace
            isTtsReady = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _uiState.update { it.copy(isSpeaking = true, zoyaAmplitude = 0.7f) }
                }

                override fun onDone(utteranceId: String?) {
                    _uiState.update { it.copy(isSpeaking = false, zoyaAmplitude = 0f) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _uiState.update { it.copy(isSpeaking = false, zoyaAmplitude = 0f) }
                }
            })
        }
    }

    fun setAudioPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
    }

    fun updateApiKey(key: String) {
        _uiState.update { it.copy(apiKey = key) }
    }

    /**
     * Connect or disconnect the Gemini Live session.
     */
    fun toggleConnection() {
        val current = _uiState.value.connectionStatus
        if (current == ConnectionStatus.CONNECTED || current == ConnectionStatus.LISTENING || current == ConnectionStatus.SPEAKING) {
            disconnectSession()
        } else {
            connectSession()
        }
    }

    fun connectSession() {
        val key = _uiState.value.apiKey
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            // Activate interactive demo / companion mode so user can immediately test!
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    statusMessage = "Connected to Zoya (Interactive Companion Mode)",
                    isDemoFallbackActive = true
                )
            }
            startMic()
            speakSassyPhrase("Hey there handsome! Zoya is in the house. What's on your mind today?")
            return
        }

        _uiState.update { it.copy(isDemoFallbackActive = false) }
        geminiService.connect(key)
    }

    fun disconnectSession() {
        geminiService.disconnect()
        stopMic()
        audioPlayer?.interrupt()
        tts?.stop()
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                statusMessage = "Session disconnected. Tap to wake Zoya up.",
                isMicActive = false,
                isSpeaking = false,
                userAmplitude = 0f,
                zoyaAmplitude = 0f
            )
        }
    }

    fun toggleMic() {
        if (_uiState.value.isMicActive) {
            stopMic()
        } else {
            startMic()
        }
    }

    private fun startMic() {
        if (!_uiState.value.hasAudioPermission) {
            _uiState.update { it.copy(statusMessage = "Microphone permission required") }
            return
        }
        audioRecorder?.start(viewModelScope)
        _uiState.update {
            it.copy(
                isMicActive = true,
                statusMessage = "Listening... Speak naturally to Zoya"
            )
        }
    }

    private fun stopMic() {
        audioRecorder?.stop()
        _uiState.update {
            it.copy(
                isMicActive = false,
                userAmplitude = 0f,
                statusMessage = if (it.connectionStatus == ConnectionStatus.CONNECTED) "Microphone muted" else it.statusMessage
            )
        }
    }

    /**
     * Interruption handling: immediately cease playback when user interrupts.
     */
    fun interrupt() {
        audioPlayer?.interrupt()
        tts?.stop()
        _uiState.update {
            it.copy(
                isSpeaking = false,
                zoyaAmplitude = 0f,
                statusMessage = "Zoya paused for you"
            )
        }
    }

    /**
     * Sends a quick conversation prompt or banter line.
     */
    fun sendPrompt(text: String) {
        // Cease any current speech on new prompt
        interrupt()

        // Record transcript
        val userItem = TranscriptItem(text = text, isUser = true)
        _uiState.update { it.copy(transcripts = it.transcripts + userItem) }

        if (geminiService.isConnected() && !_uiState.value.isDemoFallbackActive) {
            geminiService.sendTextTurn(text)
            _uiState.update { it.copy(statusMessage = "Zoya is thinking of a witty reply...") }
        } else {
            // Interactive Companion response
            val response = ZoyaPersonality.getSassyResponse(text)
            val zoyaItem = TranscriptItem(text = response, isUser = false)
            _uiState.update {
                it.copy(
                    transcripts = it.transcripts + zoyaItem,
                    statusMessage = "Zoya is speaking..."
                )
            }
            speakSassyPhrase(response)

            // Check if openWebsite was implied in prompt
            if (text.contains("YouTube", ignoreCase = true)) {
                onToolCall(
                    ToolCallEvent(
                        id = "call_sim_${System.currentTimeMillis()}",
                        name = "openWebsite",
                        args = mapOf("url" to "https://www.youtube.com")
                    )
                )
            } else if (text.contains("Maps", ignoreCase = true)) {
                onToolCall(
                    ToolCallEvent(
                        id = "call_sim_${System.currentTimeMillis()}",
                        name = "openWebsite",
                        args = mapOf("url" to "https://maps.google.com")
                    )
                )
            }
        }
    }

    private fun speakSassyPhrase(text: String) {
        if (isTtsReady) {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "zoya_${System.currentTimeMillis()}")
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "zoya_${System.currentTimeMillis()}")
        }
    }

    fun dismissToolEvent() {
        _uiState.update { it.copy(lastToolEvent = null) }
    }

    fun launchUrlInBrowser(url: String) {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        viewModelScope.launch {
            _browserIntentEvent.emit(cleanUrl)
        }
    }

    // --- GeminiLiveListener Callbacks ---

    override fun onConnectionStatusChanged(status: ConnectionStatus, message: String?) {
        _uiState.update {
            it.copy(
                connectionStatus = status,
                statusMessage = message ?: when (status) {
                    ConnectionStatus.CONNECTED -> "Zoya is online & listening"
                    ConnectionStatus.CONNECTING -> "Connecting to Gemini Live..."
                    ConnectionStatus.DISCONNECTED -> "Disconnected"
                    ConnectionStatus.ERROR -> "Connection error"
                    else -> it.statusMessage
                }
            )
        }

        if (status == ConnectionStatus.CONNECTED) {
            startMic()
        }
    }

    override fun onAudioReceived(audioPcm: ByteArray) {
        audioPlayer?.playAudioChunk(audioPcm)
    }

    override fun onTranscriptReceived(text: String, isUser: Boolean) {
        _uiState.update {
            it.copy(
                transcripts = it.transcripts + TranscriptItem(text = text, isUser = isUser)
            )
        }
    }

    override fun onInterrupted() {
        interrupt()
    }

    override fun onToolCall(toolCall: ToolCallEvent) {
        Log.d(tag, "Executing toolCall: ${toolCall.name} with args: ${toolCall.args}")
        _uiState.update { it.copy(lastToolEvent = toolCall) }

        if (toolCall.name == "openWebsite") {
            val rawUrl = toolCall.args["url"]?.toString() ?: "https://www.google.com"
            var cleanUrl = rawUrl.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }

            // Execute browser intent
            launchUrlInBrowser(cleanUrl)

            // Send toolResponse back to Gemini Live
            geminiService.sendToolResponse(
                callId = toolCall.id,
                result = mapOf(
                    "status" to "success",
                    "url" to cleanUrl,
                    "message" to "Website $cleanUrl opened in user's browser"
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder?.stop()
        audioPlayer?.release()
        geminiService.disconnect()
        tts?.stop()
        tts?.shutdown()
    }
}
