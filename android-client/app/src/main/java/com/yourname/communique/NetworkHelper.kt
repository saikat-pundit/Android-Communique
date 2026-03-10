package com.yourname.communique

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class NetworkHelper(private val httpClient: OkHttpClient, private val gson: Gson) {

    fun fetchChatHistory(): List<ChatMessage>? {
        val request = Request.Builder()
            .url("https://api.github.com/gists/${CryptoHelper.getSecret(BuildConfig.CHAT_GIST_ID)}?t=${System.currentTimeMillis()}")
            .addHeader("Authorization", "Bearer ${CryptoHelper.getSecret(BuildConfig.GIST_TOKEN)}")
            .addHeader("Cache-Control", "no-store, no-cache")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    if (responseData.isNullOrEmpty()) return@use null
                    
                    val rawContent = JSONObject(responseData)
                        .getJSONObject("files")
                        .getJSONObject("chat_ledger.json")
                        .getString("content")
                    
                    // NEW: If it starts with "[", it's old plain text. Otherwise, decrypt the massive hidden blob!
                    val jsonContent = if (rawContent.trim().startsWith("[")) rawContent else CryptoHelper.decrypt(rawContent)
                    
                    val type = object : TypeToken<List<ChatMessage>>() {}.type
                    val fetchedList: List<ChatMessage>? = gson.fromJson(jsonContent, type)
                    fetchedList ?: emptyList()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun pushGistUpdate(history: List<ChatMessage>) {
        if (history.size > 500) {
            val archiveCount = 400
            val messagesToArchive = history.take(archiveCount)
            val messagesToKeep = history.drop(archiveCount)

            if (safePushToArchive(messagesToArchive)) {
                pushToMainGist(messagesToKeep)
                return 
            }
        }
        pushToMainGist(history)
    }

    private fun pushToMainGist(history: List<ChatMessage>) {
        val jsonString = gson.toJson(history)
        val encryptedBlob = CryptoHelper.encrypt(jsonString) // NEW: Encrypt the entire file into gibberish!

        val payload = JSONObject().apply {
            put("files", JSONObject().apply {
                put("chat_ledger.json", JSONObject().apply {
                    put("content", encryptedBlob) // Put the giant blob instead of the array
                })
            })
        }
        val request = Request.Builder()
            .url("https://api.github.com/gists/${CryptoHelper.getSecret(BuildConfig.CHAT_GIST_ID)}")
            .addHeader("Authorization", "Bearer ${CryptoHelper.getSecret(BuildConfig.GIST_TOKEN)}")
            .patch(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try { httpClient.newCall(request).execute().close() } catch (e: Exception) { e.printStackTrace() }
    }

    private fun safePushToArchive(newArchiveMessages: List<ChatMessage>): Boolean {
        return try {
            val fetchReq = Request.Builder()
                .url("https://api.github.com/gists/${CryptoHelper.getSecret(BuildConfig.ARCHIVE_GIST_ID)}?t=${System.currentTimeMillis()}")
                .addHeader("Authorization", "Bearer ${CryptoHelper.getSecret(BuildConfig.GIST_TOKEN)}")
                .addHeader("Cache-Control", "no-store, no-cache")
                .build()

            val currentArchiveList = mutableListOf<ChatMessage>()
            httpClient.newCall(fetchReq).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    if (!responseData.isNullOrEmpty()) {
                        val rawContent = JSONObject(responseData).getJSONObject("files").optJSONObject("archive_ledger.json")?.optString("content")
                        if (rawContent != null && rawContent != "[]") {
                            val jsonContent = if (rawContent.trim().startsWith("[")) rawContent else CryptoHelper.decrypt(rawContent)
                            val type = object : TypeToken<List<ChatMessage>>() {}.type
                            val fetched: List<ChatMessage>? = gson.fromJson(jsonContent, type)
                            if (fetched != null) currentArchiveList.addAll(fetched)
                        }
                    }
                }
            }

            currentArchiveList.addAll(newArchiveMessages)
            
            val jsonString = gson.toJson(currentArchiveList)
            val encryptedBlob = CryptoHelper.encrypt(jsonString) // Encrypt the archive too!

            val payload = JSONObject().apply {
                put("files", JSONObject().apply {
                    put("archive_ledger.json", JSONObject().apply {
                        put("content", encryptedBlob)
                    })
                })
            }
            val pushReq = Request.Builder()
                .url("https://api.github.com/gists/${CryptoHelper.getSecret(BuildConfig.ARCHIVE_GIST_ID)}")
                .addHeader("Authorization", "Bearer ${CryptoHelper.getSecret(BuildConfig.GIST_TOKEN)}")
                .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(pushReq).execute().use { response -> response.isSuccessful }
        } catch (e: Exception) {
            e.printStackTrace()
            false 
        }
    }
}
