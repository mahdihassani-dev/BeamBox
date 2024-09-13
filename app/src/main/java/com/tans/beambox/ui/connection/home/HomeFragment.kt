package com.tans.beambox.ui.connection.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tans.beambox.databinding.HomeFragmentBinding
import com.tans.beambox.ui.connection.EventListener

class HomeFragment(private val onEventListener: EventListener) : Fragment() {

    private lateinit var binding: HomeFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = HomeFragmentBinding.inflate(LayoutInflater.from(context))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnFindFriend.setOnClickListener {
            onEventListener.onFindBtnClicked()


        }

    }

}