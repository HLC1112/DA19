// File: cmd-02_DSV.L2.CMD.kt
package com.aifactory.chat.contracts.v1.cmd
data class SaveChatMessageCmd(
    val chatMessageDto: Any, // DTO representation of the message
    val originalRequestDto: SendMessageCmd
)