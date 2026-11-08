package pl.astropro.p40

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.DngCreator
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var preview: TextureView
    private lateinit var info: TextView
    private lateinit var isoText: TextView
    private lateinit var timeText: TextView
    private lateinit var isoBar: SeekBar
    private lateinit var timeBar: SeekBar
    private lateinit var focusText: TextView
    private lateinit var focusBar: SeekBar
    private lateinit var rawButton: Button
    private var minFocusDistance = 0f
    private var focusDistance = 0f
    private var manualFocusSupported = false
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var rawSupported = false
    private var pendingRawImage: Image? = null
    private var pendingRawResult: TotalCaptureResult? = null
    private var request: CaptureRequest.Builder? = null
    private var iso = 800
    private var minIso = 50
    private var maxIso = 3200
    private var exposure = 10_000_000_000L
    private var minExposure = 100_000L
    private var maxExposure = 30_000_000_000L
    private var manual = false
    private val manager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF07101D.toInt()) }
        preview = TextureView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 5, 16, 8) }
        info = text("ASTRO PRO P40 • inicjalizacja")
        panel.addView(info)
        isoText = text("ISO: $iso"); panel.addView(isoText)
        isoBar = SeekBar(this).apply { max = 1000; progress = 200 }; panel.addView(isoBar)
        timeText = text("Ekspozycja: 1/100 s"); panel.addView(timeText)
        timeBar = SeekBar(this).apply { max = 1000; progress = 300 }; panel.addView(timeBar)
        focusText = text("Ostrość: automatyczna"); panel.addView(focusText)
        focusBar = SeekBar(this).apply { max = 1000; progress = 1000; isEnabled = false }; panel.addView(focusBar)
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "ZDJĘCIE"; setOnClickListener { shoot() } }, LinearLayout.LayoutParams(0, -2, 2f))
        rawButton = Button(this).apply { text = "RAW / DNG"; isEnabled = false; setOnClickListener { shootRaw() } }
        row.addView(rawButton, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "SERIA 10"; setOnClickListener { shootBurst(10) } }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "ISO 800 / 10 s"; setOnClickListener { iso = 800; exposure = 10_000_000_000L.coerceAtMost(maxExposure); sync(); applyPreview() } }, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(row); root.addView(panel); setContentView(root)
        isoBar.setOnSeekBarChangeListener(changes { p -> iso = minIso + (maxIso - minIso) * p / 1000; isoText.text = "ISO: $iso"; applyPreview() })
        timeBar.setOnSeekBarChangeListener(changes { p ->
            exposure = (minExposure * Math.pow(maxExposure.toDouble() / minExposure, p / 1000.0)).toLong()
            timeText.text = "Ekspozycja: ${formatTime(exposure)}"; applyPreview()
        })
        focusBar.setOnSeekBarChangeListener(changes { p ->
            focusDistance = minFocusDistance * (1000 - p) / 1000f
            focusText.text = if (p >= 995) "Ostrość: nieskończoność" else "Ostrość ręczna: %.2f D".format(Locale.US, focusDistance)
            applyPreview()
        })
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { openCamera() }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 7)
    }

    private fun text(s: String) = TextView(this).apply { text = s; textSize = 14f; setTextColor(0xFFEAF2FF.toInt()) }
    private fun changes(f: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) { if (user) f(p) }
        override fun onStartTrackingTouch(b: SeekBar?) {}
        override fun onStopTrackingTouch(b: SeekBar?) {}
    }

    private fun openCamera() {
        if (!preview.isAvailable || device != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val id = manager.cameraIdList.first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            val c = manager.getCameraCharacteristics(id)
            cameraCharacteristics = c
            val streamMap = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            rawSupported = (c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()).contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) &&
                !streamMap?.getOutputSizes(android.graphics.ImageFormat.RAW_SENSOR).isNullOrEmpty()
            rawButton.isEnabled = rawSupported
            rawButton.text = if (rawSupported) "RAW / DNG" else "RAW niedostępny"
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            manual = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            isoBar.isEnabled = manual
            timeBar.isEnabled = manual
            c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { minIso = it.lower; maxIso = it.upper }
            c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { minExposure = it.lower; maxExposure = min(it.upper, 30_000_000_000L) }
            minFocusDistance = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            manualFocusSupported = minFocusDistance > 0f
            focusBar.isEnabled = manualFocusSupported
            focusDistance = 0f
            focusText.text = if (manualFocusSupported) "Ostrość: nieskończoność" else "Ostrość: sterowanie niedostępne"
            iso = 800.coerceIn(minIso, maxIso); exposure = 10_000_000_000L.coerceIn(minExposure, maxExposure); sync()
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) { device = d; startPreview() }
                override fun onDisconnected(d: CameraDevice) { d.close(); device = null }
                override fun onError(d: CameraDevice, e: Int) { d.close(); device = null; info.text = "Błąd aparatu: $e" }
            }, null)
        } catch (e: Exception) { info.text = "Aparat niedostępny: ${e.message}" }
    }

    private fun sync() {
        isoText.text = "ISO: $iso"
        timeText.text = "Ekspozycja: ${formatTime(exposure)}"
        isoBar.progress = if (maxIso == minIso) 0 else 1000 * (iso - minIso) / (maxIso - minIso)
        timeBar.progress = if (maxExposure <= minExposure) 0 else (1000 * ln(exposure.toDouble() / minExposure) / ln(maxExposure.toDouble() / minExposure)).toInt().coerceIn(0,1000)
    }

    private fun cameraIdForDevice(): String = manager.cameraIdList.first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }

    private fun startPreview() {
        val d = device ?: return
        val st = preview.surfaceTexture ?: return
        st.setDefaultBufferSize(1920,1080)
        val surface = Surface(st)
        val jpegSizes = manager.getCameraCharacteristics(cameraIdForDevice()).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
        val jpegSize = jpegSizes?.maxByOrNull { it.width.toLong() * it.height } ?: Size(1920,1080)
        reader = ImageReader.newInstance(jpegSize.width,jpegSize.height,android.graphics.ImageFormat.JPEG,2)
        if (rawSupported) {
            val rawSize = manager.getCameraCharacteristics(cameraIdForDevice()).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(android.graphics.ImageFormat.RAW_SENSOR).maxByOrNull { it.width.toLong() * it.height }
            if (rawSize != null) {
                rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, android.graphics.ImageFormat.RAW_SENSOR, 2)
                rawReader!!.setOnImageAvailableListener({ r ->
                    pendingRawImage?.close()
                    pendingRawImage = r.acquireNextImage()
                    writePendingDng()
                }, null)
            }
        }
        reader!!.setOnImageAvailableListener({ r ->
            val image = r.acquireNextImage() ?: return@setOnImageAvailableListener
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining()); buffer.get(data); image.close(); save(data)
        }, null)
        val outputs = mutableListOf(surface, reader!!.surface)
        rawReader?.surface?.let { outputs.add(it) }
        configureCameraSession(d, surface, outputs, rawReader != null)
    }

    private fun configureCameraSession(d: CameraDevice, previewSurface: Surface, outputs: List<Surface>, retryWithoutRaw: Boolean) {
        d.createCaptureSession(outputs, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                request = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(previewSurface) }
                applyPreview()
            }
            override fun onConfigureFailed(s: CameraCaptureSession) {
                s.close()
                if (retryWithoutRaw && rawReader != null) {
                    rawReader?.close()
                    rawReader = null
                    rawSupported = false
                    rawButton.isEnabled = false
                    rawButton.text = "RAW niedostępny"
                    configureCameraSession(d, previewSurface, listOf(previewSurface, reader!!.surface), false)
                } else {
                    info.text = "Błąd konfiguracji podglądu"
                }
            }
        }, null)
    }

    private fun manualSettings(b: CaptureRequest.Builder) {
        if (manualFocusSupported) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
        } else {
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        if (manual) {
            // Manual ISO and shutter require AE to be explicitly disabled.
            b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, iso.coerceIn(minIso, maxIso))
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure.coerceIn(minExposure, maxExposure))
        } else {
            b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
    }

    private fun applyPreview() {
        val s = session ?: return
        val b = request ?: return
        try { manualSettings(b); s.setRepeatingRequest(b.build(), null, null) } catch (_: Exception) {}
    }

    private fun shootRaw() {
        val d = device ?: return
        val s = session ?: return
        val out = rawReader ?: return
        if (!rawSupported) return
        rawButton.isEnabled = false
        try {
            pendingRawImage?.close(); pendingRawImage = null; pendingRawResult = null
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(out.surface)
                manualSettings(this)
            }
            s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    pendingRawResult = result
                    writePendingDng()
                }
            }, null)
            info.text = "RAW: wykonywanie ekspozycji…"
        } catch (e: Exception) { rawButton.isEnabled = rawSupported; info.text = "Błąd RAW: ${e.message}" }
    }

    private fun writePendingDng() {
        val image = pendingRawImage ?: return
        val result = pendingRawResult ?: return
        val chars = cameraCharacteristics ?: return
        pendingRawImage = null
        pendingRawResult = null
        try {
            val name = "ASTRO_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.dng"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AstroProP40")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Nie można utworzyć pliku DNG")
            val output = contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Nie można otworzyć pliku DNG")
            output.use { stream -> DngCreator(chars, result).use { it.writeImage(stream, image) } }
            runOnUiThread { info.text = "Zapisano RAW / DNG w Pictures/AstroProP40" }
        } catch (e: Exception) {
            runOnUiThread { info.text = "Zapis DNG nieudany: ${e.message}" }
        } finally {
            image.close()
            runOnUiThread { rawButton.isEnabled = rawSupported }
        }
    }

    private fun shoot() {
        val d = device ?: return; val s = session ?: return; val out = reader ?: return
        try {
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(out.surface); manualSettings(this); set(CaptureRequest.JPEG_QUALITY, 100.toByte())
            }
            s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    runOnUiThread { info.text = "Zdjęcie wykonane • ISO $iso • ${formatTime(exposure)}" }
                }
            }, null)
        } catch (e: Exception) { info.text = "Nie udało się wykonać zdjęcia: ${e.message}" }
    }

    private fun shootBurst(count: Int) {
        val handler = android.os.Handler(mainLooper)
        var remaining = count
        info.text = "Seria: przygotowanie $count zdjęć"
        fun next() {
            if (remaining <= 0) {
                info.text = "Seria zakończona • zapisano $count ujęć"
                return
            }
            remaining--
            shoot()
            if (remaining > 0) {
                // Give the sensor time to finish the current long exposure before queuing the next frame.
                val waitMs = maxOf(1200L, exposure / 1_000_000L + 1000L)
                handler.postDelayed({ next() }, waitMs)
            }
        }
        next()
    }

    private fun save(data: ByteArray) {
        try {
            val name = "ASTRO_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,name); put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES + "/AstroProP40")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: return
            contentResolver.openOutputStream(uri)?.use { it.write(data) }
            runOnUiThread { info.text = "Zapisano w Galerii / Pictures / AstroProP40" }
        } catch (e: Exception) { runOnUiThread { info.text = "Błąd zapisu: ${e.message}" } }
    }

    private fun formatTime(ns: Long): String {
        val sec = ns / 1_000_000_000.0
        return if (sec >= 1) String.format(Locale.US,"%.1f s",sec) else "1/${(1/sec).toInt().coerceAtLeast(1)} s"
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if (code == 7 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) preview.post { openCamera() }
        else info.text = "Aplikacja wymaga dostępu do aparatu"
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized && preview.isAvailable) preview.post { openCamera() }
    }

    override fun onPause() {
        super.onPause()
        session?.close(); session = null
        device?.close(); device = null
        reader?.close(); reader = null
        rawReader?.close(); rawReader = null
        pendingRawImage?.close(); pendingRawImage = null; pendingRawResult = null
    }
}