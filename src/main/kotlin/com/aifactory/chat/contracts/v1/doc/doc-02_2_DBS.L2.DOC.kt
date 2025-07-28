// File: doc-02_2_DBS.L2.DOC.kt
package com.aifactory.chat.contracts.v1.doc
import com.aifactory.chat.db.entity.ChatMessageEntity
data class SnapshotDoc(
    val savedMessageEntity: ChatMessageEntity
)