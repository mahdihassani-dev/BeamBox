package com.tans.beambox.transferproto.fileexplore

import com.tans.beambox.ILog
import com.tans.beambox.netty.INettyConnectionTask
import com.tans.beambox.netty.NettyConnectionObserver
import com.tans.beambox.netty.NettyTaskState
import com.tans.beambox.netty.PackageData
import com.tans.beambox.netty.extensions.ConnectionClientImpl
import com.tans.beambox.netty.extensions.ConnectionServerClientImpl
import com.tans.beambox.netty.extensions.IClientManager
import com.tans.beambox.netty.extensions.IServer
import com.tans.beambox.netty.extensions.requestSimplify
import com.tans.beambox.netty.extensions.simplifyServer
import com.tans.beambox.netty.extensions.withClient
import com.tans.beambox.netty.extensions.withServer
import com.tans.beambox.netty.tcp.NettyTcpClientConnectionTask
import com.tans.beambox.netty.tcp.NettyTcpServerConnectionTask
import com.tans.beambox.transferproto.SimpleCallback
import com.tans.beambox.transferproto.SimpleObservable
import com.tans.beambox.transferproto.SimpleStateable
import com.tans.beambox.transferproto.TransferProtoConstant
import com.tans.beambox.transferproto.fileexplore.model.DownloadFilesReq
import com.tans.beambox.transferproto.fileexplore.model.DownloadFilesResp
import com.tans.beambox.transferproto.fileexplore.model.FileExploreDataType
import com.tans.beambox.transferproto.fileexplore.model.FileExploreFile
import com.tans.beambox.transferproto.fileexplore.model.HandshakeReq
import com.tans.beambox.transferproto.fileexplore.model.HandshakeResp
import com.tans.beambox.transferproto.fileexplore.model.ScanDirReq
import com.tans.beambox.transferproto.fileexplore.model.ScanDirResp
import com.tans.beambox.transferproto.fileexplore.model.SendFilesReq
import com.tans.beambox.transferproto.fileexplore.model.SendFilesResp
import com.tans.beambox.transferproto.fileexplore.model.SendMsgReq
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * [FileExplore]'s connection could be created by QRCode, Udp broadcast and Wifi p2p create connection, see [com.tans.beambox.transferproto.qrscanconn], [com.tans.beambox.transferproto.broadcastconn] and [com.tans.beambox.transferproto.p2pconn].
 * Server bind TCP [TransferProtoConstant.FILE_EXPLORE_PORT] port wait Client to connect.
 * After connect is created, client will send request [FileExploreDataType.HandshakeReq] [HandshakeReq] to server to handshake.
 * When handshake is ok, client and server could send requests each other, include [FileExploreDataType.DownloadFilesReq], [FileExploreDataType.SendFilesReq], [FileExploreDataType.ScanDirReq], [FileExploreDataType.SendMsgReq]
 */
class FileExplore(
    private val log: ILog,
    private val scanDirRequest: FileExploreRequestHandler<ScanDirReq, ScanDirResp>,
    // Download files from remote.
    private val sendFilesRequest: FileExploreRequestHandler<SendFilesReq, SendFilesResp>,
    // Send current device's files to remote.
    private val downloadFileRequest: FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp>,
    private val heartbeatInterval: Long = 8000,
) : SimpleStateable<FileExploreState>, SimpleObservable<FileExploreObserver> {

    override val state: AtomicReference<FileExploreState> = AtomicReference(FileExploreState.NoConnection)
    override val observers: LinkedBlockingDeque<FileExploreObserver> = LinkedBlockingDeque()
    private val exploreTask: AtomicReference<ConnectionServerClientImpl?> = AtomicReference(null)
    private val serverTask: AtomicReference<NettyTcpServerConnectionTask?> = AtomicReference(null)
    private val closeObserver: NettyConnectionObserver by lazy {
        object : NettyConnectionObserver {
            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {}
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.ConnectionClosed ||
                        nettyState is NettyTaskState.Error) {
                    closeConnectionIfActive()
                }
            }
        }
    }
    private val heartbeatTaskFuture: AtomicReference<ScheduledFuture<*>?> = AtomicReference(null)

    private val heartbeatServer: IServer<Unit, Unit> by lazy {
        simplifyServer(
            requestType = FileExploreDataType.HeartbeatReq.type,
            responseType = FileExploreDataType.HeartbeatResp.type,
            log = log,
            onRequest = { _, _, _, _ -> log.d(TAG, "Receive heartbeat.") }
        )
    }

    private val handshakeServer: IServer<HandshakeReq, HandshakeResp> by lazy {
        simplifyServer(
            requestType = FileExploreDataType.HandshakeReq.type,
            responseType = FileExploreDataType.HandshakeResp.type,
            log = log,
            onRequest = { _, _, r, isNew ->
                // Server receive client handshake.
                if (r.version == TransferProtoConstant.VERSION) {
                    val currentState = getCurrentState()
                    if (isNew && currentState is FileExploreState.Connected) {
                        newState(FileExploreState.Active(Handshake(r.fileSeparator)))
                    }
                    HandshakeResp(File.separator)
                } else {
                    null
                }
            }
        )
    }

    private val scanDirServer: IServer<ScanDirReq, ScanDirResp> by lazy {
        simplifyServer(
            requestType = FileExploreDataType.ScanDirReq.type,
            responseType = FileExploreDataType.ScanDirResp.type,
            log = log,
            onRequest = { _, _, r, isNew ->
                // Client or server handle scan current devices dir request. （Send current dirs' children file to remote）
                scanDirRequest.onRequest(isNew, r)
            }
        )
    }

    private val sendFilesServer: IServer<SendFilesReq, SendFilesResp> by lazy {
        simplifyServer(
            requestType = FileExploreDataType.SendFilesReq.type,
            responseType = FileExploreDataType.SendFilesResp.type,
            log = log,
            onRequest = { _, _, r, isNew ->
                // Client or server handle download files from remote.
                sendFilesRequest.onRequest(isNew, r)
            }
        )
    }

    private val downloadFilesServer: IServer<DownloadFilesReq, DownloadFilesResp> by lazy {
        simplifyServer(
            requestType = FileExploreDataType.DownloadFilesReq.type,
            responseType = FileExploreDataType.DownloadFilesResp.type,
            log = log,
            onRequest = { _, _, r, isNew ->
                // Client or server handle sending current devices' files to remote.
                downloadFileRequest.onRequest(isNew, r)
            }
        )
    }

    private val sendMsgServer: IServer<SendMsgReq, Unit> by lazy {
        simplifyServer(
            requestType = FileExploreDataType.SendMsgReq.type,
            responseType = FileExploreDataType.SendMsgResp.type,
            log = log,
            onRequest = { _, _, r, isNew ->
                // Client or server receive text message from remote.
                if (isNew) {
                    dispatchNewMsg(r)
                }
            }
        )
    }

    override fun addObserver(o: FileExploreObserver) {
        super.addObserver(o)
        o.onNewState(state.get())
    }

    /**
     * Server create connection.
     */
    fun bind(address: InetAddress, simpleCallback: SimpleCallback<Unit>) {
        if (getCurrentState() !is FileExploreState.NoConnection) {
            simpleCallback.onError("Error state: ${getCurrentState()}")
            return
        }
        val hasInvokeCallback = AtomicBoolean(false)
        heartbeatTaskFuture.get()?.cancel(true)
        heartbeatTaskFuture.set(null)
        this.exploreTask.get()?.stopTask()
        newState(FileExploreState.Requesting)
        val hasChildConnection = AtomicBoolean(false)
        // Server task.
        val serverTask = NettyTcpServerConnectionTask(
            bindAddress = address,
            bindPort = TransferProtoConstant.FILE_EXPLORE_PORT,
            idleLimitDuration = heartbeatInterval * 3,
            newClientTaskCallback = { task ->
                // Client coming, only one client would be accepted.
                if (hasChildConnection.compareAndSet(false, true)) {
                    /**
                     * Step2: handle client connection.
                     */
                    val exploreTask = task.withClient<ConnectionClientImpl>(log = log).withServer<ConnectionServerClientImpl>(log = log)
                    this@FileExplore.exploreTask.get()?.stopTask()
                    this@FileExplore.exploreTask.set(exploreTask)
                    log.d(TAG,"New connection: $exploreTask")
                    exploreTask.addObserver(object : NettyConnectionObserver {
                        override fun onNewState(
                            nettyState: NettyTaskState,
                            task: INettyConnectionTask
                        ) {
                            if (nettyState is NettyTaskState.Error ||
                                nettyState is NettyTaskState.ConnectionClosed ||
                                getCurrentState() !is FileExploreState.Requesting) {
                                // Client connection fail.
                                val errorMsg = "Connect error: $nettyState, ${getCurrentState()}"
                                log.e(TAG, errorMsg)
                                if (hasInvokeCallback.compareAndSet(false, true)) {
                                    simpleCallback.onError(errorMsg)
                                }
                                exploreTask.stopTask()
                                exploreTask.removeObserver(this)
                                serverTask.get()?.stopTask()
                                serverTask.set(null)
                                newState(FileExploreState.NoConnection)
                            } else {
                                // Client connection success.
                                if (nettyState is NettyTaskState.ConnectionActive) {
                                    newState(FileExploreState.Connected)
                                    log.d(TAG, "Connect success.")
                                    exploreTask.addObserver(closeObserver)
                                    exploreTask.registerServer(handshakeServer)
                                    exploreTask.registerServer(heartbeatServer)
                                    exploreTask.registerServer(scanDirServer)
                                    exploreTask.registerServer(sendFilesServer)
                                    exploreTask.registerServer(downloadFilesServer)
                                    exploreTask.registerServer(sendMsgServer)
                                    exploreTask.removeObserver(this)
                                    if (hasInvokeCallback.compareAndSet(false, true)) {
                                        simpleCallback.onSuccess(Unit)
                                    }
                                }
                            }
                        }

                        override fun onNewMessage(
                            localAddress: InetSocketAddress?,
                            remoteAddress: InetSocketAddress?,
                            msg: PackageData,
                            task: INettyConnectionTask
                        ) {
                        }

                    })
                } else {
                    task.stopTask()
                }
            }
        )
        serverTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.Error ||
                        nettyState is NettyTaskState.ConnectionClosed ||
                        getCurrentState() !is FileExploreState.Requesting) {
                    // Server task connection create fail.
                    if (hasInvokeCallback.compareAndSet(false, true)) {
                        simpleCallback.onError("Server bind error: $nettyState")
                    }
                    serverTask.removeObserver(this)
                    serverTask.stopTask()
                    log.e(TAG, "Bind server error: $nettyState")
                } else if (nettyState is NettyTaskState.ConnectionActive) {
                    // Server task connection create success.
                    serverTask.addObserver(closeObserver)
                    serverTask.removeObserver(this)
                    log.d(TAG, "Bind server success.")
                }
            }

            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {}
        })
        /**
         * Step1: Start server task.
         */
        serverTask.startTask()
        this.serverTask.get()?.stopTask()
        this.serverTask.set(serverTask)
    }

    /**
     * Client create connection.
     */
    fun connect(
        serverAddress: InetAddress,
        simpleCallback: SimpleCallback<Unit>
    ) {
        if (getCurrentState() !is FileExploreState.NoConnection) {
            simpleCallback.onError("Error state: ${getCurrentState()}")
            return
        }
        val hasInvokeCallback = AtomicBoolean(false)
        heartbeatTaskFuture.get()?.cancel(true)
        heartbeatTaskFuture.set(null)
        newState(FileExploreState.Requesting)
        // Client connection task.
        val exploreTask = NettyTcpClientConnectionTask(
            serverAddress = serverAddress,
            serverPort = TransferProtoConstant.FILE_EXPLORE_PORT,
            idleLimitDuration = heartbeatInterval * 3
        ).withClient<ConnectionClientImpl>(log = log)
            .withServer<ConnectionServerClientImpl>(log = log)
        exploreTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.Error ||
                        nettyState is NettyTaskState.ConnectionClosed ||
                        getCurrentState() !is FileExploreState.Requesting) {
                    // Create client connection fail.
                    val errorMsg = "Connect error: $nettyState, ${getCurrentState()}"
                    log.e(TAG, errorMsg)
                    if (hasInvokeCallback.compareAndSet(false, true)) {
                        simpleCallback.onError(errorMsg)
                    }
                    exploreTask.stopTask()
                    exploreTask.removeObserver(this)
                    newState(FileExploreState.NoConnection)
                } else {
                    // Create client connection success.
                    if (nettyState is NettyTaskState.ConnectionActive) {
                        newState(FileExploreState.Connected)
                        log.d(TAG, "Connect success.")
                        exploreTask.addObserver(closeObserver)
                        this@FileExplore.exploreTask.get()?.stopTask()
                        this@FileExplore.exploreTask.set(exploreTask)
                        exploreTask.registerServer(scanDirServer)
                        exploreTask.registerServer(sendFilesServer)
                        exploreTask.registerServer(downloadFilesServer)
                        exploreTask.registerServer(sendMsgServer)
                        exploreTask.removeObserver(this)
                        if (hasInvokeCallback.compareAndSet(false, true)) {
                            simpleCallback.onSuccess(Unit)
                        }
                        // Start heartbeat task, send a heartbeat each 8000 milliseconds default.
                        val future = taskScheduleExecutor.scheduleWithFixedDelay(
                            {
                                sendHeartbeat()
                            },
                            heartbeatInterval,
                            heartbeatInterval,
                            TimeUnit.MILLISECONDS
                        )
                        heartbeatTaskFuture.set(future)
                    }
                }
            }
            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {}

        })
        /**
         * Step1: Start client connection task.
         */
        exploreTask.startTask()
    }

    fun closeConnectionIfActive() {
        exploreTask.get()?.stopTask()
        exploreTask.set(null)
        serverTask.get()?.stopTask()
        serverTask.set(null)
        heartbeatTaskFuture.get()?.cancel(true)
        heartbeatTaskFuture.set(null)
        newState(FileExploreState.NoConnection)
        clearObserves()
    }

    /**
     * Client request handshake to server.
     */
    fun requestHandshake(simpleCallback: SimpleCallback<Handshake>) {
        assertState(false, simpleCallback) { task, _ ->
            task.requestSimplify<HandshakeReq, HandshakeResp>(
                type = FileExploreDataType.HandshakeReq.type,
                request = HandshakeReq(
                    version = TransferProtoConstant.VERSION,
                    fileSeparator = File.separator
                ),
                callback = object : IClientManager.RequestCallback<HandshakeResp> {
                    override fun onSuccess(
                        type: Int,
                        messageId: Long,
                        localAddress: InetSocketAddress?,
                        remoteAddress: InetSocketAddress?,
                        d: HandshakeResp
                    ) {
                        val currentState = getCurrentState()
                        if (currentState is FileExploreState.Connected) {
                            val handshake = Handshake(d.fileSeparator)
                            simpleCallback.onSuccess(handshake)
                            newState(FileExploreState.Active(handshake))
                        } else {
                            val msg = "Handshake error: state error $currentState, $d"
                            simpleCallback.onError(msg)
                            log.e(TAG, msg)
                        }
                    }

                    override fun onFail(errorMsg: String) {
                        simpleCallback.onError(errorMsg)
                        log.e(TAG, "Handshake error: $errorMsg")
                    }
                }
            )
        }
    }


    /**
     * Client or server request remote [dirPath]'s children files.
     */
    fun requestScanDir(dirPath: String, simpleCallback: SimpleCallback<ScanDirResp>) {
        assertState(simpleCallback = simpleCallback) { task, _ ->
            task.requestSimplify(
                type = FileExploreDataType.ScanDirReq.type,
                request = ScanDirReq(dirPath),
                callback = object : IClientManager.RequestCallback<ScanDirResp> {

                    override fun onSuccess(
                        type: Int,
                        messageId: Long,
                        localAddress: InetSocketAddress?,
                        remoteAddress: InetSocketAddress?,
                        d: ScanDirResp
                    ) {
                        simpleCallback.onSuccess(d)
                    }

                    override fun onFail(errorMsg: String) {
                        simpleCallback.onError(errorMsg)
                    }

                }
            )
        }
    }

    /**
     * Client or server wants to send files to remote.
     */
    fun requestSendFiles(sendFiles: List<FileExploreFile>, maxConnection: Int,  simpleCallback: SimpleCallback<SendFilesResp>) {
        assertState(simpleCallback = simpleCallback) { task, _ ->
            task.requestSimplify(
                type = FileExploreDataType.SendFilesReq.type,
                request = SendFilesReq(sendFiles = sendFiles, maxConnection = maxConnection),
                callback = object : IClientManager.RequestCallback<SendFilesResp> {

                    override fun onSuccess(
                        type: Int,
                        messageId: Long,
                        localAddress: InetSocketAddress?,
                        remoteAddress: InetSocketAddress?,
                        d: SendFilesResp
                    ) {
                        simpleCallback.onSuccess(d)
                    }

                    override fun onFail(errorMsg: String) {
                        simpleCallback.onError(errorMsg)
                    }

                }
            )
        }
    }

    /**
     * Client or server want download files from remote.
     */
    fun requestDownloadFiles(downloadFiles: List<FileExploreFile>, bufferSize: Int,  simpleCallback: SimpleCallback<DownloadFilesResp>) {
        assertState(simpleCallback = simpleCallback) { task, _ ->
            task.requestSimplify(
                type = FileExploreDataType.DownloadFilesReq.type,
                request = DownloadFilesReq(downloadFiles = downloadFiles, bufferSize = bufferSize),
                callback = object : IClientManager.RequestCallback<DownloadFilesResp> {

                    override fun onSuccess(
                        type: Int,
                        messageId: Long,
                        localAddress: InetSocketAddress?,
                        remoteAddress: InetSocketAddress?,
                        d: DownloadFilesResp
                    ) {
                        simpleCallback.onSuccess(d)
                    }

                    override fun onFail(errorMsg: String) {
                        simpleCallback.onError(errorMsg)
                    }

                }
            )
        }
    }

    /**
     * Client or server send message to remote.
     */
    fun requestMsg(msg: String,  simpleCallback: SimpleCallback<Unit>) {
        assertState(simpleCallback = simpleCallback) { task, _ ->
            task.requestSimplify(
                type = FileExploreDataType.SendMsgReq.type,
                request = SendMsgReq(sendTime = System.currentTimeMillis(), msg = msg),
                callback = object : IClientManager.RequestCallback<Unit> {

                    override fun onSuccess(
                        type: Int,
                        messageId: Long,
                        localAddress: InetSocketAddress?,
                        remoteAddress: InetSocketAddress?,
                        d: Unit
                    ) {
                        simpleCallback.onSuccess(d)
                    }

                    override fun onFail(errorMsg: String) {
                        simpleCallback.onError(errorMsg)
                    }

                }
            )
        }
    }

    override fun onNewState(s: FileExploreState) {
        for (o in observers) {
            o.onNewState(s)
        }
    }

    /**
     * Client send heartbeat to server.
     */
    private fun sendHeartbeat() {
        assertState<Unit>(false, null) { task, _ ->
            task.requestSimplify<Unit, Unit>(
                type = FileExploreDataType.HeartbeatReq.type,
                request = Unit,
                callback = object : IClientManager.RequestCallback<Unit> {

                    override fun onSuccess(
                        type: Int,
                        messageId: Long,
                        localAddress: InetSocketAddress?,
                        remoteAddress: InetSocketAddress?,
                        d: Unit
                    ) {
                        log.d(TAG, "Send heartbeat success")
                    }

                    override fun onFail(errorMsg: String) {
                        log.e(TAG, "Send heartbeat fail: $errorMsg")
                    }

                }
            )
        }
    }

    private fun <T> assertState(
        assertHandshake: Boolean = true,
        simpleCallback: SimpleCallback<T>?,
        success: (connectTask: ConnectionServerClientImpl, handShake: Handshake?) -> Unit) {
        val task = exploreTask.get()
        if (task == null) {
            simpleCallback?.onError("Connection task is null.")
            return
        }
        val currentState = getCurrentState()
        if (assertHandshake) {
            if (currentState is FileExploreState.Active) {
                success(task, currentState.handshake)
            } else {
                simpleCallback?.onError("State error: $currentState")
            }
        } else {
            if (currentState is FileExploreState.Active || currentState is FileExploreState.Connected) {
                success(task, (currentState as? FileExploreState.Active)?.handshake)
            } else {
                simpleCallback?.onError("State error: $currentState")
            }
        }
    }

    private fun dispatchNewMsg(msg: SendMsgReq) {
        for (o in observers) {
            o.onNewMsg(msg)
        }
    }

    companion object {
        private const val TAG = "FileExplore"
        private val taskScheduleExecutor: ScheduledExecutorService by lazy {
            Executors.newScheduledThreadPool(1) {
                Thread(it, "FileExploreTaskThread")
            }
        }
    }
}