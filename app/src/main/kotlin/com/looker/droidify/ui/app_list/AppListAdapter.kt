package com.looker.droidify.ui.app_list

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.looker.core_common.nullIfEmpty
import com.looker.core_model.ProductItem
import com.looker.core_model.Repository
import com.looker.droidify.R
import com.looker.droidify.database.Database
import com.looker.droidify.utility.Utils
import com.looker.droidify.utility.extension.icon
import com.looker.droidify.utility.extension.resources.*
import com.looker.droidify.widget.CursorRecyclerAdapter

class AppListAdapter(private val onClick: (ProductItem) -> Unit) :
	CursorRecyclerAdapter<AppListAdapter.ViewType, RecyclerView.ViewHolder>() {

	enum class ViewType { PRODUCT, LOADING, EMPTY }

	private class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		val name = itemView.findViewById<MaterialTextView>(R.id.name)!!
		val status = itemView.findViewById<MaterialTextView>(R.id.status)!!
		val summary = itemView.findViewById<MaterialTextView>(R.id.summary)!!
		val icon = itemView.findViewById<ShapeableImageView>(R.id.icon)!!

		val progressIcon: Drawable
		val defaultIcon: Drawable

		init {
			val (progressIcon, defaultIcon) = Utils.getDefaultApplicationIcons(icon.context)
			this.progressIcon = progressIcon
			this.defaultIcon = defaultIcon
		}
	}

	private class LoadingViewHolder(context: Context) :
		RecyclerView.ViewHolder(FrameLayout(context)) {
		init {
			itemView as FrameLayout
			val progressBar = CircularProgressIndicator(itemView.context)
			itemView.addView(progressBar)
			itemView.layoutParams = RecyclerView.LayoutParams(
				RecyclerView.LayoutParams.MATCH_PARENT,
				RecyclerView.LayoutParams.MATCH_PARENT
			)
		}
	}

	private class EmptyViewHolder(context: Context) :
		RecyclerView.ViewHolder(MaterialTextView(context)) {
		val text: MaterialTextView
			get() = itemView as MaterialTextView

		init {
			itemView as MaterialTextView
			itemView.gravity = Gravity.CENTER
			itemView.resources.sizeScaled(20).let { itemView.setPadding(it, it, it, it) }
			itemView.typeface = TypefaceExtra.light
			itemView.setTextColor(context.getColorFromAttr(android.R.attr.colorPrimary))
			itemView.setTextSizeScaled(20)
			itemView.layoutParams = RecyclerView.LayoutParams(
				RecyclerView.LayoutParams.MATCH_PARENT,
				RecyclerView.LayoutParams.MATCH_PARENT
			)
		}
	}

	var repositories: Map<Long, Repository> = emptyMap()
		set(value) {
			field = value
			notifyDataSetChanged()
		}

	var emptyText: String = ""
		set(value) {
			if (field != value) {
				field = value
				if (isEmpty) {
					notifyDataSetChanged()
				}
			}
		}

	override val viewTypeClass: Class<ViewType>
		get() = ViewType::class.java

	private val isEmpty: Boolean
		get() = super.getItemCount() == 0

	override fun getItemCount(): Int = if (isEmpty) 1 else super.getItemCount()
	override fun getItemId(position: Int): Long = if (isEmpty) -1 else super.getItemId(position)

	override fun getItemEnumViewType(position: Int): ViewType {
		return when {
			!isEmpty -> ViewType.PRODUCT
			cursor == null -> ViewType.LOADING
			else -> ViewType.EMPTY
		}
	}

	private fun getProductItem(position: Int): ProductItem {
		return Database.ProductAdapter.transformItem(moveTo(position))
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: ViewType,
	): RecyclerView.ViewHolder {
		return when (viewType) {
			ViewType.PRODUCT -> ProductViewHolder(parent.inflate(R.layout.product_item)).apply {
				itemView.setOnClickListener { onClick(getProductItem(absoluteAdapterPosition)) }
			}
			ViewType.LOADING -> LoadingViewHolder(parent.context)
			ViewType.EMPTY -> EmptyViewHolder(parent.context)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (getItemEnumViewType(position)) {
			ViewType.PRODUCT -> {
				holder as ProductViewHolder
				val productItem = getProductItem(if (position > -1) position else 0)
				holder.name.text = productItem.name
				holder.summary.text =
					if (productItem.name == productItem.summary) "" else productItem.summary
				holder.summary.visibility =
					if (holder.summary.text.isNotEmpty()) View.VISIBLE else View.GONE
				val repository: Repository? = repositories[productItem.repositoryId]
				holder.icon.load(
					repository?.let {
						productItem.packageName.icon(
							view = holder.icon,
							icon = productItem.icon,
							metadataIcon = productItem.metadataIcon,
							repository = it
						)
					}
				) {
					placeholder(holder.progressIcon)
					error(holder.defaultIcon)
				}
				holder.status.apply {
					if (productItem.canUpdate) {
						text = productItem.version
						if (background == null) {
							background =
								ResourcesCompat.getDrawable(
									holder.itemView.resources,
									R.drawable.background_border,
									context.theme
								)
							resources.sizeScaled(6).let { setPadding(it, it, it, it) }
							backgroundTintList =
								context.getColorFromAttr(R.attr.colorSecondaryContainer)
							setTextColor(context.getColorFromAttr(R.attr.colorSecondary))
						}
					} else {
						text = productItem.installedVersion.nullIfEmpty() ?: productItem.version
						if (background != null) {
							setPadding(0, 0, 0, 0)
							setTextColor(holder.status.context.getColorFromAttr(android.R.attr.colorControlNormal))
							background = null
						}
					}
				}
				val enabled = productItem.compatible || productItem.installedVersion.isNotEmpty()
				sequenceOf(holder.name, holder.status, holder.summary).forEach {
					it.isEnabled = enabled
				}
			}
			ViewType.LOADING -> {
				// Do nothing
			}
			ViewType.EMPTY -> {
				holder as EmptyViewHolder
				holder.text.text = emptyText
			}
		}::class
	}
}