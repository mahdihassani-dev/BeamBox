package com.tans.beambox.netty.extensions

import com.tans.beambox.netty.INettyConnectionTask

class ConnectionServerClientImpl(
    val connectionTask: INettyConnectionTask,
    val serverManager: IServerManager,
    val clientManager: IClientManager
) : INettyConnectionTask by connectionTask, IServerManager by serverManager, IClientManager by clientManager