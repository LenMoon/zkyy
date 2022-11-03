package com.example.zkyy.http

import com.example.zkyy.SensorData
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface DataReport {
    @POST("/api/dataCollect/{userName}")
    fun reportData(@Path("userName") userName:String,@Body sensorDatas:List<SensorData>)

}