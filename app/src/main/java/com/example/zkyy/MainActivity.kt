package com.example.zkyy

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.*
import com.example.zkyy.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "鹰眼"
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var mMapView:MapView
    private lateinit var mBaiduMap:BaiduMap
    var mLocationClient:LocationClient = LocationClient(this);






    inner class MyLocationListener : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation) {
            //mapView 销毁后不在处理新接收的位置
            Log.d(TAG,"收到位置数据$location")
            if (location == null || mMapView == null) {
                return
            }
            val locData = MyLocationData.Builder()
                .accuracy(location.radius) // 此处设置开发者获取到的方向信息，顺时针0-360
                .direction(location.direction).latitude(location.latitude)
                .longitude(location.longitude).build()
            mBaiduMap.setMyLocationData(locData)
        }
    }


    suspend fun locationAuto() {

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mMapView = binding.bmapView
        mBaiduMap = mMapView.map
        setSupportActionBar(null)

        val option = LocationClientOption()
        option.setOpenGps(true) // 打开gps
        option.setCoorType("bd09ll") // 设置坐标类型
        option.setScanSpan(1000)
        mLocationClient.setLocOption(option)
        mLocationClient.registerLocationListener(MyLocationListener())
        mLocationClient.start()

        mMapView.map.isMyLocationEnabled = true

        mBaiduMap.setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.COMPASS,
            true,null))

        val builder: MapStatus.Builder = MapStatus.Builder()
        builder.zoom(17.0f)
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))


        mBaiduMap.setOnMapTouchListener {
            mBaiduMap.setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL,
                true,null))
            Handler.createAsync(Looper.getMainLooper())
                .postDelayed({
                    mBaiduMap.setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.COMPASS,
                        true,null))
                },5000)
        }


    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return false
    }

    override fun onResume() {
        super.onResume()
        mMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mMapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView.onDestroy()
    }

}