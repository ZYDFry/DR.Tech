package com.example.drtechapp.model

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    val id: String = "",              // uid de Firebase Auth
    val dni: String = "",              // DNI del usuario
    val email: String = "",
    val firstName: String = "",        // Nombre
    val lastName: String = "",         // Apellido
    val role: String = ""              // "Admin" o "Tecnico"
) {
    // Propiedad computada para nombre completo
    val fullName: String
        get() = "$firstName $lastName".trim()
}