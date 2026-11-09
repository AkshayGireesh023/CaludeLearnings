package com.kharcha.app

import android.app.Application
import com.kharcha.app.data.AppDatabase
import com.kharcha.app.data.Repository
import com.kharcha.app.data.SettingsStore
import com.kharcha.app.ingest.BudgetAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KharchaApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: Repository
        private set
    lateinit var alerts: BudgetAlerts
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this), SettingsStore(this))
        alerts = BudgetAlerts(this, repository)
        alerts.createChannel()
    }
}
