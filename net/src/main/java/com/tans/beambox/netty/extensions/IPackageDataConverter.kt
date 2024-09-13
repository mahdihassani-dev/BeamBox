package com.tans.beambox.netty.extensions

import com.tans.beambox.netty.PackageData

interface IPackageDataConverter {

    fun couldHandle(type: Int, dataClass: Class<*>): Boolean

    fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>): PackageData?
}