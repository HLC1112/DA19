// File: cmd-04_DSV.L2.CMD.kt
package com.aifactory.chat.contracts.v1.cmd
data class ConsumeAssetCmd(
    val userId: String,
    val assetId: String,
    val amount: Int,
    val idempotencyKey: String,
    val traceId: String
)