// File: evt-04_1_DSV.L2.EVT.kt
package com.aifactory.chat.contracts.v1.evt
data class ValidationFailedEvt(
    override val status: String = "ERROR",
    val errorCode: String = "VALIDATION_FAILED",
    override val traceId: String,
) : SendMessageResultEvt()