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
import com.example.stock.core.data.model.Account
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
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
    val taxStr: String = "",
    val totalStr: String = "",
    val yearStr: String = "",
    val monthStr: String = "",
    val dayStr: String = "",
    val dateError: Boolean = false,
    val symbolError: Boolean = false,
    val priceError: Boolean = false,
    val sharesError: Boolean = false,
    val feeError: Boolean = false,
    val taxError: Boolean = false,
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
    private val initialName: String? = savedStateHandle.get<String>(AllScreens.ARG_NAME)
        .let { if (it == NAV_ARG_NONE || it == NAV_ARG_NULL) null else it }
    private var searchJob: Job? = null
    
    val transactionId: Long = savedStateHandle.get<Long>(AllScreens.TRANSACTIONS_ID) 
        ?: savedStateHandle.get<String>(AllScreens.TRANSACTIONS_ID)?.toLongOrNull() 
        ?: -1L

    private var isInitialLoading = true

    var uiState by mutableStateOf(AddTransactionUiState())
        private set

    private var settingFeeRate = MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble()
    private var settingDiscount = MarketConstants.Taiwan.DISCOUNT.toDouble()
    private var settingMinFee = MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()

    init {
        if (transactionId != -1L) {
            loadTransactionData()
        } else {
            val today = dateProvider.today()
            
            uiState = uiState.copy(
                symbol = initialSymbol ?: "",
                displaySymbol = initialSymbol?.substringBefore(".") ?: "",
                isSymbolReadOnly = initialSymbol != null,
                name = initialName ?: "",
                yearStr = today.year.toString(),
                monthStr = formatToTwoDigits(today.month.number),
                dayStr = formatToTwoDigits(today.dayOfMonth),
                isLoading = false
            )
            isInitialLoading = false
            if (initialName == null) {
                initialSymbol?.let { fetchNameForSymbol(it) }
            }
        }

        viewModelScope.launch {
            val accountFlow = settingsRepository.currentAccountIdFlow.flatMapLatest { id ->
                repository.getAccountFlowById(id)
            }

            combine(
                settingsRepository.twSettingsFlow,
                accountFlow
            ) { settings, account ->
                settingFeeRate = settings.feeRate.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_FEE_RATE.toDouble()
                settingDiscount = settings.discount.toDoubleOrNull() ?: MarketConstants.Taiwan.DISCOUNT.toDouble()
                settingMinFee = settings.minFee.toDoubleOrNull() ?: MarketConstants.Taiwan.DEFAULT_MIN_FEE.toDouble()
                
                uiState = uiState.copy(isCashManagementEnabled = account?.isCashManagementEnabled ?: false)
                
                if (!isInitialLoading && transactionId == -1L) {
                    onPriceOrSharesChanged()
                }
            }.collect {}
        }

        if (transactionId == -1L && initialSymbol == "CASH") {
            uiState = uiState.copy(type = TransactionType.DEPOSIT)
        }
    }

    fun onTypeChange(newType: TransactionType) {
        uiState = uiState.copy(
            type = newType,
            feeStr = if (newType == TransactionType.BUY || newType == TransactionType.SELL || newType == TransactionType.CAPITAL_REDUCTION) uiState.feeStr else "",
            taxStr = if (newType == TransactionType.SELL) uiState.taxStr else "",
            priceStr = if (newType == TransactionType.DEPOSIT || newType == TransactionType.WITHDRAW || newType == TransactionType.ADJUSTMENT) "" else uiState.priceStr,
            sharesStr = if (newType == TransactionType.DEPOSIT || newType == TransactionType.WITHDRAW || newType == TransactionType.ADJUSTMENT) "" else uiState.sharesStr
        )
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
        onPriceOrSharesChanged()
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
        if (!isInitialLoading) onPriceOrSharesChanged()
    }

    fun onSharesChange(newShares: String) {
        uiState = uiState.copy(sharesStr = newShares, sharesError = false)
        if (!isInitialLoading) onPriceOrSharesChanged()
    }

    fun onTotalChange(newTotal: String) {
        uiState = uiState.copy(totalStr = newTotal, totalError = false)
    }

    fun saveTransaction(onSuccess: () -> Unit) {
        val s = uiState
        val isCashType = s.type == TransactionType.DEPOSIT || s.type == TransactionType.WITHDRAW || s.type == TransactionType.ADJUSTMENT
        val isSymbolInvalid = !isCashType && s.symbol.isBlank()
        val price = s.priceStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val inputShares = s.sharesStr.replace(",", "").toIntOrNull() ?: 0
        val fee = s.feeStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val tax = s.taxStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val inputTotal = s.totalStr.replace(",", "").toDoubleOrNull() ?: 0.0

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
        val isTaxInvalid = tax < 0.0
        val isTotalInvalid = inputTotal <= 0.0

        uiState = s.copy(
            dateError = isDateInvalid,
            symbolError = isSymbolInvalid,
            priceError = isPriceInvalid,
            sharesError = isSharesInvalid,
            feeError = isFeeInvalid,
            taxError = isTaxInvalid,
            totalError = isTotalInvalid
        )

        if (!isSymbolInvalid && !isPriceInvalid && !isSharesInvalid && !isFeeInvalid && !isTaxInvalid && !isTotalInvalid && !isDateInvalid) {
            viewModelScope.launch {
                val settingsAccId = settingsRepository.currentAccountIdFlow.first()
                var targetAccount = repository.getAccountById(settingsAccId) ?: repository.allAccounts.first().firstOrNull()
                
                if (targetAccount == null) {
                    val defaultAccount = Account(name = "預設帳戶", currency = "TWD")
                    repository.upsertAccount(defaultAccount)
                    targetAccount = repository.allAccounts.first().firstOrNull()
                }
                
                val currentAccId = targetAccount?.id ?: 1L
                
                if (settingsAccId != currentAccId) {
                    settingsRepository.setCurrentAccountId(currentAccId)
                }

                val newItem = TransactionItem(
                    id = if (transactionId == -1L) 0L else transactionId,
                    accountId = currentAccId,
                    type = s.type,
                    symbol = if (isCashType) "CASH" else s.symbol,
                    name = if (isCashType) "現金" else s.name.ifEmpty { s.symbol },
                    price = price,
                    shares = when (s.type) {
                        TransactionType.CAPITAL_REDUCTION, 
                        TransactionType.SPLIT -> inputTotal.toInt() - inputShares
                        else -> finalShares
                    },
                    participatingShares = if (s.type == TransactionType.STOCK_DIVIDEND || s.type == TransactionType.SPLIT || s.type == TransactionType.CAPITAL_REDUCTION) inputShares else 0,
                    date = validDate,
                    fee = fee,
                    tax = tax,
                    dividend = if (s.type == TransactionType.DIVIDEND) inputTotal else 0.0,
                    total = when (s.type) {
                        TransactionType.CAPITAL_REDUCTION -> (price * inputShares) - fee
                        TransactionType.STOCK_DIVIDEND, TransactionType.SPLIT -> 0.0
                        else -> inputTotal
                    },
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
                TransactionType.BUY -> {
                    uiState.copy(
                        feeStr = result.fee.roundToInt().toString(),
                        taxStr = "",
                        totalStr = if (result.finalTotal > 0) result.finalTotal.toString().removeSuffix(".0") else uiState.totalStr,
                        totalError = false
                    )
                }
                TransactionType.SELL -> {
                    uiState.copy(
                        feeStr = result.fee.roundToInt().toString(),
                        taxStr = result.tax.roundToInt().toString(),
                        totalStr = if (result.finalTotal > 0) result.finalTotal.toString().removeSuffix(".0") else uiState.totalStr,
                        totalError = false
                    )
                }
                TransactionType.DIVIDEND -> {
                    uiState.copy(
                        feeStr = if (result.fee > 0) result.fee.roundToInt().toString() else uiState.feeStr,
                        taxStr = "",
                        totalStr = if (result.finalTotal > 0) result.finalTotal.toString().removeSuffix(".0") else uiState.totalStr,
                        totalError = false
                    )
                }
                TransactionType.STOCK_DIVIDEND, TransactionType.SPLIT, TransactionType.CAPITAL_REDUCTION -> {
                    uiState.copy(
                        feeStr = if (uiState.type == TransactionType.CAPITAL_REDUCTION) result.fee.roundToInt().toString() else "",
                        taxStr = "",
                        totalStr = result.stockDividendShares.toString(),
                        totalError = false
                    )
                }
                else -> uiState
            }
        } else {
            if (uiState.type != TransactionType.DEPOSIT && 
                uiState.type != TransactionType.WITHDRAW && 
                uiState.type != TransactionType.ADJUSTMENT) {
                uiState = uiState.copy(feeStr = "", taxStr = "", totalStr = "")
            }
        }
    }

    fun onFeeChanged(newFee: String) {
        uiState = uiState.copy(feeStr = newFee, feeError = false)
        if (isInitialLoading) return

        val p = uiState.priceStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val shares = uiState.sharesStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val customFee = newFee.replace(",", "").toDoubleOrNull() ?: 0.0

        if (p > 0 && shares > 0 && uiState.type != TransactionType.STOCK_DIVIDEND && 
            uiState.type != TransactionType.SPLIT && uiState.type != TransactionType.CAPITAL_REDUCTION) {
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
                totalStr = if (result.finalTotal > 0) result.finalTotal.toString().removeSuffix(".0") else "",
                totalError = false
            )
        }
    }

    fun onTaxChanged(newTax: String) {
        uiState = uiState.copy(taxStr = newTax, taxError = false)
        if (isInitialLoading) return

        val p = uiState.priceStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val shares = uiState.sharesStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val customFee = uiState.feeStr.replace(",", "").toDoubleOrNull() ?: 0.0
        val customTax = newTax.replace(",", "").toDoubleOrNull() ?: 0.0

        if (p > 0 && shares > 0 && uiState.type == TransactionType.SELL) {
            val result = calculateTransactionUseCase(
                CalculateTransactionUseCase.Params(
                    type = uiState.type,
                    price = p,
                    shares = shares,
                    feeRate = settingFeeRate,
                    discount = settingDiscount,
                    minFee = settingMinFee,
                    customFee = customFee,
                    customTax = customTax,
                    symbol = uiState.symbol
                )
            )
            
            uiState = uiState.copy(
                totalStr = if (result.finalTotal > 0) result.finalTotal.toString().removeSuffix(".0") else "",
                totalError = false
            )
        }
    }


    private fun loadTransactionData() {
        viewModelScope.launch {
            val item = repository.getTransactionItemById(transactionId)
            if (item != null) {
                val parsedDate = item.date
                var newState = uiState.copy(
                    isLoading = false,
                    type = item.type,
                    symbol = item.symbol,
                    displaySymbol = item.symbol.substringBefore("."),
                    name = item.name,
                    yearStr = parsedDate.year.toString(),
                    monthStr = formatToTwoDigits(parsedDate.month.number),
                    dayStr = formatToTwoDigits(parsedDate.dayOfMonth),
                    note = item.note,
                    priceStr = if (item.price == 0.0) "" else item.price.toString().removeSuffix(".0"),
                    feeStr = if (item.fee == 0.0) "" else item.fee.toString().removeSuffix(".0"),
                    taxStr = if (item.tax == 0.0) "" else item.tax.toString().removeSuffix(".0"),
                    isSymbolReadOnly = true
                )

                newState = when (item.type) {
                    TransactionType.BUY, TransactionType.SELL -> {
                        newState.copy(
                            sharesStr = item.shares.toString(),
                            totalStr = item.total.toString().removeSuffix(".0"),
                            feeStr = item.fee.toString().removeSuffix(".0")
                        )
                    }
                    TransactionType.DIVIDEND -> {
                        newState.copy(
                            sharesStr = if (item.shares == 0) "" else item.shares.toString(),
                            totalStr = item.dividend.toString().removeSuffix(".0")
                        )
                    }
                    TransactionType.STOCK_DIVIDEND -> {
                        newState.copy(
                            sharesStr = item.participatingShares.toString(),
                            totalStr = item.shares.toString()
                        )
                    }
                    TransactionType.CAPITAL_REDUCTION -> {
                        newState.copy(
                            sharesStr = item.participatingShares.toString(),
                            totalStr = (item.participatingShares + item.shares).toString()
                        )
                    }
                    TransactionType.SPLIT -> {
                        newState.copy(
                            sharesStr = item.participatingShares.toString(),
                            totalStr = (item.participatingShares + item.shares).toString()
                        )
                    }
                    TransactionType.DEPOSIT, TransactionType.WITHDRAW, TransactionType.ADJUSTMENT -> {
                        newState.copy(
                            totalStr = item.total.toString().removeSuffix(".0")
                        )
                    }
                }
                
                uiState = newState
                isInitialLoading = false
            } else {
                isInitialLoading = false
                uiState = uiState.copy(isLoading = false)
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
    fun clearTaxError() { uiState = uiState.copy(taxError = false) }
    fun clearTotalError() { uiState = uiState.copy(totalError = false) }
}
