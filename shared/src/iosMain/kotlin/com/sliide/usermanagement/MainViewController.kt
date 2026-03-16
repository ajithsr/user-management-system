package com.sliide.usermanagement

import androidx.compose.ui.window.ComposeUIViewController
import com.sliide.usermanagement.data.local.DatabaseDriverFactory
import com.sliide.usermanagement.di.initKoin
import com.sliide.usermanagement.presentation.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin(driverFactory = DatabaseDriverFactory())
    return ComposeUIViewController { App() }
}
