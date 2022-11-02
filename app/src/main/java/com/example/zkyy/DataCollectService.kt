package com.example.zkyy

import android.app.Notification.BigTextStyle
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.MyLocationData
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.suspendCoroutine

class DataCollectService : Service(), SensorEventListener,LocationListener {
    @Volatile
    private var sensorData:SensorData = SensorData(0.0f, 0.0f, 0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f, 0.0f, 0.0f)
    private val dataCollectBinder = DataCollectBinder()


    private var mAccSensor: Sensor?=null
    private var mGyroscopeSensor:Sensor? =null
    private var mRotationSensor:Sensor? = null
    private lateinit var mSensorManager:SensorManager
    private val mSensorDataSubject = PublishSubject.create<SensorData>()
    private lateinit var mLocationClient: LocationClient
    private val mLocationListener = MyLocationListener()
    private lateinit var mLocationManager:LocationManager
    private val mLocalDataSubject = PublishSubject.create<BDLocation>()

    inner class DataCollectBinder : Binder() {
        fun startCollectSensor() {
            mSensorManager.registerListener(this@DataCollectService,mAccSensor,SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this@DataCollectService,mGyroscopeSensor,SensorManager.SENSOR_DELAY_GAME)
            mSensorManager.registerListener(this@DataCollectService,mRotationSensor,SensorManager.SENSOR_DELAY_GAME)

        }
        fun stopCollectSensor() {
            mSensorManager.unregisterListener(this@DataCollectService)
        }
        fun startCollectGps() {
            val option = LocationClientOption()
            option.setOpenGps(true) // 打开gps
            option.setCoorType("bd09ll") // 设置坐标类型
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
            sensorData = sensorData.copy(longGPS = location.latitude.toFloat(), latGPS = location.latitude.toFloat(), speedGPS = location.speed)
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
        mLocationClient = LocationClient(this)
        mLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
            100,//每0.1秒获取一次
            0.1f,//每移动1米获取一次
            this
        )

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
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        startForeground(1,builder.build())

        var i = 0;
        // 更新前台服务显示状态示例
        mSensorDataSubject
            .throttleLast(3,TimeUnit.SECONDS)
            .subscribe {
            val builder = NotificationCompat.Builder(this, channelID)
                .setSmallIcon(com.google.android.material.R.drawable.ic_clock_black_24dp)
                .setContentTitle("中科鹰眼数据采集${i++}")
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("""
                gps状态:--    网络状态:-- 传感器状态:--
                accX:${it.accX} gyroX:${it.gyroscopeX} rotaX:${it.rotationX}
                lat:${it.latGPS} lon:${it.longGPS} speed:${it.speedGPS}
                time:${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)}
            """.trimIndent()))
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            startForeground(1,builder.build())
        }


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
                sensorData = sensorData.copy(accX = values[0], accY = values[1], accZ = values[2])

                var testAccX = sensorData.accX
                Log.d(MainActivity.TAG,"My accX：$testAccX")
            }
            Sensor.TYPE_GYROSCOPE -> {
                sensorData = sensorData.copy(gyroscopeX = values[0], gyroscopeY = values[1], gyroscopeZ = values[2])

                var testGyroscopeX = sensorData.gyroscopeX
                Log.d(MainActivity.TAG,"My gyroscopeX：$testGyroscopeX")
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                sensorData = sensorData.copy(rotationX = values[0], rotationY = values[1], rotationZ = values[2])

                var testRotationX = sensorData.rotationX
                Log.d(MainActivity.TAG,"My rotationX：$testRotationX")
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

    override fun onLocationChanged(location: Location) {
        var longi = location.longitude.toFloat()
        Log.d(MainActivity.TAG,"My经度：$longi")
    }
}