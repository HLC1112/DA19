// --- Spring Data JPA Repo (src/main/kotlin/com/aifactory/chat/db/repository/DB19_R2.kt) ---
package com.aifactory.chat.db.repository
import com.aifactory.chat.db.entity.ChatMessageEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
/**
 * 🗄️ Spring Data JPA接口
 */
interface ChatMessageJpaRepository : JpaRepository<ChatMessageEntity, UUID>