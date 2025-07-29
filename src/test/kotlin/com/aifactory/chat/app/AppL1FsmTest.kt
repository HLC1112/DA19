// 文件路径: src/test/kotlin/com/aifactory/chat/app/AppL1FsmTest.kt
// 描述: 这是一个专业级的JUnit 5测试类，用于全面测试AppL1Fsm状态机的行为。
package com.aifactory.chat.app

import com.aifactory.chat.contracts.envelope.EventEnvelope
import com.aifactory.chat.contracts.envelope.createInitialEnvelope
import com.aifactory.chat.contracts.envelope.createNextEnvelope
import com.aifactory.chat.contracts.v1.cmd.SendMessageCmd
import com.aifactory.chat.contracts.v1.doc.PersistenceResultDoc
import com.aifactory.chat.contracts.v1.doc.ValidationResultDoc
import com.aifactory.chat.contracts.v1.evt.AssetConsumedEvt
import com.aifactory.chat.contracts.v1.evt.InsufficientAssetEvt
import com.aifactory.chat.contracts.v1.evt.SendMessageCompletedEvt
import com.aifactory.chat.contracts.v1.evt.ValidationFailedEvt
import com.aifactory.chat.da0.StateCoordinatorService
import com.aifactory.chat.db.entity.ChatMessageEntity
import com.aifactory.chat.dc.ChatValidationService
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.*

@SpringBootTest
class AppL1FsmTest {

    // 使用 @MockBean 来模拟状态机动作 (Actions) 所依赖的底层服务
    // 这样我们就可以控制这些服务的行为，而无需真正地调用它们
    @MockBean
    private lateinit var validationService: ChatValidationService

    @MockBean
    private lateinit var stateCoordinator: StateCoordinatorService

    @MockBean
    private lateinit var assetClient: AssetClient

    @Autowired
    private lateinit var appL1Fsm: AppL1Fsm

    private lateinit var initialCommand: SendMessageCmd
    private lateinit var initialEnvelope: EventEnvelope<SendMessageCmd>

    @BeforeEach
    fun setUp() {
        // 在每个测试开始前，都创建一个标准的输入命令
        initialCommand = SendMessageCmd(
            userSessionToken = "token-123",
            characterId = "char-456",
            messageType = "TEXT",
            messageContent = "Hello",
            idempotencyKey = "key-${UUID.randomUUID()}",
            traceId = "trace-abc"
        )
        initialEnvelope = createInitialEnvelope(
            eventType = "DSV.L2.CMD.SendMessage.v1",
            sourceLayer = "TEST",
            sourceService = "AppL1FsmTest",
            actorId = "test-user",
            data = initialCommand
        )
    }

    @Test
    fun `当所有步骤都成功时，状态机应返回 SendMessageCompletedEvt`() {
        // --- 1. Arrange (安排) ---

        // 安排一个成功的校验结果
        val successfulValidationResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.DOC.ValidationResult.v1", "DC", "ChatValidationService",
            ValidationResultDoc(isValid = true, errorCode = null, originalRequestDto = initialCommand)
        )
        doReturn(successfulValidationResult).`when`(validationService).validate(any())

        // 安排一个成功的持久化结果
        val successfulPersistenceResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.DOC.E.v1", "DA0", "StateCoordinatorService",
            PersistenceResultDoc(savedMessageEntity = ChatMessageEntity(UUID.randomUUID(), "test content"))
        )
        doReturn(successfulPersistenceResult).`when`(stateCoordinator).saveMessage(any())

        // 安排一个成功的资产扣减结果
        val successfulAssetResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.EVT.AssetConsumed.v1", "DA_EXT", "AssetService",
            AssetConsumedEvt(isSuccess = true, errorCode = null)
        )
        doReturn(successfulAssetResult).`when`(assetClient).consume(any())

        // --- 2. Act (执行) ---
        val finalEnvelope = appL1Fsm.execute(initialCommand, "test-user")

        // --- 3. Assert (断言) ---
        // 验证最终返回的事件是我们预期的“成功”事件
        assertInstanceOf(SendMessageCompletedEvt::class.java, finalEnvelope.data)
    }

    @Test
    fun `当校验失败时，状态机应返回 ValidationFailedEvt`() {
        // --- 1. Arrange (安排) ---

        // 安排一个失败的校验结果
        val failedValidationResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.DOC.ValidationResult.v1", "DC", "ChatValidationService",
            ValidationResultDoc(isValid = false, errorCode = "INVALID_CONTENT", originalRequestDto = initialCommand)
        )
        doReturn(failedValidationResult).`when`(validationService).validate(any())

        // --- 2. Act (执行) ---
        val finalEnvelope = appL1Fsm.execute(initialCommand, "test-user")

        // --- 3. Assert (断言) ---
        // 验证最终返回的事件是我们预期的“校验失败”事件
        assertInstanceOf(ValidationFailedEvt::class.java, finalEnvelope.data)
    }

    @Test
    fun `当资产消耗失败时，状态机应返回 InsufficientAssetEvt`() {
        // --- 1. Arrange (安排) ---

        // 安排一个成功的校验结果
        val successfulValidationResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.DOC.ValidationResult.v1", "DC", "ChatValidationService",
            ValidationResultDoc(isValid = true, errorCode = null, originalRequestDto = initialCommand)
        )
        doReturn(successfulValidationResult).`when`(validationService).validate(any())

        // 安排一个成功的持久化结果 (即使资产失败，持久化也应该先成功)
        val successfulPersistenceResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.DOC.E.v1", "DA0", "StateCoordinatorService",
            PersistenceResultDoc(savedMessageEntity = ChatMessageEntity(UUID.randomUUID(), "test content"))
        )
        doReturn(successfulPersistenceResult).`when`(stateCoordinator).saveMessage(any())

        // 安排一个失败的资产扣减结果
        val failedAssetResult = createNextEnvelope(
            initialEnvelope, "DSV.L2.EVT.AssetConsumed.v1", "DA_EXT", "AssetService",
            AssetConsumedEvt(isSuccess = false, errorCode = "INSUFFICIENT_ASSET")
        )
        doReturn(failedAssetResult).`when`(assetClient).consume(any())

        // --- 2. Act (执行) ---
        val finalEnvelope = appL1Fsm.execute(initialCommand, "test-user")

        // --- 3. Assert (断言) ---
        // 验证最终返回的事件是我们预期的“资产不足”事件
        assertInstanceOf(InsufficientAssetEvt::class.java, finalEnvelope.data)
    }
}
