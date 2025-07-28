// --- ValueObject (src/main/kotlin/com/aifactory/chat/db/valueobject/DB19_V1.kt) ---
package com.aifactory.chat.db.valueobject
/**
 * 📦 [值对象] 聚合根内部使用的无ID对象
 */
@JvmInline
value class MessageContent(val value: String) {
    init { require(value.isNotBlank()) { "Message content cannot be blank" } }
}