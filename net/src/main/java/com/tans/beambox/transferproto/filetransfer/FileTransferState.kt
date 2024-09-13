package com.tans.beambox.transferproto.filetransfer


sealed class FileTransferState {
    data object NotExecute : FileTransferState()
    data object Started : FileTransferState()
    data object Canceled: FileTransferState()
    data object Finished : FileTransferState()
    data class Error(val msg: String) : FileTransferState()
    data class RemoteError(val msg: String) : FileTransferState()
}