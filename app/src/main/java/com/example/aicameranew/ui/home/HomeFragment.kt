package com.example.aicameranew.ui.home
import android.graphics.BitmapFactory

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup



import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import android.widget.TextView
import androidx.camera.core.ImageCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.aicameranew.databinding.FragmentHomeBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.Manifest
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.aicameranew.R
import com.example.aicameranew.viewmodel.SelectedPromptViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    //Instance of CameraX
    private var imageCapture: ImageCapture? = null

    //Camera Facing
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private val selectedPromptViewModel by activityViewModels<SelectedPromptViewModel>()


    //Thread to execute the photo taking
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                10
            )
        }



        binding.buttonCapture.setOnClickListener {
            takePhoto()
        }

        binding.turnCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()  // Restart the camera
        }

        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_nav_gallery)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            requireContext().externalMediaDirs.first(),
            SimpleDateFormat(
                "yyyy-MM-dd-HH-mm-ss-SSS",
                Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        requireContext(),
                        "Photo saved: ${photoFile.absolutePath}",
                        Toast.LENGTH_SHORT
                    ).show()
                    lifecycleScope.launch {
                        val currentPrompt = selectedPromptViewModel.selectedPrompt.value
                        val textToSend = currentPrompt?.text ?: "Please help me describe this scene"
                        uploadPhotoToGemini(photoFile, textToSend)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }


    private suspend fun uploadPhotoToGemini(photoFile: File, inputText: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            if (bitmap == null) {
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
            val result = response.text ?: "Didn't get any response"

            Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
