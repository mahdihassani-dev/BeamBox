package com.tans.beambox.transferproto.broadcastconn

import com.tans.beambox.transferproto.broadcastconn.model.RemoteDevice

interface BroadcastSenderObserver {

    fun onNewState(state: BroadcastSenderState)

    fun requestTransferFile(remoteDevice: RemoteDevice)
}