package com.example.zkyy

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

interface OnLocationChange {
    fun locationChange(longitude:Float,latitude:Float,speed:Float)
}

class MyLocationObserver(
    private val _service: Context
) : LifecycleObserver {

    private val service = _service

    var myLocationListener:MyLocationListener? = null
    private var locationManager: LocationManager? = null


    @SuppressLint("MissingPermission")
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private fun startGetLocation(){
        Log.d("ning---","startGetLocation")
        locationManager = service.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        myLocationListener = MyLocationListener()
        myLocationListener?.let {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                100,//每0.1秒获取一次
                1f,//每移动1米获取一次
                it
            )
        }

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun stopGetLocation(){
        Log.d("ning---","stopGetLocation")
        myLocationListener?.let { locationManager?.removeUpdates(it) }
    }

    class MyLocationListener : LocationListener {

        companion object {
            var locationChangeListener: OnLocationChange? = null
        }


        override fun onLocationChanged(location: Location) {
            Log.d("ning---", "onLocationChanged: $location")
            locationChangeListener?.let {
                setData(it, location.longitude.toFloat(), location.latitude.toFloat(), location.speed)
            }
        }

        private fun setData(locationChange: OnLocationChange, longi: Float, lati: Float, speed:Float) {
            locationChange.locationChange(longi, lati, speed)
        }

    }
}
