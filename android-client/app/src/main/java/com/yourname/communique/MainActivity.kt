package com.yourname.communique
import android.provider.MediaStore
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Okio
import okio.Sink
import okio.buffer
import android.view.inputmethod.InputMethodManager
import android.app.AlertDialog
import android.content.DialogInterface

data class ChatMessage(
    val device: String, 
    val message: String, 
    val timestamp: Long, 
    val driveFileId: String? = null, 
    val fileType: String? = null, 
    val fileName: String? = null,
    val replyToDevice: String? = null,
    val replyToText: String? = null,
    val groupName: String? = null
)

class MainActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private val networkHelper = NetworkHelper(httpClient, gson)
    private var currentDeviceName = ""
    private var chatHistory = mutableListOf<ChatMessage>()
    private var isPolling = false
    private var isFirstLoad = true
    private val CHANNEL_ID = "communique_chat"
    private var currentSearchQuery = ""
    private var searchMatchIndices = mutableListOf<Int>()
    private var currentSearchIndex = -1
    private var replyingToDevice: String? = null
    private var replyingToText: String? = null
    private var highestSeenTimestamp = 0L
    private lateinit var chatMessageContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var userCountText: TextView
    private lateinit var searchPositionText: TextView
    private lateinit var searchIndicatorLayout: LinearLayout
    private lateinit var messageInput: EditText
    private var currentGroupName: String? = null
    private lateinit var groupOverlay: FrameLayout
    private lateinit var mediaManager: MediaManager
    private lateinit var chatLayout: RelativeLayout
    private lateinit var sharedPrefs: android.content.SharedPreferences
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Toast.makeText(this, "Preparing file for upload...", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                handleFileUpload(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPrefs = getSharedPreferences("CommuniqueCache", Context.MODE_PRIVATE)
        
        try {
            val cacheFile = java.io.File(filesDir, "chat_ledger_cache.json")
            if (cacheFile.exists()) {
                val cachedJson = cacheFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<ChatMessage>>() {}.type
                val cachedList: List<ChatMessage> = gson.fromJson(cachedJson, type)
                chatHistory.clear()
                chatHistory.addAll(cachedList)
                highestSeenTimestamp = chatHistory.maxOfOrNull { it.timestamp } ?: 0L
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        mediaManager = MediaManager(this, httpClient)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        createNotificationChannel()

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        currentDeviceName = "$manufacturer ${Build.MODEL}"

        val loginLayout = findViewById<LinearLayout>(R.id.loginLayout)
        chatLayout = findViewById(R.id.chatLayout)
        val loginTriggerButton = findViewById<Button>(R.id.loginTriggerButton)
        val pinContainer = findViewById<LinearLayout>(R.id.pinContainer)
        val pinInput = findViewById<EditText>(R.id.pinInput)
        val unlockButton = findViewById<Button>(R.id.unlockButton)
        messageInput = findViewById<EditText>(R.id.messageInput)
        val detectedDeviceText = findViewById<TextView>(R.id.detectedDeviceText)
        val gifImageView = findViewById<ImageView>(R.id.loginGif)

        userCountText = findViewById(R.id.userCountText)
        val searchIcon = findViewById<ImageView>(R.id.searchIcon)
        val searchContainer = findViewById<LinearLayout>(R.id.searchContainer)
        val searchInput = findViewById<EditText>(R.id.searchInput)
        val closeSearchBtn = findViewById<ImageButton>(R.id.closeSearchBtn)
        searchIndicatorLayout = findViewById(R.id.searchIndicatorLayout)
        searchPositionText = findViewById(R.id.searchPositionText)
        val searchUpBtn = findViewById<ImageButton>(R.id.searchUpBtn)
        val searchDownBtn = findViewById<ImageButton>(R.id.searchDownBtn)
        val attachButton = findViewById<ImageButton>(R.id.attachButton)

        chatMessageContainer = findViewById(R.id.chatMessageContainer)
        chatScrollView = findViewById(R.id.chatScrollView)
        
        groupOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
            elevation = 100f
            setBackgroundColor(Color.WHITE)
        }
        addContentView(groupOverlay, groupOverlay.layoutParams)

        val backButton = androidx.appcompat.widget.AppCompatImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            
            layoutParams = LinearLayout.LayoutParams(90, 90).apply {
                setMargins(0, 0, 32, 0)
            }
            setPadding(12, 12, 12, 12)
            
            setOnClickListener {
                currentGroupName?.let { markGroupAsRead(it) }
                currentGroupName = null
                chatLayout.visibility = View.GONE
                showGroupScreen()
            }
        }
        (userCountText.parent as LinearLayout).addView(backButton, 0)

        val callButton = androidx.appcompat.widget.AppCompatImageView(this).apply {
            setImageResource(R.drawable.ic_call)
            
            layoutParams = LinearLayout.LayoutParams(90, 90).apply {
                setMargins(0, 0, 24, 0)
            }
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.attr.selectableItemBackgroundBorderless)
            
            setOnClickListener {
                if (currentGroupName == "Personal Chat" || currentGroupName == null) {
                    CallUIHelper.showCallSelectionDialog(
                        context = this@MainActivity,
                        chatHistory = chatHistory,
                        currentDeviceName = currentDeviceName
                    ) { targetUser, isVideo ->
                        CallUIHelper.showOutgoingCallScreen(
                            context = this@MainActivity,
                            targetUser = targetUser,
                            isVideo = isVideo
                        ) {
                            
                        }
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Calling is only available in Personal Chat", Toast.LENGTH_SHORT).show()
                }
            }
        }
        (userCountText.parent as LinearLayout).addView(callButton, 2)
        
        detectedDeviceText.text = "Device Name: $currentDeviceName"

        try { Glide.with(this).asGif().load(R.drawable.login).into(gifImageView) } catch (e: Exception) {}

        loginTriggerButton.setOnClickListener {
            loginTriggerButton.animate().alpha(0f).setDuration(300).withEndAction {
                loginTriggerButton.visibility = View.GONE
                pinContainer.visibility = View.VISIBLE
                pinContainer.animate().alpha(1f).setDuration(400).start()
            }.start()
        }

        unlockButton.setOnClickListener {
            if (pinInput.text.toString() == "3142") {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(pinInput.windowToken, 0)

                loginLayout.animate().alpha(0f).setDuration(500).withEndAction {
                    loginLayout.visibility = View.GONE
                    chatLayout.alpha = 1f 
                    isPolling = true
                    startPollingGist()
                    showGroupScreen()
                }.start()
            } else {
                Toast.makeText(this, "Incorrect App Lock PIN", Toast.LENGTH_SHORT).show()
            }
        }

        searchIcon.setOnClickListener {
            if (searchContainer.visibility == View.VISIBLE) {
                closeSearch(searchContainer, searchInput)
            } else {
                searchContainer.visibility = View.VISIBLE
                searchInput.requestFocus()
            }
        }

        closeSearchBtn.setOnClickListener { closeSearch(searchContainer, searchInput) }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s.toString()
                executeSearch()
            }
        })
        
        searchUpBtn.setOnClickListener {
            if (searchMatchIndices.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex > 0) currentSearchIndex - 1 else searchMatchIndices.size - 1
                updateSearchIndicatorAndScroll()
            }
        }

        searchDownBtn.setOnClickListener {
            if (searchMatchIndices.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex < searchMatchIndices.size - 1) currentSearchIndex + 1 else 0
                updateSearchIndicatorAndScroll()
            }
        }

        attachButton.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        findViewById<View>(R.id.sendButton).setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                messageInput.text.clear()
                sendMessage(text, null, null)
            }
        }
    }

    private fun saveCacheAndReadState() {
        try {
            val cacheFile = java.io.File(filesDir, "chat_ledger_cache.json")
            cacheFile.writeText(gson.toJson(chatHistory))
        } catch (e: Exception) { e.printStackTrace() }
        currentGroupName?.let { markGroupAsRead(it) }
    }

    private fun markGroupAsRead(group: String) {
        val count = chatHistory.count { (it.groupName ?: "Personal Chat") == group }
        sharedPrefs.edit().putInt("read_count_$group", count).apply()
    }

    private fun getUnreadCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val grouped = chatHistory.groupBy { it.groupName ?: "Personal Chat" }
        for ((group, messages) in grouped) {
            val lastRead = sharedPrefs.getInt("read_count_$group", 0)
            val unread = messages.size - lastRead
            if (unread > 0) counts[group] = unread
        }
        return counts
    }

    private fun showGroupScreen() {
        groupOverlay.removeAllViews()
        val screen = GroupUIHelper.buildGroupScreen(
            context = this,
            chatHistory = chatHistory,
            unreadCounts = getUnreadCounts(),
            onGroupSelected = { groupName ->
                currentGroupName = groupName
                markGroupAsRead(groupName) 
                groupOverlay.visibility = View.GONE
                chatLayout.visibility = View.VISIBLE
                updateChatUI()
                updateUserCount()
            },
            onGroupCreated = { newGroupName ->
                currentGroupName = newGroupName
                markGroupAsRead(newGroupName)
                groupOverlay.visibility = View.GONE
                chatLayout.visibility = View.VISIBLE
                sendMessage("Created group: $newGroupName", null, null, null) 
            },
            onGroupRename = { oldName, newName ->
                val updatedHistory = chatHistory.map {
                    if ((it.groupName ?: "Personal Chat") == oldName) {
                        it.copy(groupName = newName)
                    } else {
                        it
                    }
                }
                chatHistory.clear()
                chatHistory.addAll(updatedHistory)
                val sysMsgText = CryptoHelper.encrypt("🔄 Group renamed from '$oldName' to '$newName'")
                val sysMsg = ChatMessage(
                    device = currentDeviceName, 
                    message = sysMsgText, 
                    timestamp = System.currentTimeMillis(), 
                    driveFileId = null, 
                    fileType = null, 
                    fileName = null, 
                    replyToDevice = null, 
                    replyToText = null, 
                    groupName = newName
                )
                chatHistory.add(sysMsg)
                val oldReadCount = sharedPrefs.getInt("read_count_$oldName", 0)
                sharedPrefs.edit().putInt("read_count_$newName", oldReadCount + 1).remove("read_count_$oldName").apply()
                   
                saveCacheAndReadState()
                CoroutineScope(Dispatchers.IO).launch { networkHelper.pushGistUpdate(chatHistory) }
                showGroupScreen() 
            },
            onGroupDelete = { groupToDelete ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Group?")
                    .setMessage("Do you want to delete '$groupToDelete' and ALL its messages permanently?")
                    .setPositiveButton("Yes") { _: DialogInterface, _: Int ->  
                       
                        val pinInput = EditText(this).apply { 
                            hint = "Enter App PIN"
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                            gravity = Gravity.CENTER
                        }
                       
                        AlertDialog.Builder(this)
                            .setTitle("Authentication Required")
                            .setView(pinInput)
                            .setPositiveButton("Confirm") { _: DialogInterface, _: Int -> 
                                if (pinInput.text.toString() == "3142") {
                                    chatHistory.removeAll { (it.groupName ?: "Personal Chat") == groupToDelete }
                                    sharedPrefs.edit().remove("read_count_$groupToDelete").apply()
                                   
                                    saveCacheAndReadState()
                                    CoroutineScope(Dispatchers.IO).launch { networkHelper.pushGistUpdate(chatHistory) }
                                    showGroupScreen()
                                    Toast.makeText(this, "Group deleted.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "Incorrect PIN.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        )
        groupOverlay.addView(screen)
        groupOverlay.visibility = View.VISIBLE
    }

    private fun sendMessage(rawText: String, driveFileId: String? = null, fileType: String? = null, fileName: String? = null) {
        val encryptedText = CryptoHelper.encrypt(rawText)
        val encryptedFileId = driveFileId?.let { CryptoHelper.encrypt(it) }
        
        val encryptedReplyDevice = replyingToDevice?.let { CryptoHelper.encrypt(it) }
        val encryptedReplyText = replyingToText?.let { CryptoHelper.encrypt(it) }
        
        val newMessage = ChatMessage(currentDeviceName, encryptedText, System.currentTimeMillis(), encryptedFileId, fileType, fileName, encryptedReplyDevice, encryptedReplyText, currentGroupName ?: "Personal Chat")
        
        replyingToDevice = null
        replyingToText = null
        messageInput.hint = "Type a message..."
        
        CoroutineScope(Dispatchers.IO).launch {
            val latestHistory = networkHelper.fetchChatHistory()
            if (latestHistory != null) {
                chatHistory.clear()
                chatHistory.addAll(latestHistory)
            }
            
            chatHistory.add(newMessage)
            highestSeenTimestamp = newMessage.timestamp
            saveCacheAndReadState()
            networkHelper.pushGistUpdate(chatHistory)          
            CoroutineScope(Dispatchers.Main).launch { updateChatUI() }
        }
    }

    private suspend fun handleFileUpload(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
               
                var fileName = "file_${System.currentTimeMillis()}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst()) fileName = cursor.getString(nameIndex)
                }

                val fileSize = contentResolver.openInputStream(uri)?.available() ?: 0
                if (fileSize > 5 * 1024 * 1024) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "File too large (max 5MB)", Toast.LENGTH_LONG).show() }
                    return@withContext
                }

                var fileBytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext
                var finalMimeType = mimeType
                var finalFileName = fileName

                if (mimeType.startsWith("image/")) {
                    try {
                        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(contentResolver, uri)
                            ImageDecoder.decodeBitmap(source)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        }

                        val baos = ByteArrayOutputStream()
                        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                       
                        bitmap.compress(format, 80, baos)
                        fileBytes = baos.toByteArray()
                        finalMimeType = "image/webp"
                        finalFileName = fileName.substringBeforeLast(".") + ".webp"
                    } catch (e: Exception) { 
                        e.printStackTrace() 
                    }
                }

                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Encrypting and Uploading $finalFileName...", Toast.LENGTH_SHORT).show() }

                val encryptedBytes = mediaManager.encryptFileBytes(fileBytes)
                val base64File = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)

                val jsonPayload = JSONObject().apply {
                    put("fileName", finalFileName)
                    put("mimeType", "application/octet-stream")
                    put("fileBase64", base64File)
                }

                val baseRequestBody = jsonPayload.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                
                val progressRequestBody = ProgressRequestBody(baseRequestBody) { progress ->
                    CoroutineScope(Dispatchers.Main).launch {
                        messageInput.hint = "Uploading: $progress%"
                    }
                }

                val request = Request.Builder().url(CryptoHelper.getSecret(BuildConfig.GAS_UPLOAD_URL)).post(progressRequestBody).build()
                httpClient.newCall(request).execute().use { response ->
                    val responseString = response.body?.string()
                    if (response.isSuccessful && responseString != null) {
                        val jsonResponse = JSONObject(responseString)
                        if (jsonResponse.optString("status") == "success") {
                            val fileId = jsonResponse.getString("fileId")
                           
                            withContext(Dispatchers.Main) {
                                val text = messageInput.text.toString().trim().ifEmpty { "Sent an encrypted file" }
                                messageInput.text.clear()
                                messageInput.hint = "Type a message..."
                                Toast.makeText(this@MainActivity, "Upload complete!", Toast.LENGTH_SHORT).show()
                               
                                sendMessage(text, fileId, finalMimeType, finalFileName)
                            }
                        } else {
                            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "GAS Error: ${jsonResponse.optString("message")}", Toast.LENGTH_LONG).show() }
                        }
                    } else {
                        withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Upload failed (${response.code})", Toast.LENGTH_LONG).show() }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Upload error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun triggerDownload(fileId: String, fileName: String, fileType: String) {
        Toast.makeText(this, "Downloading and decrypting...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            val file = mediaManager.downloadAndDecryptFile(fileId, fileName)
            
            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@MainActivity,
                    "${applicationContext.packageName}.provider",
                    file
                )
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, fileType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "No app found to open this file.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to download or decrypt file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun closeSearch(container: LinearLayout, input: EditText) {
        container.visibility = View.GONE
        currentSearchQuery = ""
        input.text.clear()
        searchMatchIndices.clear()
        currentSearchIndex = -1
        searchIndicatorLayout.visibility = View.GONE
        updateChatUI()
    }

    private fun executeSearch() {
        searchMatchIndices.clear()
        currentSearchIndex = -1
        if (currentSearchQuery.isEmpty()) {
            searchIndicatorLayout.visibility = View.GONE
            updateChatUI()
            return
        }

        for ((index, msg) in chatHistory.withIndex()) {
            val textMatch = CryptoHelper.decrypt(msg.message).contains(currentSearchQuery, ignoreCase = true)
            val fileMatch = msg.fileName?.contains(currentSearchQuery, ignoreCase = true) == true
            
            if (textMatch || fileMatch) {
                searchMatchIndices.add(index)
            }
        }

        if (searchMatchIndices.isNotEmpty()) {
            searchIndicatorLayout.visibility = View.VISIBLE
            currentSearchIndex = searchMatchIndices.size - 1
        } else {
            searchIndicatorLayout.visibility = View.GONE
        }
        updateSearchIndicatorAndScroll()
    }

    private fun updateSearchIndicatorAndScroll() {
        if (searchMatchIndices.isEmpty()) return
        searchPositionText.text = "(${currentSearchIndex + 1}/${searchMatchIndices.size})"
        updateChatUI()
        val targetGlobalIndex = searchMatchIndices[currentSearchIndex]
        val wrapperLayout = chatMessageContainer.getChildAt(targetGlobalIndex)
        if (wrapperLayout != null) {
            chatScrollView.post { chatScrollView.smoothScrollTo(0, wrapperLayout.top) }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Communique Chat", NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun showNotification(sender: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle(sender)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } else {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    private fun playNotificationSound() {
        try {
            RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play()
        } catch (e: Exception) {}
    }

    private fun startPollingGist() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isPolling) {
                val fetchedHistory = networkHelper.fetchChatHistory()
                
                if (fetchedHistory != null && fetchedHistory != chatHistory) {
                    val lastMessage = fetchedHistory.lastOrNull()
                    val isMe = lastMessage?.device == currentDeviceName
                    val isGenuinelyNew = lastMessage != null && lastMessage.timestamp > highestSeenTimestamp

                    chatHistory.clear()
                    chatHistory.addAll(fetchedHistory)
                    saveCacheAndReadState()
                    
                    if (lastMessage != null && lastMessage.timestamp > highestSeenTimestamp) {
                        highestSeenTimestamp = lastMessage.timestamp
                    }
                 
                    CoroutineScope(Dispatchers.Main).launch {
                        if (currentGroupName == null) {
                            showGroupScreen()
                        } else {
                            val groupStillExists = chatHistory.any { (it.groupName ?: "Personal Chat") == currentGroupName } || currentGroupName == "Personal Chat"
                            if (!groupStillExists) {
                                currentGroupName = null
                                chatLayout.visibility = View.GONE
                                showGroupScreen()
                                Toast.makeText(this@MainActivity, "This group was deleted by another user.", Toast.LENGTH_LONG).show()
                            } else {
                                updateChatUI()
                                updateUserCount()
                            }
                        }
                        
                        if (!isFirstLoad && !isMe && isGenuinelyNew && lastMessage != null) {
                            playNotificationSound()
                            showNotification(lastMessage.device, CryptoHelper.decrypt(lastMessage.message))
                        }
                        isFirstLoad = false
                    }
                } else if (isFirstLoad && fetchedHistory != null) {
                    val lastMsg = fetchedHistory.lastOrNull()
                    if (lastMsg != null && lastMsg.timestamp > highestSeenTimestamp) {
                        highestSeenTimestamp = lastMsg.timestamp
                    }
                    isFirstLoad = false
                    chatHistory.clear()
                    chatHistory.addAll(fetchedHistory)
                    saveCacheAndReadState()
                    CoroutineScope(Dispatchers.Main).launch {
                        if (currentGroupName == null) showGroupScreen() else { updateChatUI(); updateUserCount() }
                    }
                }
                delay(5000)
            }
        }
    }

    private fun updateUserCount() {
        val group = currentGroupName ?: "Personal Chat"
        val groupHistory = chatHistory.filter { (it.groupName ?: "Personal Chat") == group }
        ChatUIHelper.updateUserCountBar(this, userCountText, groupHistory, currentDeviceName, group)
    }

    private fun updateChatUI() {
        chatMessageContainer.removeAllViews()
        val groupHistory = chatHistory.filter { (it.groupName ?: "Personal Chat") == (currentGroupName ?: "Personal Chat") }
        val imageIndices = groupHistory.indices.filter { groupHistory[it].fileType?.startsWith("image/") == true }
        val autoDownloadIndices = imageIndices.takeLast(2)
        for ((index, msg) in groupHistory.withIndex()) {
            val decryptedText = CryptoHelper.decrypt(msg.message)
            val decryptedFileId = msg.driveFileId?.let { CryptoHelper.decrypt(it) }
            val isFocusedMatch = searchMatchIndices.isNotEmpty() && currentSearchIndex >= 0 && searchMatchIndices[currentSearchIndex] == index
            val isAutoDownload = index in autoDownloadIndices

            val bubbleView = ChatUIHelper.buildMessageBubble(
                context = this,
                msg = msg,
                currentDeviceName = currentDeviceName,
                isAutoDownload = isAutoDownload,
                decryptedText = decryptedText,
                decryptedFileId = decryptedFileId,
                currentSearchQuery = currentSearchQuery,
                isFocusedSearchMatch = isFocusedMatch,
                mediaManager = mediaManager,
                onDownloadClicked = { fileId, fileName, fileType ->
                    triggerDownload(fileId, fileName, fileType)
                },
                onMessageLongClick = { quotedDevice, quotedText ->
                    replyingToDevice = quotedDevice
                    replyingToText = quotedText
                    messageInput.hint = "Replying to $quotedDevice (Tap to cancel)..."
                    
                    messageInput.setOnClickListener {
                        if (messageInput.text.isEmpty() && replyingToDevice != null) {
                            replyingToDevice = null
                            replyingToText = null
                            messageInput.hint = "Type a message..."
                            Toast.makeText(this@MainActivity, "Reply cancelled", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) 

            chatMessageContainer.addView(bubbleView)
        }

        if (currentSearchQuery.isEmpty() && chatMessageContainer.childCount > 0) {
            chatScrollView.post { chatScrollView.smoothScrollTo(0, chatMessageContainer.bottom) }
        }
    }
}

class ProgressRequestBody(
    private val requestBody: RequestBody,
    private val onProgressUpdate: (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = requestBody.contentType()

    override fun contentLength(): Long = requestBody.contentLength()

    override fun writeTo(sink: BufferedSink) {
        var lastProgress = -1
        
        val countingSink: Sink = object : ForwardingSink(sink) {
            var bytesWritten = 0L
            var contentLength = 0L

            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                if (contentLength == 0L) {
                    contentLength = contentLength()
                }
                bytesWritten += byteCount
                val progress = ((bytesWritten.toFloat() / contentLength.toFloat()) * 100).toInt()
                
                if (progress != lastProgress) {
                    lastProgress = progress
                    onProgressUpdate(progress)
                }
            }
        }
        
        val bufferedSink = countingSink.buffer()
        requestBody.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}
