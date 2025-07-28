// File: cmd-03_DSV.L2.CMD.kt
package com.aifactory.chat.contracts.v1.cmd
data class ValidateMessageCmd(
    val validationInputDto: Any,
    val originalRequestDto: SendMessageCmd
)