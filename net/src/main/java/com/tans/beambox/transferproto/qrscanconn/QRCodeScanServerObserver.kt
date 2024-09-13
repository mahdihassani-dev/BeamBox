package com.tans.beambox.transferproto.qrscanconn

import com.tans.beambox.transferproto.broadcastconn.model.RemoteDevice

interface QRCodeScanServerObserver : QRCodeScanObserver {

    fun requestTransferFile(remoteDevice: RemoteDevice)
}