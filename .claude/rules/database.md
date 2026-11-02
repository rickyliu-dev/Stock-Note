---
paths:
  - "app/src/**/dataBase/**"
  - "app/src/**/*Database*.kt"
  - "app/src/**/*Dao*.kt"
  - "app/src/**/model/**"
  - "app/schemas/**"
---

# 資料庫規則

修改任何 Room Entity、DAO、@Database 或 schema 前，先讀 `docs/database-migrations.md` 並照做。

- 不准使用 `fallbackToDestructiveMigration()`。
- 改 Entity 一定要升版本號並提供遷移（新增欄位或表用 AutoMigration，其餘手寫 Migration）。
- 新的 schema JSON 要產生並保留，舊版本的 schema JSON 不准修改。
- StockDatabase（核心）與 HedgeDatabase（personal）版本號各自獨立，兩者都要在遷移規範文件記錄。
