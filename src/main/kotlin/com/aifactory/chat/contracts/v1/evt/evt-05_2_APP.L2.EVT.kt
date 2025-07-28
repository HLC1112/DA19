// File: evt-05_2_APP.L2.EVT.kt
package com.aifactory.chat.contracts.v1.evt
data class InternalServerErrorEvt(
    override val status: String = "ERROR",
    val message: String = "Internal Server Error",
    override val traceId: String,
) : SendMessageResultEvt()