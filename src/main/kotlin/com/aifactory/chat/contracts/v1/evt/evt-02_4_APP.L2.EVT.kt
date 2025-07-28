// File: evt-02_4_APP.L2.EVT.kt
package com.aifactory.chat.contracts.v1.evt
sealed class SendMessageResultEvt {
    abstract val status: String
    abstract val traceId: String
}