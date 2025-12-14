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

    // Compartido con la Activity para que otros fragments (Create/Detail) compartan estado
    private val viewModel: TechViewModel by activityViewModels()
    private lateinit var adapter: RepairOrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sin menú: no llamar setHasOptionsMenu
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

        // Seleccionar tab Inicial (Pendientes)
        binding.tabLayout.getTabAt(0)?.select()
    }

    private fun setupRecyclerView() {
        adapter = RepairOrderAdapter(
            onItemClick = { order ->
                // Navegar al detalle (usa SafeArgs si está configurado)
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

    private fun setupFab() {
        binding.fabCreateOrder.setOnClickListener {
            // Navegar a crear orden
            findNavController().navigate(R.id.action_technicianHome_to_createOrder)
        }
    }

    private fun observeViewModel() {
        viewModel.userRole.observe(viewLifecycleOwner) { role ->
            updateUIForRole(role)
        }

        viewModel.currentUserId.observe(viewLifecycleOwner) { /* solo almacenamos si hace falta */ }

        viewModel.userName.observe(viewLifecycleOwner) { name ->
            binding.tvWelcome.text = "Hola, ${name ?: "usuario"}"
        }

        viewModel.orders.observe(viewLifecycleOwner) { orders ->
            if (orders.isNullOrEmpty()) {
                binding.emptyContainer.isVisible = true
                binding.rvOrders.isVisible = false
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
                adapter.notifyDataSetChanged()
                binding.tvTitle.text = "Panel de Administración"
                updateEmptyStateMessage(true)
            }
            ROLE_TECHNICIAN -> {
                binding.fabCreateOrder.isVisible = false
                adapter.showAssignedTo = false
                adapter.notifyDataSetChanged()
                binding.tvTitle.text = "Mis Órdenes de Reparación"
                updateEmptyStateMessage(false)
            }
            else -> {
                binding.fabCreateOrder.isVisible = false
                adapter.showAssignedTo = false
                adapter.notifyDataSetChanged()
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

    private fun loadOrders(status: String) {
        val isAdmin = viewModel.isAdmin()
        val userId = viewModel.currentUserId.value

        when {
            isAdmin -> viewModel.loadOrdersByStatus(status)
            status == STATUS_PENDING -> viewModel.loadOrdersByStatus(STATUS_PENDING)
            userId != null -> viewModel.loadMyOrders(userId, status)
            else -> {
                // No hay userId aún; intentar recargar perfil
                viewModel.loadUserProfile()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}