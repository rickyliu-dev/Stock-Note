package com.example.stock.core.data.hedge

import com.example.stock.core.domain.hedge.HedgeSymbolConfig
import com.example.stock.core.domain.hedge.HedgeSymbolConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HedgeSymbolConfigRepositoryImpl @Inject constructor(
    private val dao: HedgeSymbolConfigDao
) : HedgeSymbolConfigRepository {

    override fun getConfigsByAccount(accountId: Long): Flow<List<HedgeSymbolConfig>> =
        dao.getConfigsByAccount(accountId).map { list -> list.map { it.toDomain() } }

    override suspend fun upsertConfig(config: HedgeSymbolConfig) {
        dao.upsert(config.toEntity())
    }

    override suspend fun deleteConfig(accountId: Long, symbol: String) {
        dao.delete(accountId, symbol)
    }
}

private fun HedgeSymbolConfigEntity.toDomain() = HedgeSymbolConfig(
    accountId = accountId,
    symbol = symbol,
    includeInExposure = includeInExposure,
    weightTimes100 = weightTimes100
)

private fun HedgeSymbolConfig.toEntity() = HedgeSymbolConfigEntity(
    accountId = accountId,
    symbol = symbol,
    includeInExposure = includeInExposure,
    weightTimes100 = weightTimes100
)
