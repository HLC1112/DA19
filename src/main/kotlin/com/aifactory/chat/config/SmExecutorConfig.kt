package com.aifactory.chat.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor

@Configuration
class SmExecutorConfig {

    /**
     * 定义一个名为 "stateMachineTaskExecutor" 的Bean，它是一个同步的任务执行器。
     * Spring Statemachine 会自动寻找这个特定名称的Bean来执行其内部任务。
     * 在测试中，使用同步执行器可以消除异步带来的不确定性，确保测试结果的可靠性。
     */
    @Bean("stateMachineTaskExecutor")
    fun stateMachineTaskExecutor(): TaskExecutor = SyncTaskExecutor()
}
