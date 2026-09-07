package com.example.threesixtycamera

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class GalleryAdapter(
    private val items: List<Uri>,
    private val onClick: (Uri) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val imageView: ImageView =
            itemView.findViewById(R.id.ivThumb)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_gallery_image,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val uri = items[position]

        holder.imageView.setImageURI(uri)

        holder.itemView.setOnClickListener {
            onClick(uri)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}