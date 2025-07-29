package com.aifactory.chat.da

import com.aifactory.chat.app.SendMessageEvents
import com.aifactory.chat.app.SendMessageStates
import com.aifactory.chat.app.AssetClient
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

/**
 * 🧠 [ DA层 ] - 状态机动作实现
 * 这个类是状态机业务逻辑的核心。
 * 它以Bean的形式提供了多个Action方法，每个方法对应状态机配置中的一个具体动作。
 */
@Component
class SendMessageActions(
    // 注入依赖的服务
    private val validationService: ChatValidationService, // 领域计算服务，负责具体验证逻辑
    private val stateCoordinator: StateCoordinatorService, // 状态协调服务，负责与数据库交互
    private val assetClient: AssetClient // 外部资产服务的客户端
) {
    /**
     * 【动作1】验证动作 (validateAction)
     * 当状态机接收到 START_VALIDATION 事件时，会执行此方法。
     */
    @Bean
    fun validateAction(): Action<SendMessageStates, SendMessageEvents> = Action { context ->
        try {
            // 1. 从状态机的上下文中获取初始命令数据。
            val commandEnvelope = context.extendedState.variables["INITIAL_ENVELOPE"] as EventEnvelope<SendMessageCmd>

            // 2. 构造并调用验证服务。
            val validationCmdEnvelope = createNextEnvelope(
                commandEnvelope, "DSV.L2.CMD.ValidateMessage.v1", "DA", "SendMessageDomainAgent",
                ValidateMessageCmd(commandEnvelope.data.messageContent, commandEnvelope.data)
            )
            val validationResult = validationService.validate(validationCmdEnvelope)

            // 3. 根据验证结果，确定下一个要发送的事件。
            val nextEvent = if (validationResult.data.isValid) SendMessageEvents.VALIDATION_SUCCEEDED
            else SendMessageEvents.VALIDATION_FAILED

            println("✅ [validateAction] Will dispatch event asynchronously: $nextEvent")

            // ⭐ 核心修复：异步发送事件
            // 直接在Action中同步sendEvent()，状态机可能因为正忙于状态转换而拒绝事件。
            // 使用新线程并稍作延迟，可以确保状态机在接收新事件前已完成当前转换。
            Thread {
                try {
                    Thread.sleep(10) // 短暂休眠，给状态机完成转换留出时间。
                    val sent = context.stateMachine.sendEvent(nextEvent)
                    println("🌀 [validateAction-thread] Async event '$nextEvent' sent status: $sent")
                    // 如果事件仍然被拒绝，这是一个严重问题，需要强制流程失败。
                    if (!sent) {
                        System.err.println("❌ [validateAction-thread] Async event '$nextEvent' was not accepted!")
                        context.stateMachine.sendEvent(SendMessageEvents.FAIL_INTERNALLY)
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    System.err.println("❌ [validateAction-thread] Thread was interrupted while trying to send event.")
                }
            }.start()

        } catch (e: Exception) {
            // 捕获任何预料之外的异常，并触发内部错误事件。
            println("❌ [validateAction] Exception occurred during validation action.")
            e.printStackTrace()
            context.stateMachine.sendEvent(SendMessageEvents.FAIL_INTERNALLY)
        }
    }

    /**
     * 【动作2】并行（或顺序）执行持久化和外部调用 (parallelActions)
     * 当状态机接收到 VALIDATION_SUCCEEDED 事件时，会执行此方法。
     * “parallel”在这里是概念上的，实际执行可以是顺序的，重点是处理多个任务。
     */
    @Bean
    fun parallelActions(): Action<SendMessageStates, SendMessageEvents> = Action { context ->
        try {
            println("🚀 [parallelActions] Action started.")
            // 1. 同样从上下文中获取初始命令。
            val commandEnvelope = context.extendedState.variables["INITIAL_ENVELOPE"] as? EventEnvelope<SendMessageCmd>
                ?: throw IllegalStateException("INITIAL_ENVELOPE missing or invalid in state machine context")
            println("📥 [parallelActions] Received commandEnvelope with traceId: ${commandEnvelope.traceHeader.correlationId}")

            // 2. 执行业务逻辑：先持久化消息，再扣减资产。
            val saveCmdEnvelope = createNextEnvelope(commandEnvelope, "DSV.L2.CMD.SaveChatMessage.v1", "DA", "SendMessageDomainAgent",
                SaveChatMessageCmd("DTO from original command", commandEnvelope.data)
            )
            stateCoordinator.saveMessage(saveCmdEnvelope)
            val consumeAssetCmdEnvelope = createNextEnvelope(commandEnvelope, "DSV.L2.CMD.ConsumeAsset.v1", "DA", "SendMessageDomainAgent",
                ConsumeAssetCmd("user1", "gift1", 1, commandEnvelope.data.idempotencyKey, commandEnvelope.traceHeader.correlationId)
            )
            val assetResult = assetClient.consume(consumeAssetCmdEnvelope)

            // 3. 根据资产扣减的结果，确定下一个事件。
            val nextEvent = if (assetResult.data.isSuccess) {
                SendMessageEvents.EXTERNAL_CALLS_SUCCEEDED
            } else {
                SendMessageEvents.ASSET_CALL_FAILED
            }

            println("🔥 [parallelActions] Will dispatch event asynchronously: $nextEvent")

            // ⭐ 核心修复：使用新线程异步发送事件。
            Thread {
                try {
                    Thread.sleep(10)
                    val sent = context.stateMachine.sendEvent(nextEvent)
                    println("🌀 [parallelActions-thread] Async event '$nextEvent' sent status: $sent")
                    if (!sent) {
                        System.err.println("🚨 [parallelActions-thread] Failed to send event '$nextEvent'. Forcing internal error.")
                        context.stateMachine.sendEvent(SendMessageEvents.FAIL_INTERNALLY)
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    System.err.println("❌ [parallelActions-thread] Thread was interrupted while trying to send event.")
                }
            }.start()

        } catch (e: Exception) {
            // 捕获此阶段的任何异常。
            println("❌❌❌ [parallelActions] Exception caught! ❌❌❌")
            e.printStackTrace()
            println("❌ [parallelActions] Exception message: ${e.message}")
            context.stateMachine.sendEvent(SendMessageEvents.FAIL_INTERNALLY)
        }
    }
}
