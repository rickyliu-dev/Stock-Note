# 資料庫遷移規範

本 App 有兩個 Room 資料庫，版本號各自獨立，schema 都匯出在 `app/schemas/` 並納入版控：

| 資料庫 | 手機上的檔名 | 存在於 | 目前版本 |
|---|---|---|---|
| `StockDatabase`（核心） | `stock_database` | basic、personal | 27 |
| `HedgeDatabase`（避險，`src/personal`） | `hedge_database` | 只有 personal | 1 |

下面的規範兩個資料庫都適用。

## 為什麼要有這份規範
這個 App 記錄的是使用者的交易資料，升級時不能悄悄遺失。
因此 debug 與 release 版都不使用 `fallbackToDestructiveMigration()`：找不到遷移規則時，
寧可啟動失敗，也不清空資料。debug 版行為與 release 相同，遷移只需測一次；
開發中若資料庫壞掉，請手動清除 App 資料或重新安裝。

## 每次修改資料表時
1. 修改 Entity（新增、刪除欄位，或改型別）。
2. 把 `@Database(version = N)` 加 1。
3. 寫遷移：
    - 只是新增欄位、索引或資料表：用 `@AutoMigration(from = N, to = N + 1)`。
    - 刪除欄位、改名、改型別、拆表：手寫 `Migration(N, N + 1)`。
4. 編譯一次，讓 Room 產生新的 `N+1.json`，並納入版控。
5. 用上一版 release 的真實資料覆蓋安裝，確認資料還在、金額沒變。

## 不要做的事
- 不要修改已釋出版本的 schema JSON。
- 不要在版本號不變的情況下改 Entity，現有資料庫的結構指紋（identityHash）會對不上。
- 不要加回 `fallbackToDestructiveMigration()`（debug 版也不要）。

## 版本紀錄：StockDatabase
| DB 版本 | 說明 |
|---|---|
| 25 | 基準版本（更舊的版本沒有遷移路徑，不支援直接升級） |
| 26 | transactions 新增 (accountId, symbol, date) 索引（AutoMigration） |
| 27 | 新增 asset_snapshots 資產走勢表，主鍵 (accountId, date)，刪除帳戶時一併刪除（AutoMigration） |

## HedgeDatabase

- 程式碼：`app/src/personal/java/com/example/stock/core/data/hedge/HedgeDatabase.kt`，
  schema：`app/schemas/com.example.stock.core.data.hedge.HedgeDatabase/`。
- 從第一版就照本規範走：不使用 destructive migration、改 Entity 要升版並提供遷移。
- 與 `StockDatabase` 之間無法建外鍵：`accountId` 對應核心 `accounts.id`，帳戶刪除時不會自動清除這裡的資料。
- 雲端備份為 `hedge_ledger.personal.db`／`hedge_ledger.personal.debug.db`；還原時雲端缺少此檔不算失敗（舊備份沒有它）。

### 版本紀錄：HedgeDatabase
| DB 版本 | 說明 |
|---|---|
| 1 | 建立 hedge_symbol_config（避險標的設定），主鍵 (accountId, symbol)；includeInExposure 預設 false、weightTimes100 預設 100 |
