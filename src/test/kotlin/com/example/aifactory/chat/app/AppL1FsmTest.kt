// 文件路径: src/test/kotlin/com/aifactory/chat/app/AppL1FsmTest.kt

package com.aifactory.chat.app

import com.aifactory.chat.contracts.envelope.*
import com.aifactory.chat.contracts.v1.cmd.SendMessageCmd
import com.aifactory.chat.contracts.v1.doc.PersistenceResultDoc
import com.aifactory.chat.contracts.v1.doc.ValidationResultDoc
import com.aifactory.chat.contracts.v1.evt.AssetConsumedEvt
import com.aifactory.chat.contracts.v1.evt.SendMessageCompletedEvt
import com.aifactory.chat.contracts.v1.evt.ValidationFailedEvt
import com.aifactory.chat.da0.StateCoordinatorService
import com.aifactory.chat.db.entity.ChatMessageEntity
import com.aifactory.chat.dc.ChatValidationService
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import java.util.UUID

// 这是一个模拟的外部服务客户端接口，用于测试
interface AssetClient {
    fun consume(envelope: EventEnvelope<*>): EventEnvelope<AssetConsumedEvt>
}

@SpringBootTest
class AppL1FsmTest {

    @TestConfiguration
    internal class TestConfig {
        @Bean
        fun validationService(): ChatValidationService = Mockito.mock(ChatValidationService::class.java)

        @Bean
        fun stateCoordinator(): StateCoordinatorService = Mockito.mock(StateCoordinatorService::class.java)

        @Bean
        fun assetClient(): AssetClient = Mockito.mock(AssetClient::class.java)

    }

    @Autowired
    private lateinit var appL1Fsm: AppL1Fsm

    // 使用 @MockBean 来模拟状态机动作 (Actions) 所依赖的底层服务
    // 这样我们就可以控制这些服务的行为，而无需真正地调用它们
    @Autowired
    private lateinit var validationService: ChatValidationService

    @Autowired
    private lateinit var stateCoordinator: StateCoordinatorService

    @Autowired
    private lateinit var assetClient: AssetClient

    @Test
    fun `当所有步骤都成功时，状态机应返回 SendMessageCompletedEvt`() {
        // --- 1. Arrange (安排) ---
        val initialCommand = createDummyCommand().data
        val initialEnvelope = createInitialEnvelope("DSV.L2.CMD.SendMessage.v1", "APP", "Test", "test-user", initialCommand)

        // 安排一个成功的校验结果
        val successfulValidationResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.DOC.ValidationResult.v1", "DC", "ChatValidationService",
            ValidationResultDoc(isValid = true, errorCode = null, originalRequestDto = initialCommand)
        )
        // 当 validationService.validate 被调用时，我们让它返回预设的成功结果
        Mockito.`when`(validationService.validate(any())).thenReturn(successfulValidationResult)

        // 安排一个成功的资产扣减结果
        val successfulAssetResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.EVT.AssetConsumed.v1", "DA_EXT", "AssetService",
            AssetConsumedEvt(isSuccess = true, errorCode = null)
        )
        Mockito.`when`(assetClient.consume(any())).thenReturn(successfulAssetResult)

        // ⭐ 关键修复：stateCoordinator.saveMessage 方法有返回值，不能使用 doNothing()
        // 我们需要模拟一个成功的持久化结果并返回它
        val successfulPersistenceResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.DOC.E.v1", "DA0", "StateCoordinatorService",
            PersistenceResultDoc(savedMessageEntity = ChatMessageEntity(UUID.randomUUID(), "test content"))
        )
        Mockito.`when`(stateCoordinator.saveMessage(any())).thenReturn(successfulPersistenceResult)

        // --- 2. Act (执行) ---
        // 执行FSM
        val finalEnvelope = appL1Fsm.execute(initialCommand, "test-user")

        // --- 3. Assert (断言) ---
        // 验证最终返回的事件是我们预期的“成功”事件
        assertInstanceOf(SendMessageCompletedEvt::class.java, finalEnvelope.data)
    }

    @Test
    fun `当校验失败时，状态机应返回 ValidationFailedEvt`() {
        // --- 1. Arrange (安排) ---
        val initialCommand = createDummyCommand().data
        val initialEnvelope = createInitialEnvelope("DSV.L2.CMD.SendMessage.v1", "APP", "Test", "test-user", initialCommand)

        // 安排一个失败的校验结果
        val failedValidationResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.DOC.ValidationResult.v1", "DC", "ChatValidationService",
            ValidationResultDoc(isValid = false, errorCode = "INVALID_CONTENT", originalRequestDto = initialCommand)
        )
        // 当 validationService.validate 被调用时，返回预设的失败结果
        Mockito.`when`(validationService.validate(any())).thenReturn(failedValidationResult)

        // --- 2. Act (执行) ---
        val finalEnvelope = appL1Fsm.execute(initialCommand, "test-user")

        // --- 3. Assert (断言) ---
        // 验证最终返回的事件是我们预期的“校验失败”事件
        assertInstanceOf(ValidationFailedEvt::class.java, finalEnvelope.data)
    }

    // 辅助函数，用于创建一个完整的、虚拟的输入命令信封
    private fun createDummyCommand(): EventEnvelope<SendMessageCmd> {
        val commandData = SendMessageCmd(
            userSessionToken = "token-123",
            characterId = "char-456",
            messageType = "TEXT",
            messageContent = "Hello",
            idempotencyKey = "key-789",
            traceId = "trace-abc"
        )
        return createInitialEnvelope(
            eventType = "DSV.L2.CMD.SendMessage.v1",
            sourceLayer = "TEST",
            sourceService = "AppL1FsmTest",
            actorId = "test-user",
            data = commandData
        )
    }

    // Mockito 的 any() 方法在 Kotlin 中需要一个辅助函数
    private fun <T> any(): T = Mockito.any()
}
