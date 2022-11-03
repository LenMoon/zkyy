package com.example.zkyy

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.baidu.trace.LBSTraceClient
import com.baidu.trace.model.OnTraceListener
import com.baidu.trace.model.PushMessage
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val TAG = "鹰眼扩展"
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


suspend fun Context.confirm(title: String, content: String,okText:String,cancelText:String):Boolean {
    return suspendCoroutine {r->
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton(okText
            ) { dialog, p1 ->
                dialog.dismiss()
                r.resume(true)
            }
            .setNegativeButton(cancelText){
                    dialog,p1 ->
                dialog.dismiss()
                r.resume(false)
            }
            .setCancelable(false)
            .show()
    }
}


suspend fun AppCompatActivity.loading(title:String,runable: suspend ()->Unit) {
    var loading = ProgressDialog(this)
    loading.setCancelable(false)
    try{
        loading.setTitle(title)
        loading.show()
        runable.invoke()
    }finally {
        loading.dismiss()
    }
}

fun View.snakcBar(title: String) {
    Snackbar.make(this,title,Snackbar.LENGTH_LONG).show()
}

open class MyTraceListener  : OnTraceListener {
    override fun onBindServiceCallback(p0: Int, p1: String?) {
        Log.d(MainActivity.TAG,"绑定service成功"+p1)
    }

    // 开启服务回调
    override fun onStartTraceCallback(status: Int, message: String) {
        Log.d(MainActivity.TAG,"开启服务回调"+message+status)
    }

    // 停止服务回调
    override fun onStopTraceCallback(status: Int, message: String) {
        Log.d(MainActivity.TAG,"停止服务回调"+message+status)
    }

    // 开启采集回调
    override fun onStartGatherCallback(status: Int, message: String) {
        Log.d(MainActivity.TAG,"开始采集回调"+message+status)
    }

    // 停止采集回调
    override fun onStopGatherCallback(status: Int, message: String) {
        Log.d(MainActivity.TAG,"停止采集回调"+message+status)
    }

    // 推送回调
    override fun onPushCallback(messageNo: Byte, message: PushMessage) {
        Log.d(MainActivity.TAG,"推送回调"+message)
    }
    override fun onInitBOSCallback(p0: Int, p1: String?) {
        Log.d(MainActivity.TAG,"初始化BOS"+p1)
    }
}
suspend fun LBSTraceClient.stopGather():Pair<Boolean,Int> {
    return suspendCancellableCoroutine {cc->
        stopGather(object : MyTraceListener() {
            override fun onStopGatherCallback(status: Int, msg: String) {
                Log.d(TAG,"开启鹰眼:$msg,$status")
                if (msg == "成功") {
                    cc.resume(true to status) { }
                } else {
                    cc.resume(false to status) { }
                }
            }
        });
    }
}
suspend fun LBSTraceClient.startGather():Pair<Boolean,Int> {
    return suspendCancellableCoroutine {cc->
        startGather(object : MyTraceListener() {
            override fun onStartGatherCallback(status: Int, message: String) {
                Log.d(TAG,"开启鹰眼:$message,$status")
                if (message == "成功") {
                    cc.resume(true to status) { }
                } else {
                    cc.resume(false to status) { }
                }
            }
        });
    }
}