package io.github.sds100.keymapper.system.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.getSystemService
import io.github.sds100.keymapper.util.Error
import io.github.sds100.keymapper.util.Result
import io.github.sds100.keymapper.util.Success

/**
 * Created by sds100 on 21/04/2021.
 */
class AndroidPhoneAdapter(context: Context) : PhoneAdapter {
    private val ctx: Context = context.applicationContext
    private val telecomManager: TelecomManager = ctx.getSystemService()!!

    override fun startCall(number: String): Result<*> {
        try {
            Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                ctx.startActivity(this)
            }

            return Success(Unit)
        } catch (e: ActivityNotFoundException) {
            return Error.NoAppToPhoneCall
        }
    }

    override fun answerCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telecomManager.acceptRingingCall()
        }
    }

    override fun endCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            telecomManager.endCall()
        }
    }
}