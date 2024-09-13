package com.tans.beambox

import com.tans.beambox.netty.findLocalAddressV4
import com.tans.beambox.transferproto.fileexplore.FileExplore
import com.tans.beambox.transferproto.fileexplore.FileExploreRequestHandler
import com.tans.beambox.transferproto.fileexplore.connectSuspend
import com.tans.beambox.transferproto.fileexplore.handshakeSuspend
import com.tans.beambox.transferproto.fileexplore.model.DownloadFilesReq
import com.tans.beambox.transferproto.fileexplore.model.DownloadFilesResp
import com.tans.beambox.transferproto.fileexplore.model.ScanDirReq
import com.tans.beambox.transferproto.fileexplore.model.ScanDirResp
import com.tans.beambox.transferproto.fileexplore.model.SendFilesReq
import com.tans.beambox.transferproto.fileexplore.model.SendFilesResp
import com.tans.beambox.transferproto.fileexplore.waitClose
import kotlinx.coroutines.runBlocking

object FileExploreClientTest {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val address = findLocalAddressV4()[0]
        val fileExplore = FileExplore(
            log = TestLog,
            scanDirRequest = object : FileExploreRequestHandler<ScanDirReq, ScanDirResp> {
                override fun onRequest(isNew: Boolean, request: ScanDirReq): ScanDirResp? {
                    return null
                }
            },
            sendFilesRequest = object : FileExploreRequestHandler<SendFilesReq, SendFilesResp> {
                override fun onRequest(isNew: Boolean, request: SendFilesReq): SendFilesResp? {
                    return null
                }
            },
            downloadFileRequest = object : FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> {
                override fun onRequest(
                    isNew: Boolean,
                    request: DownloadFilesReq
                ): DownloadFilesResp? {
                    return null
                }
            }
        )
        fileExplore.connectSuspend(address)
        println("Client: connection success.")
        val handShake = fileExplore.handshakeSuspend()
        println("Client: handshake success: $handShake")
        fileExplore.waitClose()
        println("Client: closed")
    }
}