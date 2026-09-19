package app.cursor.android

import android.app.Application
import app.cursor.android.streaming.RunResumeCoordinator

class CursorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RunResumeCoordinator.start(this)
    }
}
