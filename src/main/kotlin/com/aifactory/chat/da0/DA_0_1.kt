// --- DA_0 层 (src/main/kotlin/com/aifactory/chat/da0/DA_0_1.kt) ---

package com.aifactory.chat.da0

import com.aifactory.chat.contracts.envelope.EventEnvelope
import com.aifactory.chat.contracts.envelope.createNextEnvelope
import com.aifactory.chat.contracts.v1.cmd.SaveChatMessageCmd
import com.aifactory.chat.contracts.v1.cmd.SaveCmd
import com.aifactory.chat.contracts.v1.doc.PersistenceResultDoc
import com.aifactory.chat.db.entity.ChatMessageEntity
import com.aifactory.chat.db.repository.ChatMessageRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 🔗 [DA_0层 - 状态协调]
 */
@Service
class StateCoordinatorService(
    private val chatMessageRepository: ChatMessageRepository
) {
    fun saveMessage(commandEnvelope: EventEnvelope<SaveChatMessageCmd>): EventEnvelope<PersistenceResultDoc> {
        // 步骤 5: 协调并调用DB层
        val entityToSave = ChatMessageEntity(UUID.randomUUID(), commandEnvelope.data.chatMessageDto.toString())
        val saveCmdEnvelope = createNextEnvelope(commandEnvelope, "DBS.L2.CMD.Save.v1", "DA0", "StateCoordinatorService", SaveCmd(entityToSave))

        // ⭐ 修正：直接调用repository的方法
        val snapshotDoc = chatMessageRepository.save(saveCmdEnvelope.data)

        return createNextEnvelope(commandEnvelope, "DSV.L2.DOC.E.v1", "DA0", "StateCoordinatorService",
            PersistenceResultDoc(snapshotDoc.savedMessageEntity)
        )
    }
}