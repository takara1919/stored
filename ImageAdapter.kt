package com.gom.draw.ui.home.adapter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.gom.draw.R
import com.gom.draw.databinding.ImageItemLayoutBinding
import com.gom.draw.model.Image
import com.gom.draw.util.logData

class ImageAdapter(
    private val glide: RequestManager,
    val onFavoriteClick: (Image) -> Unit,
    val onItemClick: ( Bitmap, Image) -> Unit,
) : ListAdapter<Image, ImageAdapter.ViewHolder>(ImageDiffCallback) {
    inner class ViewHolder(
        private val binding: ImageItemLayoutBinding,
        private val glide: RequestManager,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Image) {
            logData(item)
            glide.load(item.thumb).encodeQuality(35)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .dontAnimate()
                .placeholder(R.drawable.bg_blur)
                .into(binding.img)

            binding.btn.setOnClickListener {
                onFavoriteClick(item)
                item.isFavorite = !item.isFavorite

                if (item.isFavorite ) {
                    binding.icon.setImageResource(R.drawable.filled_heart)
                } else {
                    binding.icon.setImageResource(R.drawable.empty_heart)
                }
            }

            binding.img.setOnClickListener {
                val bitmap= createBitmap(it.width, it.height)
                val canvas = Canvas(bitmap)
                it.draw(canvas)
                onItemClick(bitmap, item)
            }

            if(item.isFavorite){
                binding.icon.setImageResource(R.drawable.filled_heart)
            } else {
                binding.icon.setImageResource(R.drawable.empty_heart)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ImageItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, glide)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(currentList[position])
    }
}