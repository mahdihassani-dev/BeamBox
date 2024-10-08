package com.tans.beambox.transferproto.fileexplore.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class SendFilesResp(
    val bufferSize: Int
)
