package com.tans.beambox.transferproto.fileexplore


interface FileExploreRequestHandler<Req, Resp> {

    fun onRequest(isNew: Boolean, request: Req): Resp?
}