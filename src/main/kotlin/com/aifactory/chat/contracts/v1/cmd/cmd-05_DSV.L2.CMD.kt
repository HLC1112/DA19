// File: cmd-05_DSV.L2.CMD.kt
package com.aifactory.chat.contracts.v1.cmd
data class UpdateRelationshipStatsCmd(
    val userId: String,
    val characterId: String,
    val changeEventType: String
)