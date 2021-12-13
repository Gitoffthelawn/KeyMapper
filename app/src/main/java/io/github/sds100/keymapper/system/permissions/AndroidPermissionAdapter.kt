package io.github.sds100.keymapper.system.permissions

import android.Manifest
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.github.sds100.keymapper.Constants
import io.github.sds100.keymapper.shizuku.ShizukuUtils
import io.github.sds100.keymapper.system.DeviceAdmin
import io.github.sds100.keymapper.system.accessibility.ServiceAdapter
import io.github.sds100.keymapper.system.root.SuAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * Created by sds100 on 17/03/2021.
 */
class AndroidPermissionAdapter(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val suAdapter: SuAdapter,
    private val notificationReceiverAdapter: ServiceAdapter,
) : PermissionAdapter {
    companion object {
        const val REQUEST_SHIZUKU_PERMISSION = 1
    }

    private val ctx = context.applicationContext

    override val onPermissionsUpdate: MutableSharedFlow<Unit> = MutableSharedFlow()

    private val _request = MutableSharedFlow<Permission>()
    val request = _request.asSharedFlow()

    init {
        suAdapter.isGranted
            .drop(1)
            .onEach { onPermissionsChanged() }
            .launchIn(coroutineScope)

        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            coroutineScope.launch {
                if (requestCode == REQUEST_SHIZUKU_PERMISSION) {
                    onPermissionsUpdate.emit(Unit)
                }
            }
        }

        notificationReceiverAdapter.state
            .drop(1)
            .onEach { onPermissionsChanged() }
            .launchIn(coroutineScope)
    }

    override fun request(permission: Permission) {
        coroutineScope.launch { _request.emit(permission) }
    }

    override fun isGranted(permission: Permission): Boolean {

        return when (permission) {
            Permission.WRITE_SETTINGS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.System.canWrite(ctx)
                } else {
                    true
                }

            Permission.CAMERA ->
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.CAMERA
                ) == PERMISSION_GRANTED

            Permission.DEVICE_ADMIN -> {
                val devicePolicyManager: DevicePolicyManager = ctx.getSystemService()!!
                devicePolicyManager.isAdminActive(ComponentName(ctx, DeviceAdmin::class.java))
            }

            Permission.READ_PHONE_STATE -> ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_PHONE_STATE
            ) == PERMISSION_GRANTED

            Permission.ACCESS_NOTIFICATION_POLICY ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val notificationManager: NotificationManager = ctx.getSystemService()!!
                    notificationManager.isNotificationPolicyAccessGranted
                } else {
                    true
                }

            Permission.WRITE_SECURE_SETTINGS -> {
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.WRITE_SECURE_SETTINGS
                ) == PERMISSION_GRANTED
            }

            Permission.NOTIFICATION_LISTENER ->
                NotificationManagerCompat.getEnabledListenerPackages(ctx)
                    .contains(Constants.PACKAGE_NAME)

            Permission.CALL_PHONE ->
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.CALL_PHONE
                ) == PERMISSION_GRANTED

            Permission.ROOT -> suAdapter.isGranted.value

            Permission.IGNORE_BATTERY_OPTIMISATION ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val powerManager = ctx.getSystemService<PowerManager>()

                    val ignoringOptimisations =
                        powerManager?.isIgnoringBatteryOptimizations(Constants.PACKAGE_NAME)

                    when {
                        powerManager == null -> Timber.i("Power manager is null")
                        ignoringOptimisations == true -> Timber.i("Battery optimisation is disabled")
                        ignoringOptimisations == false -> Timber.e("Battery optimisation is enabled")
                    }

                    ignoringOptimisations ?: false
                } else {
                    true
                }

            //this check is super quick (~0ms) so this doesn't need to be cached.
            Permission.SHIZUKU -> {
                if (ShizukuUtils.isSupportedForSdkVersion() && Shizuku.getBinder() != null) {
                    Shizuku.checkSelfPermission() == PERMISSION_GRANTED
                } else {
                    false
                }
            }

            Permission.ACCESS_FINE_LOCATION ->
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PERMISSION_GRANTED
            
            Permission.ANSWER_PHONE_CALL ->
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PERMISSION_GRANTED
        }
    }

    override fun isGrantedFlow(permission: Permission): Flow<Boolean> {
        return callbackFlow {
            send(isGranted(permission))

            onPermissionsUpdate.collect {
                send(isGranted(permission))
            }
        }
    }

    fun onPermissionsChanged() {
        coroutineScope.launch {
            onPermissionsUpdate.emit(Unit)
        }
    }
}