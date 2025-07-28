// File: cmd-02_1_DBS.L2.CMD.kt
package com.aifactory.chat.contracts.v1.cmd
import com.aifactory.chat.db.entity.ChatMessageEntity
data class SaveCmd(
    val chatMessageEntity: ChatMessageEntity
)