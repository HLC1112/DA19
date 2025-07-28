// File: doc-03_1_DSV.L2.DOC.kt
package com.aifactory.chat.contracts.v1.doc
import com.aifactory.chat.contracts.v1.cmd.SendMessageCmd
data class ValidationResultDoc(
    val isValid: Boolean,
    val errorCode: String?,
    val originalRequestDto: SendMessageCmd
)