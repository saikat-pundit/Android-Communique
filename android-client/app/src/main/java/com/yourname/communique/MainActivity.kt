package com.yourname.communique

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class ChatMessage(val device: String, val message: String, val timestamp: Long)

class MainActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private var currentDeviceName = ""
    private var chatHistory = mutableListOf<ChatMessage>()
    private var isPolling = false

    private lateinit var chatMessageContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Fetch exact OEM Device Name automatically
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        currentDeviceName = "$manufacturer ${Build.MODEL}"

        val loginLayout = findViewById<LinearLayout>(R.id.loginLayout)
        val chatLayout = findViewById<LinearLayout>(R.id.chatLayout)
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val detectedDeviceText = findViewById<TextView>(R.id.detectedDeviceText)
        
        chatMessageContainer = findViewById(R.id.chatMessageContainer)
        chatScrollView = findViewById(R.id.chatScrollView)

        detectedDeviceText.text = "Device: $currentDeviceName"

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            // Log in automatically without a PIN
            loginLayout.visibility = View.GONE
            chatLayout.visibility = View.VISIBLE
            
            isPolling = true
            startPollingGist()
        }

        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                messageInput.text.clear()
                
                // Encrypt using the Universal Key
                val encryptedText = encryptMessage(text)
                val newMessage = ChatMessage(currentDeviceName, encryptedText, System.currentTimeMillis())
                
                chatHistory.add(newMessage)
                updateChatUI()
                
                CoroutineScope(Dispatchers.IO).launch {
                    pushGistUpdate(chatHistory)
                }
            }
        }
    }

    // --- AES-256 UNIVERSAL ENCRYPTION LOGIC ---
    private fun getSecretKey(): SecretKeySpec {
        // Fetches the secret injected by GitHub Actions securely
        val universalKey = BuildConfig.ENCRYPTION_KEY
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(universalKey.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encryptMessage(message: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }

    private fun decryptMessage(encryptedMessage: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey())
            val decodedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            "🔒 [Decryption Failed - Key Mismatch]"
        }
    }
    // --------------------------------

    private fun startPollingGist() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isPolling) {
                fetchGist()
                delay(3000)
            }
        }
    }

    private fun fetchGist() {
        val request = Request.Builder()
            .url("https://api.github.com/gists/${BuildConfig.CHAT_GIST_ID}")
            .addHeader("Authorization", "Bearer ${BuildConfig.GIST_TOKEN}")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    if (responseData != null) {
                        val jsonObject = JSONObject(responseData)
                        val content = jsonObject.getJSONObject("files").getJSONObject("chat_ledger.json").getString("content")

                        val listType = object : TypeToken<List<ChatMessage>>() {}.type
                        val fetchedHistory: List<ChatMessage> = gson.fromJson(content, listType) ?: emptyList()

                        if (fetchedHistory.size > chatHistory.size) {
                            chatHistory.clear()
                            chatHistory.addAll(fetchedHistory)
                            CoroutineScope(Dispatchers.Main).launch { updateChatUI() }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun pushGistUpdate(history: List<ChatMessage>) {
        val payload = JSONObject()
        val files = JSONObject()
        val fileContent = JSONObject()
        fileContent.put("content", gson.toJson(history))
        files.put("chat_ledger.json", fileContent)
        payload.put("files", files)

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.github.com/gists/${BuildConfig.CHAT_GIST_ID}")
            .addHeader("Authorization", "Bearer ${BuildConfig.GIST_TOKEN}")
            .patch(body)
            .build()

        try { httpClient.newCall(request).execute().close() } catch (e: Exception) {}
    }

    // --- UPGRADED CHAT UI ---
    private fun updateChatUI() {
        chatMessageContainer.removeAllViews()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        for (msg in chatHistory) {
            // Decrypt using the Universal Key
            val decryptedText = decryptMessage(msg.message)
            val isMe = msg.device == currentDeviceName

            val bubbleShape = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(if (isMe) Color.parseColor("#DCF8C6") else Color.parseColor("#FFFFFF"))
            }

            val bubbleLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = bubbleShape
                setPadding(32, 24, 32, 24)
            }

            if (!isMe) {
                val senderView = TextView(this).apply {
                    text = msg.device
                    textSize = 12f
                    setTextColor(Color.parseColor("#075E54"))
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 8)
                }
                bubbleLayout.addView(senderView)
            }

            val messageView = TextView(this).apply {
                text = decryptedText
                textSize = 16f
                setTextColor(Color.BLACK)
            }
            bubbleLayout.addView(messageView)

            val timeView = TextView(this).apply {
                text = timeFormat.format(Date(msg.timestamp))
                textSize = 10f
                setTextColor(Color.GRAY)
                setTypeface(null, Typeface.ITALIC)
                gravity = Gravity.END
                setPadding(0, 8, 0, 0)
            }
            bubbleLayout.addView(timeView)

            val wrapper = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 8, 0, 8)
                }
                gravity = if (isMe) Gravity.END else Gravity.START
                setPadding(if (isMe) 120 else 0, 0, if (isMe) 0 else 120, 0)
            }
            wrapper.addView(bubbleLayout)
            chatMessageContainer.addView(wrapper)
        }

        chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
    }
}
