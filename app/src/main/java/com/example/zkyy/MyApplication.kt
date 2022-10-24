package com.example.zkyy

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer

class MyApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        SDKInitializer.setAgreePrivacy(this,true)
        LocationClient.setAgreePrivacy(true)
        SDKInitializer.initialize(this)
        SDKInitializer.setCoordType(CoordType.BD09LL)

    }
}