package com.tans.beambox.netty.extensions

interface IServerManager {

    fun <Request, Response> registerServer(s: IServer<Request, Response>)

    fun <Request, Response> unregisterServer(s: IServer<Request, Response>)

    fun clearAllServers()
}