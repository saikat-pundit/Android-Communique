package com.yourname.communique

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

object GroupUIHelper {
    fun buildGroupScreen(
        context: Context,
        chatHistory: List<ChatMessage>,
        onGroupSelected: (String) -> Unit,
        onGroupCreated: (String) -> Unit
    ): LinearLayout {
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#E5DDD5")) // Match app background
            setPadding(40, 80, 40, 40)
        }

        // Title
        val title = TextView(context).apply {
            text = "💬 Your Groups"
            textSize = 28f
            setTextColor(Color.parseColor("#075E54"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }
        mainLayout.addView(title)

        // Scrollable List
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val listLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // Extract Distinct Groups (Old messages are mapped to "Personal Chat")
        val groups = chatHistory.map { it.groupName ?: "Personal Chat" }.distinct().toMutableList()
        if (!groups.contains("Personal Chat")) groups.add(0, "Personal Chat")

        groups.forEach { group ->
            val groupBtn = Button(context).apply {
                text = "📁  $group"
                textSize = 18f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(50, 50, 50, 50)
                isAllCaps = false
                setTextColor(Color.BLACK)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 24f
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                    setMargins(0, 0, 0, 20) 
                }
                setOnClickListener { onGroupSelected(group) }
            }
            listLayout.addView(groupBtn)
        }

        scrollView.addView(listLayout)
        mainLayout.addView(scrollView)

        // "+ Create a Group" Button
        val addBtn = Button(context).apply {
            text = "➕ Create a Group"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00A884"))
                cornerRadius = 24f
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                setMargins(0, 30, 0, 0) 
            }
            setOnClickListener {
                val input = EditText(context).apply { hint = "Enter new group name..." }
                AlertDialog.Builder(context)
                    .setTitle("New Group")
                    .setView(input)
                    .setPositiveButton("Create") { _, _ ->
                        val text = input.text.toString().trim()
                        if (text.isNotEmpty()) onGroupCreated(text)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        mainLayout.addView(addBtn)

        return mainLayout
    }
}
