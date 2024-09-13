package com.tans.beambox

import com.tans.beambox.netty.findLocalAddressV4
import com.tans.beambox.netty.getBroadcastAddress
import com.tans.beambox.transferproto.SimpleCallback
import com.tans.beambox.transferproto.broadcastconn.BroadcastReceiver
import com.tans.beambox.transferproto.broadcastconn.BroadcastReceiverObserver
import com.tans.beambox.transferproto.broadcastconn.BroadcastReceiverState
import com.tans.beambox.transferproto.broadcastconn.model.RemoteDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object BroadcastReceiverTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val receiver = BroadcastReceiver(
            deviceName = "TestReceiver",
            log = TestLog
        )

        receiver.addObserver(object : BroadcastReceiverObserver {
            override fun onNewState(state: BroadcastReceiverState) {
                println("Receiver state: $state")
            }

            override fun onNewBroadcast(
                remoteDevice: RemoteDevice
            ) {
                println("Receiver receive broadcast: $remoteDevice")
//                receiver.requestFileTransfer(
//                    targetAddress = remoteDevice.remoteAddress.address,
//                    simpleCallback = object : SimpleCallback<BroadcastTransferFileResp> {
//
//                        override fun onError(errorMsg: String) {
//                            println("Receiver request transfer fail: $errorMsg")
//                        }
//
//                        override fun onSuccess(data: BroadcastTransferFileResp) {
//                            println("Receiver request transfer success: $data")
//                        }
//                    }
//                )
            }

            override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {}
        })

        receiver.startBroadcastReceiver(
            localAddress = localAddress,
            broadcastAddress = localAddress.getBroadcastAddress().first,
            simpleCallback = object : SimpleCallback<Unit> {
                override fun onError(errorMsg: String) {
                    println("Receiver start error: $errorMsg")
                }

                override fun onSuccess(data: Unit) {
                    super.onSuccess(data)
                    println("Receiver start success")
                }
            }
        )

        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}