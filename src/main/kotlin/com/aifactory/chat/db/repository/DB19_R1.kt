// --- Repository Impl (src/main/kotlin/com/aifactory/chat/db/repository/DB19_R1.kt) ---
package com.aifactory.chat.db.repository
import com.aifactory.chat.contracts.v1.cmd.SaveCmd
import com.aifactory.chat.contracts.v1.doc.SnapshotDoc
import org.springframework.stereotype.Repository
/**
 * 💾 [仓库实现] DB端口的JPA实现
 *
 */
@Repository
class ChatMessageRepository(
    private val jpaRepository: ChatMessageJpaRepository
) {
    /**
     * 步骤 6: 向 chat_messages 表写入一条新的玩家消息记录。
     * ⭐ 修正：移除了 'override' 关键字
     */
    fun save(command: SaveCmd): SnapshotDoc {
        val savedEntity = jpaRepository.save(command.chatMessageEntity)
        println("Saved message to DB with ID: ${savedEntity.id}")
        return SnapshotDoc(savedMessageEntity = savedEntity)
    }
}