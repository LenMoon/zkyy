package com.example.zkyy

import java.time.LocalDateTime

data class SensorData(
    @Volatile
    var longGPS:Float,
    @Volatile
    var latGPS:Float,
    @Volatile
    var speedGPS:Float,
    @Volatile
    var gpsState:Int,

    @Volatile
    var accX:Float,
    @Volatile
    var accY:Float,
    @Volatile
    var accZ:Float,
    @Volatile
    var accState:Int,

    @Volatile
    var gyroscopeX:Float,
    @Volatile
    var gyroscopeY:Float,
    @Volatile
    var gyroscopeZ:Float,
    @Volatile
    var gyroscopeState:Int,

    @Volatile
    var rotationX:Float,
    @Volatile
    var rotationY:Float,
    @Volatile
    var rotationZ:Float,
    @Volatile
    var rotationState:Int,

    @Volatile
    var magneticX:Float,
    @Volatile
    var magneticY:Float,
    @Volatile
    var magneticZ:Float,
    @Volatile
    var magneticState:Int,
    @Volatile
    var reportTime:String?
    )
