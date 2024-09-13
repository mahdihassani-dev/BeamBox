package com.tans.beambox

import com.tans.beambox.netty.INettyConnectionTask
import com.tans.beambox.netty.NettyConnectionObserver
import com.tans.beambox.netty.NettyTaskState
import com.tans.beambox.netty.PackageData
import com.tans.beambox.netty.extensions.*
import com.tans.beambox.netty.findLocalAddressV4
import com.tans.beambox.netty.tcp.NettyTcpServerConnectionTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

object TcpServerTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val serverTask = NettyTcpServerConnectionTask(
            bindAddress = localAddress,
            bindPort = 1996,
            newClientTaskCallback = { newClientTask ->
                println("NewClientTask: $newClientTask")
                val serverConnection = newClientTask
                    .withServer<ConnectionServerImpl>(log = TestLog)
                    .withClient<ConnectionServerClientImpl>(log = TestLog)
                serverConnection
                    .registerServer(object : IServer<String, String> {
                        override val requestClass: Class<String> = String::class.java
                        override val responseClass: Class<String> = String::class.java
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
                        ): String {
                            if (isNewRequest) {
                                println("Receive client request: $r from $remoteAddress")
                            }
                            return "Hello, Client."
                        }

                    })
                serverConnection.addObserver(object : NettyConnectionObserver {
                    override fun onNewState(
                        nettyState: NettyTaskState,
                        task: INettyConnectionTask
                    ) {
                        println("ClientTaskState: $nettyState")
                    }

                    override fun onNewMessage(
                        localAddress: InetSocketAddress?,
                        remoteAddress: InetSocketAddress?,
                        msg: PackageData,
                        task: INettyConnectionTask
                    ) {
                    }
                })
            }
        )

        serverTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                println("ServerTaskState: $nettyState")
            }

            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {}
        })
        serverTask.startTask()
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}