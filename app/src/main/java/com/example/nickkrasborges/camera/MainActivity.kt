package com.example.nickkrasborges.camera

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import android.R.attr.bitmap
import android.util.Base64
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.content.Context.CAMERA_SERVICE
import android.support.v4.content.ContextCompat.getSystemService
import android.Manifest.permission
import android.content.Context
import android.support.v4.app.ActivityCompat
import android.hardware.camera2.params.StreamConfigurationMap
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import java.util.Collections.singletonList
import android.hardware.camera2.CameraDevice
import android.opengl.ETC1.getHeight
import android.opengl.ETC1.getWidth
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.Surface
import com.example.nickkrasborges.camera.R.id.textureView
import java.io.*
import java.text.Format
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    //lateinit var imageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        iniciaCamera()

    }

    /*fun iniciaCameraIntent(){
        var btnCamera: Button = findViewById<Button>(R.id.btnCamera) as Button
         imageView = findViewById<ImageView>(R.id.imageView) as ImageView
        btnCamera.setOnClickListener(View.OnClickListener {
            try {
                var intent: Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(intent, 0)

            } catch(exception: Exception){
                Toast.makeText(this, exception.message, Toast.LENGTH_LONG)
            }

        })
    }*/

/*
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        try {

            var bitmap: Bitmap = data!!.extras.get("data") as Bitmap
            imageView.setImageBitmap(bitmap)

            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            val encoded = Base64.encodeToString(byteArray, Base64.DEFAULT)

            Toast.makeText(this, encoded, Toast.LENGTH_LONG)

        } catch(exception: Exception){
            Toast.makeText(this, exception.message, Toast.LENGTH_LONG)
        }
    }

*/

    lateinit var fab_take_photo: Button
    lateinit var textureView: TextureView

    lateinit var cameraManager: CameraManager
    var cameraFacing: Int = 0
    var CAMERA_REQUEST_CODE = 200

    lateinit var surfaceTextureListener: TextureView.SurfaceTextureListener
    lateinit var previewSize: Size
    lateinit var cameraId: String

    var backgroundHandler: Handler? = null
    var backgroundThread: HandlerThread? = null

    var cameraDevices: CameraDevice? = null

    var stateCallback = object:CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            cameraDevices = cameraDevice
            createPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice?) {
            cameraDevice!!.close()
            cameraDevices = null

        }

        override fun onError(cameraDevice: CameraDevice?, p1: Int) {
            cameraDevice!!.close()
            cameraDevices = null
        }

    }

    var cameraCaptureSession: CameraCaptureSession? = null

    lateinit var captureRequestBuilder: CaptureRequest.Builder

    lateinit var captureRequest: CaptureRequest

    var galleryFolder: File? = null

    private fun lock() {
        try {
            cameraCaptureSession!!.capture(
                captureRequestBuilder.build(),
                null, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun unlock() {
        try {
            cameraCaptureSession!!.setRepeatingRequest(
                captureRequestBuilder.build(),
                null, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    fun iniciaCamera(){

        fab_take_photo = findViewById<Button>(R.id.fab_take_photo) as Button
        fab_take_photo.setOnClickListener({
            lock()
            var outputPhoto: FileOutputStream? = null
            try {
                createImageGallery()
                outputPhoto = FileOutputStream(createImageFile(galleryFolder!!))
                textureView.getBitmap().compress(Bitmap.CompressFormat.PNG, 100, outputPhoto)

                var file: File = File("/storage/emulated/0/Pictures/Camera/", "aaa7349841874557231051.jpg")
                var bitmap: Bitmap = BitmapFactory.decodeStream(FileInputStream(file))

                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                val byteArray = byteArrayOutputStream.toByteArray()

                val encoded = Base64.encodeToString(byteArray, Base64.DEFAULT)

                Toast.makeText(this, encoded, Toast.LENGTH_LONG)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                unlock()
                try {
                    if (outputPhoto != null) {
                        outputPhoto.close()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        })

        textureView = findViewById<TextureView>(R.id.textureView) as TextureView

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), CAMERA_REQUEST_CODE)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraFacing = CameraCharacteristics.LENS_FACING_BACK

        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                setUpCamera()
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {

            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {

            }
        }
    }

    private fun setUpCamera() {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) === cameraFacing) {
                    val streamConfigurationMap = cameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                    )
                    previewSize = streamConfigurationMap!!.getOutputSizes(SurfaceTexture::class.java)[0]
                    this.cameraId = cameraId
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun openBackgroundThread() {
        backgroundThread = HandlerThread("camera_background_thread")
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.getLooper())
    }

    override fun onResume() {
        super.onResume()
        openBackgroundThread()
        if (textureView.isAvailable()) {
            setUpCamera()
            openCamera()
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener)
        }
    }

    override fun onStop() {
        super.onStop()
        closeCamera()
        closeBackgroundThread()
    }

    private fun closeBackgroundThread() {
        if (backgroundHandler != null) {
            backgroundThread!!.quitSafely()
            backgroundThread = null
            backgroundHandler = null
        }
    }

    private fun closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession!!.close()
            cameraCaptureSession = null
        }

        if (cameraDevices != null) {
            cameraDevices!!.close()
            cameraDevices = null
        }
    }

    private fun createPreviewSession() {
        try {
            val surfaceTexture = textureView.surfaceTexture
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight())
            val previewSurface = Surface(surfaceTexture)
            captureRequestBuilder = cameraDevices!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(previewSurface)

            cameraDevices!!.createCaptureSession(Collections.singletonList(previewSurface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (cameraDevices == null) {
                            return
                        }

                        try {
                            captureRequest = captureRequestBuilder.build()
                            this@MainActivity.cameraCaptureSession = cameraCaptureSession
                            this@MainActivity.cameraCaptureSession!!.setRepeatingRequest(captureRequest, null, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }

                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {

                    }
                }, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }


    @Throws(IOException::class)
    private fun createImageFile(galleryFolder: File): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        //val imageFileName = "image_" + timeStamp + "_" //todo:
        val imageFileName = "aaa"
        return File.createTempFile(imageFileName, ".jpg", galleryFolder)
    }

    fun createImageGallery() {
        val storageDirectory: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        galleryFolder = File(storageDirectory, getResources().getString(R.string.app_name))
        if (!galleryFolder!!.exists()) {
            var wasCreated: Boolean = galleryFolder!!.mkdirs()
            if (!wasCreated) {
                Log.e("CapturedImages", "Failed to create directory")
            }
        }
    }
}
