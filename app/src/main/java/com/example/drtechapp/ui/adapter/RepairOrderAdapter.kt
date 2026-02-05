package com.example.drtechapp.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.drtechapp.R
import com.example.drtechapp.databinding.ItemOrderBinding
import com.example.drtechapp.model.RepairOrder
import com.example.drtechapp.utils.STATUS_FINISHED
import com.example.drtechapp.utils.STATUS_PENDING
import com.example.drtechapp.utils.STATUS_WORKING
import java.text.SimpleDateFormat
import java.util.*

class RepairOrderAdapter(
    private val onItemClick: (RepairOrder) -> Unit,
    var showAssignedTo: Boolean = false
) : ListAdapter<RepairOrder, RepairOrderAdapter.OrderViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(
        private val binding: ItemOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(order: RepairOrder) {
            // ID de la orden
            binding.tvOrderId.text = "#${order.id.take(8).uppercase()}"

            // Dispositivo
            binding.tvDeviceType.text = order.deviceModel

            // Descripción del problema
            val description = if (order.issueDescription.length > 60) {
                order.issueDescription.take(60) + "..."
            } else {
                order.issueDescription
            }
            binding.tvIssueDescription.text = description

            // Ubicación en estante
            if (!order.shelfLocation.isNullOrBlank()) {
                binding.tvShelfLocation.visibility = View.VISIBLE
                binding.tvShelfLocation.text = "📍 ${order.shelfLocation}"
            } else {
                binding.tvShelfLocation.visibility = View.GONE
            }

            // Estado con colores
            binding.tvStatus.text = order.status
            val context = binding.root.context

            when (order.status) {
                STATUS_PENDING -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                }
                STATUS_WORKING -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_working)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                }
                STATUS_FINISHED -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_finished)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                }
                else -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.white))
                }
            }

            // Fecha de creación
            if (order.dateCreated > 0) {
                val date = Date(order.dateCreated)
                val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                binding.tvCreatedAt.text = formatter.format(date)
            } else {
                binding.tvCreatedAt.text = "Fecha no disponible"
            }

            // Técnico asignado (solo para Admin)
            if (showAssignedTo && order.status != STATUS_PENDING) {
                binding.tvAssignedTo.visibility = View.VISIBLE
                binding.tvAssignedTo.text = "👤 ${order.assignedTechnicianName ?: "No asignado"}"
            } else {
                binding.tvAssignedTo.visibility = View.GONE
            }
            val techName = order.assignedTechnicianName
            val creatorName = order.createdByName // Asegúrate de tener este campo en tu modelo

            if (!techName.isNullOrBlank()) {
                // CASO 1: Hay técnico (En Proceso / Terminada) -> Color VERDE
                binding.tvAssignedTo.visibility = View.VISIBLE
                binding.tvAssignedTo.text = "👤 $techName"
                binding.tvAssignedTo.setTextColor(Color .parseColor("#4CAF50"))
            }
            else if (!creatorName.isNullOrBlank()) {
                // CASO 2: Pendiente (Sin técnico) -> Color GRIS
                binding.tvAssignedTo.visibility = View.VISIBLE
                binding.tvAssignedTo.text = "📝 Creado por: $creatorName"
                binding.tvAssignedTo.setTextColor(Color.GRAY)
            }
            else {
                // CASO 3: Nada que mostrar
                binding.tvAssignedTo.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RepairOrder>() {
        override fun areItemsTheSame(oldItem: RepairOrder, newItem: RepairOrder): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RepairOrder, newItem: RepairOrder): Boolean {
            return oldItem == newItem
        }
    }
}