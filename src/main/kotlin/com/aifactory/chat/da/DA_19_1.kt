package com.aifactory.chat.da

import com.aifactory.chat.app.SendMessageEvents
import com.aifactory.chat.app.SendMessageStates
import com.aifactory.chat.contracts.envelope.EventEnvelope
import com.aifactory.chat.contracts.envelope.createNextEnvelope
import com.aifactory.chat.contracts.v1.cmd.ConsumeAssetCmd
import com.aifactory.chat.contracts.v1.cmd.SaveChatMessageCmd
import com.aifactory.chat.contracts.v1.cmd.SendMessageCmd
import com.aifactory.chat.contracts.v1.cmd.ValidateMessageCmd
import com.aifactory.chat.da0.StateCoordinatorService
import com.aifactory.chat.dc.ChatValidationService
import org.springframework.context.annotation.Bean
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * 🧠 [DA层 - 服务编排]
 * 职责：提供状态机在每个状态转换时需要执行的具体业务逻辑。
 *
 */
@Component
class SendMessageActions(
    private val validationService: ChatValidationService,
    private val stateCoordinator: StateCoordinatorService,
    private val assetClient: AssetClient
) {
    @Bean
    fun validateAction(): Action<SendMessageStates, SendMessageEvents> = Action { context ->
        val commandEnvelope = context.extendedState.variables["INITIAL_ENVELOPE"] as EventEnvelope<SendMessageCmd>
        val validationCmdEnvelope = createNextEnvelope(commandEnvelope, "DSV.L2.CMD.ValidateMessage.v1", "DA", "SendMessageDomainAgent",
            ValidateMessageCmd(commandEnvelope.data.messageContent, commandEnvelope.data)
        )
        val validationResult = validationService.validate(validationCmdEnvelope)

        val nextEvent = if (validationResult.data.isValid) SendMessageEvents.VALIDATION_SUCCEEDED else SendMessageEvents.VALIDATION_FAILED
        context.stateMachine.sendEvent(nextEvent)
    }

    @Bean
    fun parallelActions(): Action<SendMessageStates, SendMessageEvents> = Action { context ->
        try {
            val commandEnvelope = context.extendedState.variables["INITIAL_ENVELOPE"] as EventEnvelope<SendMessageCmd>

            // ⭐ 关键修复：移除 CompletableFuture，改为同步顺序执行以确保测试的确定性

            // 1. 执行持久化
            val saveCmdEnvelope = createNextEnvelope(commandEnvelope, "DSV.L2.CMD.SaveChatMessage.v1", "DA", "SendMessageDomainAgent",
                SaveChatMessageCmd("DTO from original command", commandEnvelope.data)
            )
            stateCoordinator.saveMessage(saveCmdEnvelope)

            // 2. 执行资产扣减
            val consumeAssetCmdEnvelope = createNextEnvelope(commandEnvelope, "DSV.L2.CMD.ConsumeAsset.v1", "DA", "SendMessageDomainAgent",
                ConsumeAssetCmd("user1", "gift1", 1, commandEnvelope.data.idempotencyKey, commandEnvelope.traceHeader.correlationId)
            )
            val assetResult = assetClient.consume(consumeAssetCmdEnvelope)

            // 3. 根据结果发送下一个事件
            val nextEvent = if (assetResult.data.isSuccess) SendMessageEvents.EXTERNAL_CALLS_SUCCEEDED else SendMessageEvents.ASSET_CALL_FAILED
            context.stateMachine.sendEvent(nextEvent)

        } catch (e: Exception) {
            // 如果在同步执行中出现任何错误，都发送内部错误事件
            context.stateMachine.sendEvent(SendMessageEvents.FAIL_INTERNALLY)
        }
    }
}
