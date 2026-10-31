package com.example.mobilespectrometer.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.mobilespectrometer.R
import java.io.File


class GraphAdapter(private val graphFiles: List<File>) :
    RecyclerView.Adapter<GraphAdapter.GraphViewHolder>() {

    class GraphViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.graphImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GraphViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.graph_image_item, parent, false)
        return GraphViewHolder(view)
    }

    override fun onBindViewHolder(holder: GraphViewHolder, position: Int) {
        val graphFile = graphFiles[position]
        holder.imageView.setImageBitmap(BitmapFactory.decodeFile(graphFile.absolutePath))
    }

    override fun getItemCount() = graphFiles.size
}

