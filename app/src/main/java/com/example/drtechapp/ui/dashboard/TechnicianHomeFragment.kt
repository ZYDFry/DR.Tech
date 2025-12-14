package com.example.drtechapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.drtechapp.R
import com.example.drtechapp.databinding.FragmentTechnicianHomeBinding
import com.example.drtechapp.ui.adapter.RepairOrderAdapter
import com.example.drtechapp.utils.*
import com.example.drtechapp.viewmodel.TechViewModel
import com.google.android.material.tabs.TabLayout

class TechnicianHomeFragment : Fragment() {

    private var _binding: FragmentTechnicianHomeBinding? = null
    private val binding get() = _binding!!

    // Compartido con la Activity
    private val viewModel: TechViewModel by activityViewModels()
    private lateinit var adapter: RepairOrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTechnicianHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabs()
        setupFab()
        observeViewModel()

        // Seleccionar tab Inicial visualmente
        binding.tabLayout.getTabAt(0)?.select()

        // --- CORRECCIÓN AQUÍ ---
        // Forzamos la carga de datos inicial manualmente
        // para que no aparezca la pantalla en blanco al entrar.
        loadOrders(STATUS_PENDING)

        // --- NUEVO: Configurar el botón de Salir ---
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupRecyclerView() {
        adapter = RepairOrderAdapter(
            onItemClick = { order ->
                val action = TechnicianHomeFragmentDirections
                    .actionTechnicianHomeToOrderDetail(order.id)
                findNavController().navigate(action)
            },
            showAssignedTo = false
        )

        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TechnicianHomeFragment.adapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.apply {
            addTab(newTab().setText("🔴 Pendientes"))
            addTab(newTab().setText("🔵 En Reparación"))
            addTab(newTab().setText("🟢 Terminadas"))
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                clearOrdersList()
                when (tab?.position) {
                    0 -> loadOrders(STATUS_PENDING)
                    1 -> loadOrders(STATUS_WORKING)
                    2 -> loadOrders(STATUS_FINISHED)
                }
                // Actualizamos el mensaje de "Vacío" según la tab seleccionada
                updateEmptyStateMessage(viewModel.isAdmin())
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupFab() {
        binding.fabCreateOrder.setOnClickListener {
            findNavController().navigate(R.id.action_technicianHome_to_createOrder)
        }
    }

    private fun observeViewModel() {
        viewModel.userRole.observe(viewLifecycleOwner) { role ->
            updateUIForRole(role)
            // Opcional: Si cambia el rol, recargamos la lista actual para asegurar
            val currentTab = binding.tabLayout.selectedTabPosition
            val status = when(currentTab) {
                1 -> STATUS_WORKING
                2 -> STATUS_FINISHED
                else -> STATUS_PENDING
            }
            loadOrders(status)
        }

        viewModel.currentUserId.observe(viewLifecycleOwner) { /* ... */ }

        viewModel.userName.observe(viewLifecycleOwner) { name ->
            binding.tvWelcome.text = "Hola, ${name ?: "usuario"}"
        }

        viewModel.orders.observe(viewLifecycleOwner) { orders ->
            if (orders.isNullOrEmpty()) {
                binding.emptyContainer.isVisible = true
                binding.rvOrders.isVisible = false
                // Actualizar mensaje de texto vacío
                updateEmptyStateMessage(viewModel.isAdmin())
            } else {
                binding.emptyContainer.isVisible = false
                binding.rvOrders.isVisible = true
                adapter.submitList(orders)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun updateUIForRole(role: String?) {
        when (role) {
            ROLE_ADMIN -> {
                binding.fabCreateOrder.isVisible = true
                adapter.showAssignedTo = true
                adapter.notifyDataSetChanged() // Refrescar para mostrar nombres
                binding.tvTitle.text = "Panel de Administración"
            }
            ROLE_TECHNICIAN -> {
                binding.fabCreateOrder.isVisible = false
                adapter.showAssignedTo = false
                adapter.notifyDataSetChanged()
                binding.tvTitle.text = "Mis Órdenes de Reparación"
            }
            else -> {
                binding.fabCreateOrder.isVisible = false
                adapter.showAssignedTo = false
            }
        }
        updateEmptyStateMessage(role == ROLE_ADMIN)
    }

    private fun updateEmptyStateMessage(isAdmin: Boolean) {
        val currentTab = binding.tabLayout.selectedTabPosition
        binding.tvEmptyState.text = when {
            isAdmin -> when (currentTab) {
                0 -> "No hay órdenes pendientes\nCrea una nueva orden con el botón +"
                1 -> "No hay órdenes en reparación"
                2 -> "No hay órdenes terminadas"
                else -> "No hay órdenes"
            }
            else -> when (currentTab) {
                0 -> "No hay órdenes pendientes globales"
                1 -> "No tienes órdenes asignadas\nVe a 'Pendientes' y toma una."
                2 -> "No has terminado ninguna orden todavía"
                else -> "No hay órdenes"
            }
        }
    }

    private fun loadOrders(status: String) {
        val isAdmin = viewModel.isAdmin()
        val userId = viewModel.currentUserId.value

        when {
            isAdmin -> viewModel.loadOrdersByStatus(status)
            // Si es PENDIENTE, siempre cargamos la lista global (admin o técnico ven lo mismo)
            status == STATUS_PENDING -> viewModel.loadOrdersByStatus(STATUS_PENDING)
            // Si es WORKING/FINISHED y es técnico, cargamos SUS órdenes
            userId != null -> viewModel.loadMyOrders(userId, status)
            else -> {
                // Si llegamos aquí y no hay UserID, intentamos cargar perfil de nuevo
                viewModel.loadUserProfile()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que quieres salir?")
            .setPositiveButton("Sí, salir") { _, _ ->
                // 1. Decirle al ViewModel que cierre sesión
                viewModel.logout()

                // 2. Navegar a la pantalla de Login
                // (Asegúrate de que 'loginFragment' sea el ID correcto en tu nav_graph)
                findNavController().navigate(
                    R.id.action_technicianHome_to_loginFragment,
                    null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.technicianHomeFragment, true) // Borra el historial para no poder volver atrás
                        .build()
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    private fun clearOrdersList() {
        binding.rvOrders.isVisible = false       // Ocultamos la lista
        binding.emptyContainer.isVisible = false // Ocultamos avisos de vacío
        binding.progressBar.isVisible = true     // Mostramos cargando
        adapter.submitList(emptyList())          // Vaciamos los items del adaptador
    }
}