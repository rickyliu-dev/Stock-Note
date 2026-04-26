package com.example.stock.feature.addTransaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stock.core.data.DateFormatter
import com.example.stock.core.data.DateFormatter.formatToTwoDigits
import com.example.stock.core.data.DateProvider
import com.example.stock.core.data.EnterConstants
import com.example.stock.core.data.MarketConstants
import com.example.stock.core.data.model.TransactionItem
import com.example.stock.core.data.model.TransactionType
import com.example.stock.core.data.repository.SettingsRepository
import com.example.stock.core.data.repository.TransactionRepository
import com.example.stock.core.domain.CalculateTransactionUseCase
import com.example.stock.navigation.AllScreens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.number
import javax.inject.Inject
import kotlin.math.roundToInt

data class AddTransactionUiState(
    val isLoading: Boolean = true,
    val type: TransactionType = TransactionType.BUY,
    val symbol: String = "",
    val displaySymbol: String = "",
    val isSymbolReadOnly: Boolean = false,
    val name: String = "",
    val priceStr: String = "",
    val sharesStr: String = "",
    val feeStr: String = "",
    val totalStr: String = "",
    val yearStr: String = "",
    val monthStr: String = "",
    val dayStr: String = "",
    val dateError: Boolean = false,
    val symbolError: Boolean = false,
    val priceError: Boolean = false,
    val sharesError: Boolean = false,
    val feeError: Boolean = false,
    val totalError: Boolean = false,
    val note: String = "",
    val searchResults: List<Pair<String, String>> = emptyList(),
    val isCashManagementEnabled: Boolean = false
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateTransactionUseCase: CalculateTransactionUseCase,
    private val dateProvider: DateProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val NAV_ARG_NONE = "none"
        private const val NAV_ARG_NULL = "null"
    }

    private val initialSymbol: String? = savedStateHandle.get<String>(AllScreens.ARG_SYMBOL)
        .let { if (it == NAV_ARG_NONE || it == NAV_ARG_NULL) null else it }
    private var searchJob: Job? = null
    
    val transactionId: Long = savedStateHandle.get<Long>(AllScreens.TRANSACTIONS_ID) ?: -1L

    var uiState by mutableStateOf(AddTransactionUiState())
        private set

    private var settingFeeRate = MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble()
    private var settingDiscount = MarketConstants.Taiwan.DISCOUNT.toDouble()
    private var settingMinFee = MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()

    init {
        val today = dateProvider.today()
        uiState = uiState.copy(
            symbol = initialSymbol ?: "",
            displaySymbol = initialSymbol?.substringBefore(".") ?: "",
            isSymbolReadOnly = initialSymbol != null,
            yearStr = today.year.toString(),
            monthStr = formatToTwoDigits(today.month.number),
            dayStr = formatToTwoDigits(today.dayOfMonth)
        )

        viewModelScope.launch {
            val accountFlow = settingsRepository.currentAccountIdFlow.flatMapLatest { id ->
                repository.getAccountFlowById(id)
            }

            kotlinx.coroutines.flow.combine(
                settingsRepository.twSettingsFlow,
                accountFlow
            ) { settings, account ->
                settingFeeRate = settings.feeRate.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble()
                settingDiscount = settings.discount.toDoubleOrNull() ?: MarketConstants.Taiwan.DISCOUNT.toDouble()
                settingMinFee = settings.minFee.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()
                
                uiState = uiState.copy(isCashManagementEnabled = account?.isCashManagementEnabled ?: false)
                
                // 當設定載入完成後，觸發一次重算，確保初始顯示正確
                onPriceOrSharesChanged()
            }.collect {}
        }

        if (transactionId == -1L && initialSymbol == "CASH") {
            uiState = uiState.copy(type = TransactionType.DEPOSIT)
        }

        if (transactionId != -1L) {
            loadTransactionData()
        } else {
            uiState = uiState.copy(isLoading = false)
            initialSymbol?.let { fetchNameForSymbol(it) }
        }
    }

    fun onTypeChange(newType: TransactionType) {
        uiState = uiState.copy(type = newType)
        // 切換類型時，如果是入金/出金/調整且代號是 CASH，則強制鎖定
        if (uiState.symbol == "CASH" && (newType != TransactionType.DEPOSIT && newType != TransactionType.WITHDRAW && newType != TransactionType.ADJUSTMENT)) {
             // 其實在 UI 層已經過濾了，但保險起見這裡可以做處理，或者維持現狀
        }
        onPriceOrSharesChanged()
    }

    fun onNoteChange(newNote: String) {
        uiState = uiState.copy(note = newNote)
    }

    fun onYearChange(newYear: String) {
        uiState = uiState.copy(yearStr = newYear.filter { it.isDigit() }, dateError = false)
    }

    fun onMonthChange(newMonth: String) {
        uiState = uiState.copy(monthStr = newMonth.filter { it.isDigit() }, dateError = false)
    }

    fun onDayChange(newDay: String) {
        uiState = uiState.copy(dayStr = newDay.filter { it.isDigit() }, dateError = false)
    }

    fun onSymbolChange(newSymbol: String) {
        uiState = uiState.copy(displaySymbol = newSymbol, symbolError = false, name = "")

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (newSymbol.isBlank()) {
                uiState = uiState.copy(searchResults = emptyList())
                return@launch
            }
            delay(EnterConstants.DEBOUNCE_DELAY)
            // 搜尋時使用使用者輸入的內容
            val results = repository.searchStocks(newSymbol)
            uiState = uiState.copy(searchResults = results)
        }
    }

    private fun fetchNameForSymbol(query: String) {
        viewModelScope.launch {
            val results = repository.searchStocks(query)
            val match = results.find { it.first == query } ?: results.firstOrNull()
            if (match != null) {
                uiState = uiState.copy(name = match.second)
            }
        }
    }

    fun onResultSelected(resSymbol: String, resDisplaySymbol: String, resName: String) {
        uiState = uiState.copy(
            symbol = resSymbol,
            displaySymbol = resDisplaySymbol,
            name = resName,
            searchResults = emptyList()
        )
    }

    fun selectFirstStock(): Boolean {
        val first = uiState.searchResults.firstOrNull()
        return if (first != null) {
            val (s, n) = first
            val displaySymbol = s.substringBefore(".")
            onResultSelected(s, displaySymbol, n)
            true
        } else {
            false
        }
    }

    fun onPriceChange(newPrice: String) {
        uiState = uiState.copy(priceStr = newPrice, priceError = false)
        onPriceOrSharesChanged()
    }

    fun onSharesChange(newShares: String) {
        uiState = uiState.copy(sharesStr = newShares, sharesError = false)
        onPriceOrSharesChanged()
    }

    fun onTotalChange(newTotal: String) {
        uiState = uiState.copy(totalStr = newTotal, totalError = false)
    }

    fun saveTransaction(onSuccess: () -> Unit) {
        val s = uiState
        val isCashType = s.type == TransactionType.DEPOSIT || s.type == TransactionType.WITHDRAW || s.type == TransactionType.ADJUSTMENT
        val isSymbolInvalid = !isCashType && s.symbol.isBlank()
        val price = s.priceStr.toDoubleOrNull() ?: 0.0
        val inputShares = s.sharesStr.toIntOrNull() ?: 0
        val fee = s.feeStr.toDoubleOrNull() ?: 0.0
        val inputTotal = s.totalStr.toDoubleOrNull() ?: 0.0

        val finalShares = if (s.type == TransactionType.STOCK_DIVIDEND) inputTotal.toInt() else inputShares
        val validDate = DateFormatter.parseDate(s.yearStr, s.monthStr, s.dayStr)
        val isDateInvalid = validDate == null

        val isPriceInvalid = when (s.type) {
            TransactionType.BUY, TransactionType.SELL -> price <= 0.0
            else -> false
        }
        val isSharesInvalid = when (s.type) {
            TransactionType.BUY, TransactionType.SELL, TransactionType.STOCK_DIVIDEND, TransactionType.CAPITAL_REDUCTION, TransactionType.SPLIT -> inputShares <= 0
            else -> false
        }
        val isFeeInvalid = fee < 0.0
        val isTotalInvalid = inputTotal <= 0.0

        uiState = s.copy(
            dateError = isDateInvalid,
            symbolError = isSymbolInvalid,
            priceError = isPriceInvalid,
            sharesError = isSharesInvalid,
            feeError = isFeeInvalid,
            totalError = isTotalInvalid
        )

        if (!isSymbolInvalid && !isPriceInvalid && !isSharesInvalid && !isFeeInvalid && !isTotalInvalid && !isDateInvalid) {
            viewModelScope.launch {
                val currentAccId = settingsRepository.currentAccountIdFlow.first()
                val cashEnabled = settingsRepository.isCashManagementEnabledFlow.first()
                
                val result = calculateTransactionUseCase(
                    CalculateTransactionUseCase.Params(
                        type = s.type,
                        price = price,
                        shares = finalShares.toDouble(),
                        feeRate = settingFeeRate,
                        discount = settingDiscount,
                        minFee = settingMinFee,
                        customFee = fee,
                        symbol = s.symbol
                    )
                )

                // 條件式手續費：如果現金管理關閉，手續費強製為 0
                val finalFee = if (cashEnabled || isCashType) fee + result.tax else 0.0

                val newItem = TransactionItem(
                    id = if (transactionId == -1L) 0L else transactionId,
                    accountId = currentAccId,
                    type = s.type,
                    symbol = if (isCashType) "CASH" else s.symbol,
                    name = if (isCashType) "現金" else s.name.ifEmpty { s.symbol },
                    price = price,
                    shares = when (s.type) {
                        TransactionType.CAPITAL_REDUCTION -> -(inputShares - inputTotal.toInt())
                        TransactionType.SPLIT -> inputTotal.toInt() - inputShares
                        else -> finalShares
                    },
                    participatingShares = if (s.type == TransactionType.STOCK_DIVIDEND || s.type == TransactionType.SPLIT || s.type == TransactionType.CAPITAL_REDUCTION) inputShares else 0,
                    date = validDate!!,
                    fee = finalFee,
                    dividend = if (s.type == TransactionType.DIVIDEND) inputTotal else 0.0,
                    multiplier = 1.0,
                    total = if (s.type == TransactionType.CAPITAL_REDUCTION) price * inputShares else inputTotal,
                    note = s.note
                )
                repository.upsert(newItem)
                onSuccess()
            }
        }
    }

    fun onPriceOrSharesChanged() {
        val p = uiState.priceStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val shares = uiState.sharesStr.replace(",", "").toDoubleOrNull() ?: 0.0

        if (p > 0 && shares > 0) {
            val result = calculateTransactionUseCase(
                CalculateTransactionUseCase.Params(
                    type = uiState.type,
                    price = p,
                    shares = shares,
                    feeRate = settingFeeRate,
                    discount = settingDiscount,
                    minFee = settingMinFee,
                    symbol = uiState.symbol
                )
            )

                uiState = when (uiState.type) {
                    TransactionType.BUY, TransactionType.SELL, TransactionType.CAPITAL_REDUCTION -> {
                        uiState.copy(
                            feeStr = result.fee.roundToInt().toString(),
                            totalStr = if (result.finalTotal > 0) result.finalTotal.toLong().toString() else "",
                            totalError = false
                        )
                    }
                    TransactionType.DIVIDEND -> {
                        uiState.copy(
                            totalStr = if (result.finalTotal > 0) result.finalTotal.toLong().toString() else "",
                            totalError = false
                        )
                    }
                    TransactionType.STOCK_DIVIDEND -> {
                        uiState.copy(
                            totalStr = result.stockDividendShares.toString(),
                            totalError = false
                        )
                    }
                    TransactionType.SPLIT -> {
                        uiState.copy(
                            totalStr = result.stockDividendShares.toString(),
                            totalError = false
                        )
                    }
                    TransactionType.DEPOSIT, TransactionType.WITHDRAW, TransactionType.ADJUSTMENT -> {
                        uiState.copy(
                            totalStr = if (result.finalTotal > 0) result.finalTotal.toLong().toString() else "",
                            totalError = false
                        )
                    }
                }
        } else {
            uiState = uiState.copy(feeStr = "", totalStr = "")
        }
    }

    fun onFeeChanged(newFee: String) {
        uiState = uiState.copy(feeStr = newFee, feeError = false)
        val p = uiState.priceStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val shares = uiState.sharesStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val customFee = newFee.replace(",", "").toDoubleOrNull() ?: 0.0

        if (p > 0 && shares > 0 && uiState.type != TransactionType.STOCK_DIVIDEND) {
            val result = calculateTransactionUseCase(
                CalculateTransactionUseCase.Params(
                    type = uiState.type,
                    price = p,
                    shares = shares,
                    feeRate = settingFeeRate,
                    discount = settingDiscount,
                    minFee = settingMinFee,
                    customFee = customFee,
                    symbol = uiState.symbol
                )
            )
            uiState = uiState.copy(
                totalStr = if (result.finalTotal > 0) result.finalTotal.toLong().toString() else "",
                totalError = false
            )
        }
    }

    private fun calculateFinalTotal(subtotal: Double, fee: Double) {
        // 此方法已整合進 UseCase，稍後將刪除
    }

    private fun loadTransactionData() {
        viewModelScope.launch {
            repository.getTransactionItemById(transactionId)?.let { item ->
                val parsedDate = item.date
                
                var newState = uiState.copy(
                    type = item.type,
                    symbol = item.symbol,
                    name = item.name,
                    yearStr = parsedDate.year.toString(),
                    monthStr = formatToTwoDigits(parsedDate.month.number),
                    dayStr = formatToTwoDigits(parsedDate.dayOfMonth),
                    priceStr = if (item.price > 0.0) item.price.toString().removeSuffix(".0") else "",
                    sharesStr = if (item.shares > 0) item.shares.toString() else "",
                    feeStr = if (item.fee > 0.0) item.fee.toLong().toString() else ""
                )

                newState = when (item.type) {
                    TransactionType.DIVIDEND -> newState.copy(totalStr = if (item.dividend > 0.0) item.dividend.toLong().toString() else "")
                    TransactionType.STOCK_DIVIDEND -> newState.copy(
                        totalStr = if (item.shares > 0) item.shares.toString() else "",
                        sharesStr = if (item.participatingShares > 0) item.participatingShares.toString() else ""
                    )
                    TransactionType.DEPOSIT, TransactionType.WITHDRAW, TransactionType.ADJUSTMENT -> newState.copy(
                        totalStr = if (item.total > 0.0) item.total.toLong().toString() else ""
                    )
                    else -> newState.copy(totalStr = if (item.total > 0.0) item.total.toLong().toString() else "")
                }
                uiState = newState.copy(isLoading = false, note = item.note)
            }
        }
    }

    fun updateDateFromMillis(millis: Long) {
        val date = dateProvider.fromEpochMillis(millis)
        uiState = uiState.copy(
            yearStr = date.year.toString(),
            monthStr = formatToTwoDigits(date.month.number),
            dayStr = formatToTwoDigits(date.dayOfMonth),
            dateError = false
        )
    }

    fun deleteTransaction(onSuccess: () -> Unit) {
        if (transactionId == -1L && initialSymbol == "CASH") {
            uiState = uiState.copy(type = TransactionType.DEPOSIT)
        }

        if (transactionId != -1L) {
            viewModelScope.launch {
                repository.deleteTransactionById(transactionId)
                onSuccess()
            }
        }
    }

    fun clearSymbolError() { uiState = uiState.copy(symbolError = false) }
    fun clearPriceError() { uiState = uiState.copy(priceError = false) }
    fun clearSharesError() { uiState = uiState.copy(sharesError = false) }
    fun clearFeeError() { uiState = uiState.copy(feeError = false) }
    fun clearTotalError() { uiState = uiState.copy(totalError = false) }
}
