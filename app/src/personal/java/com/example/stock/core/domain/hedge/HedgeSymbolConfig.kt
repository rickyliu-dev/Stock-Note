package com.example.stock.core.domain.hedge

/**
 * 避險標的設定：某帳戶的某檔標的是否計入曝險、以及市值加權。
 * 以「帳戶＋代號」為單位；交易仍在核心資料庫，這裡只放避險需要的額外屬性。
 *
 * @param includeInExposure 是否計入曝險（新標的預設不勾，不要默默算進曝險）
 * @param weightTimes100 市值加權 ×100（1 倍 = 100，00685L 正2 = 200）
 */
data class HedgeSymbolConfig(
    val accountId: Long,
    val symbol: String,
    val includeInExposure: Boolean = false,
    val weightTimes100: Long = DEFAULT_WEIGHT_TIMES_100
) {
    companion object {
        /** 1 倍 */
        const val DEFAULT_WEIGHT_TIMES_100 = 100L
    }
}
