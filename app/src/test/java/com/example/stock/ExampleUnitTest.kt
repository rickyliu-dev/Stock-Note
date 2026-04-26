package com.example.stock

import com.example.stock.core.data.LicenseManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}

class KeyGeneratorTest {

    @Test
    fun generateBatchOfKeys() {
        println("==================================")
        println("🚀 準備產生 5 組全新的 VIP 序號 🚀")
        println("==================================")

        // 這裡直接呼叫我們寫好的 LicenseManager
        for (i in 1..5) {
            val newKey = LicenseManager.generateRandomKey()
            println("序號 $i: $newKey")

            // 順便自我測試一下這組序號能不能通過驗證
            val isValid = LicenseManager.isValidProductKey(newKey)
            println("   -> 驗證測試: ${if (isValid) "✅ 成功" else "❌ 失敗"}")
        }

        println("==================================")
    }
}