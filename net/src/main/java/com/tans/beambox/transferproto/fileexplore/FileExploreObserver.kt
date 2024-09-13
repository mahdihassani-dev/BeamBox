package com.tans.beambox.transferproto.fileexplore

import com.tans.beambox.transferproto.fileexplore.model.SendMsgReq

interface FileExploreObserver {

    fun onNewState(state: FileExploreState)

    fun onNewMsg(msg: SendMsgReq)
}