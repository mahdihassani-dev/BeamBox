package com.tans.beambox.transferproto.filetransfer.model

import com.tans.beambox.transferproto.fileexplore.model.FileExploreFile
import java.io.File

data class SenderFile(
    val realFile: File,
    val exploreFile: FileExploreFile
)