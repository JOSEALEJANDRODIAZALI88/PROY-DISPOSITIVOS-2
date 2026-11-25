package com.pacefit.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pacefit.ai.ui.PaceFitRoot
import com.pacefit.ai.ui.ProfileViewModel
import com.pacefit.ai.ui.LocalViewModel
import com.pacefit.ai.ui.ActivityViewModel
import com.pacefit.ai.ui.CloudViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as PaceFitApplication
            val profile: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory(app.healthRepository))
            val local: LocalViewModel = viewModel(factory = LocalViewModel.Factory(app, app.localRepository))
            val activity: ActivityViewModel = viewModel()
            val cloud: CloudViewModel = viewModel()
            PaceFitRoot(profile, local, activity, cloud)
        }
    }
}
