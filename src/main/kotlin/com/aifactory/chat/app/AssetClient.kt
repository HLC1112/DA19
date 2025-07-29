package com.aifactory.chat.app

import com.aifactory.chat.contracts.envelope.EventEnvelope
import com.aifactory.chat.contracts.v1.evt.AssetConsumedEvt

/**
 * 外部资产服务(DA_EXT)的客户端接口定义
 */
interface AssetClient {
    fun consume(envelope: EventEnvelope<*>): EventEnvelope<AssetConsumedEvt>
}
