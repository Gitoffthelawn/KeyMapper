package io.github.sds100.keymapper.util.delegate

import android.view.KeyEvent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.github.sds100.keymapper.Constants
import io.github.sds100.keymapper.data.model.Action
import io.github.sds100.keymapper.data.model.KeyMap
import io.github.sds100.keymapper.data.model.Trigger
import io.github.sds100.keymapper.data.model.Trigger.Companion.LONG_PRESS
import io.github.sds100.keymapper.util.ActionType
import io.github.sds100.keymapper.util.IClock
import io.github.sds100.keymapper.util.SystemAction
import junit.framework.Assert.assertEquals
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import junitparams.naming.TestCaseName
import kotlinx.coroutines.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Created by sds100 on 17/05/2020.
 */

@ExperimentalCoroutinesApi
@RunWith(JUnitParamsRunner::class)
class KeymapDetectionDelegateTest {

    companion object {
        private const val FAKE_KEYBOARD_DESCRIPTOR = "fake_keyboard"
        private const val FAKE_HEADPHONE_DESCRIPTOR = "fake_headphone"

        private val FAKE_DESCRIPTORS = arrayOf(
            FAKE_KEYBOARD_DESCRIPTOR,
            FAKE_HEADPHONE_DESCRIPTOR
        )

        private const val LONG_PRESS_DELAY = 500
        private const val DOUBLE_PRESS_DELAY = 300

        private val TEST_ACTION = Action(ActionType.SYSTEM_ACTION, SystemAction.TOGGLE_FLASHLIGHT)
        private val TEST_ACTION_2 = Action(ActionType.APP, Constants.PACKAGE_NAME)

        private val TEST_ACTIONS = arrayOf(
            TEST_ACTION,
            TEST_ACTION_2
        )
    }

    private lateinit var mDelegate: KeymapDetectionDelegate

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun init() {
        val iClock = object : IClock {
            override val currentTime: Long
                get() = System.currentTimeMillis()
        }

        mDelegate = KeymapDetectionDelegate(GlobalScope, LONG_PRESS_DELAY, DOUBLE_PRESS_DELAY, iClock)
    }

    @Test
    fun longPressSequenceTrigger_validLongPressTooShort_keyImitated() {
        val trigger = sequenceTrigger(
            Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, clickType = LONG_PRESS),
            Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP)
        )

        val keymap = createKeymapFromTriggerKey(0, *trigger.keys.toTypedArray())
        mDelegate.keyMapListCache = listOf(keymap)

        runBlocking {
            mockTriggerKeyInput(Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, clickType = LONG_PRESS), 100)
        }

        val value = mDelegate.imitateButtonPress.getOrAwaitValue()

        assertEquals(KeyEvent.KEYCODE_VOLUME_DOWN, value.getContentIfNotHandled())
    }

    @Test
    @Parameters(method = "params_multipleActionsPerformed")
    fun validInput_multipleActionsPerformed(description: String, trigger: Trigger) {
        //GIVEN
        val keymap = KeyMap(0, trigger, TEST_ACTIONS.toList())
        mDelegate.keyMapListCache = listOf(keymap)

        //WHEN
        if (keymap.trigger.mode == Trigger.PARALLEL) {
            mockParallelTriggerKeys(*keymap.trigger.keys.toTypedArray())
        } else {
            runBlocking {
                keymap.trigger.keys.forEach {
                    mockTriggerKeyInput(it)
                }
            }
        }

        //THEN
        var actionPerformedCount = 0

        for (i in TEST_ACTIONS.indices) {
            mDelegate.performAction.getOrAwaitValue()
            actionPerformedCount++
        }

        assertEquals(TEST_ACTIONS.size, actionPerformedCount)
    }

    fun params_multipleActionsPerformed() = listOf(
        arrayOf("sequence", sequenceTrigger(Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE))),
        arrayOf("parallel", parallelTrigger(
            Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE),
            Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, Trigger.Key.DEVICE_ID_THIS_DEVICE)
        ))
    )

    @Test
    @Parameters(method = "params_allTriggerKeyCombinations")
    @TestCaseName("{0}")
    fun invalidInput_downNotConsumed(description: String, keymap: KeyMap) {
        //GIVEN
        mDelegate.keyMapListCache = listOf(keymap)

        //WHEN
        var consumedCount = 0

        keymap.trigger.keys.forEach {
            val consumed = inputKeyEvent(999, KeyEvent.ACTION_DOWN, deviceIdToDescriptor(it.deviceId))

            if (consumed) {
                consumedCount++
            }
        }

        //THEN
        assertEquals(0, consumedCount)
    }

    @Test
    @Parameters(method = "params_allTriggerKeyCombinations")
    @TestCaseName("{0}")
    fun validInput_downConsumed(description: String, keymap: KeyMap) {
        //GIVEN
        mDelegate.keyMapListCache = listOf(keymap)

        var consumedCount = 0

        keymap.trigger.keys.forEach {
            val consumed = inputKeyEvent(it.keyCode, KeyEvent.ACTION_DOWN, deviceIdToDescriptor(it.deviceId))

            if (consumed) {
                consumedCount++
            }
        }

        assertEquals(keymap.trigger.keys.size, consumedCount)
    }

    fun params_allTriggerKeyCombinations(): List<Array<Any>> {
        val triggerAndDescriptions = listOf(
            "sequence single short-press this-device" to sequenceTrigger(Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE)),
            "sequence single long-press this-device" to sequenceTrigger(Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.LONG_PRESS)),
            "sequence single double-press this-device" to sequenceTrigger(Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.DOUBLE_PRESS)),
            "sequence multiple short-press this-device" to sequenceTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.SHORT_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.SHORT_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.SHORT_PRESS)
            ),
            "sequence multiple long-press this-device" to sequenceTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.LONG_PRESS)
            ),
            "sequence multiple double-press this-device" to sequenceTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.DOUBLE_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.DOUBLE_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.DOUBLE_PRESS)
            ),
            "sequence multiple mix this-device" to sequenceTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.SHORT_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.DOUBLE_PRESS)
            ),
            "sequence multiple mix external-device" to sequenceTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, FAKE_KEYBOARD_DESCRIPTOR, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, FAKE_HEADPHONE_DESCRIPTOR, clickType = Trigger.SHORT_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, FAKE_HEADPHONE_DESCRIPTOR, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, FAKE_KEYBOARD_DESCRIPTOR, clickType = Trigger.DOUBLE_PRESS)
            ),
            "parallel multiple short-press this-device" to parallelTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.SHORT_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.SHORT_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.SHORT_PRESS)
            ),
            "parallel multiple long-press this-device" to parallelTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, Trigger.Key.DEVICE_ID_THIS_DEVICE, clickType = Trigger.LONG_PRESS)
            ),
            "parallel multiple short-press external-device" to parallelTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, FAKE_KEYBOARD_DESCRIPTOR, clickType = Trigger.SHORT_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, FAKE_HEADPHONE_DESCRIPTOR, clickType = Trigger.SHORT_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, FAKE_HEADPHONE_DESCRIPTOR, clickType = Trigger.SHORT_PRESS)
            ),
            "parallel multiple long-press external-device" to parallelTrigger(
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_DOWN, FAKE_HEADPHONE_DESCRIPTOR, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_VOLUME_UP, FAKE_HEADPHONE_DESCRIPTOR, clickType = Trigger.LONG_PRESS),
                Trigger.Key(KeyEvent.KEYCODE_A, FAKE_HEADPHONE_DESCRIPTOR, clickType = Trigger.LONG_PRESS)
            )
        )

        return triggerAndDescriptions.mapIndexed { i, triggerAndDescription ->
            arrayOf(triggerAndDescription.first, KeyMap(i.toLong(), triggerAndDescription.second, listOf(TEST_ACTION)))
        }
    }

    @Test
    @Parameters(method = "params_allTriggerKeyCombinations")
    @TestCaseName("{0}")
    fun validInput_actionPerformed(description: String, keymap: KeyMap) {
        mDelegate.keyMapListCache = listOf(keymap)

        //WHEN
        if (keymap.trigger.mode == Trigger.PARALLEL) {
            mockParallelTriggerKeys(*keymap.trigger.keys.toTypedArray())
        } else {
            runBlocking {
                keymap.trigger.keys.forEach {
                    mockTriggerKeyInput(it)
                }
            }
        }

        //THEN
        val value = mDelegate.performAction.getOrAwaitValue()

        assertThat(value.getContentIfNotHandled(), `is`(TEST_ACTION))
    }

    private fun sequenceTrigger(vararg key: Trigger.Key) = Trigger(key.toList()).apply { mode = Trigger.SEQUENCE }
    private fun parallelTrigger(vararg key: Trigger.Key) = Trigger(key.toList()).apply { mode = Trigger.PARALLEL }

    private suspend fun mockTriggerKeyInput(key: Trigger.Key, delay: Long? = null) {
        val deviceDescriptor = deviceIdToDescriptor(key.deviceId)
        val pressDelay: Long = delay ?: when (key.clickType) {
            Trigger.LONG_PRESS -> 600L
            else -> 50L
        }

        inputKeyEvent(key.keyCode, KeyEvent.ACTION_DOWN, deviceDescriptor)

        when (key.clickType) {
            Trigger.SHORT_PRESS -> {
                delay(pressDelay)
                inputKeyEvent(key.keyCode, KeyEvent.ACTION_UP, deviceDescriptor)
            }

            Trigger.LONG_PRESS -> {
                delay(pressDelay)
                inputKeyEvent(key.keyCode, KeyEvent.ACTION_UP, deviceDescriptor)
            }

            Trigger.DOUBLE_PRESS -> {
                delay(pressDelay)
                inputKeyEvent(key.keyCode, KeyEvent.ACTION_UP, deviceDescriptor)
                delay(pressDelay)

                inputKeyEvent(key.keyCode, KeyEvent.ACTION_DOWN, deviceDescriptor)
                delay(pressDelay)
                inputKeyEvent(key.keyCode, KeyEvent.ACTION_UP, deviceDescriptor)
            }
        }
    }

    private fun inputKeyEvent(keyCode: Int, action: Int, deviceDescriptor: String?) =
        mDelegate.onKeyEvent(
            keyCode,
            action,
            deviceDescriptor ?: "",
            isExternal = deviceDescriptor != null
        )

    private fun mockParallelTriggerKeys(
        vararg key: Trigger.Key,
        delay: Long? = null) {
        key.forEach {
            val deviceDescriptor = deviceIdToDescriptor(it.deviceId)

            inputKeyEvent(it.keyCode, KeyEvent.ACTION_DOWN, deviceDescriptor)
        }

        GlobalScope.launch {
            if (delay != null) {
                delay(delay)
            } else {
                when (key[0].clickType) {
                    Trigger.SHORT_PRESS -> delay(50)
                    Trigger.LONG_PRESS -> delay(600)
                }
            }

            key.forEach {
                val deviceDescriptor = deviceIdToDescriptor(it.deviceId)

                inputKeyEvent(it.keyCode, KeyEvent.ACTION_UP, deviceDescriptor)
            }
        }
    }

    private fun deviceIdToDescriptor(deviceId: String): String? {
        return when (deviceId) {
            Trigger.Key.DEVICE_ID_THIS_DEVICE -> null
            Trigger.Key.DEVICE_ID_ANY_DEVICE -> {
                val randomInt = Random.nextInt(-1, FAKE_DESCRIPTORS.lastIndex)

                if (randomInt == -1) {
                    ""
                } else {
                    FAKE_DESCRIPTORS[randomInt]
                }
            }
            else -> deviceId
        }
    }

    private fun createKeymapFromTriggerKey(id: Long, vararg key: Trigger.Key) =
        KeyMap(id, Trigger(keys = key.toList()), actionList = listOf(TEST_ACTION))
}