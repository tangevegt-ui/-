package com.example.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "ChatViewModel"

enum class GeminiModelTier(val modelName: String, val displayName: String, val description: String) {
    FLASH_LITE("gemini-3.1-flash-lite", "⚡ ตอบไว (Flash-Lite)", "เหมาะสำหรับถามตอบรวดเร็วทั่วไป"),
    FLASH_GENERAL("gemini-3.8-flash", "✨ มาตรฐาน (Flash 3.8)", "ครอบคลุมการวิเคราะห์ทั่วไปได้ยอดเยี่ยม"),
    ARGON_COMPLEX("gemini-4-argon", "🧠 เจาะลึก (Argon 4)", "วิเคราะห์การเงินและคำนวณขั้นสูง")
}

class ChatViewModel : ViewModel() {

    private val _selectedTier = MutableStateFlow(GeminiModelTier.FLASH_GENERAL)
    val selectedTier: StateFlow<GeminiModelTier> = _selectedTier.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "model",
                text = "สวัสดีครับ! ผมคือ **'น้องกระเป๋าตังค์' (Pocket AI)** ที่ปรึกษาการเงินส่วนตัวของคุณ 💼✨\n\nยินดีช่วยวิเคราะห์ค่าใช้จ่าย แนะนำวิธีประหยัดเงิน หรือวางแผนงบประมาณประจำเดือน มีเรื่องการเงินอะไรให้ผมช่วยแนะนำไหมครับ?"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val systemPrompt = content {
        text(
            "คุณคือ 'น้องกระเป๋าตังค์' (Pocket AI) ที่ปรึกษาทางการเงินส่วนตัวอัจฉริยะในแอปสรุปรายรับรายจ่าย " +
            "บุคลิกภาพ: สุภาพ เป็นกันเอง ให้กำลังใจ และให้คำแนะนำที่สามารถลงมือทำได้จริง " +
            "หน้าที่หลัก: " +
            "1. ให้คำปรึกษาเรื่องการบริหารเงินส่วนบุคคล การวางแผนรายรับ-รายจ่าย " +
            "2. แนะนำเคล็ดลับการประหยัดค่าใช้จ่าย เช่น ค่าอาหาร ของฟุ่มเฟือย ค่าน้ำค่าไฟ " +
            "3. ช่วยวางแผนการออมเงินตามหลักสากล เช่น กฎ 50/30/20 " +
            "4. ช่วยตอบคำถามเรื่องการจัดการหนี้สิน และข้อคิดการลงทุนเบื้องต้นอย่างปลอดภัย " +
            "5. ตอบเป็นภาษาไทยที่กระชับ สวยงาม อ่านง่าย จัดหัวข้อและ Bullet points ชัดเจน " +
            "6. หากผู้ใช้ถามเรื่องที่ไม่เกี่ยวกับการเงินเลย ให้ตอบกลับอย่างสุภาพและเชื่อมโยงกลับมาสู่การเงินและเป้าหมายในชีวิต"
        )
    }

    private fun getModel(tier: GeminiModelTier) = Firebase.ai.generativeModel(
        modelName = tier.modelName,
        systemInstruction = systemPrompt
    )

    fun selectTier(tier: GeminiModelTier) {
        _selectedTier.value = tier
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearHistory() {
        _messages.value = listOf(
            ChatMessage(
                sender = "model",
                text = "เริ่มการสนทนาใหม่แล้วครับ! มีคำถามเรื่องการเงินหรืออยากให้ช่วยวางแผนส่วนไหน ถามได้เลยครับ 💰"
            )
        )
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _isGenerating.value) return

        val userMessage = ChatMessage(sender = "user", text = trimmed)
        val currentHistory = _messages.value
        _messages.value = currentHistory + userMessage

        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null

            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = ChatMessage(
                id = assistantMsgId,
                sender = "model",
                text = "",
                isStreaming = true
            )
            _messages.value = _messages.value + initialAssistantMsg

            try {
                val model = getModel(_selectedTier.value)

                // Build conversation history for startChat
                val historyContents = currentHistory
                    .filter { it.text.isNotBlank() }
                    .takeLast(10) // Retain recent context
                    .map { msg ->
                        content(msg.sender) {
                            text(msg.text)
                        }
                    }

                val chat = model.startChat(history = historyContents)
                val responseBuilder = StringBuilder()

                withTimeout(60_000L) {
                    chat.sendMessageStream(trimmed).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        responseBuilder.append(chunkText)
                        updateAssistantMessage(assistantMsgId, responseBuilder.toString(), isStreaming = true)
                    }
                }

                val finalText = responseBuilder.toString().ifBlank {
                    "ขออภัยครับ ขณะนี้ไม่สามารถประมวลผลคำตอบได้ กรุณาลองใหม่อีกครั้งครับ"
                }
                updateAssistantMessage(assistantMsgId, finalText, isStreaming = false)

            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat request timed out", e)
                val timeoutText = "คำขอหมดเวลา (60 วินาที) กรุณาลองส่งใหม่อีกครั้งครับ"
                updateAssistantMessage(assistantMsgId, timeoutText, isStreaming = false)
                _errorMessage.value = timeoutText
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat failed", e)
                val errorText = "เกิดข้อผิดพลาด: ${e.localizedMessage ?: "ไม่สามารถติดต่อ AI ได้ในขณะนี้"}"
                updateAssistantMessage(assistantMsgId, errorText, isStreaming = false)
                _errorMessage.value = errorText
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun updateAssistantMessage(id: String, text: String, isStreaming: Boolean) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == id) {
                msg.copy(text = text, isStreaming = isStreaming)
            } else {
                msg
            }
        }
    }
}
