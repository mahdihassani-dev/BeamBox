package com.tans.beambox.transferproto

interface SimpleCallback<T> {

    fun onError(errorMsg: String) {}

    fun onSuccess(data: T) {}
}