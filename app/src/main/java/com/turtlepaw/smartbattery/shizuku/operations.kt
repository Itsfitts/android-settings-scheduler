/**
 * This code has been modified from https://github.com/SimpleAppProjects/SimpleWear licensed under Apache License 2.0 (https://github.com/SimpleAppProjects/SimpleWear/blob/master/LICENSE.txt)
 */

package com.turtlepaw.smartbattery.shizuku

import android.Manifest
import android.content.Context
import android.content.pm.IPackageManager
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

private const val TAG = "ShizukuPermission"

fun Context.grantSecureSettingsPermission(): Boolean {
    return try {
        val packageMgr = SystemServiceHelper.getSystemService("package")
            .let(::ShizukuBinderWrapper)
            .let(IPackageManager.Stub::asInterface)

        val userId = ShizukuUtils.getUserId()

        packageMgr.grantRuntimePermission(packageName, Manifest.permission.WRITE_SECURE_SETTINGS, userId)
        Log.d(TAG, "Successfully granted permission")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to grant permission", e)
        false
    }
}