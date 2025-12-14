package com.example.drtechapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.drtechapp.R
import com.example.drtechapp.databinding.FragmentTechnicianHomeBinding
import com.example.drtechapp.ui.adapter.RepairOrderAdapter
import com.example.drtechapp.utils.*
import com.example.drtechapp.viewmodel.TechViewModel
import com.google.android.material.tabs.TabLayout

class TechnicianHomeFragment  : Fragment() {

    private var _binding: FragmentTechnicianHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TechViewModel by viewModels()
    private lateinit var adapter: RepairOrderAdapter

    private var currentUserRole: String? = null
    private var currentUserId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTechnicianHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabs()
        setupFAB()
        observeViewModel()

        // Cargar órdenes pendientes por defecto
        binding.tabLayout.getTabAt(0)?.select()
    }

    private fun setupRecyclerView() {
        adapter = RepairOrderAdapter(
            onItemClick = { order ->
                // Navegar al detalle de la orden
                val action = TechnicianHomeFragmentDirections
                    .actionTechnicianHomeToOrderDetail(order.id)
                findNavController().navigate(action)
            },
            showAssignedTo = false // Se actualizará según el rol
        )

        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TechnicianHomeFragment.adapter
        }
    }

    private fun setupTabs() {
        // Agregar tabs con constantes
        binding.tabLayout.apply {
            addTab(newTab().setText("🔴 Pendientes"))
            addTab(newTab().setText("🔵 En Reparación"))
            addTab(newTab().setText("🟢 Terminadas"))
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> loadOrders(STATUS_PENDING)
                    1 -> loadOrders(STATUS_WORKING)
                    2 -> loadOrders(STATUS_FINISHED)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupFAB() {
        // El FAB se mostrará/ocultará según el rol
        binding.fabCreateOrder.setOnClickListener {
            findNavController().navigate(R.id.action_technicianHome_to_createOrder)
        }
    }

    private fun observeViewModel() {
        // Observar rol del usuario
        viewModel.userRole.observe(viewLifecycleOwner) { role ->
            currentUserRole = role
            updateUIForRole(role)
        }

        // Observar ID del usuario
        viewModel.currentUserId.observe(viewLifecycleOwner) { userId ->
            currentUserId = userId
        }

        // Observar nombre del usuario
        viewModel.userName.observe(viewLifecycleOwner) { name ->
            binding.tvWelcome.text = "Hola, $name"
        }

        // Observar órdenes
        viewModel.orders.observe(viewLifecycleOwner) { orders ->
            if (orders.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.rvOrders.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.rvOrders.visibility = View.VISIBLE
                adapter.submitList(orders)
            }
        }

        // Observar estado de carga
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observar mensajes de error
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun updateUIForRole(role: String?) {
        when (role) {
            ROLE_ADMIN -> {
                // Admin puede crear órdenes
                binding.fabCreateOrder.visibility = View.VISIBLE

                // Admin ve quién está asignado a cada orden
                adapter.showAssignedTo = true

                // Cambiar título
                binding.tvTitle.text = "Panel de Administración"

                // Actualizar mensaje de estado vacío
                updateEmptyStateMessage(isAdmin = true)
            }
            ROLE_TECHNICIAN -> {
                // Técnico NO puede crear órdenes
                binding.fabCreateOrder.visibility = View.GONE

                // Técnico NO ve asignaciones de otros
                adapter.showAssignedTo = false

                // Cambiar título
                binding.tvTitle.text = "Mis Órdenes de Reparación"

                updateEmptyStateMessage(isAdmin = false)
            }
        }
    }

    private fun loadOrders(status: String) {
        val isAdmin = currentUserRole == ROLE_ADMIN
        val userId = currentUserId ?: return

        when {
            // Admin ve TODAS las órdenes de cualquier estado
            isAdmin -> {
                viewModel.loadOrdersByStatus(status)
            }
            // Técnico ve:
            // - Pendientes: todas (para poder tomarlas)
            // - En Reparación: solo las suyas
            // - Terminadas: solo las suyas
            status == STATUS_PENDING -> {
                viewModel.loadOrdersByStatus(STATUS_PENDING)
            }
            else -> {
                viewModel.loadMyOrders(userId, status)
            }
        }
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
                0 -> "No hay órdenes disponibles para tomar"
                1 -> "No tienes órdenes en reparación\nToma una orden de la pestaña Pendientes"
                2 -> "No has terminado ninguna orden todavía"
                else -> "No hay órdenes"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}