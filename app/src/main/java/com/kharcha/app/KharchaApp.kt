package com.kharcha.app

import android.app.Application
import com.kharcha.app.data.AppDatabase
import com.kharcha.app.data.Repository
import com.kharcha.app.data.SettingsStore
import com.kharcha.app.ingest.BudgetAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KharchaApp : Application(), androidx.work.Configuration.Provider {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: Repository
        private set
    lateinit var alerts: BudgetAlerts
        private set
    lateinit var brain: com.kharcha.app.ai.ClaudeBrain
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this), SettingsStore(this))
        alerts = BudgetAlerts(this, repository)
        brain = com.kharcha.app.ai.ClaudeBrain(repository)
        alerts.createChannel()
        runCatching { com.kharcha.app.ingest.SyncWorker.schedule(this) }
    }

    // WorkManager initialises on demand from this, instead of from a startup provider.
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder().build()
}
