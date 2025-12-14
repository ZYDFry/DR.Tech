package com.example.drtechapp.ui.dashboard

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.drtechapp.databinding.FragmentCreateOrderBinding
import com.example.drtechapp.viewmodel.TechViewModel
import java.io.ByteArrayOutputStream

class CreateOrderFragment : Fragment() {

    private var _binding: FragmentCreateOrderBinding? = null
    private val binding get() = _binding!!

    // Conexión con el ViewModel compartido
    private val viewModel: TechViewModel by activityViewModels()

    // Imagen seleccionada (en bytes)
    private var selectedImageBytes: ByteArray? = null

    // --- LAUNCHERS DE IMAGEN ---

    // 1. Galería
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val input = requireContext().contentResolver.openInputStream(it)
                val bytes = input?.readBytes()
                input?.close()
                if (bytes != null) {
                    selectedImageBytes = bytes
                    binding.ivPreview.isVisible = true
                    Glide.with(requireContext()).load(it).into(binding.ivPreview)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Permiso Cámara
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePicturePreviewLauncher.launch(null)
        else Toast.makeText(requireContext(), "Permiso denegado", Toast.LENGTH_SHORT).show()
    }

    // 3. Tomar Foto
    private val takePicturePreviewLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val stream = ByteArrayOutputStream()
            // Compresión visual (para mostrar en pantalla solamente)
            it.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            selectedImageBytes = stream.toByteArray()
            stream.close()
            binding.ivPreview.isVisible = true
            binding.ivPreview.setImageBitmap(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddPhoto.setOnClickListener { showPhotoChoiceDialog() }

        binding.btnCreateOrder.setOnClickListener {
            sendToViewModel()
        }

        observeViewModel()
    }

    private fun showPhotoChoiceDialog() {
        val options = arrayOf("Elegir de la galería", "Tomar foto con cámara", "Quitar foto")
        AlertDialog.Builder(requireContext())
            .setTitle("Imagen")
            .setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)) { d, i ->
                when (i) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    2 -> {
                        selectedImageBytes = null
                        binding.ivPreview.isVisible = false
                    }
                }
                d.dismiss()
            }
            .show()
    }

    private fun sendToViewModel() {
        val device = binding.etDeviceModel.text.toString().trim()
        val description = binding.etIssueDescription.text.toString().trim()
        val shelf = binding.etShelf.text.toString().trim().ifBlank { null }

        if (device.isBlank() || description.isBlank()) {
            Toast.makeText(requireContext(), "Falta dispositivo o descripción", Toast.LENGTH_SHORT).show()
            return
        }

        // Delegamos todo el trabajo al ViewModel
        viewModel.createOrder(
            deviceModel = device,
            issueDescription = description,
            shelfLocation = shelf,
            imageBytes = selectedImageBytes
        )
    }

    private fun observeViewModel() {
        // Observar carga (Spinner y bloquear botón)
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
            binding.btnCreateOrder.isEnabled = !loading
        }

        // Observar éxito
        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null && msg.contains("Orden creada", ignoreCase = true)) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
                findNavController().popBackStack() // Cerrar pantalla
            }
        }

        // Observar error
        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}