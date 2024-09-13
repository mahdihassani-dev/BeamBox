package com.tans.beambox.transferproto.filetransfer

import com.tans.beambox.transferproto.fileexplore.model.FileExploreFile

interface FileTransferObserver {

    fun onNewState(s: FileTransferState)

    fun onStartFile(file: FileExploreFile)

    fun onProgressUpdate(file: FileExploreFile, progress: Long)

    fun onEndFile(file: FileExploreFile)
}