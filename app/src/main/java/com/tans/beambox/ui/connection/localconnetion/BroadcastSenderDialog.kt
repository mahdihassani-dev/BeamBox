package com.tans.beambox.ui.connection.localconnetion

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.tans.beambox.R
import com.tans.beambox.databinding.BroadcastSenderDialogLayoutBinding
import com.tans.beambox.file.LOCAL_DEVICE
import com.tans.beambox.logs.AndroidLog
import com.tans.beambox.netty.getBroadcastAddress
import com.tans.beambox.transferproto.broadcastconn.BroadcastSender
import com.tans.beambox.transferproto.broadcastconn.BroadcastSenderObserver
import com.tans.beambox.transferproto.broadcastconn.BroadcastSenderState
import com.tans.beambox.transferproto.broadcastconn.model.RemoteDevice
import com.tans.beambox.transferproto.broadcastconn.startSenderSuspend
import com.tans.beambox.transferproto.broadcastconn.waitClose
import com.tans.beambox.ui.commomdialog.CoroutineDialogCancelableResultCallback
import com.tans.beambox.ui.commomdialog.coroutineShowSafe
import com.tans.beambox.utils.showToastShort
import com.tans.tuiutils.dialog.BaseCoroutineStateCancelableResultDialogFragment
import com.tans.tuiutils.dialog.DialogCancelableResultCallback
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.InetAddress
import kotlin.coroutines.resume

class BroadcastSenderDialog : BaseCoroutineStateCancelableResultDialogFragment<Unit, RemoteDevice> {

    private val sender: BroadcastSender by lazy {
        BroadcastSender(
            deviceName = LOCAL_DEVICE,
            log = AndroidLog
        )
    }

    private val localAddress: InetAddress?
    constructor() : super(Unit, null) {
        localAddress = null
    }

    constructor(localAddress: InetAddress, callback: DialogCancelableResultCallback<RemoteDevice>) : super(Unit, callback) {
        this.localAddress = localAddress
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
       return LayoutInflater.from(context).inflate(R.layout.broadcast_sender_dialog_layout, parent, false)
    }

    override fun firstLaunchInitData() {  }

    override fun bindContentView(view: View) {
        val localAddress = this.localAddress ?: return
        val viewBinding = BroadcastSenderDialogLayoutBinding.bind(view)
        viewBinding.cancelButton.clicks(this) {
            onCancel()
        }
        launch {
            runCatching {
                // Start broadcast server.
                withContext(Dispatchers.IO) {
                    sender.startSenderSuspend(localAddress, localAddress.getBroadcastAddress().first)
                }
            }.onSuccess {
                AndroidLog.d(TAG, "Start sender success.")
                sender.addObserver(object : BroadcastSenderObserver {

                    // new client transfer file request.
                    override fun requestTransferFile(remoteDevice: RemoteDevice) {
                        onResult(remoteDevice)
                    }

                    override fun onNewState(state: BroadcastSenderState) {
                        AndroidLog.d(TAG, "BroadcastSender state: $state")
                    }
                })

                // Waiting broadcast server error or closed.
                sender.waitClose()
                onCancel()
                requireActivity().showToastShort("Connection closed")
            }.onFailure {
                onCancel()
                AndroidLog.e(TAG, "Start sender error: ${it.message}")
                requireActivity().showToastShort(R.string.error_toast)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            sender.closeConnectionIfActive()
        }
    }

    companion object {
        private const val TAG = "BroadcastSenderDialog"
    }
}
suspend fun FragmentManager.showBroadcastSenderDialogSuspend(localAddress: InetAddress): RemoteDevice? {
    return suspendCancellableCoroutine { cont ->
        val d = BroadcastSenderDialog(localAddress, CoroutineDialogCancelableResultCallback(cont))
        if (!coroutineShowSafe(d, "BroadcastSenderDialog#${System.currentTimeMillis()}", cont)) {
            if (cont.isActive) {
                cont.resume(null)
            }
        }
    }
}