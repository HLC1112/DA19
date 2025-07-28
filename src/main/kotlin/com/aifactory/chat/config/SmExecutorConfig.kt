import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor

@Configuration
class SmExecutorConfig {

    /**
     * 3.x 新写法
     * 只要名字叫 stateMachineTaskExecutor，StateMachine 会自动拿来用
     */
    @Bean("stateMachineTaskExecutor")
    fun stateMachineTaskExecutor(): TaskExecutor = SyncTaskExecutor()
}