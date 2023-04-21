package ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import com.myapplication.common.ChatMessageEntity
import data.repository.ChatMessageRepository
import kotlinx.coroutines.launch

class ChatScreenModel(
    private val chatMessageRepository: ChatMessageRepository,
) : ScreenModel {

    var text by mutableStateOf("Hello, World!")

    var messages: List<ChatMessageEntity> by mutableStateOf(emptyList())
        private set

    init {
        coroutineScope.launch {
            chatMessageRepository.getMessages().collect { messages = it }
        }
    }

    fun sendMessage(message: String) {
        // Reset text
        text = ""

        // Send message
        coroutineScope.launch {
            chatMessageRepository.sendMessage(message)
            println("finish here !")
        }
    }

}
