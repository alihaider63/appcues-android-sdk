package com.appcues.debugger.screencapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.appcues.Appcues
import com.appcues.AppcuesConfig
import com.appcues.Screenshot
import com.appcues.data.remote.RemoteError
import com.appcues.data.remote.customerapi.CustomerApiRemoteSource
import com.appcues.data.remote.customerapi.response.PreUploadScreenshotResponse
import com.appcues.data.remote.imageupload.ImageUploadRemoteSource
import com.appcues.data.remote.interceptor.CustomerApiBaseUrlInterceptor
import com.appcues.data.remote.sdksettings.SdkSettingsRemoteSource
import com.appcues.monitor.AppcuesActivityMonitor
import com.appcues.ui.utils.getParentView
import com.appcues.util.ContextWrapper
import com.appcues.util.ResultOf
import com.appcues.util.ResultOf.Failure
import com.appcues.util.ResultOf.Success
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Date

internal class ScreenCaptureProcessor(
    private val config: AppcuesConfig,
    private val contextWrapper: ContextWrapper,
    private val sdkSettingsRemoteSource: SdkSettingsRemoteSource,
    private val customerApiRemoteSource: CustomerApiRemoteSource,
    private val imageUploadRemoteSource: ImageUploadRemoteSource,
) {

    suspend fun captureScreen(): Capture? {
        val activity = AppcuesActivityMonitor.activity ?: return null
        return activity.getParentView().let {

            val timestamp = Date()
            val displayName = it.screenCaptureDisplayName()
            val screenshot = Appcues.elementTargeting.captureScreenshot() ?: it.screenshot(activity.window)
            val layout = Appcues.elementTargeting.captureLayout()
            val capture = if (screenshot != null && layout != null) {
                Capture(
                    appId = config.applicationId,
                    displayName = displayName,
                    screenshotImageUrl = null,
                    layout = layout,
                    metadata = contextWrapper.generateCaptureMetadata(screenshot),
                    timestamp = timestamp,
                ).apply {
                    this.screenshot = screenshot.bitmap
                }
            } else null

            capture
        }
    }

    suspend fun save(capture: Capture, token: String): ResultOf<Capture, RemoteError> {
        // Saving a screen is a 4-step chain. This is implemented here as a sequence of calls, chaining
        // a success continuation on each call to move to the next step. If any step fails, the RemoteError
        // is bubbled up to the caller of this function instead of the successful result, and an error
        // can be shown and handled.
        return try {
            // step 1 - get the settings, with the path to customer API
            configureCustomerApi()
                // step 2 - use the customer API path to get the link to upload the screenshot
                .getUploadPath(capture, token)
                // step 3 - upload the screenshot
                .uploadImage(capture)
                // step 4 - update the screenshot link and save the screen
                .saveScreen(token)
                .let { Success(it) }
        } catch (e: ScreenCaptureSaveException) {
            Failure(e.error)
        }
    }

    // step 1 - get the settings, with the path to customer API
    private suspend fun configureCustomerApi(): CustomerApiRemoteSource {
        return when (val settings = sdkSettingsRemoteSource.sdkSettings()) {
            is Failure -> throw ScreenCaptureSaveException(settings.reason)
            is Success -> {
                CustomerApiBaseUrlInterceptor.baseUrl = settings.value.services.customerApi.toHttpUrl()
                customerApiRemoteSource
            }
        }
    }

    // step 2 - use the customer API path to get the link to upload the screenshot
    private suspend fun CustomerApiRemoteSource.getUploadPath(capture: Capture, token: String): PreUploadScreenshotResponse {
        return when (val preUploadResponse = preUploadScreenshot(capture, token)) {
            is Failure -> throw ScreenCaptureSaveException(preUploadResponse.reason)
            is Success -> preUploadResponse.value
        }
    }

    // step 3 - upload the screenshot
    private suspend fun PreUploadScreenshotResponse.uploadImage(capture: Capture): Capture {
        val density = contextWrapper.displayMetrics.density
        val original = capture.screenshot
        val bitmap = Bitmap.createScaledBitmap(
            capture.screenshot,
            original.width.toDp(density),
            original.height.toDp(density),
            true
        )
        return when (
            val uploadResponse = imageUploadRemoteSource.upload(this.upload.presignedUrl, bitmap)
        ) {
            is Failure -> throw ScreenCaptureSaveException(uploadResponse.reason)
            is Success -> capture.copy(screenshotImageUrl = this.url)
        }
    }

    // step 4 - update the screenshot link and save the screen
    private suspend fun Capture.saveScreen(token: String) =
        when (val screenResult = customerApiRemoteSource.screen(this, token)) {
            is Failure -> throw ScreenCaptureSaveException(screenResult.reason)
            is Success -> this
        }

    private suspend fun View.screenshot(window: Window) =
        if (this.width > 0 && this.height > 0) {
            val bitmap = awaitCaptureBitmap(this, window)
            val density = context.resources.displayMetrics.density
            val width = width.toDp(density)
            val height = height.toDp(density)
            val insets = ViewCompat.getRootWindowInsets(this)?.getInsets(WindowInsetsCompat.Type.systemBars())
                ?: Insets.NONE

            Screenshot(
                bitmap = bitmap,
                size = Size(width, height),
                insets = Insets.of(
                    insets.left.toDp(density),
                    insets.top.toDp(density),
                    insets.right.toDp(density),
                    insets.bottom.toDp(density),
                )
            )
        } else {
            null
        }

    // converts async function (captureBitmap) to a suspend function that will await for completion
    private suspend fun awaitCaptureBitmap(view: View, window: Window): Bitmap {
        return with(CompletableDeferred<Bitmap>()) {
            captureBitmap(view, window) { complete(it) }

            await()
        }
    }

    private fun captureBitmap(view: View, window: Window, bitmapCallback: (Bitmap) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Above Android O, use PixelCopy
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val location = IntArray(2)
            view.getLocationInWindow(location)

            PixelCopy.request(
                window,
                Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                bitmap,
                {
                    if (it == PixelCopy.SUCCESS) {
                        bitmapCallback.invoke(bitmap)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } else {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            canvas.setBitmap(null)
            bitmapCallback.invoke(bitmap)
        }
    }
}

private fun View.screenCaptureDisplayName(): String {
    var name: String
    val activity = AppcuesActivityMonitor.activity
    if (activity != null) {
        name = activity.javaClass.simpleName
        if (name != "Activity") {
            name = name.replace("Activity", "")
        }
    } else {
        name = this.javaClass.simpleName
    }
    return name
}

private class ScreenCaptureSaveException(val error: RemoteError) : Exception("ScreenCaptureException: $error")
