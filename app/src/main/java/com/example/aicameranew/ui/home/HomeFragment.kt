package com.example.aicameranew.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.aicameranew.R
import com.example.aicameranew.databinding.FragmentHomeBinding
import com.example.aicameranew.data.Prompt
import com.example.aicameranew.data.PromptRepository
import com.example.aicameranew.viewmodel.SelectedPromptViewModel
import com.google.android.gms.location.*
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.sqrt // Make sure this is imported

class HomeFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private val selectedPromptViewModel by activityViewModels<SelectedPromptViewModel>()

    private var aiResultDialog: AlertDialog? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var promptRepository: PromptRepository

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationDeferred: CompletableDeferred<Location?>

    // Sensor-related variables
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var lightSensor: Sensor? = null

    // For shake detection (refined)
    private var lastAccelerometerValues: FloatArray? = null
    private var lastShakeTimestamp: Long = 0
    private val SHAKE_THRESHOLD_ACCELERATION = 12.0f // Higher value for "violent" shake, adjust as needed (m/s^2)
    private val SHAKE_COOLDOWN_MILLIS = 3000L // Don't show shake toast too frequently (3 seconds)

    // For ambient light (example usage)
    private var currentAmbientLight: Float = 0f

    // NEW: For Level Indicator (Horizontal Angle)
    private var lastLevelToastTime: Long = 0
    private val LEVEL_TOAST_COOLDOWN_MILLIS = 2000L // Show level toast every 2 seconds
    private val LEVEL_THRESHOLD_DEGREES = 5.0f // Within +/- 5 degrees is considered "level"

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
        private const val LOCATION_FETCH_TIMEOUT_MS = 5000L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        promptRepository = PromptRepository(requireContext())


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.Builder(1000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateDelayMillis(500L)
            .setDurationMillis(3000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (::locationDeferred.isInitialized && !locationDeferred.isCompleted) {
                        locationDeferred.complete(location)
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }
        }

        val currentPrompt = selectedPromptViewModel.selectedPrompt.value
        binding.currentPromptDisplay.text = " Current Prompt: ${currentPrompt?.text ?: "No prompt selected"}"

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }

        binding.buttonCapture.setOnClickListener {
            takePhoto(currentPrompt)
        }

        binding.turnCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
            startCamera()
        }

        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_nav_gallery)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize SensorManager and sensors
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Observe sensor assistance state from ViewModel
        selectedPromptViewModel.isSensorAssistanceEnabled.observe(viewLifecycleOwner) { isEnabled ->
            if (isEnabled) {
                registerSensors()
            } else {
                unregisterSensors()
            }
        }
    }

    private fun registerSensors() {
        Log.d("HomeFragment", "Registering sensors...")
        accelerometer?.let {
            // SENSOR_DELAY_GAME is often good for real-time motion detection
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d("HomeFragment", "Accelerometer registered.")
        } ?: run { Log.w("HomeFragment", "Accelerometer not available.") }

        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d("HomeFragment", "Gyroscope registered.")
        } ?: run { Log.w("HomeFragment", "Gyroscope not available.") }

        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("HomeFragment", "Light sensor registered.")
        } ?: run { Log.w("HomeFragment", "Light sensor not available.") }
    }

    private fun unregisterSensors() {
        Log.d("HomeFragment", "Unregistering sensors...")
        sensorManager?.unregisterListener(this)
        lastAccelerometerValues = null // Clear old values when unregistered
        lastLevelToastTime = 0 // Reset level toast time
    }

    override fun onResume() {
        super.onResume()
        if (selectedPromptViewModel.isSensorAssistanceEnabled.value == true) {
            registerSensors()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterSensors()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val currentTime = System.currentTimeMillis() // Get current time once for efficiency

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Shake detection logic (exists)
                if (selectedPromptViewModel.isSensorAssistanceEnabled.value == true) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    if (lastAccelerometerValues == null) {
                        lastAccelerometerValues = floatArrayOf(x, y, z)
                        return
                    }

                    val deltaX = x - lastAccelerometerValues!![0]
                    val deltaY = y - lastAccelerometerValues!![1]
                    val deltaZ = z - lastAccelerometerValues!![2]

                    lastAccelerometerValues!![0] = x
                    lastAccelerometerValues!![1] = y
                    lastAccelerometerValues!![2] = z

                    val accelerationMagnitude = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

                    if (accelerationMagnitude > SHAKE_THRESHOLD_ACCELERATION && (currentTime - lastShakeTimestamp) > SHAKE_COOLDOWN_MILLIS) {
                        Log.d("ShakeDetector", "VIOLENT SHAKE DETECTED! Magnitude: ${String.format("%.2f", accelerationMagnitude)}")
                        lastShakeTimestamp = currentTime
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Please hold the phone steady for photo taking.", Toast.LENGTH_LONG).show()
                        }
                    }

                    // NEW: Level Indicator Logic
                    if ((currentTime - lastLevelToastTime) > LEVEL_TOAST_COOLDOWN_MILLIS) {
                        val gX = event.values[0]
                        val gY = event.values[1]
                        val gZ = event.values[2]

                        // Calculate roll angle (rotation around the Z-axis, how much it tilts left/right)
                        // atan2(y, sqrt(x*x + z*z)) for roll (Y-axis pointing up/down device)
                        // This assumes device is mostly flat or held like a camera.
                        val roll = atan2(gY, sqrt(gX * gX + gZ * gZ)) * (180 / Math.PI).toFloat()

                        val levelMessage: String
                        if (roll > -LEVEL_THRESHOLD_DEGREES && roll < LEVEL_THRESHOLD_DEGREES) {
                            levelMessage = "Phone is Level: ${String.format("%.1f", roll)}°"
                        } else {
                            levelMessage = "Tilt: ${String.format("%.1f", roll)}°"
                        }
                        Log.d("LevelDetector", levelMessage)
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), levelMessage, Toast.LENGTH_SHORT).show()
                        }
                        lastLevelToastTime = currentTime
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val angularSpeedX = event.values[0]
                val angularSpeedY = event.values[1]
                val angularSpeedZ = event.values[2]
                Log.d("SensorAssistance", "Gyro: X=${String.format("%.2f", angularSpeedX)}, Y=${String.format("%.2f", angularSpeedY)}, Z=${String.format("%.2f", angularSpeedZ)}")
            }
            Sensor.TYPE_LIGHT -> {
                currentAmbientLight = event.values[0]
                Log.d("SensorAssistance", "Ambient Light: ${String.format("%.2f", currentAmbientLight)} lux")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("SensorAssistance", "Sensor accuracy changed for ${sensor?.name}: $accuracy")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("CameraX", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("MissingPermission")
    private fun takePhoto(currentPrompt: Prompt? = null) {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            requireContext().externalMediaDirs.first(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    promptRepository.incrementFrequency(currentPrompt)
                    Toast.makeText(requireContext(), "Photo saved: ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        var textForGemini = currentPrompt?.text ?: "Please help me describe this scene."

                        // Handle Geolocation (existing logic)
                        val isGeolocationEnabled = selectedPromptViewModel.isGeolocationEnabled.value ?: false
                        var location: Location? = null
                        if (isGeolocationEnabled) {
                            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                try {
                                    location = fusedLocationClient.lastLocation.await()
                                    if (location == null || location.elapsedRealtimeNanos == 0L || System.currentTimeMillis() - location.time > 30 * 1000) {
                                        Toast.makeText(requireContext(), "Getting fresh location...", Toast.LENGTH_SHORT).show()
                                        locationDeferred = CompletableDeferred()
                                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                                        location = withTimeout(LOCATION_FETCH_TIMEOUT_MS) {
                                            locationDeferred.await()
                                        }
                                    }
                                    selectedPromptViewModel.setCurrentLocation(location)
                                } catch (e: TimeoutCancellationException) {
                                    Toast.makeText(requireContext(), "Location request timed out. Cannot add location info.", Toast.LENGTH_LONG).show()
                                    selectedPromptViewModel.setCurrentLocation(null)
                                } catch (e: Exception) {
                                    Log.e("HomeFragment", "Error getting location: ${e.message}")
                                    Toast.makeText(requireContext(), "Error getting location: ${e.message}", Toast.LENGTH_LONG).show()
                                    selectedPromptViewModel.setCurrentLocation(null)
                                } finally {
                                    if (::locationDeferred.isInitialized && !locationDeferred.isCompleted) {
                                        fusedLocationClient.removeLocationUpdates(locationCallback)
                                    }
                                }
                            } else {
                                Toast.makeText(requireContext(), "Location permission not granted. Geolocation is enabled but cannot fetch location.", Toast.LENGTH_LONG).show()
                                selectedPromptViewModel.setCurrentLocation(null)
                            }
                        } else {
                            selectedPromptViewModel.setCurrentLocation(null)
                        }

                        // Incorporate Sensor Data into prompt if enabled (optional, for AI)
                        val isSensorAssistanceEnabled = selectedPromptViewModel.isSensorAssistanceEnabled.value ?: false
                        if (isSensorAssistanceEnabled) {
                            val sensorNote = StringBuilder()
                            sensorNote.append("\nNote: Sensor assistance was active during capture.")
                            sensorNote.append(" Ambient light: ${String.format("%.2f", currentAmbientLight)} lux.")
                            // You could add logic here to track and report if a shake was detected right before capture
                            // For instance, pass a boolean flag from onSensorChanged if a recent shake happened
                            // textForGemini += sensorNote.toString() // Uncomment to add to AI prompt
                            Log.d("HomeFragment", "Sensor Data for photo: Ambient Light = ${currentAmbientLight} lux")
                        }

                        Toast.makeText(requireContext(), "Prompt:\n$textForGemini", Toast.LENGTH_LONG).show()
                        Log.d("GeminiPrompt", "Sent prompt: $textForGemini")

                        uploadPhotoToGemini(photoFile, textForGemini)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission denied.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        aiResultDialog?.dismiss()
        aiResultDialog = null
        _binding = null
        cameraExecutor.shutdown()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        unregisterSensors()
    }

    private suspend fun uploadPhotoToGemini(photoFile: File, inputText: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: run {
                Toast.makeText(requireContext(), "Image decode failed", Toast.LENGTH_SHORT).show()
                return
            }

            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel("gemini-2.0-flash")

            val prompt = content {
                image(bitmap)
                text(inputText)
            }

            val response = model.generateContent(prompt)

            val locationInfo = selectedPromptViewModel.currentLocation.value?.let {
                "📍 Location: Latitude: ${it.latitude}, Longitude: ${it.longitude}\n\n"
            } ?: ""

            val sensorAssistanceInfo = selectedPromptViewModel.isSensorAssistanceEnabled.value?.let { isEnabled ->
                if (isEnabled) {
                    "💡 Ambient Light: ${String.format("%.2f", currentAmbientLight)} lux\n\n"
                } else ""
            } ?: ""


            val result = locationInfo + sensorAssistanceInfo + (response.text ?: "Didn't get any response from AI.")

            requireActivity().runOnUiThread {
                aiResultDialog?.dismiss()

                val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_result, null)
                dialogView.findViewById<TextView>(R.id.text_result).text = result

                val dialog = AlertDialog.Builder(requireContext())
                    .setView(dialogView)
                    .create()

                dialog.show()
                aiResultDialog = dialog

                dialog.window?.apply {
                    val displayMetrics = resources.displayMetrics
                    setLayout(
                        (displayMetrics.widthPixels * 0.93).toInt(),
                        (displayMetrics.heightPixels * 0.75).toInt()
                    )
                    val params = attributes
                    params.gravity = Gravity.CENTER
                    params.y = -60
                    attributes = params
                }

                dialogView.findViewById<ImageButton>(R.id.btn_retake).setOnClickListener {
                    takePhoto(selectedPromptViewModel.selectedPrompt.value)
                    dialog.dismiss()
                }

                dialogView.findViewById<ImageButton>(R.id.btn_exit).setOnClickListener {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), "Exited AI analysis.", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error communicating with AI: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}