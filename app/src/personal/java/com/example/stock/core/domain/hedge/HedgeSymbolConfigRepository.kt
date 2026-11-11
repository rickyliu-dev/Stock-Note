package com.example.stock.core.domain.hedge

import kotlinx.coroutines.flow.Flow

/** 避險標的設定的存取介面（實作在 core/data/hedge，資料存在 HedgeDatabase） */
interface HedgeSymbolConfigRepository {

    /** 某帳戶的所有避險標的設定，依代號排序 */
    fun getConfigsByAccount(accountId: Long): Flow<List<HedgeSymbolConfig>>

    /** 新增或覆蓋（以 accountId＋symbol 為鍵） */
    suspend fun upsertConfig(config: HedgeSymbolConfig)

    /** 刪除某帳戶某代號的設定；不存在時不做任何事 */
    suspend fun deleteConfig(accountId: Long, symbol: String)
}
