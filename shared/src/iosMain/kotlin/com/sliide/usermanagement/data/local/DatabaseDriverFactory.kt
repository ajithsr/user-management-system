package com.sliide.usermanagement.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.sliide.usermanagement.data.local.db.UserDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(UserDatabase.Schema, "users.db")
}
