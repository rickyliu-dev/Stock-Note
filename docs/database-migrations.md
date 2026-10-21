# 資料庫遷移規範

目前資料庫版本：25（Room，schema 匯出在 `app/schemas/`）。

## 為什麼要有這份規範
這個 App 記錄的是使用者的交易資料，升級時不能悄悄遺失。
因此 release 版不使用 `fallbackToDestructiveMigration()`：找不到遷移規則時，
寧可啟動失敗，也不清空資料。debug 版為了開發方便，保留清空重建。

## 每次修改資料表時
1. 修改 Entity（新增、刪除欄位，或改型別）。
2. 把 `@Database(version = N)` 加 1。
3. 寫遷移：
    - 只是新增欄位：用 `@AutoMigration(from = N, to = N + 1)`。
    - 刪除欄位、改名、改型別、拆表：手寫 `Migration(N, N + 1)`。
4. 編譯一次，讓 Room 產生新的 `N+1.json`，並納入版控。
5. 用上一版 release 的真實資料覆蓋安裝，確認資料還在、金額沒變。

## 不要做的事
- 不要修改已釋出版本的 schema JSON。
- 不要在版本號不變的情況下改 Entity，現有資料庫的結構指紋（identityHash）會對不上。
- 不要在 release 版加回 `fallbackToDestructiveMigration()`。

## 版本紀錄
| DB 版本 | 說明 |
|---|---|
| 25 | 目前基準版本 |