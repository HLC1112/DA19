// File: evt-04_2_APP.L2.EVT.kt
package com.aifactory.chat.contracts.v1.evt
data class InsufficientAssetEvt(
    override val status: String = "ERROR",
    val errorCode: String = "INSUFFICIENT_ASSET",
    override val traceId: String,
) : SendMessageResultEvt()