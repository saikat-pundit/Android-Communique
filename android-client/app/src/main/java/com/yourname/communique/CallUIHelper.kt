package com.yourname.communique

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*

object CallUIHelper {

    fun showCallSelectionDialog(context: Context, chatHistory: List<ChatMessage>, currentDeviceName: String, onCallInitiated: (String, Boolean) -> Unit) {
        val users = chatHistory.map { it.device }.distinct().filter { it != currentDeviceName }
        
        val dialog = AlertDialog.Builder(context).create()
        val dialogView = LinearLayout(context)
        dialogView.orientation = LinearLayout.VERTICAL
        dialogView.setPadding(60, 60, 60, 60)
        
        val bgShape = GradientDrawable()
        bgShape.setColor(Color.WHITE)
        bgShape.cornerRadius = 48f
        dialogView.background = bgShape

        val titleText = TextView(context)
        titleText.text = "Start a Call"
        titleText.textSize = 20f
        titleText.setTextColor(Color.parseColor("#075E54"))
        titleText.setTypeface(null, Typeface.BOLD)
        titleText.setPadding(0, 0, 0, 40)
        dialogView.addView(titleText)

        if (users.isEmpty()) {
            val emptyText = TextView(context)
            emptyText.text = "No other users available to call."
            dialogView.addView(emptyText)
        } else {
            val scrollView = ScrollView(context)
            val listLayout = LinearLayout(context)
            listLayout.orientation = LinearLayout.VERTICAL

            users.forEach { user ->
                val userRow = LinearLayout(context)
                userRow.orientation = LinearLayout.HORIZONTAL
                userRow.gravity = Gravity.CENTER_VERTICAL
                userRow.setPadding(0, 20, 0, 20)

                val nameText = TextView(context)
                nameText.text = user
                nameText.textSize = 16f
                nameText.setTextColor(Color.BLACK)
                val nameParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                nameText.layoutParams = nameParams

                val audioBtn = Button(context)
                audioBtn.text = "📞"
                audioBtn.setBackgroundColor(Color.TRANSPARENT)
                audioBtn.setOnClickListener {
                    dialog.dismiss()
                    onCallInitiated(user, false)
                }

                val videoBtn = Button(context)
                videoBtn.text = "📹"
                videoBtn.setBackgroundColor(Color.TRANSPARENT)
                videoBtn.setOnClickListener {
                    dialog.dismiss()
                    onCallInitiated(user, true)
                }

                userRow.addView(nameText)
                userRow.addView(audioBtn)
                userRow.addView(videoBtn)
                listLayout.addView(userRow)
            }
            scrollView.addView(listLayout)
            dialogView.addView(scrollView)
        }

        dialog.setView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    fun showOutgoingCallScreen(context: Context, targetUser: String, isVideo: Boolean, onCancel: () -> Unit) {
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).create()
        val view = LinearLayout(context)
        view.orientation = LinearLayout.VERTICAL
        view.gravity = Gravity.CENTER
        view.setBackgroundColor(Color.parseColor("#121212"))

        val statusText = TextView(context)
        statusText.text = if (isVideo) "Calling $targetUser\n(Video)..." else "Calling $targetUser\n(Audio)..."
        statusText.textSize = 24f
        statusText.setTextColor(Color.WHITE)
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 0, 0, 100)
        view.addView(statusText)

        val endBtn = Button(context)
        endBtn.text = "End Call"
        endBtn.setTextColor(Color.WHITE)
        val btnShape = GradientDrawable()
        btnShape.setColor(Color.RED)
        btnShape.cornerRadius = 100f
        endBtn.background = btnShape
        endBtn.setOnClickListener {
            dialog.dismiss()
            onCancel()
        }
        view.addView(endBtn)

        dialog.setView(view)
        dialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            delay(30000)
            if (dialog.isShowing) {
                dialog.dismiss()
                Toast.makeText(context, "Call Unanswered", Toast.LENGTH_SHORT).show()
                onCancel()
            }
        }
    }

    fun showIncomingCallScreen(context: Context, callerName: String, isVideo: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).create()
        val view = LinearLayout(context)
        view.orientation = LinearLayout.VERTICAL
        view.gravity = Gravity.CENTER
        view.setBackgroundColor(Color.parseColor("#075E54"))

        val titleText = TextView(context)
        titleText.text = "Incoming ${if (isVideo) "Video" else "Audio"} Call"
        titleText.textSize = 18f
        titleText.setTextColor(Color.LTGRAY)
        titleText.gravity = Gravity.CENTER
        view.addView(titleText)

        val callerText = TextView(context)
        callerText.text = callerName
        callerText.textSize = 32f
        callerText.setTextColor(Color.WHITE)
        callerText.setTypeface(null, Typeface.BOLD)
        callerText.gravity = Gravity.CENTER
        callerText.setPadding(0, 20, 0, 150)
        view.addView(callerText)

        val buttonLayout = LinearLayout(context)
        buttonLayout.orientation = LinearLayout.HORIZONTAL
        buttonLayout.gravity = Gravity.CENTER

        val declineBtn = Button(context)
        declineBtn.text = "Decline"
        declineBtn.setTextColor(Color.WHITE)
        val decShape = GradientDrawable()
        decShape.setColor(Color.RED)
        decShape.cornerRadius = 50f
        declineBtn.background = decShape
        val decParams = LinearLayout.LayoutParams(wrapContent, wrapContent)
        decParams.setMargins(0, 0, 40, 0)
        declineBtn.layoutParams = decParams
        declineBtn.setOnClickListener {
            dialog.dismiss()
            onDecline()
        }

        val acceptBtn = Button(context)
        acceptBtn.text = "Accept"
        acceptBtn.setTextColor(Color.WHITE)
        val accShape = GradientDrawable()
        accShape.setColor(Color.parseColor("#25D366"))
        accShape.cornerRadius = 50f
        acceptBtn.background = accShape
        acceptBtn.setOnClickListener {
            dialog.dismiss()
            onAccept()
        }

        buttonLayout.addView(declineBtn)
        buttonLayout.addView(acceptBtn)
        view.addView(buttonLayout)

        dialog.setView(view)
        dialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            delay(30000)
            if (dialog.isShowing) {
                dialog.dismiss()
                onDecline()
            }
        }
    }
    
    private val wrapContent = LinearLayout.LayoutParams.WRAP_CONTENT
}
