package com.tans.beambox

import com.tans.beambox.netty.findLocalAddressV4
import com.tans.beambox.transferproto.SimpleCallback
import com.tans.beambox.transferproto.broadcastconn.model.RemoteDevice
import com.tans.beambox.transferproto.qrscanconn.QRCodeScanServer
import com.tans.beambox.transferproto.qrscanconn.QRCodeScanServerObserver
import com.tans.beambox.transferproto.qrscanconn.QRCodeScanState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object QRCodeScanServerTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val server = QRCodeScanServer(log = TestLog)
        server.addObserver(object : QRCodeScanServerObserver {

            override fun requestTransferFile(remoteDevice: RemoteDevice) {
                println("Server receive request: $remoteDevice")
            }

            override fun onNewState(state: QRCodeScanState) {
                println("Server state: $state")
            }

        })
        server.startQRCodeScanServer(
            localAddress = localAddress,
            callback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    println("Server connect success")
                }

                override fun onError(errorMsg: String) {
                    println("Server $errorMsg")
                }
            }
        )
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}