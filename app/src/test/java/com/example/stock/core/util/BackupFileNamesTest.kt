package com.example.stock.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFileNamesTest {

    @Test
    fun `basicRelease 檔名與舊版相同`() {
        assertEquals("stock_ledger.db", driveFileNameFor("stock_ledger", "com.example.stock"))
    }

    @Test
    fun `basicDebug 加上 debug 後綴`() {
        assertEquals("stock_ledger.debug.db", driveFileNameFor("stock_ledger", "com.example.stock.debug"))
    }

    @Test
    fun `personalRelease 加上 personal 後綴`() {
        assertEquals("stock_ledger.personal.db", driveFileNameFor("stock_ledger", "com.example.stock.personal"))
    }

    @Test
    fun `personalDebug 加上 personal debug 後綴`() {
        assertEquals(
            "stock_ledger.personal.debug.db",
            driveFileNameFor("stock_ledger", "com.example.stock.personal.debug")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `applicationId 不是 com_example_stock 開頭時丟例外`() {
        driveFileNameFor("stock_ledger", "com.other.app")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `applicationId 只有字首相同但不是子套件時丟例外`() {
        driveFileNameFor("stock_ledger", "com.example.stockfoo")
    }
}
