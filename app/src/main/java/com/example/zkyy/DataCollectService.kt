package com.example.zkyy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.CoordType
import com.example.zkyy.http.DataReport
import com.trello.rxlifecycle.RxLifecycle
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.suspendCoroutine


class DataCollectService : LifecycleService(), SensorEventListener {
    @Volatile
    private var sensorData:SensorData = SensorData(0.0f, 0.0f, 0.0f, 0,
        0.0f,0.0f,0.0f, 0,
        0.0f,0.0f,0.0f, 0,
        0.0f, 0.0f, 0.0f, 0,
        0.0f, 0.0f, 0.0f, 0, null)
    private val dataCollectBinder = DataCollectBinder()


    private var mAccSensor: Sensor? = null
    private var mGyroscopeSensor:Sensor? = null
    private var mRotationSensor:Sensor? = null
    private var mMagneticSensor:Sensor? = null
    private lateinit var mSensorManager:SensorManager
    private val mSensorDataSubject = PublishSubject.create<SensorData>()
    private lateinit var mLocationClient: LocationClient
    private val mLocationListener = MyLocationListener()
    private val mLocalDataSubject = PublishSubject.create<BDLocation>()
    private lateinit var dataReport: DataReport
    private lateinit var mPowerManager: PowerManager
    private var mWakeLock: PowerManager.WakeLock?=null

    private val mLifeTime = 5


    override fun onDestroy() {
        super.onDestroy()
        mWakeLock?.release()
    }

    inner class DataCollectBinder : Binder() {
        fun startCollectSensor() {
            mSensorManager.registerListener(this@DataCollectService,mAccSensor,SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this@DataCollectService,mGyroscopeSensor,SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this@DataCollectService,mRotationSensor,SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this@DataCollectService,mMagneticSensor,SensorManager.SENSOR_DELAY_GAME)
        }
        fun stopCollectSensor() {
            mSensorManager.unregisterListener(this@DataCollectService)
        }
        fun startCollectGps() {
            val option = LocationClientOption()
            option.setOpenGps(true) // 打开gps
            option.setCoorType(CoordType.BD09LL.name) // 设置坐标类型
            option.setScanSpan(1000)
            mLocationClient.setLocOption(option)
            mLocationClient.registerLocationListener(mLocationListener)
            mLocationClient.start()
        }
        fun stopCollectGps() {
            mLocationClient.stop()
        }
        fun sensorDataObservable(): Observable<SensorData> {
            return mSensorDataSubject
        }

        fun locationDataObservable(): Observable<BDLocation> {
            return mLocalDataSubject
        }
    }

    inner class MyLocationListener : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation) {

            val lon = location.longitude.toFloat()
            val lat = location.latitude.toFloat()
            val speed  = location.speed
            Log.d(MainActivity.TAG,"经度:$lon 维度:$lat 速度:$speed")

            sensorData = sensorData.apply {
                longGPS = location.longitude.toFloat()
                latGPS = location.latitude.toFloat()
                speedGPS = location.speed
                gpsState = mLifeTime
            }

            mSensorDataSubject.onNext(sensorData)
            mLocalDataSubject.onNext(location)
        }
    }


    override fun onCreate() {
        super.onCreate()
        mSensorManager= getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        mMagneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        mLocationClient = LocationClient(this)

        mPowerManager = getSystemService(PowerManager::class.java)

        mWakeLock= mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zkhy::zkyy").apply {
            acquire()
        }
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()


        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.22.2.76:8082/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()




        dataReport = retrofit.create(DataReport::class.java)

        val intent = Intent(this,MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val channelID = "zkhy_noti"
        // 创建通道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val name = "中科慧眼前台服务"
            val descriptionText = "前台服务"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(channelID, name, importance)
            mChannel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
        val builder = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(com.google.android.material.R.drawable.ic_clock_black_24dp)
            .setContentTitle("中科鹰眼数据采集")
            .setContentText("""
                gps状态:--    网络状态:-- 传感器状态:--
            """.trimIndent())
//            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (Build.VERSION.SDK_INT > 28)
        {
            startForeground(1,builder.build(),FOREGROUND_SERVICE_TYPE_DATA_SYNC or FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1,builder.build())
        }

        var i = 0;
        // 更新前台服务显示状态示例
        val disposable = mSensorDataSubject
            .throttleLast(3,TimeUnit.SECONDS)
            .subscribe {
            val builder = NotificationCompat.Builder(this, channelID)
                .setSmallIcon(com.google.android.material.R.drawable.ic_clock_black_24dp)
                .setContentTitle("中科鹰眼数据采集${i++}")
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("""
                gps状态:${it.gpsState > 0}    网络状态:-- 传感器状态:${it.accState > 0 && it.gyroscopeState > 0 && it.rotationState > 0 && it.magneticState > 0}
                accX:${it.accX} gyroX:${it.gyroscopeX} rotaX:${it.rotationX}
                lat:${it.latGPS} lon:${it.longGPS} speed:${it.speedGPS}
                time:${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)}
            """.trimIndent()))
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            startForeground(1,builder.build(),FOREGROUND_SERVICE_TYPE_DATA_SYNC or FOREGROUND_SERVICE_TYPE_LOCATION)
        }


        val userName = getUserInfo()?.name ?: "未知"

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        mSensorDataSubject.observeOn(Schedulers.io())
            .buffer(100)
            .subscribe{
                try {
                    val response = dataReport.reportData(userName, it).execute()
                    Log.d(TAG, "上报数据返回结果:" + response.body()+":"+ response.message()+":"+response.code())
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(TAG,"上传数据报错了",e)
                }
            }






        Timer().schedule(object : TimerTask() {
            override fun run() {
                var tempGpsState = sensorData.gpsState - 1
                var tempAccState = sensorData.accState - 1
                var tempGyroscopeState = sensorData.gyroscopeState - 1
                var tempRotationState = sensorData.rotationState - 1
                var tempMagneticState = sensorData.magneticState - 1

                sensorData = sensorData.apply {
                    gpsState = tempGpsState
                    accState = tempAccState
                    gyroscopeState = tempGyroscopeState
                    rotationState = tempRotationState
                    magneticState = tempMagneticState
                }
            }
        }, 0,1000) //1秒检测一次数据流是否正常，mLifeTime = 5 即5秒燃尽后表示数据流异常。

    }



    override fun onBind(intent: Intent): IBinder {
        return dataCollectBinder
    }

    override fun onSensorChanged(se: SensorEvent?) {
        if (se == null) {
            return
        }
        val sensorType = se.sensor.type
        val values = se.values
        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> {
                sensorData = sensorData.apply {
                    accX = values[0]; accY = values[1]; accZ = values[2];
                    accState = mLifeTime ;
                }
                var testAccX = sensorData.accX
                Log.d(MainActivity.TAG,"My accX：$testAccX")
            }
            Sensor.TYPE_GYROSCOPE -> {
                sensorData = sensorData.apply {
                    gyroscopeX = values[0]; gyroscopeY = values[1]; gyroscopeZ = values[2];
                    gyroscopeState = mLifeTime;
                }

                var testGyroscopeX = sensorData.gyroscopeX
                Log.d(MainActivity.TAG,"My gyroscopeX：$testGyroscopeX")
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                sensorData = sensorData.apply {
                    rotationX = values[0]; rotationY = values[1]; rotationZ = values[2];
                    rotationState = mLifeTime;
                }

                var testRotationX = sensorData.rotationX
                Log.d(MainActivity.TAG,"My rotationX：$testRotationX")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                sensorData = sensorData.apply {
                    magneticX = values[0]; magneticY = values[1]; magneticZ = values[2];
                    magneticState = mLifeTime;
                }

                var testMagneticX = sensorData.magneticX
                Log.d(MainActivity.TAG,"My magneticX：$testMagneticX")
            }

            else -> {}
        }
        mSensorDataSubject.onNext(sensorData)

    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }



    companion object {
        suspend fun bindService(context: Context):DataCollectBinder {
            val intent = Intent(context,DataCollectService::class.java)
            return suspendCoroutine<DataCollectBinder> {r->
                context.bindService(intent,object:ServiceConnection{
                    override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
                        r.resumeWith(Result.success(p1 as DataCollectBinder))
                    }

                    override fun onServiceDisconnected(p0: ComponentName?) {

                    }

                },1)
            }
        }
    }

}