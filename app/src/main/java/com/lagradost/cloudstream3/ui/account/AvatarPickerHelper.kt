package com.lagradost.cloudstream3.ui.account

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.databinding.DialogAvatarPickerBinding
import com.lagradost.cloudstream3.databinding.ItemAvatarCategoryTabBinding
import com.lagradost.cloudstream3.databinding.ItemAvatarPickerBinding
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

data class AvatarItem(
    @JsonProperty("name") val name: String = "",
    @JsonProperty("show") val show: String = "",
    @JsonProperty("season") val season: Int = 1,
    @JsonProperty("url") val url: String = ""
)

data class AvatarCategory(
    @JsonProperty("category") val category: String = "",
    @JsonProperty("count") val count: Int = 0,
    @JsonProperty("avatars") val avatars: List<AvatarItem> = emptyList()
)

data class AvatarCatalog(
    @JsonProperty("categories") val categories: List<AvatarCategory> = emptyList(),
    @JsonProperty("total") val total: Int = 0
)

object AvatarPickerHelper {
    private var cachedCatalog: AvatarCatalog? = null

    fun getCatalog(context: Context): AvatarCatalog {
        cachedCatalog?.let { return it }
        return try {
            val json = context.assets.open("avatars.json").bufferedReader().use { it.readText() }
            val parsed = parseJson<AvatarCatalog>(json)
            cachedCatalog = parsed
            parsed
        } catch (e: Exception) {
            e.printStackTrace()
            AvatarCatalog()
        }
    }

    fun showAvatarPicker(
        context: Context,
        onAvatarSelected: (selectedUrl: String) -> Unit
    ) {
        val catalog = getCatalog(context)
        val allAvatars = catalog.categories.flatMap { it.avatars }
        if (allAvatars.isEmpty()) return

        // Build a curated Popular category from popular shows
        val popularCategoryNames = listOf(
            "Stranger Things", "Squid Game", "Wednesday", "Money Heist",
            "Cobra Kai", "The Witcher", "One Piece", "Dark", "Lucifer",
            "Alice in Borderland", "Lost in Space", "The Classics"
        )
        val popularAvatars = mutableListOf<AvatarItem>()
        for (catName in popularCategoryNames) {
            val matching = catalog.categories.firstOrNull {
                it.category.contains(catName, ignoreCase = true)
            }
            if (matching != null) {
                popularAvatars.addAll(matching.avatars.take(3))
            }
        }
        if (popularAvatars.isEmpty()) {
            popularAvatars.addAll(allAvatars.take(24))
        }

        val binding = DialogAvatarPickerBinding.inflate(LayoutInflater.from(context))

        // Fullscreen Dialog to avoid any BottomSheet gesture stealing - provides silky smooth scrolling
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#121212")))
        }

        binding.pickerCloseButton.setOnClickListener {
            dialog.dismiss()
        }

        var selectedCategory: String = "Popular"
        var currentQuery: String = ""
        var currentAllPageSize = 40

        fun filterAvatars(): List<AvatarItem> {
            if (currentQuery.isNotBlank()) {
                val q = currentQuery.trim().lowercase()
                return allAvatars.filter {
                    it.name.lowercase().contains(q) || it.show.lowercase().contains(q)
                }
            }

            return when (selectedCategory) {
                "Popular" -> popularAvatars
                "All" -> allAvatars.take(currentAllPageSize)
                else -> catalog.categories.firstOrNull { it.category == selectedCategory }?.avatars ?: popularAvatars
            }
        }

        var displayedAvatars = filterAvatars()

        // Avatar Grid Adapter with thumbnail sizing & memory caching for instant render
        val gridAdapter = object : RecyclerView.Adapter<AvatarViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
                val itemBinding = ItemAvatarPickerBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                return AvatarViewHolder(itemBinding)
            }

            override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
                val item = displayedAvatars[position]
                holder.binding.avatarName.text = item.name

                // Load with downsampled thumbnail size (150x150) and aggressive caching
                holder.binding.avatarImage.loadImage(item.url) {
                    size(150, 150)
                    crossfade(100)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }

                val clickAction = {
                    onAvatarSelected(item.url)
                    dialog.dismiss()
                }
                holder.binding.avatarCard.setOnClickListener { clickAction() }
                holder.binding.avatarImage.setOnClickListener { clickAction() }
                holder.binding.root.setOnClickListener { clickAction() }
            }

            override fun getItemCount(): Int = displayedAvatars.size
        }

        val spanCount = if (context.resources.configuration.screenWidthDp >= 600) 5 else 4
        val gridLayoutManager = GridLayoutManager(context, spanCount)
        binding.avatarGridRecycler.apply {
            setHasFixedSize(true)
            isNestedScrollingEnabled = true
            layoutManager = gridLayoutManager
            adapter = gridAdapter
        }

        // Infinite pagination for "All" tab to prevent loading 621 network images all at once
        binding.avatarGridRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && selectedCategory == "All" && currentQuery.isBlank()) {
                    val visibleItemCount = gridLayoutManager.childCount
                    val totalItemCount = gridLayoutManager.itemCount
                    val pastVisibleItems = gridLayoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 8) {
                        if (currentAllPageSize < allAvatars.size) {
                            currentAllPageSize = (currentAllPageSize + 40).coerceAtMost(allAvatars.size)
                            displayedAvatars = allAvatars.take(currentAllPageSize)
                            gridAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        })

        // Category Tabs
        val categoryList = listOf("Popular", "All") + catalog.categories.map { it.category }
        val categoryAdapter = object : RecyclerView.Adapter<CategoryViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
                val tabBinding = ItemAvatarCategoryTabBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                return CategoryViewHolder(tabBinding)
            }

            override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
                val cat = categoryList[position]
                val isSelected = cat == selectedCategory
                holder.binding.categoryTabText.text = cat

                val bg = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 30f
                    if (isSelected) {
                        setColor(Color.parseColor("#E50914"))
                    } else {
                        setColor(Color.parseColor("#222222"))
                        setStroke(1, Color.parseColor("#444444"))
                    }
                }
                holder.binding.categoryTabText.background = bg
                holder.binding.categoryTabText.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#AAAAAA"))

                holder.binding.categoryTabText.setOnClickListener {
                    selectedCategory = cat
                    currentAllPageSize = 40
                    notifyDataSetChanged()
                    displayedAvatars = filterAvatars()
                    gridAdapter.notifyDataSetChanged()
                    binding.avatarGridRecycler.scrollToPosition(0)
                }
            }

            override fun getItemCount(): Int = categoryList.size
        }

        binding.categoryTabsRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.categoryTabsRecycler.adapter = categoryAdapter

        // Search Filter
        binding.pickerSearch.doOnTextChanged { text, _, _, _ ->
            currentQuery = text?.toString() ?: ""
            displayedAvatars = filterAvatars()
            gridAdapter.notifyDataSetChanged()
            binding.avatarGridRecycler.scrollToPosition(0)
        }

        dialog.show()
    }

    private class AvatarViewHolder(val binding: ItemAvatarPickerBinding) : RecyclerView.ViewHolder(binding.root)
    private class CategoryViewHolder(val binding: ItemAvatarCategoryTabBinding) : RecyclerView.ViewHolder(binding.root)
}
