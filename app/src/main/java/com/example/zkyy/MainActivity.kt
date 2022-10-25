package com.example.zkyy

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.hardware.Sensor
import android.hardware.Sensor.*
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
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


class MainActivity : AppCompatActivity(),SensorEventListener {
    companion object {
        const val TAG = "鹰眼"
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var mMapView:MapView
    private lateinit var mBaiduMap:BaiduMap
    private lateinit var mTraceClient:LBSTraceClient
    private val serviceId = 234710
    private  var flagThreshold = 1
    private  var flagCount = 20
    var mLocationClient:LocationClient = LocationClient(this);
    val cancelLocationAuto: Subject<Unit> = PublishSubject.create<Unit?>()
    var yyIsStart = false
    private val gatherInterval = 30
    private val packInterval = 200
    private lateinit var mTrace:Trace
    private lateinit var  sensorManager:SensorManager
    @Volatile
    private var sensorData:SensorData = SensorData(0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f)
    private var gyroscope:Subject<Float> = PublishSubject.create()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTraceClient = LBSTraceClient(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mMapView = binding.bmapView
        mBaiduMap = mMapView.map
        setSupportActionBar(null)
        sensorManager= getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lifecycleScope.launchWhenCreated {
            initBdMap()
            initUserInfo()

            initTrace()
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

        //注册监听各种传感器数据
        val accSensor = sensorManager.getDefaultSensor(TYPE_ACCELEROMETER)
        val gyroscopeSensor = sensorManager.getDefaultSensor(TYPE_GYROSCOPE)
        val rotationSensor = sensorManager.getDefaultSensor(TYPE_ROTATION_VECTOR)
        Log.d(TAG,"accSensor:$accSensor,gyrSensor:$gyroscopeSensor,rotationSensor:$rotationSensor")
        sensorManager.registerListener(this,accSensor,SensorManager.SENSOR_DELAY_FASTEST)
        sensorManager.registerListener(this,gyroscopeSensor,SensorManager.SENSOR_DELAY_FASTEST)
        sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_FASTEST)
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
            "acc_x" to "${sd.accX}",
            "acc_y" to "${sd.accY}",
            "acc_z" to "${sd.accZ}",
            "gyroscope_z" to "${sd.gyroscopeZ}",
            "rotation_x" to "${sd.rotationX}",
            "rotation_y" to "${sd.rotationY}",
            "rotation_z" to "${sd.rotationZ}"
        )
    }


    inner class MyLocationListener : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation) {
            //mapView 销毁后不在处理新接收的位置
            if (mMapView == null) {
                return
            }
            val locData = MyLocationData.Builder()
                .accuracy(location.radius) // 此处设置开发者获取到的方向信息，顺时针0-360
                .direction(location.direction).latitude(location.latitude)
                .longitude(location.longitude).build()
            mBaiduMap.setMyLocationData(locData)
        }
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

        //申请权限
        mMapView.map.isMyLocationEnabled = true
        val option = LocationClientOption()
        option.setOpenGps(true) // 打开gps
        option.setCoorType("bd09ll") // 设置坐标类型
        option.setScanSpan(1000)
        mLocationClient.setLocOption(option)
        mLocationClient.registerLocationListener(MyLocationListener())
        mLocationClient.start()

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
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(se: SensorEvent?) {
        if (se == null) {
            return
        }
        val sensorType = se.sensor.type
        val values = se.values
        when (sensorType) {
            TYPE_ACCELEROMETER -> {
                sensorData = sensorData.copy(accX = values[0], accY = values[1], accZ = values[2])
            }
            TYPE_GYROSCOPE -> {
                sensorData = sensorData.copy(gyroscopeZ = values[2])
                gyroscope.onNext(values[2])
            }
            TYPE_ROTATION_VECTOR -> {
                sensorData = sensorData.copy(rotationX = values[0], rotationY = values[1], rotationZ = values[2])
            }
            else -> {}
        }
        notifySensorDataUpdate()
    }

    private fun notifySensorDataUpdate() {
        binding.accX.text = "accX:${sensorData.accX}m/s²"
        binding.accY.text = "accY:${sensorData.accY}m/s²"
        binding.accZ.text = "accZ:${sensorData.accZ}m/s²"
        binding.gyroscopeZ.text = "gyroscopeZ:${sensorData.gyroscopeZ}rnd/s"
        binding.rotationX.text = "rotationX:${sensorData.rotationX}"
        binding.rotationY.text = "rotationY:${sensorData.rotationY}"
        binding.rotationZ.text = "rotationZ:${sensorData.rotationZ}"
    }


    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

}