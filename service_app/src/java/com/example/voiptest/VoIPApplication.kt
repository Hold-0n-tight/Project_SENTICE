package com.example.voiptest

import android.app.Application

class VoIPApplication : Application() {


    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("pjsua2")
    }
}

