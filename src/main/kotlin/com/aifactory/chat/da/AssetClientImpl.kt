package com.aifactory.chat.da

import com.aifactory.chat.contracts.envelope.EventEnvelope
import com.aifactory.chat.contracts.envelope.createNextEnvelope
import com.aifactory.chat.contracts.v1.evt.AssetConsumedEvt
import com.aifactory.chat.da.AssetClient
import org.springframework.stereotype.Component

/**
 * 外部资产服务(DA_EXT)客户端的存根(Stub)实现
 * 在真实环境中，这里会使用 RestTemplate 或 WebClient 来发起 HTTP 调用。
 * 为了本地开发和测试，我们提供一个模拟的实现。
 */
@Component // ⭐ 关键：将这个类注册为一个Spring Bean
class AssetClientImpl : AssetClient {

    override fun consume(envelope: EventEnvelope<*>): EventEnvelope<AssetConsumedEvt> {
        println("[DA_EXT Client Stub]: Simulating asset consumption for traceId: ${envelope.traceHeader.correlationId}")

        // 模拟一个成功的调用结果
        val isSuccess = true
        val errorCode = if (isSuccess) null else "INSUFFICIENT_ASSET"

        // 创建并返回一个标准的成功事件信封
        return createNextEnvelope(
            previousEnvelope = envelope,
            newEventType = "DSV.L2.EVT.AssetConsumed.v1",
            newSourceLayer = "DA_EXT",
            newSourceService = "AssetService",
            newData = AssetConsumedEvt(
                isSuccess = isSuccess,
                errorCode = errorCode
            )
        )
    }
}
