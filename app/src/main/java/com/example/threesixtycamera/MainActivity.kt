package com.example.threesixtycamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.threesixtycamera.network.SupabaseUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity(),
    android.hardware.SensorEventListener {

    private lateinit var previewView: PreviewView

    private lateinit var topBar: View
    private lateinit var btnCapture: View
    private lateinit var captureStopIcon: View
    private lateinit var btnGallery: View
    private lateinit var btnStart360: View
    private lateinit var btnFinish360: View

    private lateinit var panel360: View
    private lateinit var progress360: ProgressBar
    private lateinit var tvHint: TextView

    private lateinit var guideContainer: View
    private lateinit var progressLineSolid: View
    private lateinit var ivDirectionArrow: View

    private lateinit var panoResultOverlay: View
    private lateinit var ivPanoResult: Panorama360View
    private lateinit var btnClosePanoResult: View
    private lateinit var btnViewInGallery: View

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var sensorManager:
            android.hardware.SensorManager

    private var rotationSensor:
            android.hardware.Sensor? = null

    private var lastGyroTimestampNs = 0L
    private var gyroAngleDeg = 0f
    private var lastCapturedAngleDeg = 0f
    private var gyroDirection = 0f

    private var panoActive = false
    private var isCapturingFrame = false

    private val panoFrames =
        mutableListOf<PanoFrame>()



    private var lastCaptureTimeMs = 0L

    private var totalRotationDeg = 0f

    private var panoFileName = ""

    private var slowDownJob: Job? = null

    /*
     * More overlap than the old 25° system.
     */
    private val captureStepDeg = 12f

    /*
     * Almost one complete horizontal rotation.
     */
    private val targetTotalDeg = 360f

    /*
     * 19 frames × approximately 20°.
     */
    private val maxShots = 31

    private val fastRotationThresholdDegPerSec = 75f

    private val panoScope =
        CoroutineScope(
            Dispatchers.Default + SupervisorJob()
        )

    private val rotationMatrix =
        FloatArray(9)

    private val orientationAngles =
        FloatArray(3)

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            } else {

                Toast.makeText(
                    this,
                    "Camera permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        if (OpenCVLoader.initDebug()) {
            Log.d(
                TAG,
                "OpenCV loaded"
            )
        } else {
            Log.e(
                TAG,
                "OpenCV initialization failed"
            )
        }

        previewView =
            findViewById(R.id.previewView)

        topBar =
            findViewById(R.id.topBar)

        btnCapture =
            findViewById(R.id.btnCapture)

        captureStopIcon =
            findViewById(R.id.captureStopIcon)

        btnGallery =
            findViewById(R.id.btnGallery)

        btnStart360 =
            findViewById(R.id.btnStart360)

        btnFinish360 =
            findViewById(R.id.btnFinish360)

        panel360 =
            findViewById(R.id.panel360)

        progress360 =
            findViewById(R.id.progress360)

        tvHint =
            findViewById(R.id.tvHint)

        guideContainer =
            findViewById(R.id.guideContainer)

        progressLineSolid =
            findViewById(R.id.progressLineSolid)

        ivDirectionArrow =
            findViewById(R.id.ivDirectionArrow)

        panoResultOverlay =
            findViewById(R.id.panoResultOverlay)

        ivPanoResult =
            findViewById(R.id.ivPanoResult)

        btnClosePanoResult =
            findViewById(R.id.btnClosePanoResult)

        btnViewInGallery =
            findViewById(R.id.btnViewInGallery)

        cameraExecutor =
            Executors.newSingleThreadExecutor()

        sensorManager =
            getSystemService(
                SENSOR_SERVICE
            ) as android.hardware.SensorManager

        rotationSensor =
            sensorManager.getDefaultSensor(
                android.hardware.Sensor.TYPE_GYROSCOPE
            )

        panel360.visibility =
            View.GONE

        btnFinish360.visibility =
            View.GONE

        panoResultOverlay.visibility =
            View.GONE

        progress360.max =
            targetTotalDeg.toInt()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {

            requestPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }

        btnCapture.setOnClickListener {
            takeNormalPhoto()
        }

        btnGallery.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    GalleryActivity::class.java
                )
            )
        }

        btnStart360.setOnClickListener {
            startPanoSession()
        }

        btnFinish360.setOnClickListener {
            finishPanoSession(false)
        }

        btnClosePanoResult.setOnClickListener {
            closePanoResultOverlay()
        }

        btnViewInGallery.setOnClickListener {

            closePanoResultOverlay()

            startActivity(
                Intent(
                    this,
                    GalleryActivity::class.java
                )
            )
        }
    }

    // =========================================================
    // CAMERA
    // =========================================================

    private fun startCamera() {

        val future =
            ProcessCameraProvider.getInstance(
                this
            )

        future.addListener({

            val provider =
                future.get()

            val preview =
                Preview.Builder()
                    .build()
                    .also {

                        it.setSurfaceProvider(
                            previewView.surfaceProvider
                        )
                    }

            imageCapture =
                ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    )
                    .setTargetRotation(
                        previewView.display?.rotation
                            ?: android.view.Surface.ROTATION_0
                    )
                    .build()

            try {

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Camera bind failed",
                    e
                )
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeNormalPhoto() {

        val capture =
            imageCapture ?: return

        val name =
            "IMG_${timestamp()}.jpg"

        val options =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaStoreValues(name)
            ).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    output:
                    ImageCapture.OutputFileResults
                ) {

                    Toast.makeText(
                        this@MainActivity,
                        "Photo saved",
                        Toast.LENGTH_SHORT
                    ).show()

                    output.savedUri?.let { uri ->

                        uploadSingleImageToSupabase(
                            uri,
                            name
                        )
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {

                    Toast.makeText(
                        this@MainActivity,
                        "Capture failed: ${
                            exception.message
                        }",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // =========================================================
    // 360 SESSION
    // =========================================================

    private fun startPanoSession() {

        if (rotationSensor == null) {

            Toast.makeText(
                this,
                "Rotation sensor not available",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        clearPanoFrames()

        lastGyroTimestampNs = 0L
        gyroAngleDeg = 0f
        lastCapturedAngleDeg = 0f
        gyroDirection = 0f
        lastCaptureTimeMs = SystemClock.elapsedRealtime()
        totalRotationDeg = 0f

        panoActive = true

        panel360.visibility =
            View.VISIBLE

        topBar.visibility =
            View.GONE

        btnStart360.visibility =
            View.GONE

        btnFinish360.visibility =
            View.VISIBLE

        captureStopIcon.visibility =
            View.VISIBLE

        progress360.progress =
            0

        tvHint.text =
            "Hold phone straight and rotate slowly →"

        progressLineSolid.layoutParams =
            progressLineSolid.layoutParams.apply {
                width = 0
            }

        progressLineSolid.requestLayout()

        ivDirectionArrow.translationX =
            0f

        sensorManager.registerListener(
            this,
            rotationSensor,
            android.hardware.SensorManager.SENSOR_DELAY_GAME
        )

        /*
         * Capture anchor.
         * Yaw will be assigned from the first
         * sensor reading.
         */
        panoScope.launch {
            delay(250)
            withContext(Dispatchers.Main) {
                if (panoActive) {
                    lastCaptureTimeMs = SystemClock.elapsedRealtime()
                    captureFrameForPano(0f)
                }
            }
        }
    }

    private fun finishPanoSession(
        userCancelled: Boolean
    ) {

        if (!panoActive) {
            return
        }

        panoActive = false

        sensorManager.unregisterListener(
            this
        )

        slowDownJob?.cancel()

        topBar.visibility =
            View.VISIBLE

        btnStart360.visibility =
            View.VISIBLE

        btnFinish360.visibility =
            View.GONE

        captureStopIcon.visibility =
            View.GONE

        if (
            userCancelled ||
            panoFrames.size < 3
        ) {

            clearPanoFrames()

            panel360.visibility =
                View.GONE

            if (!userCancelled) {

                Toast.makeText(
                    this,
                    "Not enough frames captured",
                    Toast.LENGTH_SHORT
                ).show()
            }

            return
        }

        val frames =
            synchronized(panoFrames) {
                panoFrames.toList()
            }

        panoFrames.clear()

        panel360.visibility =
            View.VISIBLE

        tvHint.text =
            "Creating 360° panorama..."

        panoScope.launch {

            try {

                val panorama =
                    PanoramaStitcher.stitch(
                        frames
                    )

                frames.forEach { frame ->

                    if (
                        !frame.bitmap.isRecycled
                    ) {
                        frame.bitmap.recycle()
                    }
                }

                val uri =
                    savePanoramaToGallery(
                        panorama
                    )

                withContext(
                    Dispatchers.Main
                ) {

                    panel360.visibility =
                        View.GONE

                    ivPanoResult.setPanorama(
                        panorama
                    )

                    panoResultOverlay.visibility =
                        View.VISIBLE
                }

                uri?.let {

                    uploadSingleImageToSupabase(
                        it,
                        panoFileName
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "360 stitching failed",
                    e
                )

                frames.forEach { frame ->

                    if (
                        !frame.bitmap.isRecycled
                    ) {
                        frame.bitmap.recycle()
                    }
                }

                withContext(
                    Dispatchers.Main
                ) {

                    panel360.visibility =
                        View.GONE

                    Toast.makeText(
                        this@MainActivity,
                        "Panorama failed: ${
                            e.message
                        }",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // =========================================================
    // FRAME CAPTURE
    // =========================================================

    private fun captureFrameForPano(
        angleDeg: Float
    ) {

        val capture =
            imageCapture ?: return

        if (
            isCapturingFrame ||
            !panoActive
        ) {
            return
        }

        isCapturingFrame = true

        val file =
            File(
                cacheDir,
                "pano_${
                    System.currentTimeMillis()
                }.jpg"
            )

        val options =
            ImageCapture.OutputFileOptions
                .Builder(file)
                .build()

        capture.takePicture(
            options,
            cameraExecutor,
            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    output:
                    ImageCapture.OutputFileResults
                ) {

                    try {

                        val bitmap =
                            decodeSampledBitmap(
                                file.absolutePath,
                                1400
                            )

                        if (bitmap != null) {

                            synchronized(
                                panoFrames
                            ) {

                                panoFrames.add(
                                    PanoFrame(
                                        bitmap,
                                        angleDeg
                                    )
                                )
                            }
                        }

                    } finally {

                        file.delete()

                        isCapturingFrame =
                            false
                    }

                    if (
                        panoFrames.size >=
                        maxShots
                    ) {

                        runOnUiThread {

                            finishPanoSession(
                                false
                            )
                        }
                    }
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    isCapturingFrame =
                        false

                    Log.e(
                        TAG,
                        "360 frame failed",
                        exception
                    )
                }
            }
        )
    }

    // =========================================================
    // SENSOR
    // =========================================================

    override fun onSensorChanged(
        event: android.hardware.SensorEvent
    ) {

        if (!panoActive) return
        if (event.sensor.type != android.hardware.Sensor.TYPE_GYROSCOPE) return

        if (lastGyroTimestampNs == 0L) {
            lastGyroTimestampNs = event.timestamp
            return
        }

        val dt =
            ((event.timestamp - lastGyroTimestampNs) / 1_000_000_000.0)
                .coerceIn(0.0, 0.1)

        lastGyroTimestampNs = event.timestamp

        // In portrait, the phone's Y axis is the vertical axis.
        // Rotating the phone around the room's vertical axis is therefore
        // primarily the Y gyro component.
        val angularVelocity = event.values[1].toDouble()
        val instantDeg = Math.toDegrees(angularVelocity * dt).toFloat()

        if (abs(instantDeg) < 0.05f) return

        if (gyroDirection == 0f && abs(instantDeg) > 0.15f) {
            gyroDirection = if (instantDeg >= 0f) 1f else -1f
        }

        val directed = instantDeg * gyroDirection
        if (directed <= 0f) return

        gyroAngleDeg += directed
        totalRotationDeg = gyroAngleDeg.coerceAtMost(targetTotalDeg)

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastCaptureTimeMs
        val speed = if (dt > 0.0) abs(instantDeg) / dt.toFloat() else 0f

        // Capture a dense set of frames. If CameraX is busy, the next
        // sensor event will trigger the next frame as soon as enough angle
        // has accumulated.
        if (
            !isCapturingFrame &&
            totalRotationDeg - lastCapturedAngleDeg >= captureStepDeg &&
            elapsed >= 120L
        ) {
            lastCapturedAngleDeg = totalRotationDeg
            lastCaptureTimeMs = now
            captureFrameForPano(totalRotationDeg)
        }

        updatePanoUi(speed)

        if (totalRotationDeg >= targetTotalDeg - 1f && !isCapturingFrame) {
            runOnUiThread { finishPanoSession(false) }
        }
    }

    private fun updatePanoUi(
        speed: Float
    ) {

        runOnUiThread {

            progress360.progress =
                totalRotationDeg
                    .toInt()
                    .coerceAtMost(
                        progress360.max
                    )

            val fraction =
                (
                        totalRotationDeg /
                                targetTotalDeg
                        ).coerceIn(
                        0f,
                        1f
                    )

            val width =
                guideContainer.width

            if (width > 0) {

                val filled =
                    (
                            fraction * width
                            ).toInt()

                progressLineSolid
                    .layoutParams =
                    progressLineSolid
                        .layoutParams
                        .apply {
                            this.width =
                                filled
                        }

                progressLineSolid
                    .requestLayout()

                ivDirectionArrow
                    .translationX =
                    (
                            filled -
                                    ivDirectionArrow
                                        .width / 2f
                            ).coerceIn(
                            0f,
                            (
                                    width -
                                            ivDirectionArrow
                                                .width
                                    ).toFloat()
                        )
            }

            if (
                speed >
                fastRotationThresholdDegPerSec
            ) {

                showSlowDownHint()

                return@runOnUiThread
            }

            tvHint.text =
                when {

                    totalRotationDeg < 70f ->
                        "Keep rotating slowly →"

                    totalRotationDeg < 160f ->
                        "Good — keep going →"

                    totalRotationDeg < 250f ->
                        "Halfway around →"

                    totalRotationDeg < 330f ->
                        "Almost complete →"

                    else ->
                        "Finishing 360°..."
                }
        }
    }

    private fun showSlowDownHint() {

        tvHint.text =
            "Slow down"

        slowDownJob?.cancel()

        slowDownJob =
            panoScope.launch {

                delay(1000)

                withContext(
                    Dispatchers.Main
                ) {

                    if (panoActive) {

                        tvHint.text =
                            "Rotate slowly →"
                    }
                }
            }
    }

    override fun onAccuracyChanged(
        sensor:
        android.hardware.Sensor?,
        accuracy: Int
    ) {
    }

    // =========================================================
    // BITMAP
    // =========================================================

    private fun decodeSampledBitmap(
        path: String,
        maxDimension: Int
    ): Bitmap? {

        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        BitmapFactory.decodeFile(
            path,
            bounds
        )

        if (
            bounds.outWidth <= 0 ||
            bounds.outHeight <= 0
        ) {
            return null
        }

        var sample = 1

        while (
            bounds.outWidth / sample >
            maxDimension ||
            bounds.outHeight / sample >
            maxDimension
        ) {

            sample *= 2
        }

        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig =
                    Bitmap.Config.ARGB_8888
            }
        )
    }

    // =========================================================
    // SAVE PANORAMA
    // =========================================================

    private fun savePanoramaToGallery(
        bitmap: Bitmap
    ): Uri? {

        panoFileName =
            "PANO_${timestamp()}.jpg"

        val uri =
            contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaStoreValues(
                    panoFileName
                )
            ) ?: return null

        try {

            contentResolver
                .openOutputStream(uri)
                ?.use { output ->

                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        94,
                        output
                    )
                }

            return uri

        } catch (e: Exception) {

            contentResolver.delete(
                uri,
                null,
                null
            )

            return null
        }
    }

    private fun mediaStoreValues(
        displayName: String
    ): ContentValues {

        return ContentValues().apply {

            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                displayName
            )

            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {

                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/360Camera"
                )
            }
        }
    }

    // =========================================================
    // SUPABASE
    // =========================================================

    private fun uploadSingleImageToSupabase(
        uri: Uri,
        fileName: String
    ) {

        panoScope.launch {

            try {

                val bytes =
                    contentResolver
                        .openInputStream(uri)
                        ?.use {
                            it.readBytes()
                        }

                if (bytes == null) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        Toast.makeText(
                            this@MainActivity,
                            "Upload failed: Could not read file",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    return@launch
                }

                val result =
                    SupabaseUploader.uploadBytes(
                        bytes,
                        fileName
                    )

                withContext(
                    Dispatchers.Main
                ) {

                    if (result.isSuccess) {

                        Toast.makeText(
                            this@MainActivity,
                            "Uploaded to Supabase",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        Toast.makeText(
                            this@MainActivity,
                            "Upload failed: ${
                                result.exceptionOrNull()
                                    ?.message
                            }",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Upload exception",
                    e
                )
            }
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    private fun clearPanoFrames() {

        synchronized(
            panoFrames
        ) {

            panoFrames.forEach { frame ->

                if (
                    !frame.bitmap.isRecycled
                ) {
                    frame.bitmap.recycle()
                }
            }

            panoFrames.clear()
        }
    }

    private fun closePanoResultOverlay() {

        ivPanoResult.setPanorama(
            null
        )

        panoResultOverlay.visibility =
            View.GONE
    }

    private fun timestamp(): String {

        return SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())
    }

    override fun onDestroy() {

        sensorManager.unregisterListener(
            this
        )

        slowDownJob?.cancel()

        panoScope.cancel()

        cameraExecutor.shutdown()

        clearPanoFrames()

        super.onDestroy()
    }

    companion object {

        private const val TAG =
            "ThreeSixtyCamera"
    }
}