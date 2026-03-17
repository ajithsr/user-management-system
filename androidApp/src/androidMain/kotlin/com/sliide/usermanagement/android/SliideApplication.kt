package com.sliide.usermanagement.android

import android.app.Application
import com.sliide.usermanagement.connectivity.NetworkMonitor
import com.sliide.usermanagement.data.local.DatabaseDriverFactory
import com.sliide.usermanagement.di.initKoin
import org.koin.android.ext.koin.androidContext

class SliideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            driverFactory  = DatabaseDriverFactory(context = this),
            networkMonitor = NetworkMonitor(context = this),
            appDeclaration = {
                androidContext(this@SliideApplication)
            }
        )
    }
}
