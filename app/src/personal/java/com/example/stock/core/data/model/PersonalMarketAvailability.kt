package com.example.stock.core.data.model

import javax.inject.Inject

/** personal 可搜尋台股、美股與混合搜尋。 */
class PersonalMarketAvailability @Inject constructor() : MarketAvailability {
    override val searchRegions: Set<SearchRegion> =
        setOf(SearchRegion.TW, SearchRegion.US, SearchRegion.ALL)
}
