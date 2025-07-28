// File: doc-02_3_DSV.L2.DOC.kt
package com.aifactory.chat.contracts.v1.doc
import com.aifactory.chat.db.entity.ChatMessageEntity
data class PersistenceResultDoc(
    val savedMessageEntity: ChatMessageEntity
)