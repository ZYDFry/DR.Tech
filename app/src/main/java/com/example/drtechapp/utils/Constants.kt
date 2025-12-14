package com.example.drtechapp.utils

// Colecciones de Firestore
const val COLL_ORDERS = "work_orders"
const val COLL_USERS = "users"
const val COLL_CONFIG = "config"

// Estados de las órdenes (DEBEN COINCIDIR EXACTAMENTE con tu Firebase)
const val STATUS_PENDING = "Pendiente"        // 🔴 Color Rojo
const val STATUS_WORKING = "En Reparación"    // 🔵 Color Azul
const val STATUS_FINISHED = "Terminado"       // 🟢 Color Verde

// Roles de usuario
const val ROLE_ADMIN = "Admin"
const val ROLE_TECHNICIAN = "Tecnico"

// Códigos de acceso (documento en Firestore)
const val CONFIG_DOC_ID = "access_codes"
const val KEY_ADMIN_CODE = "admin_code"
const val KEY_TECH_CODE = "tech_code"

// Campos de la base de datos (para queries y actualizaciones)
object OrderFields {
    const val STATUS = "status"
    const val DATE_CREATED = "dateCreated"
    const val DATE_STARTED = "dateStarted"
    const val DATE_FINISHED = "dateFinished"
    const val ASSIGNED_TECHNICIAN_ID = "assignedTechnicianId"
    const val ASSIGNED_TECHNICIAN_NAME = "assignedTechnicianName"
    const val DEVICE_MODEL = "deviceModel"
    const val ISSUE_DESCRIPTION = "issueDescription"
    const val SHELF_LOCATION = "shelfLocation"
    const val PHOTO_URL = "photoUrl"
    const val CREATED_BY = "createdBy"
}

object UserFields {
    const val DNI = "dni"
    const val EMAIL = "email"
    const val FIRST_NAME = "firstName"
    const val LAST_NAME = "lastName"
    const val ROLE = "role"
}