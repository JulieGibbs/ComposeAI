package ui.screens.chat

import analytics.AnalyticsHelper
import analytics.logConversationSelected
import analytics.logCreateNewConversation
import analytics.logMessageCopied
import analytics.logMessageShared
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import com.ebfstudio.appgpt.common.ChatEntity
import com.ebfstudio.appgpt.common.ChatMessageEntity
import data.repository.ChatMessageRepository
import data.repository.ChatRepository
import expect.shareText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatScreenModel(
    private val chatRepository: ChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val analyticsHelper: AnalyticsHelper,
    initialChatId: String?,
) : ScreenModel {

    private var chatId: MutableStateFlow<String?> =
        MutableStateFlow(initialChatId)

    val screenUiState: MutableStateFlow<ChatScreenUiState> =
        MutableStateFlow(ChatScreenUiState())

    val messagesUiState: StateFlow<ChatMessagesUiState> =
        chatId.flatMapLatest { id ->
            if (id == null) {
                MutableStateFlow(ChatMessagesUiState.Empty)
            } else {
                chatRepository.getChatStream(id).flatMapLatest {
                    chatMessageRepository.getMessagesStream(id)
                        .map { messages ->
                            ChatMessagesUiState.Success(messages = messages)
                        }
                }
            }
        }.stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = ChatMessagesUiState.Loading,
        )

    val currentChat: StateFlow<ChatEntity?> =
        chatId.flatMapLatest { id ->
            if (id == null) {
                MutableStateFlow(null)
            } else {
                chatRepository.getChatStream(id)
            }
        }.stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val chatsUiState: StateFlow<ChatsUiState> =
        chatRepository.getChatsStream()
            .map { chats -> ChatsUiState.Success(chats = chats) }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.Eagerly,
                initialValue = ChatsUiState.Loading,
            )

    init {
        // If no chat id is provided, get the latest chat id
        if (initialChatId == null) {
            coroutineScope.launch {
                val latestChat = chatRepository.getChatsStream().first().firstOrNull()
                if (latestChat != null) {
                    chatId.update { latestChat.id }
                }
            }
        }
    }

    fun onSendMessage() {
        coroutineScope.launch {
            // Get chat id, or create a new one
            val chatId = when {
                chatId.value != null -> chatId.value
                else -> {
                    chatRepository.createChat().let { id ->
                        chatId.update { id }
                        id
                    }
                }
            } ?: return@launch

            // Get message text (before reset)
            val contentText = screenUiState.value.text

            // Reset text and start loading
            screenUiState.update {
                it.copy(
                    text = "",
                    isSending = true
                )
            }

            // Send message
            val sendMessageResult = chatMessageRepository.sendMessage(
                chatId = chatId,
                contentMessage = contentText
            )

            // Stop loading
            screenUiState.update {
                it.copy(isSending = false)
            }

            // Update chat title
            if (sendMessageResult.isSuccess) {
                chatMessageRepository.generateTitleFromChat(chatId).onSuccess {
                    chatRepository.updateChatTitle(chatId, it)
                }
            }
        }
    }

    fun onRetrySendMessage() {
        val chatId = chatId.value ?: return

        coroutineScope.launch {
            // Start loading
            screenUiState.update {
                it.copy(isSending = true)
            }

            // Retry send message
            chatMessageRepository.retrySendMessage(chatId = chatId)

            // Stop loading
            screenUiState.update {
                it.copy(isSending = false)
            }
        }
    }

    fun onTextChange(text: String) {
        screenUiState.update { it.copy(text = text) }
    }

    fun onNewChat() {
        chatId.update { null }
        analyticsHelper.logCreateNewConversation()
    }

    fun onChatSelected(chatId: String) {
        this.chatId.update { chatId }
        analyticsHelper.logConversationSelected()
    }

    fun onMessageCopied() {
        analyticsHelper.logMessageCopied()
    }

    fun onMessageShared(text: String) {
        shareText(text)
        analyticsHelper.logMessageShared()
    }

}

sealed interface ChatsUiState {
    object Loading : ChatsUiState
    data class Success(
        val chats: List<ChatEntity> = emptyList()
    ) : ChatsUiState
}

sealed interface ChatMessagesUiState {
    object Empty : ChatMessagesUiState
    object Loading : ChatMessagesUiState
    data class Success(
        val messages: List<ChatMessageEntity> = emptyList()
    ) : ChatMessagesUiState
}

data class ChatScreenUiState(
    val text: String = "",
    val isSending: Boolean = false,
)
