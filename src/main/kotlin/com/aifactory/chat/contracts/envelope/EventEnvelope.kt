package com.aifactory.chat.contracts.envelope

import java.time.Instant
import java.util.UUID

// --- 统一事件信封结构 ---

/**
 * 5.1 标准载荷结构：多头部元数据信封
 * @param T 数据体 (Data Payload) 的具体类型
 */
data class EventEnvelope<T>(
    val protocolHeader: ProtocolHeader,
    val traceHeader: TraceHeader,
    val governanceHeader: GovernanceHeader,
    val data: T
)

/**
 * 协议头 (Protocol Header) - 用于路由、解析和版本控制
 */
data class ProtocolHeader(
    val eventId: String = "evt-uuid-${UUID.randomUUID()}",
    val eventType: String, // e.g., "DSV.L2.CMD.SendMessage.v1"
    val eventSchemaUrl: String? = null,
    val eventTimestamp: Instant = Instant.now()
)

/**
 * 追踪头 (Trace Header) - 用于业务流程与因果链追踪
 */
data class TraceHeader(
    val correlationId: String, // 标识整个业务流程的唯一ID
    val causationId: String,   // 标识引起此事件的上一个事件的ID
    val source: Source
) {
    data class Source(
        val layer: String, // e.g., "APP", "DA", "DC"
        val service: String
    )
}

/**
 * 治理头 (Governance Header) - DEP 钩子
 */
data class GovernanceHeader(
    val traceContext: TraceContext? = null,
    val runtimeContext: RuntimeContext? = null,
    val securityContext: SecurityContext,
    val tags: List<String> = emptyList()
) {
    data class TraceContext(val traceId: String, val spanId: String)
    data class RuntimeContext(val podName: String?, val nodeName: String?, val namespace: String?)
    data class SecurityContext(val actorId: String, val tenantId: String?)
}

// --- 辅助创建函数 ---

/**
 * 创建一个新的、作为流程起点的事件信封
 */
fun <T> createInitialEnvelope(
    eventType: String,
    sourceLayer: String,
    sourceService: String,
    actorId: String,
    data: T
): EventEnvelope<T> {
    val correlationId = "corr-uuid-${UUID.randomUUID()}"
    return EventEnvelope(
        protocolHeader = ProtocolHeader(eventType = eventType),
        traceHeader = TraceHeader(
            correlationId = correlationId,
            causationId = "root", // 流程起点的causationId为root
            source = TraceHeader.Source(layer = sourceLayer, service = sourceService)
        ),
        governanceHeader = GovernanceHeader(
            securityContext = GovernanceHeader.SecurityContext(actorId = actorId, tenantId = null)
        ),
        data = data
    )
}

/**
 * 根据上一个事件，创建一个新的下游事件信封，并正确传递追踪信息
 */
fun <T_IN, T_OUT> createNextEnvelope(
    previousEnvelope: EventEnvelope<T_IN>,
    newEventType: String,
    newSourceLayer: String,
    newSourceService: String,
    newData: T_OUT
): EventEnvelope<T_OUT> {
    return EventEnvelope(
        protocolHeader = ProtocolHeader(eventType = newEventType),
        traceHeader = TraceHeader(
            correlationId = previousEnvelope.traceHeader.correlationId, // 传递相同的 correlationId
            causationId = previousEnvelope.protocolHeader.eventId,      // 将上一个事件的ID作为 causationId
            source = TraceHeader.Source(layer = newSourceLayer, service = newSourceService)
        ),
        governanceHeader = previousEnvelope.governanceHeader, // 传递相同的治理头
        data = newData
    )
}
