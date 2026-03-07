package com.yourname.communique

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View
import android.widget.ScrollView
object ChatUIHelper {

    fun updateUserCountBar(context: Context, userCountText: TextView, chatHistory: List<ChatMessage>, currentDeviceName: String) {
        val users = chatHistory.map { it.device }.distinct()
        val count = users.size
        
        if (count == 0) {
            userCountText.text = "0 users"
            return
        }

        // NEW: Map the list to append "(You)" to the current device
        val displayUsers = users.map { if (it == currentDeviceName) "$it (You)" else it }

        // Show "3 users (Phone A, Phone B...)"
        val displayNames = displayUsers.take(2).joinToString(", ") + if (count > 2) "..." else ""
        userCountText.text = "$count users ($displayNames)"

        // Open Modal on Click
        // Open Modal on Click using a custom modern UI
        userCountText.setOnClickListener {
            val dialog = AlertDialog.Builder(context).create()

            // 1. The Main Rounded Card Background
            val dialogView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 60, 60, 60)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 48f // Sleek rounded corners just like the web image!
                }
            }
            val headerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val titleText = TextView(context).apply {
                text = "👥 Connected Users"
                textSize = 18f
                setTextColor(Color.parseColor("#075E54"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val closeBtn = TextView(context).apply {
                text = "✖"
                textSize = 20f
                setTextColor(Color.GRAY)
                setTypeface(null, Typeface.BOLD)
                setPadding(20, 0, 0, 0)
                setOnClickListener { dialog.dismiss() } // Click X to close
            }

            headerLayout.addView(titleText)
            headerLayout.addView(closeBtn)
            dialogView.addView(headerLayout)
            val headerDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                    setMargins(0, 30, 0, 10)
                }
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            dialogView.addView(headerDivider)
            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            
            val listLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            displayUsers.forEach { user ->
                val isMe = user.endsWith("(You)")

                val itemLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 30, 0, 30)
                }

                // Native user icon
                val iconLabel = TextView(context).apply {
                    text = if (isMe) "🟢" else "👤" // Distinct native icon to separate "You" from others
                    textSize = 18f
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 0, 30, 0)
                    }
                }

                val nameText = TextView(context).apply {
                    text = user
                    textSize = 15f
                    setTextColor(Color.BLACK)
                    if (isMe) setTypeface(null, Typeface.BOLD)
                }

                itemLayout.addView(iconLabel)
                itemLayout.addView(nameText)
                listLayout.addView(itemLayout)
                val itemDivider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                    setBackgroundColor(Color.parseColor("#F5F5F5"))
                }
                listLayout.addView(itemDivider)
            }

            scrollView.addView(listLayout)
            dialogView.addView(scrollView)

            dialog.setView(dialogView)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
        }
    }

    // FEATURE 2: Minimized Padding, Media Download Restriction, and Message Quoting!
    fun buildMessageBubble(
        context: Context,
        msg: ChatMessage,
        currentDeviceName: String,
        isAutoDownload: Boolean,
        decryptedText: String,
        decryptedFileId: String?,
        currentSearchQuery: String,
        isFocusedSearchMatch: Boolean,
        mediaManager: MediaManager,
        onDownloadClicked: (String, String, String) -> Unit,
        onMessageLongClick: (String, String) -> Unit // <--- ADDED LISTENER FOR REPLIES
    ): LinearLayout {
        val isMe = msg.device == currentDeviceName
        
        val bubbleShape = GradientDrawable()
        bubbleShape.cornerRadius = 32f 
        if (isMe) bubbleShape.setColor(Color.parseColor("#DCF8C6")) else bubbleShape.setColor(Color.parseColor("#FFFFFF"))

        val bubbleLayout = LinearLayout(context)
        bubbleLayout.orientation = LinearLayout.VERTICAL
        bubbleLayout.background = bubbleShape
        bubbleLayout.setPadding(24, 16, 24, 16) 
        bubbleLayout.elevation = 2f

        if (!isMe) {
            val deviceText = TextView(context)
            deviceText.text = msg.device
            deviceText.textSize = 11f 
            deviceText.setTextColor(Color.parseColor("#007BFF"))
            deviceText.setTypeface(null, Typeface.BOLD)
            deviceText.setPadding(0, 0, 0, 4) 
            bubbleLayout.addView(deviceText)
        }

        if (decryptedFileId != null && msg.fileType != null) {
            val fileName = msg.fileName ?: "attachment"
            
            // --- NEW: Helper function to apply highlighting to file names ---
            fun getHighlightedFileName(baseText: String): CharSequence {
                if (currentSearchQuery.isNotEmpty() && baseText.contains(currentSearchQuery, ignoreCase = true)) {
                    val spannable = SpannableString(baseText)
                    val startPos = baseText.indexOf(currentSearchQuery, ignoreCase = true)
                    val highlightColor = if (isFocusedSearchMatch) Color.parseColor("#FF9800") else Color.YELLOW
                    val textColor = if (isFocusedSearchMatch) Color.WHITE else Color.BLACK
                    spannable.setSpan(BackgroundColorSpan(highlightColor), startPos, startPos + currentSearchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(ForegroundColorSpan(textColor), startPos, startPos + currentSearchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    return spannable
                }
                return baseText
            }
            // ---------------------------------------------------------------

            if (msg.fileType.startsWith("image/") && isAutoDownload) {
                val thumbnailView = ImageView(context)
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400) 
                params.setMargins(0, 4, 0, 8)
                thumbnailView.layoutParams = params
                thumbnailView.scaleType = ImageView.ScaleType.CENTER_CROP
                thumbnailView.setBackgroundColor(Color.parseColor("#DDDDDD"))
                
                thumbnailView.setOnClickListener { onDownloadClicked(decryptedFileId, fileName, msg.fileType) }
                bubbleLayout.addView(thumbnailView)

                // NEW: Show the image filename under the thumbnail so it can be searched and highlighted!
                val imageFileNameText = TextView(context)
                imageFileNameText.text = getHighlightedFileName("🖼️ $fileName")
                imageFileNameText.textSize = 11f
                imageFileNameText.setTextColor(Color.GRAY)
                imageFileNameText.setPadding(0, 0, 0, 8)
                bubbleLayout.addView(imageFileNameText)

                CoroutineScope(Dispatchers.IO).launch {
                    mediaManager.loadThumbnail(decryptedFileId, fileName, thumbnailView)
                }
            } else {
                val attachmentContainer = LinearLayout(context)
                attachmentContainer.orientation = LinearLayout.HORIZONTAL
                attachmentContainer.gravity = Gravity.CENTER_VERTICAL
                attachmentContainer.setPadding(0, 4, 0, 8)

                val downloadIcon = ImageView(context)
                downloadIcon.setImageResource(android.R.drawable.ic_menu_save)
                downloadIcon.setColorFilter(Color.parseColor("#075E54"))
                val iconParams = LinearLayout.LayoutParams(40, 40) 
                iconParams.setMargins(0, 0, 12, 0)
                downloadIcon.layoutParams = iconParams

                val attachmentText = TextView(context)
                
                // UPDATED: Include the filename for images too, and apply highlight
                val baseText = if (msg.fileType.startsWith("image/")) "🖼️ Image: $fileName" else "📎 $fileName"
                attachmentText.text = getHighlightedFileName(baseText)
                attachmentText.textSize = 13f
                attachmentText.setTextColor(Color.parseColor("#075E54"))

                attachmentContainer.addView(downloadIcon)
                attachmentContainer.addView(attachmentText)
                
                attachmentContainer.setOnClickListener { onDownloadClicked(decryptedFileId, fileName, msg.fileType) }
                bubbleLayout.addView(attachmentContainer)
            }
        }

        // --- NEW: RENDER QUOTED MESSAGE ---
        if (msg.replyToDevice != null && msg.replyToText != null) {
            val decryptedReplyDevice = CryptoHelper.decrypt(msg.replyToDevice)
            val decryptedReplyText = CryptoHelper.decrypt(msg.replyToText)

            val quoteLayout = LinearLayout(context)
            quoteLayout.orientation = LinearLayout.VERTICAL
            quoteLayout.setPadding(16, 8, 16, 8)
            
            val quoteShape = GradientDrawable()
            quoteShape.cornerRadius = 16f
            quoteShape.setColor(Color.parseColor(if (isMe) "#CDEBB5" else "#F0F0F0"))
            quoteLayout.background = quoteShape
            
            val quoteParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            quoteParams.setMargins(0, 0, 0, 12)
            quoteLayout.layoutParams = quoteParams

            val quoteDevice = TextView(context)
            quoteDevice.text = decryptedReplyDevice
            quoteDevice.textSize = 10f
            quoteDevice.setTextColor(Color.parseColor("#007BFF"))
            quoteDevice.setTypeface(null, Typeface.BOLD)
            
            val quoteText = TextView(context)
            quoteText.text = if (decryptedReplyText.length > 50) decryptedReplyText.take(50) + "..." else decryptedReplyText
            quoteText.textSize = 12f
            quoteText.setTextColor(Color.DKGRAY)
            quoteText.setTypeface(null, Typeface.ITALIC)

            quoteLayout.addView(quoteDevice)
            quoteLayout.addView(quoteText)
            bubbleLayout.addView(quoteLayout)
        }
        // ----------------------------------

        val messageView = TextView(context)
        messageView.textSize = 15f
        messageView.setTextColor(Color.BLACK)

        if (currentSearchQuery.isNotEmpty() && decryptedText.contains(currentSearchQuery, ignoreCase = true)) {
            val spannable = SpannableString(decryptedText)
            val startPos = decryptedText.indexOf(currentSearchQuery, ignoreCase = true)
            
            val highlightColor = if (isFocusedSearchMatch) Color.parseColor("#FF9800") else Color.YELLOW
            val textColor = if (isFocusedSearchMatch) Color.WHITE else Color.BLACK

            spannable.setSpan(BackgroundColorSpan(highlightColor), startPos, startPos + currentSearchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(textColor), startPos, startPos + currentSearchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            messageView.text = spannable
        } else {
            messageView.text = decryptedText
        }
        bubbleLayout.addView(messageView)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeText = TextView(context)
        timeText.text = timeFormat.format(Date(msg.timestamp))
        timeText.textSize = 9f
        timeText.setTextColor(Color.GRAY)
        timeText.setTypeface(null, Typeface.ITALIC)
        timeText.gravity = Gravity.END
        timeText.setPadding(0, 4, 0, 0) 
        bubbleLayout.addView(timeText)

        // --- NEW: LONG CLICK TO TRIGGER REPLY ---
        bubbleLayout.setOnLongClickListener {
            onMessageLongClick(msg.device, decryptedText)
            true
        }
        // ----------------------------------------

        val wrapper = LinearLayout(context)
        val wrapperParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        wrapperParams.setMargins(0, 6, 0, 6) 
        wrapper.layoutParams = wrapperParams
        
        if (isMe) {
            wrapper.gravity = Gravity.END
            wrapper.setPadding(100, 0, 0, 0) 
        } else {
            wrapper.gravity = Gravity.START
            wrapper.setPadding(0, 0, 100, 0)
        }
        
        wrapper.addView(bubbleLayout)
        return wrapper
    }
}
