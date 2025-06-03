package com.gom.draw.ui.home.adapter

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.RequestManager
import com.gom.draw.databinding.CategoryTabLayoutBinding
import com.gom.draw.model.Category
import com.gom.draw.model.Image

class ImageViewPagerAdapter(
    val requestManager: RequestManager,
    val onFavoriteClick: (Image) -> Unit,
    val onItemClick: (Bitmap, Image) -> Unit,
    val images: List<Image>,
    val categories: List<Category>,
) : RecyclerView.Adapter<ImageViewPagerAdapter.ViewHolder>(){
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = CategoryTabLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val category = categories[position]

        val items = if(category.id == 1) images else images.filter {
            category.id == it.categoryId
        }

        holder.bind(
            images = items,
            imageAdapter = ImageAdapter(
                onItemClick = onItemClick,
                onFavoriteClick = onFavoriteClick,
                glide = requestManager
            )
        )
    }

    override fun getItemCount(): Int = categories.size

    inner class ViewHolder(val binding: CategoryTabLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(images: List<Image>, imageAdapter : ImageAdapter) {
            binding.rvImages.apply {
                layoutManager =
                    StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
                adapter = imageAdapter
                addItemDecoration(ImageDecoration(binding.root.context))
            }
            imageAdapter.submitList(images)
        }
    }
}