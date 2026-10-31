package com.example.mobilespectrometer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.mobilespectrometer.R
import com.example.mobilespectrometer.adapters.GraphAdapter
import com.example.mobilespectrometer.models.GraphData
import java.io.File


class HistoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var graphAdapter: GraphAdapter
    private val graphList = mutableListOf<GraphData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val graphFiles = loadSavedGraphImages()
        recyclerView.adapter = GraphAdapter(graphFiles)
    }

    private fun loadSavedGraphImages(): List<File> {
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SavedGraphs")
        return if (directory.exists()) {
            directory.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }



}


