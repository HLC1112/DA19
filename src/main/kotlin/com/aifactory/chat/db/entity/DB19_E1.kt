// --- Entity (src/main/kotlin/com/aifactory/chat/db/entity/DB19_E1.kt) ---
package com.aifactory.chat.db.entity
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.util.UUID
/**
 * 📄 [JPA实体] 与数据库表结构一一对应
 */
@Entity(name = "chat_messages")
data class ChatMessageEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    val content: String = ""
)

