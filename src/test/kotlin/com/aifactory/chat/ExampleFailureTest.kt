package com.aifactory.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ExampleFailureTest {

    @Test
    fun `this test is designed to fail to trigger the AIOps workflow`() {
        // 我们只保留这个会失败的测试，以确保Gradle的test任务会返回一个失败的状态码
        val expected = "Hello AIOps"
        val actual = "Hello World"
        assertEquals(expected, actual, "We expect this assertion to fail to trigger AI analysis.")
    }
}
