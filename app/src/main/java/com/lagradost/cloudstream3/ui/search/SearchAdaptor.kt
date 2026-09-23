package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.SearchResultGridBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import kotlin.math.roundToInt

/** Click */
const val SEARCH_ACTION_LOAD = 0

/** Long press */
const val SEARCH_ACTION_SHOW_METADATA = 1
const val SEARCH_ACTION_PLAY_FILE = 2
const val SEARCH_ACTION_FOCUSED = 4

class SearchClickCallback(
    val action: Int,
    val view: View,
    val position: Int,
    val card: SearchResponse
)

class SearchAdapter(
    private val resView: AutofitRecyclerView,
    private val isHorizontal:Boolean = false,
    private val clickCallback: (SearchClickCallback) -> Unit,
) : NoStateAdapter<SearchResponse>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    if (a.id != null || b.id != null) {
        a.id == b.id
    } else {
        a.name == b.name
    }
})) {
    companion object {
        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 10) }
    }

    var hasNext: Boolean = false

    private val coverRatio = if(isHorizontal) 1.8 else 0.68

    private val coverHeight: Int get() = (resView.itemWidth / coverRatio).roundToInt()

    // Track the top match item so header can bind it
    private var topMatchItem: SearchResponse? = null

    /** Call this to update which item is the "Top Match" header */
    fun setTopMatch(item: SearchResponse?) {
        val hadMatch = topMatchItem != null
        topMatchItem = item
        val hasMatch = topMatchItem != null
        if (hadMatch != hasMatch) {
            // header count changed
            notifyDataSetChanged()
        } else if (hasMatch) {
            notifyItemChanged(0) // rebind header
        }
    }

    // Expose headers so the top match card appears as a header row
    override val headers: Int get() = if (topMatchItem != null) 1 else 0

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)

        val layout =
            if (parent.context.isBottomLayout()) SearchResultGridExpandedBinding.inflate(
                inflater,
                parent,
                false
            ) else SearchResultGridBinding.inflate(
                inflater,
                parent,
                false
            )
        return ViewHolderState(layout)
    }

    override fun onCreateHeader(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(
            com.lagradost.cloudstream3.R.layout.search_result_top_match,
            parent,
            false
        )
        return object : ViewHolderState<Any>(
            object : androidx.viewbinding.ViewBinding {
                override fun getRoot() = view
            }
        ) {}
    }

    override fun onBindHeader(holder: ViewHolderState<Any>) {
        val item = topMatchItem ?: return
        val root = holder.itemView
        val imageView = root.findViewById<ImageView>(com.lagradost.cloudstream3.R.id.top_match_image)
        val titleView = root.findViewById<TextView>(com.lagradost.cloudstream3.R.id.top_match_title)
        val typeView = root.findViewById<TextView>(com.lagradost.cloudstream3.R.id.top_match_type)
        val playButton = root.findViewById<View>(com.lagradost.cloudstream3.R.id.top_match_play)

        titleView?.text = item.name
        val typeStr = when (item.type) {
            com.lagradost.cloudstream3.TvType.TvSeries -> "TV Show"
            com.lagradost.cloudstream3.TvType.Movie -> "Movie"
            com.lagradost.cloudstream3.TvType.Anime -> "Anime"
            com.lagradost.cloudstream3.TvType.AnimeMovie -> "Anime Movie"
            else -> item.type?.name ?: ""
        }
        typeView?.text = typeStr
        imageView?.loadImage(item.posterUrl)

        val card = root.findViewById<View>(com.lagradost.cloudstream3.R.id.top_match_card) ?: root
        playButton?.setOnClickListener {
            clickCallback(SearchClickCallback(SEARCH_ACTION_LOAD, it, -1, item))
        }
        card.setOnClickListener {
            clickCallback(SearchClickCallback(SEARCH_ACTION_LOAD, it, -1, item))
        }
        card.setOnLongClickListener {
            clickCallback(SearchClickCallback(SEARCH_ACTION_SHOW_METADATA, it, -1, item))
            true
        }
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        clearImage(
            when (val binding = holder.view) {
                is SearchResultGridExpandedBinding -> binding.imageView
                is SearchResultGridBinding -> binding.imageView
                else -> null
            }
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SearchResponse, position: Int) {
        val imageView = when (val binding = holder.view) {
            is SearchResultGridExpandedBinding -> binding.imageView
            is SearchResultGridBinding -> binding.imageView
            else -> null
        }

        if (imageView != null) {
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                coverHeight
            )
            if (imageView.layoutParams.width != params.width || imageView.layoutParams.height != params.height) {
                imageView.layoutParams = params
            }
        }
        SearchResultBuilder.bind(clickCallback, item, position, holder.view.root)
    }
}