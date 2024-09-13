package com.tans.beambox

import com.tans.beambox.netty.findLocalAddressV4
import com.tans.beambox.netty.getBroadcastAddress
import com.tans.beambox.transferproto.SimpleCallback
import com.tans.beambox.transferproto.broadcastconn.BroadcastSender
import com.tans.beambox.transferproto.broadcastconn.BroadcastSenderObserver
import com.tans.beambox.transferproto.broadcastconn.BroadcastSenderState
import com.tans.beambox.transferproto.broadcastconn.model.RemoteDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object BroadcastSenderTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val sender = BroadcastSender(
            deviceName = "TestSender",
            log = TestLog
        )
        sender.addObserver(object : BroadcastSenderObserver {

            override fun onNewState(state: BroadcastSenderState) {
                println("Sender state: $state")
            }

            override fun requestTransferFile(
                remoteDevice: RemoteDevice
            ) {
                println("Sender receive transfer request: $remoteDevice")
            }
        })
        sender.startBroadcastSender(
            localAddress = localAddress,
            broadcastAddress = localAddress.getBroadcastAddress().first,
            simpleCallback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    super.onSuccess(data)
                    println("Sender start Success")
                }

                override fun onError(errorMsg: String) {
                    super.onError(errorMsg)
                    println("Sender start fail: $errorMsg")
                }
            }
        )
        runBlocking {
            delay(60 * 1000 * 50)
        }
    }
}