package com.example.drtechapp.ui.detail

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.drtechapp.databinding.FragmentOrderDetailBinding
import com.example.drtechapp.utils.STATUS_FINISHED
import com.example.drtechapp.utils.STATUS_PENDING
import com.example.drtechapp.utils.STATUS_WORKING
import com.example.drtechapp.viewmodel.TechViewModel
import java.text.SimpleDateFormat
import java.util.*

class OrderDetailFragment : Fragment() {

    private var _binding: FragmentOrderDetailBinding? = null
    private val binding get() = _binding!!

    // Argumentos de navegación (contiene el orderId)
    private val args: OrderDetailFragmentArgs by navArgs()

    // ViewModel compartido
    private val viewModel: TechViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Cargar el detalle de la orden al iniciar
        viewModel.loadOrderDetail(args.orderId)

        // 2. Observar cambios en la orden
        viewModel.currentOrder.observe(viewLifecycleOwner) { order ->
            if (order == null) return@observe

            // --- Llenar datos de texto ---
            binding.tvDevice.text = order.deviceModel.ifBlank { "—" }
            binding.tvDescription.text = order.issueDescription.ifBlank { "—" }
            binding.tvShelf.text = order.shelfLocation ?: "Sin ubicación"
            binding.tvStatus.text = order.status

            // Asignación de técnico
            val assignedName = when {
                !order.assignedTechnicianName.isNullOrBlank() -> order.assignedTechnicianName
                !order.assignedTechnicianId.isNullOrBlank() -> "ID: ${order.assignedTechnicianId}"
                else -> "No asignado"
            }
            binding.tvAssignedTo.text = assignedName

            // Creador y Fecha
            binding.tvCreatedBy.text = order.createdBy.ifBlank { "Desconocido" }
            binding.tvCreatedAt.text = formatTimestamp(order.dateCreated)


            // --- LÓGICA HÍBRIDA DE IMAGEN ---
            binding.ivPhoto.visibility = View.GONE // Ocultar por defecto

            // PRIORIDAD 1: Imagen nueva (Base64 guardada como texto)
            if (!order.photoBase64.isNullOrEmpty()) {
                try {
                    val decodedBytes = Base64.decode(order.photoBase64, Base64.DEFAULT)
                    val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    binding.ivPhoto.setImageBitmap(decodedBitmap)
                    binding.ivPhoto.visibility = View.VISIBLE
                } catch (e: Exception) {
                    // Si falla la conversión, no mostramos nada
                }
            }
            // PRIORIDAD 2: Imagen vieja (URL de Storage)
            else if (order.photoUrl.isNotEmpty()) {
                binding.ivPhoto.visibility = View.VISIBLE
                Glide.with(requireContext())
                    .load(order.photoUrl)
                    .centerCrop()
                    .into(binding.ivPhoto)
            }


            // --- ACTUALIZAR BOTONES ---
            updateButtons(order.status, order.assignedTechnicianId)
        }

        // 3. Observar carga (ProgressBar)
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        // 4. Observar mensajes de éxito
        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
                // Recargar para ver los cambios de estado reflejados
                viewModel.loadOrderDetail(args.orderId)
            }
        }

        // 5. Observar errores
        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }

        // 6. Configurar Clicks de Botones
        binding.btnTake.setOnClickListener {
            viewModel.takeOrder(args.orderId)
        }

        binding.btnFinish.setOnClickListener {
            showConfirmationDialog("Finalizar orden", "¿Estás seguro de marcar esta orden como terminada?") {
                viewModel.finishOrder(args.orderId)
            }
        }

        binding.btnReturn.setOnClickListener {
            showConfirmationDialog("Devolver a pendiente", "¿Deseas devolver esta orden a estado Pendiente?") {
                viewModel.returnToPending(args.orderId)
            }
        }
    }

    /**
     * Lógica visual: Qué botones mostrar según el estado y quién soy.
     */
    private fun updateButtons(status: String, assignedTechId: String?) {
        val isAdmin = viewModel.isAdmin()
        val currentUserId = viewModel.currentUserId.value

        // Ocultar todo primero
        binding.btnTake.visibility = View.GONE
        binding.btnFinish.visibility = View.GONE
        binding.btnReturn.visibility = View.GONE

        when (status) {
            STATUS_PENDING -> {
                // Cualquier técnico puede tomar una orden libre
                binding.btnTake.visibility = View.VISIBLE
            }
            STATUS_WORKING -> {
                // Finalizar: Solo el técnico dueño O el Admin
                if (assignedTechId == currentUserId || isAdmin) {
                    binding.btnFinish.visibility = View.VISIBLE
                }
                // Devolver: Solo Admin (para corregir errores)
                if (isAdmin) {
                    binding.btnReturn.visibility = View.VISIBLE
                }
            }
            STATUS_FINISHED -> {
                // Reactivar: Solo Admin
                if (isAdmin) {
                    binding.btnReturn.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Helper para mostrar diálogos de confirmación
     */
    private fun showConfirmationDialog(title: String, msg: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("Sí") { _, _ -> onConfirm() }
            .setNegativeButton("No", null)
            .show()
    }

    /**
     * Helper para formatear fecha (Milisegundos -> Texto legible)
     */
    private fun formatTimestamp(ms: Long?): String {
        if (ms == null || ms <= 0L) return ""
        return try {
            val date = Date(ms)
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            sdf.format(date)
        } catch (e: Exception) {
            ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}