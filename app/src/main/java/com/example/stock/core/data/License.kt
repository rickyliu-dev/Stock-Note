package com.example.stock.core.data

object LicenseManager {

    // 🟢 這是你的「最高機密」，絕對不要洩漏給別人。
    // 只要改變這個字串，以前所有算出來的序號就會全部失效！
    private const val SECRET_SALT = "RICKY_STOCK_PRO_2026_SECRET"

    /**
     * 1. App 端專用：驗證使用者輸入的序號是否合法
     */
    fun isValidProductKey(input: String): Boolean {
        // 先清掉空白跟橫槓，並轉大寫
        val cleanKey = input.replace("-", "").replace(" ", "").uppercase()

        // 我們設計的序號長度固定為 12 碼
        if (cleanKey.length != 12) return false

        // 前 11 碼是「隨機內容」，最後 1 碼是「檢查碼」
        val payload = cleanKey.substring(0, 11)
        val expectedChecksumChar = cleanKey[11]

        // 拿前 11 碼去算，看看算出來的檢查碼跟使用者輸入的是不是一樣
        val actualChecksumChar = calculateChecksum(payload)

        return expectedChecksumChar == actualChecksumChar
    }

    /**
     * 2. 核心演算法：根據前 11 碼與機密鹽值，計算出第 12 碼
     */
    private fun calculateChecksum(payload: String): Char {
        // 把隨機碼和你的機密字串黏在一起
        val combined = payload + SECRET_SALT

        var sum = 0
        // 將每個字元的 ASCII 碼乘上它的位置權重，加總起來
        for (i in combined.indices) {
            sum += combined[i].code * (i + 1)
        }

        // 算出除以 36 的餘數 (因為我們要用 0-9 和 A-Z，共 36 個字元)
        val remainder = sum % 36

        // 將餘數轉回字元 (0~9 轉成數字字元，10~35 轉成字母 A~Z)
        return if (remainder < 10) {
            (remainder + '0'.code).toChar()
        } else {
            (remainder - 10 + 'A'.code).toChar()
        }
    }

    /**
     * 3. 開發者專用：產生一組全新的合法序號
     * (你可以寫個簡單的單元測試或獨立腳本來呼叫這個 function，幫朋友產序號)
     */
    fun generateRandomKey(): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        // 隨機抽 11 個字元當作 payload
        val payload = (1..11).map { chars.random() }.joinToString("")

        // 算出這 11 個字元專屬的第 12 碼
        val checksum = calculateChecksum(payload)
        val fullKey = payload + checksum

        // 幫它加上橫槓，變成漂亮格式：XXXX-XXXX-XXXX
        return "${fullKey.substring(0, 4)}-${fullKey.substring(4, 8)}-${fullKey.substring(8, 12)}"
    }
}