package com.example.drtechapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drtechapp.model.RepairOrder
import com.example.drtechapp.repository.AuthRepository
import com.example.drtechapp.repository.WorkOrderRepository
import com.example.drtechapp.utils.ROLE_ADMIN
import com.example.drtechapp.utils.ROLE_TECHNICIAN
import kotlinx.coroutines.launch

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

            orderRepository.getOrdersByStatus(status).fold(
                onSuccess = { ordersList ->
                    _orders.value = ordersList
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al cargar órdenes: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Carga solo las órdenes asignadas a un técnico específico
     * (para Técnicos que ven solo sus órdenes)
     */
    fun loadMyOrders(technicianId: String, status: String) {
        viewModelScope.launch {
            _isLoading.value = true

            orderRepository.getOrdersByTechnicianAndStatus(technicianId, status).fold(
                onSuccess = { ordersList ->
                    _orders.value = ordersList
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al cargar mis órdenes: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Carga una orden específica por ID (para detalle)
     */
    fun loadOrderDetail(orderId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            orderRepository.getOrderById(orderId).fold(
                onSuccess = { order ->
                    _currentOrder.value = order
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
     * Toma una orden pendiente (asigna al técnico actual)
     */
    fun takeOrder(orderId: String) {
        val userId = _currentUserId.value ?: return

        viewModelScope.launch {
            _isLoading.value = true

            orderRepository.takeOrder(orderId, userId).fold(
                onSuccess = {
                    _successMessage.value = "Orden tomada exitosamente"
                    _isLoading.value = false
                    // Recargar la orden actual si estamos en detalle
                    loadOrderDetail(orderId)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Finaliza una orden en reparación
     */
    fun finishOrder(orderId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            orderRepository.finishOrder(orderId).fold(
                onSuccess = {
                    _successMessage.value = "Orden finalizada exitosamente"
                    _isLoading.value = false
                    // Recargar la orden actual
                    loadOrderDetail(orderId)
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al finalizar: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Devuelve una orden a estado pendiente (solo Admin)
     */
    fun returnToPending(orderId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            orderRepository.returnOrderToPending(orderId).fold(
                onSuccess = {
                    _successMessage.value = "Orden devuelta a pendiente"
                    _isLoading.value = false
                    // Recargar la orden actual
                    loadOrderDetail(orderId)
                },
                onFailure = { error ->
                    _errorMessage.value = "Error: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Crea una nueva orden (solo Admin)
     */
    fun createOrder(
        deviceModel: String,
        issueDescription: String,
        shelfLocation: String?,
        photoUrl: String?
    ) {
        val userId = _currentUserId.value ?: return

        viewModelScope.launch {
            _isLoading.value = true

            orderRepository.createOrder(
                deviceModel = deviceModel,
                issueDescription = issueDescription,
                shelfLocation = shelfLocation,
                photoUrl = photoUrl,
                createdBy = userId
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
     * Actualiza una orden existente (solo Admin)
     */
    fun updateOrder(orderId: String, updates: Map<String, Any?>) {
        viewModelScope.launch {
            _isLoading.value = true

            orderRepository.updateOrder(orderId, updates).fold(
                onSuccess = {
                    _successMessage.value = "Orden actualizada exitosamente"
                    _isLoading.value = false
                    // Recargar la orden
                    loadOrderDetail(orderId)
                },
                onFailure = { error ->
                    _errorMessage.value = "Error al actualizar: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Verifica si el usuario actual es Admin
     */
    fun isAdmin(): Boolean {
        return _userRole.value == ROLE_ADMIN
    }

    /**
     * Verifica si el usuario actual es Técnico
     */
    fun isTechnician(): Boolean {
        return _userRole.value == ROLE_TECHNICIAN
    }

    /**
     * Limpia mensajes de error/éxito
     */
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}