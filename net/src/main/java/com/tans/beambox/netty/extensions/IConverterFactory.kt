package com.tans.beambox.netty.extensions

interface IConverterFactory {

    fun findBodyConverter(type: Int, dataClass: Class<*>) : IBodyConverter?

    fun findPackageDataConverter(type: Int, dataClass: Class<*>): IPackageDataConverter?
}