package com.aifactory.chat.app

import com.aifactory.chat.contracts.envelope.EventEnvelope
import com.aifactory.chat.contracts.envelope.createInitialEnvelope
import com.aifactory.chat.contracts.envelope.createNextEnvelope
import com.aifactory.chat.contracts.v1.cmd.SendMessageCmd
import com.aifactory.chat.contracts.v1.evt.*
import com.aifactory.chat.da.SendMessageActions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// --- 1. 状态机定义：状态 (States) & 事件 (Events) ---

enum class SendMessageStates {
    READY, VALIDATING, PERSISTING_AND_EXTERNAL_CALLS,
    SUCCESS, VALIDATION_FAILED, ASSET_FAILED, INTERNAL_ERROR
}

enum class SendMessageEvents {
    START_VALIDATION, VALIDATION_SUCCEEDED, VALIDATION_FAILED,
    EXTERNAL_CALLS_SUCCEEDED, ASSET_CALL_FAILED, FAIL_INTERNALLY
}

// --- 2. 状态机配置：转换规则 (Transitions) ---

@Configuration
@EnableStateMachineFactory
class AppFsmConfig(
    private val sendMessageActions: SendMessageActions
) : EnumStateMachineConfigurerAdapter<SendMessageStates, SendMessageEvents>() {

    override fun configure(states: StateMachineStateConfigurer<SendMessageStates, SendMessageEvents>) {
        states.withStates()
            .initial(SendMessageStates.READY)
            .end(SendMessageStates.SUCCESS)
            .end(SendMessageStates.VALIDATION_FAILED)
            .end(SendMessageStates.ASSET_FAILED)
            .end(SendMessageStates.INTERNAL_ERROR)
            .states(SendMessageStates.entries.toSet())
    }

    override fun configure(transitions: StateMachineTransitionConfigurer<SendMessageStates, SendMessageEvents>) {
        transitions
            .withExternal()
            .source(SendMessageStates.READY).target(SendMessageStates.VALIDATING)
            .event(SendMessageEvents.START_VALIDATION).action(sendMessageActions.validateAction())
            .and().withExternal()
            .source(SendMessageStates.VALIDATING).target(SendMessageStates.PERSISTING_AND_EXTERNAL_CALLS)
            .event(SendMessageEvents.VALIDATION_SUCCEEDED).action(sendMessageActions.parallelActions())
            .and().withExternal()
            .source(SendMessageStates.VALIDATING).target(SendMessageStates.VALIDATION_FAILED)
            .event(SendMessageEvents.VALIDATION_FAILED)
            .and().withExternal()
            .source(SendMessageStates.PERSISTING_AND_EXTERNAL_CALLS).target(SendMessageStates.SUCCESS)
            .event(SendMessageEvents.EXTERNAL_CALLS_SUCCEEDED)
            .and().withExternal()
            .source(SendMessageStates.PERSISTING_AND_EXTERNAL_CALLS).target(SendMessageStates.ASSET_FAILED)
            .event(SendMessageEvents.ASSET_CALL_FAILED)
            .and().withExternal()
            .source(SendMessageStates.PERSISTING_AND_EXTERNAL_CALLS).target(SendMessageStates.INTERNAL_ERROR)
            .event(SendMessageEvents.FAIL_INTERNALLY)
    }

    @Bean("stateMachineTaskExecutor")
    fun stateMachineTaskExecutor(): TaskExecutor {
        return SyncTaskExecutor()
    }
}

// --- 3. 状态机客户端 & HTTP 适配器 ---

/**
 * 🎮 [APPFSM层 - APP] - L1 统一状态机 (客户端)
 */
@Service
class AppL1Fsm(
    private val stateMachineFactory: StateMachineFactory<SendMessageStates, SendMessageEvents>
) {
    fun execute(commandData: SendMessageCmd, actorId: String): EventEnvelope<out SendMessageResultEvt> {
        val initialEnvelope = createInitialEnvelope("DSV.L2.CMD.SendMessage.v1", "APP", "ChatController", actorId, commandData)
        val stateMachine = stateMachineFactory.getStateMachine("sendMessageFsm_${initialEnvelope.traceHeader.correlationId}")

        // ⭐ 关键修复：使用 CountDownLatch 和 Listener 来同步等待状态机完成
        val latch = CountDownLatch(1)
        val listener = object : StateMachineListenerAdapter<SendMessageStates, SendMessageEvents>() {
            // 当状态机转换到任何一个“终止状态”时，我们就释放锁
            override fun stateChanged(from: State<SendMessageStates, SendMessageEvents>?, to: State<SendMessageStates, SendMessageEvents>?) {
                if (to?.isEnd == true) {
                    latch.countDown()
                }
            }
        }
        stateMachine.addStateListener(listener)

        return try {
            stateMachine.extendedState.variables["INITIAL_ENVELOPE"] = initialEnvelope
            stateMachine.start()
            stateMachine.sendEvent(SendMessageEvents.START_VALIDATION)

            // 等待最多10秒，直到锁被释放
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw RuntimeException("State machine processing timed out.")
            }

            val correlationId = initialEnvelope.traceHeader.correlationId
            when (stateMachine.state.id) {
                SendMessageStates.SUCCESS -> createNextEnvelope(initialEnvelope, "APP.L2.EVT.SendMessageCompleted.v1", "APP", "AppL1Fsm", SendMessageCompletedEvt(traceId = correlationId))
                SendMessageStates.VALIDATION_FAILED -> createNextEnvelope(initialEnvelope, "APP.L2.EVT.ValidationFailed.v1", "APP", "AppL1Fsm", ValidationFailedEvt(traceId = correlationId))
                SendMessageStates.ASSET_FAILED -> createNextEnvelope(initialEnvelope, "APP.L2.EVT.InsufficientAsset.v1", "APP", "AppL1Fsm", InsufficientAssetEvt(traceId = correlationId))
                else -> createNextEnvelope(initialEnvelope, "APP.L2.EVT.InternalServerError.v1", "APP", "AppL1Fsm", InternalServerErrorEvt(traceId = correlationId))
            }
        } catch (e: Exception) {
            createNextEnvelope(initialEnvelope, "APP.L2.EVT.InternalServerError.v1", "APP", "AppL1Fsm", InternalServerErrorEvt(traceId = initialEnvelope.traceHeader.correlationId, message = e.message ?: "Unknown error"))
        } finally {
            // ⭐ 关键修复：使用 isComplete 属性来判断状态机是否已到达终止状态
            if (!stateMachine.isComplete) {
                stateMachine.stop()
            }
            stateMachine.removeStateListener(listener)
        }
    }
}

/**
 * 🎮 [APPFSM层 - APP] - HTTP 协议适配器
 */
@RestController
@RequestMapping("/api/v1/chat")
class ChatController(private val appL1Fsm: AppL1Fsm) {
    @PostMapping("/send")
    fun sendMessage(
        @RequestBody commandData: SendMessageCmd,
        @RequestHeader("X-Actor-ID") actorId: String
    ): ResponseEntity<EventEnvelope<out SendMessageResultEvt>> {
        val resultEnvelope = appL1Fsm.execute(commandData, actorId)
        val finalEvent = resultEnvelope.data
        return when {
            finalEvent.status == "SUCCESS" -> ResponseEntity.ok(resultEnvelope)
            else -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(resultEnvelope)
        }
    }
}
