package com.example.zkyy

import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine

const val TAG = "权限"
suspend fun AppCompatActivity.requestPermission(vararg permissions: String): Map<String, Boolean> {
    val shouldPermissions = permissions.map {  ContextCompat.checkSelfPermission(this,it) }.filter { it != PackageManager.PERMISSION_GRANTED }
    if (shouldPermissions.isEmpty()) {
        return permissions.associateWith { true }
    }
    return suspendCancellableCoroutine {cc->
        val lancher  = registerForActivityResult(RequestMultiplePermissions()){
            Log.d(TAG,"权限结果:$it")
            cc.resume(it){
                err->
                err.printStackTrace()
            }
        }
        lancher.launch(permissions)
    }
}