package com.example.stock.core.data.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionMapperTest {

    private val sampleItem = TransactionItem(
        id = 11L,
        accountId = 3L,
        type = TransactionType.DIVIDEND,
        symbol = "2330",
        name = "台積電",
        price = 1.5,
        shares = 2000,
        date = LocalDate(2026, 3, 9),
        fee = 7.0,
        tax = 13.0,
        note = "第一季股利",
        dividend = 3000.0,
        total = 2980.0,
        participatingShares = 1000
    )

    @Test
    fun `Item 轉 Entity 再轉回 Item 不會遺失或對調任何欄位`() {
        val roundTripped = TransactionMapper.toItem(TransactionMapper.toEntity(sampleItem))
        assertEquals(sampleItem, roundTripped)
    }

    @Test
    fun `toEntity 把日期存成 ISO 字串`() {
        assertEquals("2026-03-09", TransactionMapper.toEntity(sampleItem).date)
    }

    @Test
    fun `toItem 遇到無法解析的日期時退回 1970-01-01`() {
        val entity = Transaction(
            id = 1L,
            accountId = 1L,
            type = TransactionType.BUY,
            symbol = "2330",
            name = "台積電",
            price = 100.0,
            shares = 1000,
            date = "not-a-date",
            fee = 0.0,
            tax = 0.0,
            note = "",
            dividend = 0.0,
            total = 0.0,
            participatingShares = 0
        )
        assertEquals(LocalDate(1970, 1, 1), TransactionMapper.toItem(entity).date)
    }
}