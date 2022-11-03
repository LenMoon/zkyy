package com.example.zkyy

data class SensorData(
    @Volatile
    var longGPS:Float,
    @Volatile
    var latGPS:Float,
    @Volatile
    var speedGPS:Float,
    @Volatile
    var accX:Float,
    @Volatile
    var accY:Float,
    @Volatile
    var accZ:Float,
    @Volatile
    var gyroscopeX:Float,
    @Volatile
    var gyroscopeY:Float,
    @Volatile
    var gyroscopeZ:Float,
    @Volatile
    var rotationX:Float,
    @Volatile
    var rotationY:Float,
    @Volatile
    var rotationZ:Float
    )
