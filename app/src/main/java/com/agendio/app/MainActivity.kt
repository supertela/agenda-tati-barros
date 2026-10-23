package com.agendio.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.agendio.app.data.DbHelper
import com.agendio.app.notify.ReminderScheduler
import com.agendio.app.ui.AgendaFragment
import com.agendio.app.ui.ClientsFragment
import com.agendio.app.ui.FinanceFragment
import com.agendio.app.ui.HomeFragment
import com.agendio.app.ui.ServicesFragment
import com.agendio.app.ui.showAboutDialog
import com.agendio.app.util.Formats
import com.agendio.app.util.ImportManager
import com.agendio.app.util.ImportResult
import com.agendio.app.util.SocialLinks
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar

    private val prefs by lazy {
        getSharedPreferences("agendio_prefs", MODE_PRIVATE)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val contactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showDddDialog()
        else showPermissionDenied(Manifest.permission.READ_CONTACTS, "contatos")
    }

    private val calendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) confirmCalendarImport()
        else showPermissionDenied(Manifest.permission.READ_CALENDAR, "agenda")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        bottomNav = findViewById(R.id.bottomNav)

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
            openFragment(HomeFragment(), getString(R.string.nav_home))
        } else {
            if (bottomNav.selectedItemId == 0) {
                bottomNav.selectedItemId = R.id.nav_home
            }
            updateToolbarTitle(bottomNav.selectedItemId)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> openFragment(HomeFragment(), getString(R.string.nav_home))
                R.id.nav_agenda -> openFragment(AgendaFragment(), getString(R.string.nav_agenda))
                R.id.nav_clients -> openFragment(ClientsFragment(), getString(R.string.nav_clients))
                R.id.nav_services -> openFragment(ServicesFragment(), getString(R.string.nav_services))
                R.id.nav_finance -> openFragment(FinanceFragment(), getString(R.string.nav_finance))
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        ReminderScheduler.ensureChannel(this)
        requestNotificationPermission()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import_contacts -> {
                importContacts()
                true
            }

            R.id.action_import_calendar -> {
                importCalendar()
                true
            }

            R.id.action_instagram -> {
                SocialLinks.openInstagram(this)
                true
            }

            R.id.action_about -> {
                showAboutDialog(this)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::bottomNav.isInitialized) updateToolbarTitle(bottomNav.selectedItemId)
    }

    fun openTab(itemId: Int) {
        if (!::bottomNav.isInitialized) return
        if (bottomNav.selectedItemId == itemId) {
            refreshCurrentTab()
        } else {
            bottomNav.selectedItemId = itemId
        }
    }

    fun importContacts() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showDddDialog()
        } else {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun importCalendar() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            confirmCalendarImport()
        } else {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    private fun showDddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_ddd, null)
        val etDdd = view.findViewById<TextInputEditText>(R.id.etDefaultDdd)
        etDdd.setText(prefs.getString("default_ddd", "").orEmpty())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_contacts)
            .setView(view)
            .setPositiveButton("Importar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ddd = etDdd.text?.toString()?.trim().orEmpty()
                if (ddd.isNotEmpty() && ddd.length != 2) {
                    etDdd.error = "Informe 2 dígitos"
                    return@setOnClickListener
                }
                prefs.edit().putString("default_ddd", ddd).apply()
                dialog.dismiss()
                runContactsImport(ddd)
            }
        }
        dialog.show()
    }

    private fun runContactsImport(ddd: String) {
        val progress = showProgress(
            "Importando contatos",
            "Lendo os contatos do celular. Isso pode levar alguns segundos."
        )
        Thread {
            val db = DbHelper(applicationContext)
            val result = try {
                ImportManager.importContacts(applicationContext, db, ddd)
            } finally {
                db.close()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                showImportResult("Contatos", result)
                refreshCurrentTab()
            }
        }.start()
    }

    private fun confirmCalendarImport() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_calendar)
            .setMessage(
                "Serão importados os eventos dos últimos 30 dias e dos próximos 90 dias.\n\n" +
                    "Cada evento vira um atendimento, usando o título como nome do cliente. " +
                    "Eventos de aniversário são ignorados e os já importados não são duplicados. " +
                    "Você pode editar cliente, serviço e valor depois."
            )
            .setPositiveButton("Importar") { _, _ -> runCalendarImport() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun runCalendarImport() {
        val progress = showProgress(
            "Importando agenda",
            "Lendo os eventos do calendário do celular."
        )
        val today = Formats.dayStart(System.currentTimeMillis())
        val start = Formats.addDays(today, -30)
        val end = Formats.addDays(today, 90)
        Thread {
            val db = DbHelper(applicationContext)
            val result = try {
                ImportManager.importCalendar(applicationContext, db, start, end)
            } finally {
                db.close()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                showImportResult("Agenda", result)
                refreshCurrentTab()
            }
        }.start()
    }

    private fun showImportResult(label: String, result: ImportResult) {
        val message = if (result.error != null) {
            result.error
        } else {
            val parts = mutableListOf<String>()
            parts.add("${result.inserted} novo(s) item(ns) importado(s).")
            if (result.updated > 0) {
                parts.add("${result.updated} foto(s) de contato atualizada(s).")
            }
            if (result.skipped > 0) {
                parts.add("${result.skipped} item(ns) ignorado(s) por já existirem ou estarem incompletos.")
            }
            if (result.inserted == 0 && result.skipped == 0 && result.updated == 0) {
                parts.add("Nenhum item foi encontrado no período.")
            }
            parts.joinToString("\n\n")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (result.error == null) "Importação concluída" else "Não foi possível importar")
            .setMessage("$label\n\n$message")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPermissionDenied(permission: String, label: String) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("Permissão necessária")
            .setMessage(getString(R.string.import_permission_denied) + "\n\nPermissão: $label.")
            .setPositiveButton("Tentar novamente") { _, _ ->
                when (permission) {
                    Manifest.permission.READ_CONTACTS ->
                        contactsPermission.launch(permission)

                    Manifest.permission.READ_CALENDAR ->
                        calendarPermission.launch(permission)
                }
            }
            .setNegativeButton("Agora não", null)

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            builder.setNeutralButton("Configurações") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        builder.show()
    }

    private fun showProgress(title: String, message: String): AlertDialog {
        return MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    private fun refreshCurrentTab() {
        if (!::bottomNav.isInitialized) return
        when (bottomNav.selectedItemId) {
            R.id.nav_home -> openFragment(HomeFragment(), getString(R.string.nav_home))
            R.id.nav_agenda -> openFragment(AgendaFragment(), getString(R.string.nav_agenda))
            R.id.nav_clients -> openFragment(ClientsFragment(), getString(R.string.nav_clients))
            R.id.nav_services -> openFragment(ServicesFragment(), getString(R.string.nav_services))
            R.id.nav_finance -> openFragment(FinanceFragment(), getString(R.string.nav_finance))
        }
    }

    private fun openFragment(fragment: Fragment, title: String) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
        updateToolbarTitleFor(title)
    }

    private fun updateToolbarTitle(itemId: Int) {
        updateToolbarTitleFor(titleFor(itemId))
    }

    private fun updateToolbarTitleFor(title: String) {
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.subtitle = title
    }

    private fun titleFor(itemId: Int): String {
        return when (itemId) {
            R.id.nav_home -> getString(R.string.nav_home)
            R.id.nav_clients -> getString(R.string.nav_clients)
            R.id.nav_services -> getString(R.string.nav_services)
            R.id.nav_finance -> getString(R.string.nav_finance)
            else -> getString(R.string.nav_agenda)
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
