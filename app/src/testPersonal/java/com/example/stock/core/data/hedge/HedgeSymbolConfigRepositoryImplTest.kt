package com.example.stock.core.data.hedge

import com.example.stock.core.domain.hedge.HedgeSymbolConfig
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HedgeSymbolConfigRepositoryImplTest {

    private val dao = mockk<HedgeSymbolConfigDao>(relaxed = true)
    private val repo = HedgeSymbolConfigRepositoryImpl(dao)

    @Test
    fun `依帳戶取得設定時轉成業務模型`() = runTest {
        every { dao.getConfigsByAccount(3L) } returns flowOf(
            listOf(
                HedgeSymbolConfigEntity(accountId = 3L, symbol = "0050", includeInExposure = true, weightTimes100 = 100L),
                HedgeSymbolConfigEntity(accountId = 3L, symbol = "00685L", includeInExposure = true, weightTimes100 = 200L)
            )
        )

        val result = repo.getConfigsByAccount(3L).first()

        assertEquals(
            listOf(
                HedgeSymbolConfig(accountId = 3L, symbol = "0050", includeInExposure = true, weightTimes100 = 100L),
                HedgeSymbolConfig(accountId = 3L, symbol = "00685L", includeInExposure = true, weightTimes100 = 200L)
            ),
            result
        )
        verify(exactly = 1) { dao.getConfigsByAccount(3L) }
    }

    @Test
    fun `帳戶沒有設定時回傳空清單`() = runTest {
        every { dao.getConfigsByAccount(9L) } returns flowOf(emptyList())

        assertEquals(emptyList<HedgeSymbolConfig>(), repo.getConfigsByAccount(9L).first())
    }

    @Test
    fun `upsert 時所有欄位原樣傳給 DAO`() = runTest {
        repo.upsertConfig(
            HedgeSymbolConfig(accountId = 1L, symbol = "00685L", includeInExposure = true, weightTimes100 = 200L)
        )

        coVerify(exactly = 1) {
            dao.upsert(
                HedgeSymbolConfigEntity(accountId = 1L, symbol = "00685L", includeInExposure = true, weightTimes100 = 200L)
            )
        }
    }

    @Test
    fun `新標的預設不計入曝險且加權為1倍`() = runTest {
        val config = HedgeSymbolConfig(accountId = 1L, symbol = "006208")
        assertFalse(config.includeInExposure)
        assertEquals(100L, config.weightTimes100)

        repo.upsertConfig(config)

        coVerify(exactly = 1) {
            dao.upsert(
                HedgeSymbolConfigEntity(accountId = 1L, symbol = "006208", includeInExposure = false, weightTimes100 = 100L)
            )
        }
    }

    @Test
    fun `刪除時以帳戶與代號呼叫 DAO`() = runTest {
        repo.deleteConfig(accountId = 2L, symbol = "0050")

        coVerify(exactly = 1) { dao.delete(2L, "0050") }
    }
}
