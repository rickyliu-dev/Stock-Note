package com.example.stock.core.util

import java.io.IOException

/** zip 中有不安全的檔名（路徑穿越、絕對路徑、子資料夾） */
class UnsafeZipEntryException(val entryName: String) : IOException("備份檔包含不安全的路徑：$entryName")
