package com.tans.beambox.ui.filetransport

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tans.beambox.R
import com.tans.beambox.databinding.MessageFragmentBinding
import com.tans.beambox.databinding.MessageItemLayoutBinding
import com.tans.beambox.logs.AndroidLog
import com.tans.beambox.transferproto.fileexplore.FileExplore
import com.tans.beambox.transferproto.fileexplore.requestMsgSuspend
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.DataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageFragment : BaseCoroutineStateFragment<Unit>(
    Unit
) {
    override val layoutId: Int = R.layout.message_fragment

    private val inputMethodManager: InputMethodManager by lazy {
        requireActivity().getSystemService<InputMethodManager>()!!
    }

    private val fileExplore: FileExplore by lazy {
        (requireActivity() as FileTransportActivity).fileExplore
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {  }

    private val messageDataSource: DataSourceImpl<FileTransportActivity.Companion.Message> by lazy {
        DataSourceImpl()
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = MessageFragmentBinding.bind(contentView)
        val context = requireActivity() as FileTransportActivity

        viewBinding.messageRv.adapter = SimpleAdapterBuilderImpl<FileTransportActivity.Companion.Message>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.message_item_layout),
            dataSource = messageDataSource,
            dataBinder = DataBinderImpl { data, view, _ ->
                val itemViewBinding = MessageItemLayoutBinding.bind(view)
                val isRemote = data.fromRemote
                if (isRemote) {
                    itemViewBinding.remoteMessageTv.visibility = View.VISIBLE
                    itemViewBinding.myMessageTv.visibility = View.GONE
                    itemViewBinding.remoteMessageTv.text = data.msg
                } else {
                    itemViewBinding.remoteMessageTv.visibility = View.GONE
                    itemViewBinding.myMessageTv.visibility = View.VISIBLE
                    itemViewBinding.myMessageTv.text = data.msg
                }
            }
        ).build()

        launch {
            context.observeMessages()
                .map { it.reversed() }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main.immediate)
                .collect {
                    messageDataSource.submitDataList(it) {
                        if (it.isNotEmpty()) {
                            viewBinding.messageRv.scrollToPosition(0)
                        }
                    }
                }
        }

        viewBinding.sendLayout.clicks(this) {
            val text = viewBinding.editText.text.toString()
            if (text.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        fileExplore.requestMsgSuspend(text)
                    }.onSuccess {
                        context.updateNewMessage(
                            FileTransportActivity.Companion.Message(
                                time = System.currentTimeMillis(),
                                msg = text,
                                fromRemote = false
                            )
                        )
                        AndroidLog.d(TAG, "Send msg success.")
                        withContext(Dispatchers.Main) {
                            viewBinding.editText.text?.clear()
                        }
                    }.onFailure {
                        AndroidLog.e(TAG, "Send msg fail: $it", it)
                    }
                }
            }
        }

        launch {
            context.stateFlow()
                .map { it.selectedTabType }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main)
                .collect {
                    inputMethodManager.hideSoftInputFromWindow(viewBinding.editText.windowToken, 0)
                }
        }

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.editLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // SoftKeyboard
            val imeBars = insets.getInsets(WindowInsetsCompat.Type.ime())
            if (imeBars.bottom > 0) {
                // If soft keyboard show, scroll to first.
                launch {
                    val messages = context.observeMessages().first()
                    if (messages.isNotEmpty()) {
                        viewBinding.messageRv.scrollToPosition(0)
                    }
                }
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeBars.bottom)
            } else {
                // soft keyboard hide.
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            }
            insets
        }
    }

    companion object {
        private const val TAG = "MessageFragment"
    }

}