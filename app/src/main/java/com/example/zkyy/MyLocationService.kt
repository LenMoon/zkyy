package com.example.zkyy

import android.util.Log
import androidx.lifecycle.LifecycleService

class MyLocationService : LifecycleService() {

    init {
        Log.d("ning","MyLocationService")
        val observer = MyLocationObserver(this)
        lifecycle.addObserver(observer)
    }

}
