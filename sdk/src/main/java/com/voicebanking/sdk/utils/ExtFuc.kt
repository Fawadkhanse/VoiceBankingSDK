package com.voicebanking.sdk.utils

import com.google.gson.Gson
import com.google.gson.JsonObject

inline fun <reified T> String.toPojo(): T {
    return Gson().fromJson(this, T::class.java)
}

inline fun <reified T> T.toJson(): String {
    return Gson().toJson(this)
}

fun String.toJsonObject(): JsonObject {
    val gson = Gson()
    return gson.fromJson(this, JsonObject::class.java)
}

fun Any.checkIfArray(): Boolean {
    return this is Array<*>
}
