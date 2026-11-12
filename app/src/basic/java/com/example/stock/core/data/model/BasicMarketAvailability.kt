package com.example.stock.core.data.model

import javax.inject.Inject

/** basic 只能搜尋、記錄台股。 */
class BasicMarketAvailability @Inject constructor() : MarketAvailability {
    override val searchRegions: Set<SearchRegion> = setOf(SearchRegion.TW)
}
