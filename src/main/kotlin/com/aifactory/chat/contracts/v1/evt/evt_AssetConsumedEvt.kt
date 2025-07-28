// --- ⭐ 新增文件: src/main/kotlin/com/aifactory/chat/contracts/v1/evt/evt_AssetConsumedEvt.kt ---
package com.aifactory.chat.contracts.v1.evt
data class AssetConsumedEvt(
    val isSuccess: Boolean,
    val errorCode: String?
)