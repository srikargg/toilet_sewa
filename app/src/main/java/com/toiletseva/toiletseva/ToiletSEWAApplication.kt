package com.toiletseva.toiletseva

import android.app.Application
import com.google.firebase.FirebaseApp

class ToiletSEWAApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        FirebaseApp.initializeApp(this)
    }
}