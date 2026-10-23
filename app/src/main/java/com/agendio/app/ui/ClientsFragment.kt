package com.agendio.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agendio.app.MainActivity
import com.agendio.app.R
import com.agendio.app.data.Client
import com.agendio.app.data.DbHelper
import com.agendio.app.util.Formats
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class ClientsFragment : Fragment(R.layout.fragment_simple_list) {

    private lateinit var db: DbHelper
    private lateinit var adapter: ClientAdapter
    private lateinit var tvHeader: TextView
    private lateinit var tvEmpty: TextView
    private var query: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DbHelper(requireContext())
        tvHeader = view.findViewById(R.id.tvHeader)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        tvEmpty.text = "Nenhum cliente cadastrado.\n\nToque no + ou importe os contatos do celular."

        val search = view.findViewById<TextInputEditText>(R.id.etSearch)
        search.hint = "Buscar cliente por nome, telefone ou e-mail"
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                load()
            }
        })

        view.findViewById<MaterialButton>(R.id.btnHeaderAction).apply {
            visibility = View.VISIBLE
            text = "Importar contatos"
            setOnClickListener { (activity as? MainActivity)?.importContacts() }
        }

        adapter = ClientAdapter { client ->
            showClientDialog(requireContext(), db, client) { load() }
        }
        view.findViewById<RecyclerView>(R.id.rvList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ClientsFragment.adapter
        }
        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showClientDialog(requireContext(), db, null) { load() }
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
        val all = db.getClients()
        val normalized = Formats.normalize(query)
        val clients = if (normalized.isBlank()) {
            all
        } else {
            all.filter { client ->
                Formats.normalize(client.name).contains(normalized) ||
                    client.phone.contains(normalized) ||
                    Formats.normalize(client.email).contains(normalized)
            }
        }
        adapter.submit(clients)
        tvHeader.text = if (normalized.isBlank()) {
            "CLIENTES • ${all.size}"
        } else {
            "CLIENTES • ${clients.size} de ${all.size}"
        }
        tvEmpty.visibility = if (clients.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = if (normalized.isBlank()) {
            "Nenhum cliente cadastrado.\n\nToque no + ou importe os contatos do celular."
        } else {
            "Nenhum cliente encontrado para “$query”."
        }
    }
}
