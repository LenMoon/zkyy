package com.example.zkyy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.Sensor.*
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CalendarContract.Colors
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.*
import com.baidu.trace.LBSTraceClient
import com.baidu.trace.Trace
import com.baidu.trace.model.OnCustomAttributeListener
import com.baidu.trace.model.OnTraceListener
import com.baidu.trace.model.PushMessage
import com.example.zkyy.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit

import com.example.zkyy.databinding.FragmentCustomBinding

class MainActivity : AppCompatActivity()  {
    companion object {
        const val TAG = "鹰眼"
    }
    private lateinit var binding: FragmentCustomBinding
    private lateinit var mMapView:MapView
    private lateinit var mBaiduMap:BaiduMap
    private lateinit var mTraceClient:LBSTraceClient
    private val serviceId = 234710
    private  var flagThreshold = 1
    private  var flagCount = 20
    val cancelLocationAuto: Subject<Unit> = PublishSubject.create<Unit?>()
    var yyIsStart = false
    private val gatherInterval = 30
    private val packInterval = 200
    private lateinit var mTrace:Trace

    var GPS_enabled = false
    var GPS_Long = 0.0f
    var GPS_Lat = 0.0f
    var GPS_Speed = 0.0f

    @Volatile
    private var sensorData:SensorData = SensorData(0.0f, 0.0f, 0.0f, false,
        0.0f,0.0f,0.0f, false,
        0.0f,0.0f,0.0f, false,
        0.0f, 0.0f, 0.0f, false,
        0.0f, 0.0f, 0.0f, false)
    private var gyroscope:Subject<Float> = PublishSubject.create()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTraceClient = LBSTraceClient(applicationContext)

//        binding = ActivityMainBinding.inflate(layoutInflater)
        binding = FragmentCustomBinding.inflate(layoutInflater)

        setContentView(binding.root)
        mMapView = binding.bmapView
        mBaiduMap = mMapView.map
        setSupportActionBar(null)


        lifecycleScope.launchWhenCreated {
            initBdMap()
            initUserInfo()

            initTrace()

            initDataCollectService()

        }

        binding.startYy.setOnClickListener {
            lifecycleScope.launch {
                if (yyIsStart) {
                    closeYy()
                } else {
                    startYy()
                }
            }
        }
        binding.startYy.isClickable=false

        val leftFlag = binding.leftFlag
        val rightFlag = binding.rightFlag
        gyroscope.buffer(30).observeOn(AndroidSchedulers.mainThread())
            .subscribe {list->

                val isLeft = list.filter { it > flagThreshold }.size > flagCount
                val isRight = list.filter { it < -flagThreshold }.size > flagCount
                if (isLeft) {
                    Log.d(TAG, "左转")
                    leftFlag.visibility = View.VISIBLE
                } else {
                    leftFlag.visibility = View.INVISIBLE
                }
                if (isRight) {
                    Log.d(TAG, "右转")
                    rightFlag.visibility = View.VISIBLE
                } else {
                    rightFlag.visibility=View.INVISIBLE
                }

            }

    }

    private suspend fun initDataCollectService() {
        val dataCollectBinder = DataCollectService.bindService(this@MainActivity)
        dataCollectBinder.startCollectGps()
        // 启动采集传感器数据
        dataCollectBinder.startCollectSensor()
        dataCollectBinder.startCollectGps()
        dataCollectBinder.sensorDataObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                sensorData = it
                notifySensorDataUpdate()
            }


        dataCollectBinder.locationDataObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {location->
                //mapView 销毁后不在处理新接收的位置
                if (mMapView == null) {
                    return@subscribe
                }
                val locData = MyLocationData.Builder()
                    .accuracy(location.radius) // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(location.direction).latitude(location.latitude)
                    .longitude(location.longitude).speed(location.speed).build()

//            sensorData = sensorData.copy(longGPS = location.latitude.toFloat(), latGPS = location.latitude.toFloat(), speedGPS = location.speed)
                GPS_Long = location.longitude.toFloat()
                GPS_Lat = location.latitude.toFloat()
                GPS_Speed = location.speed
                Log.d(TAG,"经度:$GPS_Long 维度:$GPS_Lat 速度:$GPS_Speed")
                mBaiduMap.setMyLocationData(locData)
            }

    }


    private fun initTrace() {
        mTrace = Trace(serviceId.toLong(),
            (getUserInfo()?.name ?: ("未知" ))+Build.BOARD, false)
        mTraceClient.setInterval(gatherInterval,packInterval)
        mTraceClient.startTrace(mTrace,MyTraceListener())
        mTraceClient.setOnCustomAttributeListener(MyOnCustomAttributeListener())
    }


    inner class MyOnCustomAttributeListener : OnCustomAttributeListener {
        override fun onTrackAttributeCallback(): Map<String, String> {
            Log.d(TAG, "tracecallback,不带参数")
            return formatSensorData(getSensorData())
        }

        override fun onTrackAttributeCallback(p0: Long): Map<String, String> {
            Log.d(TAG, "tracecallback,带参数" + p0)
            return formatSensorData(getSensorData())
        }
    }


    fun getSensorData():SensorData{
        return sensorData
    }


    fun formatSensorData(sd: SensorData): Map<String, String> {
        return mapOf(
            "long_gps" to "${sd.longGPS}",
            "lat_gps" to "${sd.latGPS}",
            "speed_gps" to "${sd.speedGPS}",
            "acc_x" to "${sd.accX}",
            "acc_y" to "${sd.accY}",
            "acc_z" to "${sd.accZ}",
            "gyroscope_x" to "${sd.gyroscopeX}",
            "gyroscope_y" to "${sd.gyroscopeY}",
            "gyroscope_z" to "${sd.gyroscopeZ}",
            "rotation_x" to "${sd.rotationX}",
            "rotation_y" to "${sd.rotationY}",
            "rotation_z" to "${sd.rotationZ}",
            "magnetic_x" to "${sd.magneticX}",
            "magnetic_y" to "${sd.magneticY}",
            "magnetic_z" to "${sd.magneticZ}"
        )
    }


    suspend fun initBdMap() {

        val result = requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS
        )
        Log.d(TAG,"权限结果:$result")
        if (result.isEmpty()) {
            return
        }

        mMapView.map.isMyLocationEnabled = true


        mBaiduMap.setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.COMPASS,
            true,null))

        val builder: MapStatus.Builder = MapStatus.Builder()
        builder.zoom(17.0f)

        cancelLocationAuto.observeOn(AndroidSchedulers.mainThread())
            .debounce(10,TimeUnit.SECONDS)
            .subscribe {
                mBaiduMap.setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.COMPASS,
                    true,null))
            }

        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
        mBaiduMap.setOnMapTouchListener {
            mBaiduMap.setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL,
                true,null))
            cancelLocationAuto.onNext(Unit)
        }


    }


    private  fun updateYyState(isStart: Boolean) {
        yyIsStart = isStart
        if (isStart) {
            binding.startYy.text = "关闭鹰眼"

        } else {
            binding.startYy.text = "开启鹰眼"

        }
    }

    // 初始化轨迹服务监听器
    open inner class MyTraceListener  : OnTraceListener {
        override fun onBindServiceCallback(p0: Int, p1: String?) {
            Log.d(TAG,"绑定service成功"+p1)
        }

        // 开启服务回调
        override fun onStartTraceCallback(status: Int, message: String) {
            Log.d(TAG,"开启服务回调"+message+status)
            if (message == "成功") {
                binding.startYy.isClickable = true
                updateYyState(false)
            } else {
                binding.startYy.isClickable=false
                binding.startYy.text = "鹰眼今日额度用完"
                binding.startYy.setBackgroundColor(R.color.gray)
            }
        }

        // 停止服务回调
        override fun onStopTraceCallback(status: Int, message: String) {
            Log.d(TAG,"停止服务回调"+message+status)
        }

        // 开启采集回调
        override fun onStartGatherCallback(status: Int, message: String) {
            Log.d(TAG,"开始采集回调"+message+status)
        }

        // 停止采集回调
        override fun onStopGatherCallback(status: Int, message: String) {
            Log.d(TAG,"停止采集回调"+message+status)
        }

        // 推送回调
        override fun onPushCallback(messageNo: Byte, message: PushMessage) {
            Log.d(TAG,"推送回调"+message)
        }
        override fun onInitBOSCallback(p0: Int, p1: String?) {
            Log.d(TAG,"初始化BOS"+p1)
        }
    }

    private suspend fun startYy() {
        loading("正在打开鹰眼"){
            val (isSuccess,status) =  mTraceClient.startGather()
            mMapView.snakcBar("打开鹰眼${if(isSuccess) "成功" else "失败,错误码:"+status}")
            updateYyState(isSuccess)
        }

    }
    private suspend fun closeYy() {
        loading("正在打开鹰眼"){
            val (isSuccess,status) = mTraceClient.stopGather()
            mMapView.snakcBar("关闭鹰眼${if(isSuccess) "成功" else "失败,错误码:"+status}")
            updateYyState(!isSuccess)
        }

    }



    private suspend fun initUserInfo() {
        getSharedPreferences("user_info",Context.MODE_PRIVATE)

        val userInfo = getUserInfo()
        if (userInfo == null) {
            // 需要给个用户名
           suspendCancellableCoroutine<Unit> {cc->
               val view = layoutInflater.inflate(R.layout.dialog_input_name, null)
               AlertDialog.Builder(this)
                   .setView(view)
                   .setTitle("给个用户名吧")
                   .setPositiveButton("确定") { dialog, pid ->
                       val userName = view.findViewById<EditText>(R.id.userName).text?.toString()
                       if (userName.isNullOrBlank()) {
                           Toast.makeText(this@MainActivity, "请输入正确的名字", Toast.LENGTH_SHORT)
                               .show()
                       } else {
                           mTrace = Trace(serviceId.toLong(),
                               userName+Build.BOARD, false)
                           setUserInfo(UserInfo(userName.toString()))
                           cc.resume(Unit){}
                           dialog.dismiss()
                       }
                   }
                   .setCancelable(false)
                   .create()
                   .show()
           }

        } else {
            mMapView.snakcBar("欢迎${userInfo.name}!")
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


    private fun notifySensorDataUpdate() {
        binding.gpsLong.text = String.format("%6.3f", sensorData.longGPS)
        binding.gpsLat.text = String.format("%6.3f", sensorData.latGPS)
        binding.gpsSpeed.text = String.format("%6.3f", sensorData.speedGPS)

//        val wakeLock: PowerManager.WakeLock =
//            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
//                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
//                    acquire()
//                }
//            }
//        binding.gpsLong.text = String.format("%6.3f", GPS_Long)
//        binding.gpsLat.text = String.format("%6.3f", GPS_Lat)
//        binding.gpsSpeed.text = String.format("%6.3f", GPS_Speed)

        binding.accX.text = String.format("%6.3f", sensorData.accX)
        binding.accY.text = String.format("%6.3f", sensorData.accY)
        binding.accZ.text = String.format("%6.3f", sensorData.accZ)

        binding.gyroscopeX.text = String.format("%6.3f", sensorData.gyroscopeX)
        binding.gyroscopeY.text = String.format("%6.3f", sensorData.gyroscopeY)
        binding.gyroscopeZ.text = String.format("%6.3f", sensorData.gyroscopeZ)

        binding.rotationX.text = String.format("%6.3f", sensorData.rotationX)
        binding.rotationY.text = String.format("%6.3f", sensorData.rotationY)
        binding.rotationZ.text = String.format("%6.3f", sensorData.rotationZ)

        binding.magneticX.text = String.format("%6.3f", sensorData.magneticX)
        binding.magneticY.text = String.format("%6.3f", sensorData.magneticY)
        binding.magneticZ.text = String.format("%6.3f", sensorData.magneticZ)

        if (sensorData.gpsState) {
            binding.stateTextGPS.text = "正常"
            if (binding.stateTextGPS.background != getDrawable(R.color.green)) {
                binding.stateTextGPS.background = getDrawable(R.color.green)
            }
        } else {
            binding.stateTextGPS.text = "异常"
            if (binding.stateTextGPS.background != getDrawable(R.color.red)) {
                binding.stateTextGPS.background = getDrawable(R.color.red)
            }
        }

        if (sensorData.accState && sensorData.gyroscopeState && sensorData.rotationState && sensorData.magneticState) {
            binding.stateTextIMU.text = "正常"
            if (binding.stateTextIMU.background != getDrawable(R.color.green))
            {
                binding.stateTextIMU.background = getDrawable(R.color.green)
            }
        } else {
            binding.stateTextIMU.text = "异常"
            if (binding.stateTextIMU.background != getDrawable(R.color.red))
            {
                binding.stateTextIMU.background = getDrawable(R.color.red)
            }
        }

     }

}

