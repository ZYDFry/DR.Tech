package com.example.drtechapp.repository

import com.example.drtechapp.model.User
import com.example.drtechapp.utils.COLL_CONFIG
import com.example.drtechapp.utils.COLL_USERS
import com.example.drtechapp.utils.CONFIG_DOC_ID
import com.example.drtechapp.utils.KEY_ADMIN_CODE
import com.example.drtechapp.utils.KEY_TECH_CODE
import com.example.drtechapp.utils.ROLE_ADMIN
import com.example.drtechapp.utils.ROLE_TECHNICIAN
import com.example.drtechapp.utils.UserFields
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection(COLL_USERS)
    private val configCollection = firestore.collection(COLL_CONFIG)

    /**
     * Obtiene el ID del usuario actualmente autenticado
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Valida si el código de acceso es válido y devuelve el rol correspondiente
     */
    suspend fun validateAccessCode(code: String): Result<String> {
        return try {
            val configDoc = configCollection.document(CONFIG_DOC_ID).get().await()

            val adminCode = configDoc.getString(KEY_ADMIN_CODE)
            val techCode = configDoc.getString(KEY_TECH_CODE)

            val role = when (code) {
                adminCode -> ROLE_ADMIN
                techCode -> ROLE_TECHNICIAN
                else -> return Result.failure(Exception("Código de acceso inválido"))
            }

            Result.success(role)
        } catch (e: Exception) {
            Result.failure(Exception("Error al validar código: ${e.message}"))
        }
    }

    /**
     * Registra un nuevo usuario con email, contraseña y código de acceso
     */
    suspend fun registerUser(
        dni: String,
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        accessCode: String
    ): Result<User> {
        return try {
            // Validar código de acceso primero
            val roleResult = validateAccessCode(accessCode)
            if (roleResult.isFailure) {
                return Result.failure(roleResult.exceptionOrNull()!!)
            }
            val role = roleResult.getOrThrow()

            // Crear usuario en Firebase Auth
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Error al crear usuario"))

            // Crear documento en Firestore usando constantes
            val userData = hashMapOf(
                UserFields.DNI to dni,
                UserFields.EMAIL to email,
                UserFields.FIRST_NAME to firstName,
                UserFields.LAST_NAME to lastName,
                UserFields.ROLE to role
            )

            usersCollection.document(uid).set(userData).await()

            val user = User(
                id = uid,
                dni = dni,
                email = email,
                firstName = firstName,
                lastName = lastName,
                role = role
            )

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception("Error al registrar: ${e.message}"))
        }
    }

    /**
     * Inicia sesión con email y contraseña
     */
    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Error al iniciar sesión"))

            val userDoc = usersCollection.document(uid).get().await()

            // Usar constantes para leer campos
            val user = User(
                id = uid,
                dni = userDoc.getString(UserFields.DNI) ?: "",
                email = userDoc.getString(UserFields.EMAIL) ?: "",
                firstName = userDoc.getString(UserFields.FIRST_NAME) ?: "",
                lastName = userDoc.getString(UserFields.LAST_NAME) ?: "",
                role = userDoc.getString(UserFields.ROLE) ?: ""
            )

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception("Error al iniciar sesión: ${e.message}"))
        }
    }

    /**
     * Obtiene el rol del usuario actual
     */
    suspend fun getUserRole(userId: String): Result<String> {
        return try {
            val userDoc = usersCollection.document(userId).get().await()
            val role = userDoc.getString(UserFields.ROLE)
                ?: return Result.failure(Exception("Rol no encontrado"))

            Result.success(role)
        } catch (e: Exception) {
            Result.failure(Exception("Error al obtener rol: ${e.message}"))
        }
    }

    /**
     * Obtiene los datos completos del usuario
     */
    suspend fun getUserData(userId: String): Result<User> {
        return try {
            val userDoc = usersCollection.document(userId).get().await()

            // Usar constantes para leer campos
            val user = User(
                id = userId,
                dni = userDoc.getString(UserFields.DNI) ?: "",
                email = userDoc.getString(UserFields.EMAIL) ?: "",
                firstName = userDoc.getString(UserFields.FIRST_NAME) ?: "",
                lastName = userDoc.getString(UserFields.LAST_NAME) ?: "",
                role = userDoc.getString(UserFields.ROLE) ?: ""
            )

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception("Error al obtener datos: ${e.message}"))
        }
    }

    /**
     * Cierra sesión
     */
    fun logout() {
        auth.signOut()
    }
}