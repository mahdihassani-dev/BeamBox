package com.tans.beambox.transferproto.filetransfer.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import com.tans.beambox.transferproto.fileexplore.model.FileExploreFile

@Keep
@JsonClass(generateAdapter = true)
data class DownloadReq(
    val file: FileExploreFile,
    val start: Long,
    val end: Long
)