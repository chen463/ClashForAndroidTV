package com.github.kr328.clash.design.dialog

import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.github.kr328.clash.common.ws.WebsocketOperation
import com.github.kr328.clash.common.ws.WebsocketResult
import com.github.kr328.clash.common.ws.WsMessageHandler
import com.github.kr328.clash.common.ws.WsServerContext
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DialogTextFieldBinding
import com.github.kr328.clash.design.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun Context.requestModelTextInput(
    initial: String,
    title: CharSequence,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String {
    return this.requestModelTextInput(initial, title, null, hint, error, validator)!!
}

suspend fun Context.requestModelTextInput(
    initial: String?,
    title: CharSequence,
    reset: CharSequence?,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String? {
    return suspendCancellableCoroutine {
        val binding = DialogTextFieldBinding
            .inflate(layoutInflater, this.root, false)

        var handler: WsMessageHandler? = null
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(binding.root)
            .setCancelable(true)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = binding.textField.text?.toString() ?: ""

                if (validator(text))
                    it.resume(text)
                else
                    it.resume(initial)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setOnDismissListener { _ ->
                WsServerContext.removeHandler(handler)
                if (!it.isCompleted)
                    it.resume(initial)
            }

        if (reset != null) {
            builder.setNeutralButton(reset) { _, _ ->
                it.resume(null)
            }
        }

        val dialog = builder.create()
        handler = WsMessageHandler { operation ->
            when (operation) {
                is WebsocketOperation.Input -> binding.textField.apply {
                    setText(operation.data)
                    setSelection(0, operation.data.length)
                }
                WebsocketOperation.Submit -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .performClick()
            }
            WebsocketResult.Success
        }
        it.invokeOnCancellation {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            if (hint != null) {
                binding.textLayout.hint = hint
            }

            WsServerContext.registerHandler(handler)
            val qrCodeUrl = "http://${WsServerContext.hostIp}:${WsServerContext.serverPort}"
            val bitMatrix = QRCodeWriter().encode(qrCodeUrl, BarcodeFormat.QR_CODE, 256, 256)
            val bitmap = Bitmap.createBitmap(
                bitMatrix.width,
                bitMatrix.height,
                Bitmap.Config.RGB_565
            )

            for (x in 0 until bitMatrix.width) {
                for (y in 0 until bitMatrix.height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.qrCode.setImageBitmap(bitmap)
            binding.textField.apply {
                binding.textLayout.isErrorEnabled = error != null
                doOnTextChanged { text, _, _, _ ->
                    if (!validator(text?.toString() ?: "")) {
                        if (error != null)
                            binding.textLayout.error = error

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    } else {
                        if (error != null)
                            binding.textLayout.error = null

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }

                setText(initial)

                setSelection(0, initial?.length ?: 0)

                requestTextInput()
            }
        }

        dialog.show()
    }
}