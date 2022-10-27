package com.example.zkyy

import android.content.Context
import android.util.JsonWriter
import org.json.JSONStringer

data class UserInfo(val name: String)


private const val USER_KEY: String = "user_info_key"


fun Context.setUserInfo(userInfo: UserInfo) {
    val sp = getSharedPreferences(USER_KEY, Context.MODE_PRIVATE)
    sp.edit()
        .putString("name", userInfo.name)
        .commit()
}


fun Context.getUserInfo(): UserInfo? {
    val sp = getSharedPreferences(USER_KEY, Context.MODE_PRIVATE)
    val name = sp.getString("name", null) ?: return null
    return UserInfo(name)
}

