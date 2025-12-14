package com.example.drtechapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.drtechapp.databinding.DialogRegisterBinding
import com.example.drtechapp.databinding.FragmentLoginBinding
import com.example.drtechapp.repository.AuthRepository
import com.example.drtechapp.viewmodel.TechViewModel
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Compartir ViewModel con la Activity para mantener estado global (usuario, role, etc.)
    private val viewModel: TechViewModel by activityViewModels()
    private val authRepo = AuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Botón de login
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Completa email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE
            // Usamos lifecycleScope para lanzar la corrutina ligada al fragment
            lifecycleScope.launch {
                authRepo.loginUser(email, password).fold(
                    onSuccess = {
                        // Cargar perfil en ViewModel y navegar al home
                        viewModel.loadUserProfile()
                        binding.progressBar.visibility = View.GONE
                        findNavController().navigate(LoginFragmentDirections.actionLoginToHome())
                    },
                    onFailure = { err ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Error: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        // Abrir diálogo de registro (sin crear otro Fragment) al pulsar "Registrarse"
        binding.btnRegister.setOnClickListener {
            showRegisterDialog()
        }
    }

    /**
     * Muestra un AlertDialog con el layout dialog_register.xml usando ViewBinding.
     * Al pulsar "Registrar" valida los campos y llama a AuthRepository.registerUser(...)
     * Nota: por defecto Firebase Auth iniciará sesión con la nueva cuenta creada.
     */
    private fun showRegisterDialog() {
        val dialogBinding = DialogRegisterBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Registrar usuario")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registrar", null) // sobreescribimos para controlar validación/dismiss

        val dialog = builder.create()
        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val dni = dialogBinding.etDni.text.toString().trim()
                val firstName = dialogBinding.etFirstName.text.toString().trim()
                val lastName = dialogBinding.etLastName.text.toString().trim()
                val email = dialogBinding.etEmail.text.toString().trim()
                val password = dialogBinding.etPassword.text.toString()
                val accessCode = dialogBinding.etAccessCode.text.toString().trim()

                // Validaciones básicas
                if (dni.isBlank() || firstName.isBlank() || email.isBlank() || password.isBlank() || accessCode.isBlank()) {
                    dialogBinding.tvError.text = "Completa todos los campos"
                    return@setOnClickListener
                }

                // Deshabilitar botón y mostrar progreso
                btn.isEnabled = false
                dialogBinding.progressBar.visibility = View.VISIBLE
                dialogBinding.tvError.text = ""

                lifecycleScope.launch {
                    authRepo.registerUser(
                        dni = dni,
                        firstName = firstName,
                        lastName = lastName,
                        email = email,
                        password = password,
                        accessCode = accessCode
                    ).fold(
                        onSuccess = {
                            dialogBinding.progressBar.visibility = View.GONE
                            btn.isEnabled = true
                            Toast.makeText(requireContext(), "Usuario registrado", Toast.LENGTH_SHORT).show()

                            // IMPORTANTE: Firebase suele iniciar sesión con la cuenta recién creada.
                            // Recargamos perfil en el ViewModel para reflejar cambios (si la sesión cambió).
                            viewModel.loadUserProfile()

                            dialog.dismiss()
                        },
                        onFailure = { err ->
                            dialogBinding.progressBar.visibility = View.GONE
                            btn.isEnabled = true
                            dialogBinding.tvError.text = err.message ?: "Error al registrar"
                        }
                    )
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}