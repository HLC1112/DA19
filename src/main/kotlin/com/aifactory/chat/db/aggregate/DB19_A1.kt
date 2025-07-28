// --- Aggregate (src/main/kotlin/com/aifactory/chat/db/aggregate/DB19_A1.kt) ---
package com.aifactory.chat.db.aggregate
import com.aifactory.chat.db.valueobject.MessageContent
import java.util.UUID
/**
 * 🧩 [聚合根] 领域模型的核心, 定义业务不变量
 */
data class ChatMessageAggregate(val id: UUID, val content: MessageContent)