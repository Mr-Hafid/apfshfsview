package com.apfshfsview.reader

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import com.apfshfsview.reader.bridge.ReaderEventsApi
import com.apfshfsview.reader.bridge.ReaderHostApi
import com.apfshfsview.reader.bridge.ReaderHostApiImpl
import com.apfshfsview.reader.export.DestinationPicker
import com.apfshfsview.reader.media.VolumePlayerViewFactory
import com.apfshfsview.reader.preview.PreviewCache
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private val destinationPicker = DestinationPicker(this)
    private var hostApi: ReaderHostApiImpl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) PreviewCache(this).clear()
        super.onCreate(savedInstanceState)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        val api = ReaderHostApiImpl(this, ReaderEventsApi(messenger), destinationPicker)
        ReaderHostApi.setUp(messenger, api)
        flutterEngine.platformViewsController.registry
            .registerViewFactory(VolumePlayerViewFactory.VIEW_TYPE, VolumePlayerViewFactory(api.players))
        hostApi = api
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        ReaderHostApi.setUp(flutterEngine.dartExecutor.binaryMessenger, null)
        hostApi?.dispose()
        hostApi = null
        super.cleanUpFlutterEngine(flutterEngine)
    }

    @Deprecated("Activity result API is unavailable on FlutterActivity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!destinationPicker.handleResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) hostApi?.onAttachedIntent(intent)
    }
}
