package com.example.stock.core.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YahooQuoteParserTest {

    private fun json(
        price: String = "110.0",
        meta: String = "",
        closes: String = "[]"
    ): String {
        val metaFields = listOfNotNull(
            "\"regularMarketPrice\": $price",
            meta.ifBlank { null }
        ).joinToString(",")
        return """
            {"chart":{"result":[{
              "meta":{$metaFields},
              "indicators":{"quote":[{"close":$closes}]}
            }]}}
        """.trimIndent()
    }

    @Test
    fun `使用 regularMarketPreviousClose 計算漲跌與百分比`() {
        val r = YahooQuoteParser.parse(
            json(price = "110.0", meta = "\"regularMarketPreviousClose\": 100.0")
        )!!
        assertEquals(110.0, r.currentPrice, 0.0001)
        assertEquals(10.0, r.change, 0.0001)
        assertEquals(10.0, r.changePercent, 0.0001)
    }

    @Test
    fun `下跌時漲跌為負`() {
        val r = YahooQuoteParser.parse(
            json(price = "90.0", meta = "\"regularMarketPreviousClose\": 100.0")
        )!!
        assertEquals(-10.0, r.change, 0.0001)
        assertEquals(-10.0, r.changePercent, 0.0001)
    }

    @Test
    fun `沒有 regularMarketPreviousClose 時改用 chartPreviousClose`() {
        val r = YahooQuoteParser.parse(
            json(price = "110.0", meta = "\"chartPreviousClose\": 100.0")
        )!!
        assertEquals(10.0, r.change, 0.0001)
    }

    @Test
    fun `regularMarketPreviousClose 優先於 chartPreviousClose`() {
        val r = YahooQuoteParser.parse(
            json(
                price = "110.0",
                meta = "\"regularMarketPreviousClose\": 100.0, \"chartPreviousClose\": 50.0"
            )
        )!!
        assertEquals(10.0, r.change, 0.0001)
    }

    @Test
    fun `meta 都沒有昨收時，從 close 陣列取第一個有效值`() {
        val r = YahooQuoteParser.parse(
            json(price = "110.0", closes = "[null, 0, 100.0, 105.0]")
        )!!
        assertEquals(10.0, r.change, 0.0001)
        assertEquals(10.0, r.changePercent, 0.0001)
    }

    @Test
    fun `完全沒有昨收資料時漲跌歸零`() {
        val r = YahooQuoteParser.parse(json(price = "110.0", closes = "[]"))!!
        assertEquals(110.0, r.currentPrice, 0.0001)
        assertEquals(0.0, r.change, 0.0001)
        assertEquals(0.0, r.changePercent, 0.0001)
    }

    @Test
    fun `昨收為 0 時視為沒有，不會除以零`() {
        val r = YahooQuoteParser.parse(
            json(price = "110.0", meta = "\"regularMarketPreviousClose\": 0.0")
        )!!
        assertEquals(0.0, r.change, 0.0001)
        assertEquals(0.0, r.changePercent, 0.0001)
    }

    @Test
    fun `價格沒有變動時漲跌為零`() {
        val r = YahooQuoteParser.parse(
            json(price = "100.0", meta = "\"regularMarketPreviousClose\": 100.0")
        )!!
        assertEquals(0.0, r.change, 0.0001)
        assertEquals(0.0, r.changePercent, 0.0001)
    }

    @Test
    fun `小數價格的百分比計算`() {
        val r = YahooQuoteParser.parse(
            json(price = "23.5", meta = "\"regularMarketPreviousClose\": 22.0")
        )!!
        assertEquals(1.5, r.change, 0.0001)
        assertEquals(6.8181, r.changePercent, 0.001)
    }

    @Test
    fun `缺少 regularMarketPrice 回傳 null`() {
        val noPrice = """{"chart":{"result":[{"meta":{},"indicators":{"quote":[{"close":[]}]}}]}}"""
        assertNull(YahooQuoteParser.parse(noPrice))
    }

    @Test
    fun `result 為空陣列回傳 null`() {
        assertNull(YahooQuoteParser.parse("""{"chart":{"result":[]}}"""))
    }

    @Test
    fun `result 為 null（Yahoo 查無代號時的回應）回傳 null`() {
        assertNull(YahooQuoteParser.parse("""{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
    }

    @Test
    fun `格式壞掉的 JSON 回傳 null`() {
        assertNull(YahooQuoteParser.parse("not a json"))
        assertNull(YahooQuoteParser.parse(""))
    }

    @Test
    fun `正常回應能解析出結果`() {
        assertNotNull(
            YahooQuoteParser.parse(json(meta = "\"regularMarketPreviousClose\": 100.0"))
        )
    }
}