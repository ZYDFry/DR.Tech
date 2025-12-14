package com.example.drtechapp.model

import com.example.drtechapp.utils.STATUS_FINISHED
import com.example.drtechapp.utils.STATUS_PENDING
import com.example.drtechapp.utils.STATUS_WORKING
import com.google.firebase.firestore.DocumentId

data class RepairOrder(
    @DocumentId
    val id: String = "",

    // Información del cliente (opcionales - no están en Firebase actual)
    val clientName: String? = null,
    val clientPhone: String? = null,
    val clientEmail: String? = null,

    // Dispositivo (ajustado a tu estructura)
    val deviceModel: String = "",           // Ej: "Redmi note 7"

    // Detalles de la reparación
    val issueDescription: String = "",      // Descripción del problema
    val shelfLocation: String? = null,      // Ubicación en estante (Ej: "A-8")

    // Estado y asignación (usando constante por defecto)
    val status: String = STATUS_PENDING,
    val assignedTechnicianId: String? = null,
    val assignedTechnicianName: String? = null,

    // Foto (string en lugar de array)
    val photoUrl: String = "",              // URL de Firebase Storage

    // Quién creó la orden
    val createdBy: String = "",             // UID del admin que creó

    // Timestamps (Long en milisegundos)
    val dateCreated: Long = 0L,             // Timestamp en milisegundos
    val dateStarted: Long? = null,          // Cuando se tomó
    val dateFinished: Long? = null          // Cuando se finalizó
) {
    /**
     * Verifica si la orden está pendiente
     */
    fun isPending(): Boolean = status == STATUS_PENDING

    /**
     * Verifica si la orden está en reparación
     */
    fun isWorking(): Boolean = status == STATUS_WORKING

    /**
     * Verifica si la orden está terminada
     */
    fun isFinished(): Boolean = status == STATUS_FINISHED

    /**
     * Verifica si la orden está asignada a un técnico
     */
    fun isAssigned(): Boolean = assignedTechnicianId != null
}