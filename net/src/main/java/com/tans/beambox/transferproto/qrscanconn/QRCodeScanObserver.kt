package com.tans.beambox.transferproto.qrscanconn

interface QRCodeScanObserver {

    fun onNewState(state: QRCodeScanState)
}