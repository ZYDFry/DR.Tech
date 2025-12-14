package com.example.drtechapp.model

import com.example.drtechapp.utils.STATUS_FINISHED
import com.example.drtechapp.utils.STATUS_PENDING
import com.example.drtechapp.utils.STATUS_WORKING
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class RepairOrder(
    @DocumentId
    val id: String = "",

    val clientName: String? = null,
    val clientPhone: String? = null,
    val clientEmail: String? = null,
    val deviceModel: String = "",
    val issueDescription: String = "",
    val shelfLocation: String? = null,

    val status: String = STATUS_PENDING,
    val assignedTechnicianId: String? = null,
    val assignedTechnicianName: String? = null,
    val createdBy: String = "",

    val dateCreated: Long = 0L,
    val dateStarted: Long? = null,
    val dateFinished: Long? = null,

    // --- CAMBIOS AQUÍ ---

    // 1. Mantenemos este campo para las órdenes VIEJAS que tengan URL
    val photoUrl: String = "",

    // 2. Agregamos este campo nuevo para las órdenes NUEVAS (Base64)
    // Usamos @PropertyName para decirle a Firebase que busque exactamente "photo_base64"
    @get:PropertyName("photo_base64")
    @set:PropertyName("photo_base64")
    var photoBase64: String? = null

) {
    fun isPending(): Boolean = status == STATUS_PENDING
    fun isWorking(): Boolean = status == STATUS_WORKING
    fun isFinished(): Boolean = status == STATUS_FINISHED
    fun isAssigned(): Boolean = assignedTechnicianId != null
}