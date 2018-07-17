/*
 * Copyright 2016 Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tzutalin.dlibtest

import android.content.Context
import android.support.annotation.RawRes
import android.util.Log

import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by darrenl on 2016/3/30.
 */
object FileUtils {
    private val TAG = "FileUtils"

    fun copyFileFromRawToOthers(context: Context, @RawRes id: Int, targetPath: String) {
        val rawStream = context.resources.openRawResource(id)
        val outStream = FileOutputStream(targetPath)

        rawStream?.use { raw ->
            outStream.use { out ->
                try {
                    raw.copyTo(out)
                } catch (e: Throwable) {
                    Log.e(TAG, "copyFileFromRawToOthers", e)
                }
            }
        }
    }
}
