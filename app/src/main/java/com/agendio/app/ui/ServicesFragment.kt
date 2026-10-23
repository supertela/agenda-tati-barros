package com.agendio.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agendio.app.R
import com.agendio.app.data.DbHelper
import com.agendio.app.util.Formats
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class ServicesFragment : Fragment(R.layout.fragment_simple_list) {

    private lateinit var db: DbHelper
    private lateinit var adapter: ServiceAdapter
    private lateinit var tvHeader: TextView
    private lateinit var tvEmpty: TextView
    private var query: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DbHelper(requireContext())
        tvHeader = view.findViewById(R.id.tvHeader)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        tvEmpty.text = "Nenhum serviço cadastrado.\n\nToque no + para começar."

        val search = view.findViewById<TextInputEditText>(R.id.etSearch)
        search.hint = "Buscar serviço"
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                load()
            }
        })

        view.findViewById<MaterialButton>(R.id.btnHeaderAction).visibility = View.GONE

        adapter = ServiceAdapter { service ->
            showServiceDialog(requireContext(), db, service) { load() }
        }
        view.findViewById<RecyclerView>(R.id.rvList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ServicesFragment.adapter
        }
        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showServiceDialog(requireContext(), db, null) { load() }
        }

        load()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) load()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::db.isInitialized) db.close()
    }

    private fun load() {
        val all = db.getServices()
        val normalized = Formats.normalize(query)
        val services = if (normalized.isBlank()) {
            all
        } else {
            all.filter { Formats.normalize(it.name).contains(normalized) }
        }
        adapter.submit(services)
        tvHeader.text = if (normalized.isBlank()) {
            "SERVIÇOS • ${all.size}"
        } else {
            "SERVIÇOS • ${services.size} de ${all.size}"
        }
        tvEmpty.visibility = if (services.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = if (normalized.isBlank()) {
            "Nenhum serviço cadastrado.\n\nToque no + para começar."
        } else {
            "Nenhum serviço encontrado para “$query”."
        }
    }
}
