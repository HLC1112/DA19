// File: cmd-01_DSV.L2.CMD.kt
package com.aifactory.chat.contracts.v1.cmd
data class SendMessageCmd(
    val userSessionToken: String,
    val characterId: String,
    val messageType: String,
    val messageContent: Any, // e.g., GiftContent(giftId="g001")
    val idempotencyKey: String,
    val traceId: String
)