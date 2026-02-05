package com.example.drtechapp.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drtechapp.model.RepairOrder
import com.example.drtechapp.repository.AuthRepository
import com.example.drtechapp.repository.WorkOrderRepository
import com.example.drtechapp.utils.ROLE_ADMIN
import com.example.drtechapp.utils.ROLE_TECHNICIAN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class TechViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val orderRepository = WorkOrderRepository()

    // Estado del usuario
    private val _userRole = MutableLiveData<String?>()
    val userRole: LiveData<String?> = _userRole

    private val _currentUserId = MutableLiveData<String?>()
    val currentUserId: LiveData<String?> = _currentUserId

    private val _userName = MutableLiveData<String?>()
    val userName: LiveData<String?> = _userName

    // Lista de órdenes
    private val _orders = MutableLiveData<List<RepairOrder>>()
    val orders: LiveData<List<RepairOrder>> = _orders

    // Orden individual (para detalle)
    private val _currentOrder = MutableLiveData<RepairOrder?>()
    val currentOrder: LiveData<RepairOrder?> = _currentOrder

    // Nombre del CREADOR de la orden (separado del usuario actual)
    private val _orderCreatorName = MutableLiveData<String?>()
    val orderCreatorName: LiveData<String?> = _orderCreatorName

    // Estado de carga
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Mensajes de error
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Mensajes de éxito
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    init {
        loadUserProfile()
    }

    /**
     * Carga el perfil del usuario actual (rol, ID y nombre)
     */
    fun loadUserProfile() {
        viewModelScope.launch {
            _isLoading.value = true

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _errorMessage.value = "Usuario no autenticado"
                _isLoading.value = false
                return@launch
            }

            _currentUserId.value = userId

            authRepository.getUserData(userId).fold(
                onSuccess = { user ->
                    _userRole.value = user.role
                    _userName.value = user.fullName
                    _isLoading.value = false
                    android.util.Log.d("DEBUG_APP", "Usuario logueado: ${user.fullName} (${user.role})")
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al cargar perfil: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Carga TODAS las órdenes de un estado específico (para Admin)
     */
    fun loadOrdersByStatus(status: String) {
        viewModelScope.launch {
            _isLoading.value = true
            android.util.Log.d("DEBUG_APP", "[Admin] Buscando órdenes con estado: $status")

            orderRepository.getOrdersByStatus(status).fold(
                onSuccess = { ordersList ->
                    android.util.Log.d("DEBUG_APP", "[Admin] ¡Éxito! Encontradas: ${ordersList.size}")
                    _orders.value = ordersList
                    _isLoading.value = false
                },
                onFailure = { error ->
                    android.util.Log.e("DEBUG_APP", "[Admin] Error fatal: ${error.message}")
                    _errorMessage.value = "Error al cargar órdenes: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Carga solo las órdenes asignadas a un técnico específico
     */
    fun loadMyOrders(technicianId: String, status: String) {
        viewModelScope.launch {
            _isLoading.value = true
            android.util.Log.d("DEBUG_APP", "[Tech] Buscando MIS órdenes ($status) para ID: $technicianId")

            orderRepository.getOrdersByTechnicianAndStatus(technicianId, status).fold(
                onSuccess = { ordersList ->
                    android.util.Log.d("DEBUG_APP", "[Tech] ¡Éxito! Mis órdenes encontradas: ${ordersList.size}")
                    _orders.value = ordersList
                    _isLoading.value = false
                },
                onFailure = { error ->
                    android.util.Log.e("DEBUG_APP", "[Tech] Error buscando mis órdenes: ${error.message}")
                    _errorMessage.value = "Error al cargar mis órdenes: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Carga una orden específica por ID y busca el nombre de quien la creó
     */
    fun loadOrderDetail(orderId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _orderCreatorName.value = "Cargando..."

            orderRepository.getOrderById(orderId).fold(
                onSuccess = { order ->
                    _currentOrder.value = order

                    // Lógica para obtener el nombre del creador real
                    if (order.createdByName.isNotEmpty()) {
                        authRepository.getUserData(order.createdBy).fold(
                            onSuccess = { user ->
                                _orderCreatorName.value = user.fullName
                            },
                            onFailure = {
                                _orderCreatorName.value = "Desconocido"
                            }
                        )
                    } else {
                        _orderCreatorName.value = "Sin asignar"
                    }

                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al cargar orden: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Crea una nueva orden procesando la imagen en segundo plano
     * (Esta es la función MOVIDA desde el Fragmento)
     */
    fun createOrder(
        deviceModel: String,
        issueDescription: String,
        shelfLocation: String?,
        imageBytes: ByteArray?
    ) {
        val userId = _currentUserId.value ?: return
        val nameToSend = _userName.value ?: return

        viewModelScope.launch {
            _isLoading.value = true

            // 1. Procesar la imagen (Si existe) en hilo secundario (IO)
            val photoBase64 = if (imageBytes != null) {
                processImageToBase64(imageBytes)
            } else {
                null
            }

            // 2. Enviar a Firebase
            orderRepository.createOrder(
                deviceModel = deviceModel,
                issueDescription = issueDescription,
                shelfLocation = shelfLocation,
                photoBase64 = photoBase64,
                createdBy = userId,
                createdByName = nameToSend
            ).fold(
                onSuccess = { orderId ->
                    _successMessage.value = "Orden creada: #${orderId.take(8)}"
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al crear: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Función auxiliar para comprimir imagen
     */
    private suspend fun processImageToBase64(bytes: ByteArray): String {
        return withContext(Dispatchers.Default) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val outputStream = ByteArrayOutputStream()
                // Comprimir calidad 30
                bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream)
                val compressedBytes = outputStream.toByteArray()
                Base64.encodeToString(compressedBytes, Base64.DEFAULT)
            } catch (e: Exception) {
                e.printStackTrace()
                "" // Retornar vacío si falla
            }
        }
    }

    fun takeOrder(orderId: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            orderRepository.takeOrder(orderId, userId).fold(
                onSuccess = {
                    _successMessage.value = "Orden tomada exitosamente"
                    _isLoading.value = false
                    loadOrderDetail(orderId)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message
                    _isLoading.value = false
                }
            )
        }
    }

    fun finishOrder(orderId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            orderRepository.finishOrder(orderId).fold(
                onSuccess = {
                    _successMessage.value = "Orden finalizada exitosamente"
                    _isLoading.value = false
                    loadOrderDetail(orderId)
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al finalizar: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun returnToPending(orderId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            orderRepository.returnOrderToPending(orderId).fold(
                onSuccess = {
                    _successMessage.value = "Orden devuelta a pendiente"
                    _isLoading.value = false
                    loadOrderDetail(orderId)
                },
                onFailure = { error ->
                    _errorMessage.value = "Error: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun updateOrder(orderId: String, updates: Map<String, Any?>) {
        viewModelScope.launch {
            _isLoading.value = true
            orderRepository.updateOrder(orderId, updates).fold(
                onSuccess = {
                    _successMessage.value = "Orden actualizada exitosamente"
                    _isLoading.value = false
                    loadOrderDetail(orderId)
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al actualizar: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun isAdmin(): Boolean = _userRole.value == ROLE_ADMIN
    fun isTechnician(): Boolean = _userRole.value == ROLE_TECHNICIAN

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
    fun logout() {
        // Opción A: Si tu AuthRepository ya tiene logout
        authRepository.logout()
        // Limpiamos las variables para que no queden datos viejos
        _currentUserId.value = null
        _userRole.value = null
        _userName.value = null
    }

}