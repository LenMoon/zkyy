package com.example.zkyy

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.trace.LBSTraceClient

class MyApplication: Application() {
    lateinit var INSTANCE:Application
    override fun onCreate() {
        INSTANCE = this
        super.onCreate()
        LBSTraceClient.setAgreePrivacy(this,true)

        SDKInitializer.setAgreePrivacy(this,true)
        LocationClient.setAgreePrivacy(true)
        SDKInitializer.initialize(this)
        SDKInitializer.setCoordType(CoordType.BD09LL)

    }
}