package com.tans.beambox.ui.connection.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.adivery.sdk.AdiveryAdListener
import com.tans.beambox.databinding.HomeFragmentBinding
import com.tans.beambox.ui.connection.EventListener
import com.tans.beambox.utils.Constants

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

        val bannerAd = binding.bannerAd
        bannerAd.setPlacementId(Constants.ADIVERY_BANNER_ID)

        bannerAd.setBannerAdListener(object : AdiveryAdListener() {
            override fun onAdLoaded() {
                // تبلیغ به‌طور خودکار نمایش داده می‌شود، هر کار دیگری لازم است اینجا انجام دهید.
                Log.d(TAG, "onAdLoaded: ")
            }

            override fun onError(reason: String) {
                Log.d(TAG, "onError: $reason")
                // خطا را چاپ کنید تا از دلیل آن مطلع شوید
            }

            override fun onAdClicked() {
                // کاربر روی بنر کلیک کرده
                Log.d(TAG, "onAdClicked: ")
            }
        })

        bannerAd.loadAd()

    }

    companion object {
        const val TAG = "HomeFragmentDebug"
    }


}