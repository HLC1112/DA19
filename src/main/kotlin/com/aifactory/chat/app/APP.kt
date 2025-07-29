package com.aifactory.chat.app

import com.aifactory.chat.contracts.envelope.EventEnvelope
import com.aifactory.chat.contracts.envelope.createInitialEnvelope
import com.aifactory.chat.contracts.envelope.createNextEnvelope
import com.aifactory.chat.contracts.v1.cmd.SendMessageCmd
import com.aifactory.chat.contracts.v1.evt.*
import com.aifactory.chat.da.SendMessageActions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.messaging.Message
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State
import org.springframework.statemachine.transition.Transition
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// --- 1. 状态机定义：状态 (States) & 事件 (Events) ---

/**
 * 定义了“发送消息”流程中所有可能存在的稳定状态。
 * - READY: 初始状态，准备开始处理。
 * - VALIDATING: 正在验证输入数据的合法性。
 * - PERSISTING_AND_EXTERNAL_CALLS: 验证通过，正在执行持久化和外部服务调用（如资产扣减）。
 * - SUCCESS: 流程成功结束。
 * - VALIDATION_FAILED: 验证失败，流程终止。
 * - ASSET_FAILED: 资产扣减失败，流程终止。
 * - INTERNAL_ERROR: 发生未预期的内部错误，流程终止。
 */
enum class SendMessageStates {
    READY, VALIDATING, PERSISTING_AND_EXTERNAL_CALLS,
    SUCCESS, VALIDATION_FAILED, ASSET_FAILED, INTERNAL_ERROR
}

/**
 * 定义了驱动状态机从一个状态转换到另一个状态的“事件”。
 * 事件由业务逻辑（Actions）触发。
 */
enum class SendMessageEvents {
    START_VALIDATION,         // 开始验证
    VALIDATION_SUCCEEDED,     // 验证成功
    VALIDATION_FAILED,        // 验证失败
    EXTERNAL_CALLS_SUCCEEDED, // 外部调用（持久化、资产扣减等）全部成功
    ASSET_CALL_FAILED,        // 资产扣减失败
    FAIL_INTERNALLY           // 发生内部错误
}

// --- 2. 状态机配置：转换规则 (Transitions) ---

@Configuration
@EnableStateMachineFactory // 启用Spring状态机工厂，允许我们创建状态机实例
class AppFsmConfig(
    // 注入包含状态机具体动作逻辑的Bean
    private val sendMessageActions: SendMessageActions
) : EnumStateMachineConfigurerAdapter<SendMessageStates, SendMessageEvents>() {

    /**
     * 配置状态机的“状态”。
     * 这里定义了哪些是初始状态、结束状态，以及所有可能的状态集合。
     */
    override fun configure(states: StateMachineStateConfigurer<SendMessageStates, SendMessageEvents>) {
        states.withStates()
            .initial(SendMessageStates.READY) // 定义初始状态
            .end(SendMessageStates.SUCCESS)   // 定义一个成功的结束状态
            .end(SendMessageStates.VALIDATION_FAILED) // 定义各种失败的结束状态
            .end(SendMessageStates.ASSET_FAILED)
            .end(SendMessageStates.INTERNAL_ERROR)
            .states(SendMessageStates.entries.toSet()) // 注册所有定义过的状态
    }

    /**
     * 配置状态机的“转换”（Transitions），这是状态机的核心。
     * 它定义了 "当处于某个状态（source）时，如果接收到某个事件（event），
     * 应该执行什么动作（action），并转换到哪个新状态（target）"。
     */
    override fun configure(transitions: StateMachineTransitionConfigurer<SendMessageStates, SendMessageEvents>) {
        transitions
            // 规则1: 从 READY 状态接收到 START_VALIDATION 事件 -> 执行 validateAction -> 转换到 VALIDATING 状态
            .withExternal()
            .source(SendMessageStates.READY).target(SendMessageStates.VALIDATING)
            .event(SendMessageEvents.START_VALIDATION).action(sendMessageActions.validateAction())
            // 规则2: 从 VALIDATING 状态接收到 VALIDATION_SUCCEEDED 事件 -> 执行 parallelActions -> 转换到 PERSISTING_AND_EXTERNAL_CALLS 状态
            .and().withExternal()
            .source(SendMessageStates.VALIDATING).target(SendMessageStates.PERSISTING_AND_EXTERNAL_CALLS)
            .event(SendMessageEvents.VALIDATION_SUCCEEDED).action(sendMessageActions.parallelActions())
            // 规则3: 从 VALIDATING 状态接收到 VALIDATION_FAILED 事件 -> 转换到 VALIDATION_FAILED 结束状态
            .and().withExternal()
            .source(SendMessageStates.VALIDATING).target(SendMessageStates.VALIDATION_FAILED)
            .event(SendMessageEvents.VALIDATION_FAILED)
            // 规则4: 从 PERSISTING_AND_EXTERNAL_CALLS 状态接收到 EXTERNAL_CALLS_SUCCEEDED 事件 -> 转换到 SUCCESS 结束状态
            .and().withExternal()
            .source(SendMessageStates.PERSISTING_AND_EXTERNAL_CALLS).target(SendMessageStates.SUCCESS)
            .event(SendMessageEvents.EXTERNAL_CALLS_SUCCEEDED)
            // 规则5: 从 PERSISTING_AND_EXTERNAL_CALLS 状态接收到 ASSET_CALL_FAILED 事件 -> 转换到 ASSET_FAILED 结束状态
            .and().withExternal()
            .source(SendMessageStates.PERSISTING_AND_EXTERNAL_CALLS).target(SendMessageStates.ASSET_FAILED)
            .event(SendMessageEvents.ASSET_CALL_FAILED)
            // 规则6: 从 PERSISTING_AND_EXTERNAL_CALLS 状态接收到 FAIL_INTERNALLY 事件 -> 转换到 INTERNAL_ERROR 结束状态
            .and().withExternal()
            .source(SendMessageStates.PERSISTING_AND_EXTERNAL_CALLS).target(SendMessageStates.INTERNAL_ERROR)
            .event(SendMessageEvents.FAIL_INTERNALLY)
    }

    /**
     * 定义一个全局的状态机监听器。
     * 这个监听器对于调试非常有用，它会打印出状态机内部的所有活动，
     * 比如状态的改变、发生的转换、以及未被接受的事件。
     */
    @Bean
    fun stateMachineListener(): StateMachineListenerAdapter<SendMessageStates, SendMessageEvents> {
        return object : StateMachineListenerAdapter<SendMessageStates, SendMessageEvents>() {
            // 当一个事件因为不匹配当前状态而被拒绝时，这个方法会被调用。
            override fun eventNotAccepted(event: Message<SendMessageEvents>?) {
                System.err.println("🚨 [StateMachineListener] Event not accepted: ${event?.payload}")
            }

            // 每当发生一次成功的状态转换时，这个方法会被调用。
            override fun transition(transition: Transition<SendMessageStates, SendMessageEvents>?) {
                if (transition?.source != null && transition.target != null) {
                    println("🔄 [StateMachineListener] Transitioning from ${transition.source.id} to ${transition.target.id} on event ${transition.trigger?.event}")
                }
            }

            // 每当状态机的当前状态发生改变时，这个方法会被调用。
            override fun stateChanged(from: State<SendMessageStates, SendMessageEvents>?, to: State<SendMessageStates, SendMessageEvents>?) {
                println("📊 [StateMachineListener] State changed from ${from?.id ?: "null"} to ${to?.id ?: "null"}")
            }
        }
    }
}

// --- 3. 状态机客户端 & HTTP 适配器 ---

/**
 * 🎮 [APPFSM层 - APP] - L1 统一状态机 (客户端)
 * 状态机客户端服务 (Facade)。
 * 这是外部调用方（比如Controller）与状态机交互的统一入口。
 * 它封装了创建、启动、监控状态机实例的复杂逻辑。
 */
@Service
class AppL1Fsm(
    private val stateMachineFactory: StateMachineFactory<SendMessageStates, SendMessageEvents>
) {
    /**
     * 执行发送消息的完整流程。
     * @param commandData 包含消息内容的命令对象。
     * @param actorId 发起操作的用户ID。
     * @return 返回一个包含最终结果事件的信封。
     */
    fun execute(commandData: SendMessageCmd, actorId: String): EventEnvelope<out SendMessageResultEvt> {
        // 1. 创建初始的事件信封，包含所有追踪和元数据。
        val initialEnvelope = createInitialEnvelope("DSV.L2.CMD.SendMessage.v1", "APP", "ChatController", actorId, commandData)
        // 2. 从工厂获取一个状态机实例。使用 correlationId 保证每个流程实例都有一个独立的状态机。
        val stateMachine = stateMachineFactory.getStateMachine("sendMessageFsm_${initialEnvelope.traceHeader.correlationId}")

        // 3. 使用 CountDownLatch 来同步等待状态机执行完毕。
        //    因为状态机内部的动作（Action）可能是异步的。
        val latch = CountDownLatch(1)
        val finalStateHolder = arrayOfNulls<SendMessageStates>(1) // 用于存储状态机的最终状态

        // 定义哪些状态是流程的终点。
        val endStates = setOf(
            SendMessageStates.SUCCESS,
            SendMessageStates.VALIDATION_FAILED,
            SendMessageStates.ASSET_FAILED,
            SendMessageStates.INTERNAL_ERROR
        )

        // 4. 添加一个临时的监听器，只为了捕获当前流程的结束状态。
        val listener = object : StateMachineListenerAdapter<SendMessageStates, SendMessageEvents>() {
            override fun stateChanged(from: State<SendMessageStates, SendMessageEvents>?, to: State<SendMessageStates, SendMessageEvents>?) {
                if (to != null && endStates.contains(to.id)) {
                    finalStateHolder[0] = to.id // 记录最终状态
                    latch.countDown()           // 释放锁，通知主线程可以继续执行
                }
            }
        }
        stateMachine.addStateListener(listener)

        return try {
            // ⭐ 关键修复：将 extendedState 的设置移到 start() 之后。
            // 确保状态机完全初始化后，再将上下文变量注入，防止变量在启动过程中被重置。
            stateMachine.start()
            // 5. 将初始信封存入状态机的扩展状态（extendedState），以便在各个Action中共享数据。
            stateMachine.extendedState.variables["INITIAL_ENVELOPE"] = initialEnvelope

            // 6. 发送第一个事件，启动整个流程。
            stateMachine.sendEvent(SendMessageEvents.START_VALIDATION)

            // 7. 等待状态机到达某个终点状态，最多等待10秒。
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw RuntimeException("State machine processing timed out.")
            }

            // 8. 根据状态机的最终状态，创建并返回对应的结果事件。
            val correlationId = initialEnvelope.traceHeader.correlationId
            when (finalStateHolder[0]) {
                SendMessageStates.SUCCESS -> createNextEnvelope(initialEnvelope, "APP.L2.EVT.SendMessageCompleted.v1", "APP", "AppL1Fsm", SendMessageCompletedEvt(traceId = correlationId))
                SendMessageStates.VALIDATION_FAILED -> createNextEnvelope(initialEnvelope, "APP.L2.EVT.ValidationFailed.v1", "APP", "AppL1Fsm", ValidationFailedEvt(traceId = correlationId))
                SendMessageStates.ASSET_FAILED -> createNextEnvelope(initialEnvelope, "APP.L2.EVT.InsufficientAsset.v1", "APP", "AppL1Fsm", InsufficientAssetEvt(traceId = correlationId))
                else -> createNextEnvelope(initialEnvelope, "APP.L2.EVT.InternalServerError.v1", "APP", "AppL1Fsm", InternalServerErrorEvt(traceId = correlationId))
            }
        } catch (e: Exception) {
            // 捕获所有异常，统一返回内部错误事件。
            createNextEnvelope(initialEnvelope, "APP.L2.EVT.InternalServerError.v1", "APP", "AppL1Fsm", InternalServerErrorEvt(traceId = initialEnvelope.traceHeader.correlationId, message = e.message ?: "Unknown error"))
        } finally {
            // 9. 清理工作：确保状态机被停止，并移除监听器，防止内存泄漏。
            if (!stateMachine.isComplete) {
                stateMachine.stop()
            }
            stateMachine.removeStateListener(listener)
        }
    }
}

/**
 * 🎮 [APPFSM层 - APP] - HTTP 协议适配器
 * 这是整个业务流程的入口点，负责接收外部的HTTP请求，
 * 并将其转换为内部命令，交由 AppL1Fsm 处理。
 */
@RestController
@RequestMapping("/api/v1/chat")
class ChatController(private val appL1Fsm: AppL1Fsm) {
    @PostMapping("/send")
    fun sendMessage(
        @RequestBody commandData: SendMessageCmd,
        @RequestHeader("X-Actor-ID") actorId: String
    ): ResponseEntity<EventEnvelope<out SendMessageResultEvt>> {
        // 调用状态机客户端执行业务流程
        val resultEnvelope = appL1Fsm.execute(commandData, actorId)
        val finalEvent = resultEnvelope.data
        // 根据最终结果的状态，返回不同的HTTP状态码。
        return when {
            finalEvent.status == "SUCCESS" -> ResponseEntity.ok(resultEnvelope)
            else -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(resultEnvelope)
        }
    }
}
