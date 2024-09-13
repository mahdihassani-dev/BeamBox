package com.tans.beambox.netty.handlers

import com.tans.beambox.netty.PackageData
import com.tans.beambox.netty.readBytes
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

class BytesToPackageDataDecoder : ByteToMessageDecoder() {


    override fun decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        try {
            val type = buffer.readInt()
            val messageId = buffer.readLong()
            val body = buffer.readBytes()
            out.add(PackageData(type = type, messageId = messageId, body = body))
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            buffer.clear()
        }
    }
}