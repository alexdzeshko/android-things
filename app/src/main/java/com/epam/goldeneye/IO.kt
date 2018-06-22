package com.epam.goldeneye

import android.util.Log
import java.io.IOException

object IO {

    @JvmStatic
    fun closePlease(vararg closeable: AutoCloseable?) {
        closeable.forEach {
            try {
                it?.close()
            } catch (e: Throwable) {
                Log.e("IO", "can't close", e)
            }
        }
    }

    inline fun safely(operation: () -> Unit) {
        try {
            operation.invoke()
        } catch (ex: IOException) {
            Log.e("IO", ex.message)
        }
    }
}
