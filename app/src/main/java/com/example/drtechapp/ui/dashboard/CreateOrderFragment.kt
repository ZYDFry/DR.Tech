package com.example.drtechapp.ui.dashboard

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.drtechapp.databinding.FragmentCreateOrderBinding
import com.example.drtechapp.repository.WorkOrderRepository
import com.example.drtechapp.viewmodel.TechViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class CreateOrderFragment : Fragment() {

    private var _binding: FragmentCreateOrderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TechViewModel by activityViewModels()

    // Repositorio
    private val workOrderRepo = WorkOrderRepository()

    // Imagen seleccionada (en bytes)
    private var selectedImageBytes: ByteArray? = null

    // Launcher Galería
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
                } else {
                    Toast.makeText(requireContext(), "Error al leer imagen", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al leer imagen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher Permiso Cámara
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            takePicturePreviewLauncher.launch(null)
        } else {
            Toast.makeText(requireContext(), "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher Tomar Foto
    private val takePicturePreviewLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val stream = ByteArrayOutputStream()
            // Compresión inicial para visualización
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

        binding.btnAddPhoto.isVisible = true
        binding.ivPreview.isVisible = false

        binding.btnAddPhoto.setOnClickListener {
            showPhotoChoiceDialog()
        }

        binding.btnCreateOrder.setOnClickListener {
            createOrder()
        }

        observeViewModel()
    }

    private fun showPhotoChoiceDialog() {
        val options = arrayOf("Elegir de la galería", "Tomar foto con cámara", "Quitar foto")
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Agregar imagen")
            .setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)) { dialog, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    2 -> {
                        selectedImageBytes = null
                        binding.ivPreview.setImageDrawable(null)
                        binding.ivPreview.isVisible = false
                    }
                }
                dialog.dismiss()
            }
        builder.show()
    }

    /**
     * Convierte la imagen a Base64 comprimido para evitar errores de tamaño en Firestore
     */
    private fun processImageToBase64(bytes: ByteArray): String {
        // 1. Decodificar bytes a Bitmap
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // 2. Comprimir fuertemente (Calidad 30) para asegurar que pese menos de 1MB
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream)
        val compressedBytes = outputStream.toByteArray()

        // 3. Convertir a Base64 String
        return Base64.encodeToString(compressedBytes, Base64.DEFAULT)
    }

    private fun createOrder() {
        val device = binding.etDeviceModel.text.toString().trim()
        val description = binding.etIssueDescription.text.toString().trim()
        val shelf = binding.etShelf.text.toString().trim().ifBlank { null }

        if (device.isBlank() || description.isBlank()) {
            Toast.makeText(requireContext(), "Completa dispositivo y descripción", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnCreateOrder.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            // 1. Procesar imagen si existe
            var photoBase64Data: String? = null
            if (selectedImageBytes != null) {
                try {
                    photoBase64Data = processImageToBase64(selectedImageBytes!!)
                } catch (e: Exception) {
                    onCreateFailure("Error procesando imagen: ${e.message}")
                    return@launch
                }
            }

            // 2. Llamar al repositorio
            val created = workOrderRepo.createOrder(
                deviceModel = device,
                issueDescription = description,
                shelfLocation = shelf,

                // --- CORRECCIÓN AQUÍ: Usamos el nombre correcto del parámetro ---
                photoBase64 = photoBase64Data,
                // ---------------------------------------------------------------

                createdBy = viewModel.currentUserId.value ?: ""
            )

            created.fold(onSuccess = { orderId ->
                onCreateSuccess(orderId)
            }, onFailure = { err ->
                onCreateFailure("Error al crear orden: ${err.message}")
            })
        }
    }

    private fun onCreateSuccess(orderId: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.GONE
            binding.btnCreateOrder.isEnabled = true
            Toast.makeText(requireContext(), "Orden creada (con foto local)", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun onCreateFailure(message: String?) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.GONE
            binding.btnCreateOrder.isEnabled = true
            Toast.makeText(requireContext(), message ?: "Error", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeViewModel() {
        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}