package com.tans.beambox.transferproto.broadcastconn

import com.tans.beambox.transferproto.broadcastconn.model.RemoteDevice

interface BroadcastReceiverObserver {

    fun onNewState(state: BroadcastReceiverState)

    fun onNewBroadcast(remoteDevice: RemoteDevice)

    fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>)
}