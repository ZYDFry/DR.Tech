package com.example.drtechapp.repository

import com.example.drtechapp.model.RepairOrder
import com.example.drtechapp.utils.COLL_ORDERS
import com.example.drtechapp.utils.COLL_USERS
import com.example.drtechapp.utils.OrderFields
import com.example.drtechapp.utils.STATUS_FINISHED
import com.example.drtechapp.utils.STATUS_PENDING
import com.example.drtechapp.utils.STATUS_WORKING
import com.example.drtechapp.utils.UserFields
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WorkOrderRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val ordersCollection = firestore.collection(COLL_ORDERS)
    private val usersCollection = firestore.collection(COLL_USERS)

    /**
     * Obtiene todas las órdenes de un estado específico
     * (usado por Admin para ver todas las órdenes)
     */
    suspend fun getOrdersByStatus(status: String): Result<List<RepairOrder>> {
        return try {
            val snapshot = ordersCollection
                .whereEqualTo(OrderFields.STATUS, status)
                .orderBy(OrderFields.DATE_CREATED, Query.Direction.DESCENDING)
                .get()
                .await()

            val orders = snapshot.documents.mapNotNull { doc ->
                doc.toObject(RepairOrder::class.java)?.copy(id = doc.id)
            }

            // Enriquecer con nombres de técnicos si están asignados
            val enrichedOrders = if (status != STATUS_PENDING) {
                enrichOrdersWithTechnicianNames(orders)
            } else {
                orders
            }

            Result.success(enrichedOrders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene órdenes asignadas a un técnico específico con un estado
     * (usado por Técnicos para ver solo sus órdenes)
     */
    suspend fun getOrdersByTechnicianAndStatus(
        technicianId: String,
        status: String
    ): Result<List<RepairOrder>> {
        return try {
            val snapshot = ordersCollection
                .whereEqualTo(OrderFields.ASSIGNED_TECHNICIAN_ID, technicianId)
                .whereEqualTo(OrderFields.STATUS, status)
                .orderBy(OrderFields.DATE_CREATED, Query.Direction.DESCENDING)
                .get()
                .await()

            val orders = snapshot.documents.mapNotNull { doc ->
                doc.toObject(RepairOrder::class.java)?.copy(id = doc.id)
            }

            Result.success(orders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Toma una orden pendiente y la cambia a "En Reparación"
     * Usa transacción para evitar que dos técnicos tomen la misma orden
     */
    suspend fun takeOrder(orderId: String, technicianId: String): Result<Unit> {
        return try {
            // Obtener nombre del técnico
            val techName = getUserFullName(technicianId) ?: "Desconocido"

            firestore.runTransaction { transaction ->
                val orderRef = ordersCollection.document(orderId)
                val snapshot = transaction.get(orderRef)

                // Validar que la orden esté pendiente
                val currentStatus = snapshot.getString(OrderFields.STATUS)
                if (currentStatus != STATUS_PENDING) {
                    throw Exception("Esta orden ya fue tomada por otro técnico")
                }

                // Actualizar la orden usando constantes
                transaction.update(orderRef, mapOf(
                    OrderFields.STATUS to STATUS_WORKING,
                    OrderFields.ASSIGNED_TECHNICIAN_ID to technicianId,
                    OrderFields.ASSIGNED_TECHNICIAN_NAME to techName,
                    OrderFields.DATE_STARTED to System.currentTimeMillis()
                ))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Finaliza una orden en reparación
     */
    suspend fun finishOrder(orderId: String): Result<Unit> {
        return try {
            ordersCollection.document(orderId).update(
                mapOf(
                    OrderFields.STATUS to STATUS_FINISHED,
                    OrderFields.DATE_FINISHED to System.currentTimeMillis()
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Devuelve una orden a estado pendiente (solo Admin)
     * Limpia la asignación del técnico
     */
    suspend fun returnOrderToPending(orderId: String): Result<Unit> {
        return try {
            ordersCollection.document(orderId).update(
                mapOf(
                    OrderFields.STATUS to STATUS_PENDING,
                    OrderFields.ASSIGNED_TECHNICIAN_ID to null,
                    OrderFields.ASSIGNED_TECHNICIAN_NAME to null,
                    OrderFields.DATE_STARTED to null
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Crea una nueva orden (solo Admin)
     */
    suspend fun createOrder(
        deviceModel: String,
        issueDescription: String,
        shelfLocation: String?,
        photoUrl: String?,
        createdBy: String
    ): Result<String> {
        return try {
            // Usar constantes para los campos
            val orderData = hashMapOf(
                OrderFields.DEVICE_MODEL to deviceModel,
                OrderFields.ISSUE_DESCRIPTION to issueDescription,
                OrderFields.SHELF_LOCATION to (shelfLocation ?: ""),
                OrderFields.PHOTO_URL to (photoUrl ?: ""),
                OrderFields.CREATED_BY to createdBy,
                OrderFields.STATUS to STATUS_PENDING,
                OrderFields.DATE_CREATED to System.currentTimeMillis(),
                OrderFields.ASSIGNED_TECHNICIAN_ID to null,
                OrderFields.ASSIGNED_TECHNICIAN_NAME to null,
                OrderFields.DATE_STARTED to null,
                OrderFields.DATE_FINISHED to null
            )

            val docRef = ordersCollection.add(orderData).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Actualiza una orden existente (solo Admin)
     */
    suspend fun updateOrder(orderId: String, updates: Map<String, Any?>): Result<Unit> {
        return try {
            ordersCollection.document(orderId)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene una orden específica por ID
     */
    suspend fun getOrderById(orderId: String): Result<RepairOrder> {
        return try {
            val snapshot = ordersCollection.document(orderId).get().await()
            val order = snapshot.toObject(RepairOrder::class.java)
                ?.copy(id = snapshot.id)
                ?: throw Exception("Orden no encontrada")

            Result.success(order)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sube una imagen a Firebase Storage y devuelve la URL
     */
    suspend fun uploadOrderImage(orderId: String, imageBytes: ByteArray): Result<String> {
        return try {
            val imageId = UUID.randomUUID().toString()
            val imagePath = "orders/$orderId/$imageId.jpg"
            val storageRef = storage.reference.child(imagePath)

            storageRef.putBytes(imageBytes).await()
            val downloadUrl = storageRef.downloadUrl.await()

            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== FUNCIONES AUXILIARES =====

    /**
     * Enriquece la lista de órdenes con los nombres de los técnicos asignados
     */
    private suspend fun enrichOrdersWithTechnicianNames(
        orders: List<RepairOrder>
    ): List<RepairOrder> {
        val technicianIds = orders.mapNotNull { it.assignedTechnicianId }.distinct()
        if (technicianIds.isEmpty()) return orders

        // Cargar nombres en batch
        val technicianNames = mutableMapOf<String, String>()
        technicianIds.forEach { id ->
            getUserFullName(id)?.let { name ->
                technicianNames[id] = name
            }
        }

        // Asignar nombres a las órdenes
        return orders.map { order ->
            order.assignedTechnicianId?.let { id ->
                order.copy(assignedTechnicianName = technicianNames[id])
            } ?: order
        }
    }

    /**
     * Obtiene el nombre completo de un usuario desde Firestore
     */
    private suspend fun getUserFullName(userId: String): String? {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            val firstName = snapshot.getString(UserFields.FIRST_NAME) ?: ""
            val lastName = snapshot.getString(UserFields.LAST_NAME) ?: ""
            "$firstName $lastName".trim().ifBlank {
                snapshot.getString(UserFields.EMAIL)?.split("@")?.first()
            }
        } catch (e: Exception) {
            null
        }
    }
}