package com.tans.beambox.ui.connection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import com.tans.beambox.R
import com.tans.beambox.databinding.ConnectionActivityBinding
import com.tans.beambox.logs.AndroidLog
import com.tans.beambox.ui.commomdialog.showOptionalDialogSuspend
import com.tans.beambox.ui.commomdialog.showSettingsDialog
import com.tans.beambox.ui.connection.home.HomeFragment
import com.tans.beambox.ui.connection.localconnetion.LocalNetworkConnectionFragment
import com.tans.beambox.ui.connection.wifip2pconnection.WifiP2pConnectionFragment
import com.tans.beambox.utils.uri2FileReal
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.permission.permissionsRequestSuspend
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import com.tans.tuiutils.view.clicks
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File


@SystemBarStyle(statusBarThemeStyle = 1, navigationBarThemeStyle = 1)
class ConnectionActivity :
    BaseCoroutineStateActivity<ConnectionActivity.Companion.ConnectionActivityState>(
        defaultState = ConnectionActivityState()
    ), EventListener {
    override val layoutId: Int = R.layout.connection_activity

    private val wifiP2pFragment by lazyViewModelField("wifiP2pFragment") {
        WifiP2pConnectionFragment(this)
    }

    private val homeFragment by lazyViewModelField("homeFragment") {
        HomeFragment(this)
    }

    private val localNetworkFragment by lazyViewModelField("localNetworkFragment") {
        LocalNetworkConnectionFragment()
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "AppPreferences"
    private val KEY_DIALOG_SHOWN = "DialogShown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkIntentAction(intent)

        TapsellPlus.initialize(
            this, "KEY",
            object : TapsellPlusInitListener {
                override fun onInitializeSuccess(adNetworks: AdNetworks) {
                    Log.d("onInitializeSuccess", adNetworks.name)
                }

                override fun onInitializeFailed(
                    adNetworks: AdNetworks,
                    adNetworkError: AdNetworkError
                ) {
                    Log.e(
                        "onInitializeFailed",
                        "ad network: " + adNetworks.name + ", error: " + adNetworkError.errorMessage
                    )
                }
            })
    }

    override fun onResume() {
        super.onResume()
        // Check if Wi-Fi or Location is still off and whether dialog was previously shown
        if(sharedPreferences.getBoolean(KEY_DIALOG_SHOWN, false)){
            if (!isWifiEnabled() || !isLocationEnabled()) {
                showSettingsDialog()
            }
        }

        // Reset the flag so that dialog does not show on the first launch
        sharedPreferences.edit().putBoolean(KEY_DIALOG_SHOWN, false).apply()
    }


    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        onBackPressedDispatcher.addCallback {
            finish()
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = ConnectionActivityBinding.bind(contentView)
        launch {
            val permissionsNeed = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_IMAGES)
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_AUDIO)
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_VIDEO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        permissionsNeed.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    }
                } else {
                    permissionsNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            } else {
                permissionsNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissionsNeed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsNeed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                permissionsNeed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionsNeed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            runCatching {
                permissionsRequestSuspend(*permissionsNeed.toTypedArray())
            }.onSuccess { (_, denied) ->
                if (denied.isNotEmpty()) {
                    AndroidLog.e(TAG, "Contains denied permissions: $denied")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {

                    val grant =
                        this@ConnectionActivity.supportFragmentManager.showOptionalDialogSuspend(
                            title = getString(R.string.permission_request_title),
                            message = getString(R.string.permission_storage_request_content)
                        )

                    if (grant == true) {
                        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        i.data = Uri.fromParts("package", packageName, null)
                        startActivity(i)
                    }
                }
            }.onFailure {
                AndroidLog.e(TAG, "Request permission error: ${it.message}", it)
            }
        }

        viewBinding.toolBar.menu.findItem(R.id.settings).setOnMenuItemClickListener {
            this@ConnectionActivity.supportFragmentManager.showSettingsDialog()
            true
        }

        val homeTransaction = supportFragmentManager.beginTransaction()
        if (supportFragmentManager.findFragmentByTag(HOME_FRAGMENT_TAG) == null) {
            homeTransaction.add(
                R.id.wifi_p2p_fragment_container,
                homeFragment,
                HOME_FRAGMENT_TAG
            )
        }
        homeTransaction.commitAllowingStateLoss()


        renderStateNewCoroutine({ it.requestShareFiles }) { requestShareFiles ->
            if (requestShareFiles.isNotEmpty()) {
                viewBinding.requestShareLayout.visibility = View.VISIBLE
                viewBinding.requestShareTv.text =
                    getString(R.string.request_share_files, requestShareFiles.size)
            } else {
                viewBinding.requestShareLayout.visibility = View.GONE
            }
        }
        viewBinding.dropRequestShareBt.clicks(this) {
            updateState { it.copy(requestShareFiles = emptyList()) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkIntentAction(intent)
    }

    fun consumeRequestShareFiles(): List<String> {
        val files = currentState().requestShareFiles
        if (files.isNotEmpty()) {
            updateState { it.copy(requestShareFiles = emptyList()) }
        }
        return files.map { it.canonicalPath }
    }

    @Suppress("DEPRECATION")
    private fun checkIntentAction(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            AndroidLog.d(TAG, "Receive ACTION_SEND uri: $uri")
            if (uri != null) {
                handleSharedUris(listOf(uri))
            }
        }
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: emptyList<Uri>()
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.mapNotNull { it }
                    ?: emptyList<Uri>()
            }
            AndroidLog.d(
                TAG,
                "Receive ACTION_SEND_MULTIPLE uris: ${uris.joinToString { it.toString() }}"
            )
            if (uris.isNotEmpty()) {
                handleSharedUris(uris)
            }
        }
    }

    private fun handleSharedUris(uris: List<Uri>) {
        val files = uris.mapNotNull {
            val f = uri2FileReal(this@ConnectionActivity, it)
            if (f?.isFile == true && f.canRead() && f.length() > 0L) {
                f
            } else {
                null
            }
        }
        AndroidLog.d(TAG, "Handle shared files: $files")
        if (files.isNotEmpty()) {
            updateState {
                it.copy(requestShareFiles = files)
            }
        }
    }

    override fun onFindBtnClicked() {

        if (isWifiEnabled() && isLocationEnabled()) {
            // Both Wi-Fi and Location are enabled, replace the fragment
            replaceFragment(wifiP2pFragment, WIFI_P2P_CONNECTION_FRAGMENT_TAG, false)
        } else {
            // Either Wi-Fi or Location is disabled, show dialog
            showSettingsDialog()
        }
    }

    override fun onQrBtnClicked() {
        replaceFragment(localNetworkFragment, LOCAL_NETWORK_FRAGMENT_TAG, true)
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    private fun isLocationEnabled(): Boolean {
        val locationMode = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF
        )
        return locationMode != Settings.Secure.LOCATION_MODE_OFF
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_status, null)

        // Get references to the views in the custom layout
        val wifiIcon = dialogView.findViewById<ImageView>(R.id.wifi_icon)
        val wifiText = dialogView.findViewById<TextView>(R.id.wifi_text)
        val locationIcon = dialogView.findViewById<ImageView>(R.id.location_icon)
        val locationText = dialogView.findViewById<TextView>(R.id.location_text)

        val checkWifi = dialogView.findViewById<ImageView>(R.id.check_wifi)
        val checkLocation = dialogView.findViewById<ImageView>(R.id.check_location)

        // Update the UI based on the current status of Wi-Fi and Location
        if (isWifiEnabled()) {
            wifiIcon.setImageResource(R.drawable.baseline_wifi_24) // Assuming ic_wifi_on drawable exists
            wifiText.text = "Wi-Fi is on"
            dialogView.findViewById<FrameLayout>(R.id.btn_wifi_settings).visibility = View.INVISIBLE
            checkWifi.visibility = View.VISIBLE
        } else {
            wifiIcon.setImageResource(R.drawable.baseline_wifi_off_24) // Assuming ic_wifi_off drawable exists
            wifiText.text = "Wi-Fi is off"
            dialogView.findViewById<FrameLayout>(R.id.btn_wifi_settings).visibility = View.VISIBLE
            checkWifi.visibility = View.INVISIBLE
        }

        if (isLocationEnabled()) {
            locationIcon.setImageResource(R.drawable.baseline_location_on_24) // Assuming ic_location_on drawable exists
            locationText.text = "Location is on"
            dialogView.findViewById<FrameLayout>(R.id.btn_location_settings).visibility = View.INVISIBLE
            checkLocation.visibility = View.VISIBLE
        } else {
            locationIcon.setImageResource(R.drawable.baseline_location_off_24) // Assuming ic_location_off drawable exists
            locationText.text = "Location is off"
            dialogView.findViewById<FrameLayout>(R.id.btn_location_settings).visibility = View.VISIBLE
            checkLocation.visibility = View.INVISIBLE
        }

        // Create and show the dialog
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.show()

        // Set button click listeners
        dialogView.findViewById<FrameLayout>(R.id.btn_wifi_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            dialog.dismiss()
            // Set flag to indicate dialog has been shown
            sharedPreferences.edit().putBoolean(KEY_DIALOG_SHOWN, true).apply()
        }

        dialogView.findViewById<FrameLayout>(R.id.btn_location_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            dialog.dismiss()
            // Set flag to indicate dialog has been shown
            sharedPreferences.edit().putBoolean(KEY_DIALOG_SHOWN, true).apply()
        }
    }

    private fun replaceFragment(fragment: Fragment, tag: String, shouldAddToBackStack: Boolean) {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        if(shouldAddToBackStack)
            transaction.addToBackStack(null)
        transaction.replace(
            R.id.wifi_p2p_fragment_container,
            fragment,
            tag
        )
        transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
        transaction.commitAllowingStateLoss()
    }


    companion object {
        private const val TAG = "ConnectionActivity"


        private const val WIFI_P2P_CONNECTION_FRAGMENT_TAG = "WIFI_P2P_CONNECTION_FRAGMENT_TAG"
        private const val HOME_FRAGMENT_TAG = "WIFI_P2P_CONNECTION_FRAGMENT_TAG"
        private const val LOCAL_NETWORK_FRAGMENT_TAG = "LOCAL_NETWORK_FRAGMENT_TAG"

        data class ConnectionActivityState(
            val requestShareFiles: List<File> = emptyList()
        )
    }

}