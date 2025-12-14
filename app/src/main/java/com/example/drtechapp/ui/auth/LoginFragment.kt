package com.example.drtechapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.drtechapp.databinding.FragmentLoginBinding
import com.example.drtechapp.repository.AuthRepository
import com.example.drtechapp.viewmodel.TechViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // compartir ViewModel con la actividad (para usar mismo estado)
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
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Completa email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.Main).launch {
                authRepo.loginUser(email, password).fold(
                    onSuccess = { user ->
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

        binding.btnRegister.setOnClickListener {
            // abrir dialog de registro
            val dialog = RegisterDialogFragment()
            dialog.show(childFragmentManager, "register_dialog")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}