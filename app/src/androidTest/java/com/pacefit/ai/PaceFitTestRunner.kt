package com.pacefit.ai

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import java.io.File

/** UI/service tests never read or mutate the user's production SQLite/photos. */
class PaceFitTestApplication : PaceFitApplication() {
    override val databaseName get() = "pacefit-instrumentation.db"
    override val dataRoot get() = File(filesDir, "pacefit-instrumentation")
}
class PaceFitTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, PaceFitTestApplication::class.java.name, context)
}
