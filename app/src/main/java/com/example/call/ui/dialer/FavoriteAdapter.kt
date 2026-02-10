package com.example.call.ui.dialer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.call.databinding.ItemFavoriteBinding
import com.example.call.util.FavoritesStore

class FavoriteAdapter(
    private val onCallClick: (FavoritesStore.Favorite) -> Unit,
    private val onRemoveClick: (FavoritesStore.Favorite) -> Unit
) : ListAdapter<FavoritesStore.Favorite, FavoriteAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onCallClick, onRemoveClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemFavoriteBinding,
        private val onCallClick: (FavoritesStore.Favorite) -> Unit,
        private val onRemoveClick: (FavoritesStore.Favorite) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FavoritesStore.Favorite) {
            binding.favoriteName.text = if (item.name.isBlank()) item.number else item.name
            binding.favoriteNumber.text = item.number
            binding.root.setOnClickListener { onCallClick(item) }
            binding.root.setOnLongClickListener {
                onRemoveClick(item)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FavoritesStore.Favorite>() {
            override fun areItemsTheSame(
                oldItem: FavoritesStore.Favorite,
                newItem: FavoritesStore.Favorite
            ): Boolean = oldItem.number == newItem.number

            override fun areContentsTheSame(
                oldItem: FavoritesStore.Favorite,
                newItem: FavoritesStore.Favorite
            ): Boolean = oldItem == newItem
        }
    }
}
