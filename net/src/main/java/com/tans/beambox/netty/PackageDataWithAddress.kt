package com.tans.beambox.netty

import java.net.InetSocketAddress

data class PackageDataWithAddress(
    val receiverAddress: InetSocketAddress?,
    val senderAddress: InetSocketAddress? = null,
    val data: PackageData
)