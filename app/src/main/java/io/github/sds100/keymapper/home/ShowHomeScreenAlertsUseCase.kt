package io.github.sds100.keymapper.home

import io.github.sds100.keymapper.data.Keys
import io.github.sds100.keymapper.data.repositories.PreferenceRepository
import io.github.sds100.keymapper.mappings.PauseMappingsUseCase
import io.github.sds100.keymapper.system.accessibility.ServiceAdapter
import io.github.sds100.keymapper.system.accessibility.ServiceState
import io.github.sds100.keymapper.system.permissions.Permission
import io.github.sds100.keymapper.system.permissions.PermissionAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map

/**
 * Created by sds100 on 04/04/2021.
 */

class ShowHomeScreenAlertsUseCaseImpl(
    private val preferences: PreferenceRepository,
    private val permissions: PermissionAdapter,
    private val accessibilityServiceAdapter: ServiceAdapter,
    private val pauseMappingsUseCase: PauseMappingsUseCase
) : ShowHomeScreenAlertsUseCase {
    override val hideAlerts: Flow<Boolean> =
        preferences.get(Keys.hideHomeScreenAlerts).map { it ?: false }

    override val isBatteryOptimised: Flow<Boolean> = channelFlow {
        send(!permissions.isGranted(Permission.IGNORE_BATTERY_OPTIMISATION))

        permissions.onPermissionsUpdate.collectLatest {
            send(!permissions.isGranted(Permission.IGNORE_BATTERY_OPTIMISATION))
        }
    }

    override val areMappingsPaused: Flow<Boolean> = pauseMappingsUseCase.isPaused

    override val isLoggingEnabled: Flow<Boolean> = preferences.get(Keys.log).map { it ?: false }

    override fun disableBatteryOptimisation() {
        permissions.request(Permission.IGNORE_BATTERY_OPTIMISATION)
    }

    override val accessibilityServiceState: Flow<ServiceState> = accessibilityServiceAdapter.state

    override fun startAccessibilityService(): Boolean {
        return accessibilityServiceAdapter.start()
    }

    override fun restartAccessibilityService(): Boolean {
        return accessibilityServiceAdapter.restart()
    }

    override fun resumeMappings() {
        pauseMappingsUseCase.resume()
    }

    override fun disableLogging() {
        preferences.set(Keys.log, false)
    }
}

interface ShowHomeScreenAlertsUseCase {
    val accessibilityServiceState: Flow<ServiceState>
    fun startAccessibilityService(): Boolean
    fun restartAccessibilityService(): Boolean

    val hideAlerts: Flow<Boolean>
    fun disableBatteryOptimisation()
    val isBatteryOptimised: Flow<Boolean>
    val areMappingsPaused: Flow<Boolean>
    fun resumeMappings()

    val isLoggingEnabled: Flow<Boolean>
    fun disableLogging()
}