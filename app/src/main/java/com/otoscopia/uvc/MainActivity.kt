package com.otoscopia.uvc

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream

const val ACTION_USB_PERMISSION = "com.otoscopia.uvc.USB_PERMISSION"
const val TAG = "MainActivity"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var usbDevice: UsbDevice
    private lateinit var usbRequest: UsbRequest
    private lateinit var connection: UsbDeviceConnection
    private lateinit var usbInterface: UsbInterface
    private lateinit var usbEndPoint: UsbEndpoint
    private val buffer = ByteArray(5)
    private var isStopped = false

    private lateinit var permissionIntent: PendingIntent

    private lateinit var textureView: TextureView

    private lateinit var backgroundHandlerThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.texture_view)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        glSurfaceView = GLSurfaceView(this)
        glSurfaceView.setEGLContextClientVersion(2) // Use OpenGL ES 2.0
        glSurfaceView.setRenderer(MyGLRenderer())

        getUsbDevice()
        startBackgroundThread()
    }

    private fun usbCommunication() {
        usbInterface = usbDevice.getInterface(0)
        usbEndPoint = usbInterface.getEndpoint(0)
        connection = usbManager.openDevice(usbDevice)
        connection.claimInterface(usbInterface, true)
        usbRequest = UsbRequest()
        usbRequest.initialize(connection, usbEndPoint)
        startReceivingFrames()
    }

    private fun startReceivingFrames() {
        isStopped = false

        val receivingThread = Thread {
            while (!isStopped) {
                val requestResult = connection.bulkTransfer(usbEndPoint, buffer, buffer.size, 1000)
                if (requestResult > 0) {
                    // Handle the received frame data in the buffer
                    handleReceivedFrame(buffer, requestResult)
                }
            }
        }
    }

    private fun handleReceivedFrame(frameData: ByteArray, bytesRead: Int) {
        val decodedBitmap = decodeFrame(frameData, bytesRead)

        // Render the decoded frame onto the TextureView
        renderFrameOnTextureView(decodedBitmap)
    }

    private fun decodeFrame(frameData: ByteArray, bytesRead: Int): Bitmap {
        // Assuming YUV format for example
        // You need to convert YUV to RGB or another usable format
        val yuvImage = YuvImage(frameData, ImageFormat.NV21, width, height, null)
        val byteArrayOutputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, byteArrayOutputStream)
        val jpegByteArray = byteArrayOutputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)
    }

    private fun renderFrameOnTextureView(bitmap: Bitmap) {
        textureView.post {
            // Update the TextureView with the rendered frame
            val canvas = textureView.lockCanvas()
            canvas?.drawBitmap(bitmap, 0f, 0f, null)
            if (canvas != null) {
                textureView.unlockCanvasAndPost(canvas)
            }
        }
    }



//    override fun onResume() {
//        super.onResume()
//        startBackgroundThread()
//        if (textureView.isAvailable) {
//            //  TODO
//        } else if (!textureView.isAvailable) {
//            TODO("ON RESUME")
//        }
//    }

    override fun onPause() {
        super.onPause()
        stopBackgroundThread()
    }

    private fun getUsbDevice() {
        val usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)

        if (usbDevice != null) {
            this.usbDevice = usbDevice
            Log.d(TAG, "DEVICE ID: $usbDevice.deviceId")

            if (!usbManager.hasPermission(usbDevice)) fetchPermissionStatus(usbDevice)
            else usbCommunication()

        } else {
            Log.d(TAG, "USB Device not found")
        }
    }

    private fun fetchPermissionStatus(usbDevice: UsbDevice) {
        val usbDevicePermission = usbManager.hasPermission(usbDevice)
        val cameraDevicePermission = checkSelfPermission(Manifest.permission.CAMERA)

        if (!usbDevicePermission) {
            permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(usbPermissionReceiver, filter)
            usbManager.requestPermission(usbDevice, permissionIntent)
        }

        if (cameraDevicePermission == -1) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
        }
    }

    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("Camera2")
        backgroundHandlerThread.start()
        backgroundHandler = Handler(backgroundHandlerThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread.quitSafely()
        try {
            backgroundHandlerThread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Exception: $e")
        }
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    TODO("TODO")
                }
            }
        }
    }
}

