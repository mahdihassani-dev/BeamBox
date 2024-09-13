package com.tans.beambox

import com.tans.beambox.netty.INettyConnectionTask
import com.tans.beambox.netty.NettyConnectionObserver
import com.tans.beambox.netty.NettyTaskState
import com.tans.beambox.netty.PackageData
import com.tans.beambox.netty.extensions.ConnectionServerImpl
import com.tans.beambox.netty.extensions.IServer
import com.tans.beambox.netty.extensions.withServer
import com.tans.beambox.netty.findLocalAddressV4
import com.tans.beambox.netty.getBroadcastAddress
import com.tans.beambox.netty.udp.NettyUdpConnectionTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

object UdpBroadcastServerTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val broadcastAddress = localAddress.getBroadcastAddress().first
        val task = NettyUdpConnectionTask(
            connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Bind(
                address = broadcastAddress,
                port = 9997
            ),
            enableBroadcast = true
        ).withServer<ConnectionServerImpl>(log = TestLog)

        task.registerServer(object : IServer<String, Unit> {
            override val requestClass: Class<String> = String::class.java
            override val responseClass: Class<Unit> = Unit::class.java
            override val replyType: Int = 1
            override val log: ILog = TestLog

            override fun couldHandle(requestType: Int): Boolean {
                return requestType == 0
            }

            override fun onRequest(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                r: String,
                isNewRequest: Boolean
            ): Unit? {
                if (isNewRequest) {
                    println("UpdBroadcastServer receive broadcast: $r")
                }
                return null
            }

        })

        task.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                println("UpdBroadcastServerState: $nettyState")
            }

            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {
            }
        })
        task.startTask()
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}