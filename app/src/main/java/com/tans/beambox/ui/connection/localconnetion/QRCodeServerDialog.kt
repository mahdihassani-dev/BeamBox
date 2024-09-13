package com.tans.beambox.ui.connection.localconnetion

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.tans.beambox.R
import com.tans.beambox.databinding.QrCodeServerDialogBinding
import com.tans.beambox.file.LOCAL_DEVICE
import com.tans.beambox.logs.AndroidLog
import com.tans.beambox.netty.toInt
import com.tans.beambox.transferproto.TransferProtoConstant
import com.tans.beambox.transferproto.broadcastconn.model.RemoteDevice
import com.tans.beambox.transferproto.qrscanconn.QRCodeScanServer
import com.tans.beambox.transferproto.qrscanconn.QRCodeScanServerObserver
import com.tans.beambox.transferproto.qrscanconn.QRCodeScanState
import com.tans.beambox.transferproto.qrscanconn.model.QRCodeShare
import com.tans.beambox.transferproto.qrscanconn.startQRCodeScanServerSuspend
import com.tans.beambox.ui.commomdialog.CoroutineDialogCancelableResultCallback
import com.tans.beambox.ui.commomdialog.coroutineShowSafe
import com.tans.beambox.utils.toJson
import com.tans.tuiutils.dialog.BaseCoroutineStateCancelableResultDialogFragment
import com.tans.tuiutils.dialog.DialogCancelableResultCallback
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.glxn.qrgen.android.QRCode
import java.net.InetAddress
import kotlin.coroutines.resume

class QRCodeServerDialog : BaseCoroutineStateCancelableResultDialogFragment<Unit, RemoteDevice> {

    private val qrcodeServer: QRCodeScanServer by lazy {
        QRCodeScanServer(log = AndroidLog)
    }

    private val localAddress: InetAddress?
    constructor() : super(Unit, null) {
        localAddress = null
    }

    constructor(localAddress: InetAddress, callback: DialogCancelableResultCallback<RemoteDevice>) : super(Unit, callback) {
        this.localAddress = localAddress
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.qr_code_server_dialog, parent, false)
    }

    override fun firstLaunchInitData() {}

    override fun bindContentView(view: View) {
        val localAddress = this.localAddress ?: return
        val viewBinding = QrCodeServerDialogBinding.bind(view)

        viewBinding.cancelButton.clicks(this) {
            onCancel()
        }

        launch(Dispatchers.IO) {
            qrcodeServer.addObserver(object : QRCodeScanServerObserver {

                // Client request transfer file.
                override fun requestTransferFile(remoteDevice: RemoteDevice) {
                    AndroidLog.d(TAG, "Receive request: $remoteDevice")
                    onResult(remoteDevice)
                }

                override fun onNewState(state: QRCodeScanState) {
                    AndroidLog.d(TAG, "Qrcode server state: $state")
                }
            })
            runCatching {
                // Start QR code server connection.
                qrcodeServer.startQRCodeScanServerSuspend(localAddress = localAddress)
            }.onSuccess {
                AndroidLog.d(TAG, "Bind address success.")
                runCatching {
                    // Create QRCode bitmap and display.
                    val qrcodeContent = QRCodeShare(
                        version = TransferProtoConstant.VERSION,
                        deviceName = LOCAL_DEVICE,
                        address = localAddress.toInt()
                    ).toJson()!!
                    QRCode.from(qrcodeContent).withSize(320, 320).bitmap()
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        viewBinding.qrCodeIv.setImageBitmap(it)
                    }
                }.onFailure {
                    AndroidLog.e(TAG, "Create qrcode fail: ${it.message}", it)
                    onCancel()
                }
            }.onFailure {
                AndroidLog.e(TAG, "Bind address: $localAddress fail.")
                onCancel()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            qrcodeServer.closeConnectionIfActive()
        }
    }

    companion object {
        private const val TAG = "QRCodeServerDialog"
    }
}

suspend fun FragmentManager.showQRCodeServerDialogSuspend(localAddress: InetAddress): RemoteDevice? {
    return suspendCancellableCoroutine { cont ->
        val d = QRCodeServerDialog(localAddress, CoroutineDialogCancelableResultCallback(cont))
        if (!coroutineShowSafe(d, "QRCodeServerDialog#${System.currentTimeMillis()}", cont)) {
            if (cont.isActive) {
                cont.resume(null)
            }
        }
    }
}