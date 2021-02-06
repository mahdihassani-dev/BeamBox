package com.tans.tfiletransporter.ui.activity.filetransport

import android.graphics.Color
import androidx.recyclerview.widget.GridLayoutManager
import com.tans.rxutils.QueryMediaItem
import com.tans.rxutils.QueryMediaType
import com.tans.rxutils.getMedia
import com.tans.rxutils.switchThread
import com.tans.tadapter.recyclerviewutils.IgnoreGridLastRowVerticalDividerController
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ImageItemLayoutBinding
import com.tans.tfiletransporter.databinding.MyImagesFragmentLayoutBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.utils.dp2px

data class MyImagesState(
    val images: List<QueryMediaItem.Image> = emptyList(),
    val selectedImages: Set<QueryMediaItem.Image> = emptySet()
)

class MyImagesFragment : BaseFragment<MyImagesFragmentLayoutBinding, MyImagesState>(
    layoutId = R.layout.my_images_fragment_layout,
    default = MyImagesState()
) {
    override fun onInit() {
        refreshImages().switchThread().bindLife()
        binding.myImagesRv.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.myImagesRv.adapter = SimpleAdapterSpec<Pair<QueryMediaItem.Image, Boolean>, ImageItemLayoutBinding>(
            layoutId = R.layout.image_item_layout,
            bindData = { _, (image, select), lBinding -> lBinding.image = image; lBinding.select = select },
            dataUpdater = bindState().map { state -> state.images.map { it to state.selectedImages.contains(it) } }
        ).toAdapter()

        val horizontalDivider = MarginDividerItemDecoration.Companion.Builder()
            .divider(MarginDividerItemDecoration.Companion.ColorDivider(color = Color.TRANSPARENT, requireContext().dp2px(6)))
            .dividerDirection(MarginDividerItemDecoration.Companion.DividerDirection.Horizontal)
            .build()

        val verticalDivider = MarginDividerItemDecoration.Companion.Builder()
            .divider(MarginDividerItemDecoration.Companion.ColorDivider(color = Color.TRANSPARENT, requireContext().dp2px(6)))
            .dividerDirection(MarginDividerItemDecoration.Companion.DividerDirection.Vertical)
            .dividerController(IgnoreGridLastRowVerticalDividerController(rowSize = 2))
            .build()

        binding.myImagesRv.addItemDecoration(horizontalDivider)
        binding.myImagesRv.addItemDecoration(verticalDivider)
    }

    private fun refreshImages() = getMedia(
        context = requireContext(),
        queryMediaType = QueryMediaType.Image)
        .flatMap { media ->
            updateState {
                val images = media.filterIsInstance<QueryMediaItem.Image>()
                MyImagesState(images = images)
            }
        }

    companion object {
        const val FRAGMENT_TAG = "my_images_fragment_tag"
    }
}