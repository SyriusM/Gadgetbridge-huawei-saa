package nodomain.freeyourgadget.gadgetbridge.adapter

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.BondingUtil
import org.slf4j.LoggerFactory

object CompanionPairingHint {
    private val LOG = LoggerFactory.getLogger(CompanionPairingHint::class.java)

    private const val PREF_DISMISSED = "companion_pairing_hint_dismissed"
    private const val REQUEST_CODE = 1425

    @JvmStatic
    fun shouldShow(context: Context, device: GBDevice): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        if (!device.isInitialized) {
            return false
        }
        if (device.deviceCoordinator.connectionType == DeviceCoordinator.ConnectionType.USB) {
            return false
        }
        if (!BluetoothAdapter.checkBluetoothAddress(device.address)) {
            return false
        }
        if (GBApplication.getDeviceSpecificSharedPrefs(device.address).getBoolean(PREF_DISMISSED, false)) {
            return false
        }
        if (BondingUtil.getCompanionDeviceManager(context) == null) {
            return false
        }
        return !BondingUtil.isCompanionAssociated(context, device.address)
    }

    @JvmStatic
    fun dismiss(device: GBDevice) {
        LOG.debug("Dismissing companion pairing hint for {}", device.address)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
            putBoolean(PREF_DISMISSED, true)
        }
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.O)
    fun pair(activity: Activity, device: GBDevice) {
        @Suppress("MissingPermission")
        val bonded = BondingUtil.getBluetoothAdapter(activity)
            ?.getRemoteDevice(device.address)
            ?.bondState == BluetoothDevice.BOND_BONDED
        val useLe = !bonded && device.deviceCoordinator.connectionType == DeviceCoordinator.ConnectionType.BLE
        if (!useLe || !device.isConnected) {
            BondingUtil.associateCompanion(activity, device.address, useLe, REQUEST_CODE, null)
            return
        }
        // A connected BLE device does not advertise, so the companion scan would never find it.
        // Disconnect first, let the device start advertising, and reconnect once the chooser is done.
        LOG.debug("Disconnecting {} for companion association", device.address)
        reconnectAfterChooser = device
        GBApplication.deviceService(device).disconnect()
        Handler(Looper.getMainLooper()).postDelayed({
            BondingUtil.associateCompanion(activity, device.address, true, REQUEST_CODE, null) {
                reconnectIfPending(device.address)
            }
        }, ADVERTISING_DELAY_MS)
    }

    private const val ADVERTISING_DELAY_MS = 1500L
    private var reconnectAfterChooser: GBDevice? = null

    private fun reconnectIfPending(address: String) {
        val device = reconnectAfterChooser ?: return
        if (device.address != address) {
            return
        }
        reconnectAfterChooser = null
        LOG.debug("Reconnecting {} after companion association", address)
        GBApplication.deviceService(device).connect()
    }

    @JvmStatic
    fun onChooserClosed(context: Context, address: String) {
        if (BondingUtil.isCompanionAssociated(context, address)) {
            LOG.debug("{} is now associated as companion", address)
            BondingUtil.StartObserving(context, address)
        }
        reconnectIfPending(address)
    }
}
