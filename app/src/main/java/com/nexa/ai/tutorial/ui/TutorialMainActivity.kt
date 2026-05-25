package com.nexa.ai.tutorial.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexa.ai.R

class TutorialMainActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial_main)

        // Inicializar ViewModel
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        // Setup RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(emptyList()) // Empezamos vacío
        recyclerView.adapter = adapter

        // Observar los mensajes (LiveData)
        viewModel.messages.observe(this) { newMessages ->
            // Actualizar el adaptador cuando la lista cambie
            adapter = MessageAdapter(newMessages)
            recyclerView.adapter = adapter
            if (newMessages.isNotEmpty()) {
                recyclerView.scrollToPosition(newMessages.size - 1) // Bajar al final
            }
        }

        // Observar estado de carga
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Botón de enviar
        val btnSend = findViewById<Button>(R.id.btnSend)
        val etMessage = findViewById<EditText>(R.id.etMessage)

        btnSend.setOnClickListener {
            val text = etMessage.text.toString()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                etMessage.text.clear() // Limpiar campo
            }
        }
    }
}
