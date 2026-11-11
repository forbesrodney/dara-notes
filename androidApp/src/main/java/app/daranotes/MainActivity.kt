package app.daranotes

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.daranotes.ui.App

class DaraNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // JGit reads ~/.gitconfig; give it a writable home inside app storage.
        System.setProperty("user.home", filesDir.absolutePath)
        SyncScheduler.schedulePeriodic(this, AndroidSettings(this))
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(AndroidPlatform(application)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }
}
