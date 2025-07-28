// File: evt-03_2_APP.L2.EVT.kt
package com.aifactory.chat.contracts.v1.evt
data class SendMessageCompletedEvt(
    override val status: String = "SUCCESS",
    override val traceId: String // ⭐ 修正：添加 traceId 属性
) : SendMessageResultEvt()