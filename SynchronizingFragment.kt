package com.example.colorphone.ui.synchroniz

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.example.colorphone.R
import com.example.colorphone.adsConfig.AdmobNative
import com.example.colorphone.adsConfig.InterAdsManagers
import com.example.colorphone.adsConfig.PlacementAds
import com.example.colorphone.base.BaseFragment
import com.example.colorphone.databinding.FragmentSynchronizingBinding
import com.example.colorphone.ui.main.screenVp2.settings.googleDriver.helper.GoogleDriveApiDataRepository
import com.example.colorphone.util.Const
import com.example.colorphone.util.PrefUtil
import com.example.colorphone.util.ext.hideKeyboard
import com.google.android.gms.common.api.ApiException
import com.google.api.services.drive.Drive
import com.wecan.inote.util.gone
import com.wecan.inote.util.setPreventDoubleClick
import com.wecan.inote.util.show
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SynchronizingFragment :
    BaseFragment<FragmentSynchronizingBinding>(FragmentSynchronizingBinding::inflate) {
    var isBack = false


    override fun init(view: View) {
        Const.isShowAdsBefore = false
        Const.countSysn++
        setUpGoogle()
        initData()
        initListener()
        if (!prefUtil.isPremium){
            loadNative()
        }else{
            binding.flAds.gone()
        }

    }

    private fun loadNative() {
        val layoutAds320x250 =
            LayoutInflater.from(activity)
                .inflate(R.layout.layout_ads_native_250, null)
        activity?.let {
            CoroutineScope(Dispatchers.IO).launch {
                activity?.getString(R.string.no1_native_default)?.let { it1 ->
                    AdmobNative.load(it, object : AdmobNative.AdCallBack {
                        override fun loadSuccess() {
                            it.runOnUiThread {
                                AdmobNative.show(it, binding.flAds, layoutAds320x250)
                            }
                        }

                        override fun onAdFailToLoad() {
                            // loadBannerAd()
                            //loadAdsBannerInline()
                        }

                        override fun onAdClick() {

                        }

                    }, "ca-app-pub-6969842628973791/5153672600")
                }
            }
        }
    }

    override fun onGoogleDriveSignedInSuccess(driveApi: Drive?) {
        super.onGoogleDriveSignedInSuccess(driveApi)
        driveApi?.let { drive ->
            shareViewModel.driverRepo(drive)
        }
        repository = GoogleDriveApiDataRepository(driveApi)
        Log.d("TAGHYDUUDUDU", "sucess")
        binding.tvLogin.gone()
        binding.ivBanner.gone()
        binding.tvYouNeed.gone()
        binding.tvSys.show()
        binding.lvSys.show()
        handleSyncData {
            Handler(Looper.getMainLooper()).postDelayed({
                showInter {
                    Const.isShowDialogSysn = true
                    findNavController().popBackStack()
                }
            }, 2000)

        }
    }

    override fun onGoogleDriveSignedInFailed(exception: ApiException?) {
        super.onGoogleDriveSignedInFailed(exception)

    }

    private fun initListener() {
        binding.apply {
            ivBack.setPreventDoubleClick {
                isBack = true
                findNavController().popBackStack()
            }

            tvLogin.setPreventDoubleClick {
                startGoogleDriveSignIn()
//                handleClickSync {
//                    startGoogleDriveSignIn()
//                }
            }

        }

        activity?.onBackPressedDispatcher?.addCallback(this, true) {
            isBack = true
            findNavController().popBackStack()
        }
    }

    private fun initData() {
        InterAdsManagers.loadInterAds(activity)
        if (prefUtil.statusEmailUser == null) {
            binding.tvLogin.show()
            binding.ivBanner.show()
            binding.tvYouNeed.show()
            binding.tvSys.gone()
            binding.lvSys.gone()
            //startGoogleDriveSignIn()
        } else {
            binding.tvLogin.gone()
            binding.ivBanner.gone()
            binding.tvYouNeed.gone()
            binding.tvSys.show()
            binding.lvSys.show()
            handleSyncData {
                Log.d("TABBBDBDBD", "a")
                Handler(Looper.getMainLooper()).postDelayed({
                    showInter {
                        Log.d("TABBBDBDBD", "b")
                        Const.isShowDialogSysn = true
                        findNavController().popBackStack()
                    }
                }, 2000)

            }
        }
    }

    private fun showInter(
        onDismiss: () -> Unit
    ) {
        if (!isBack) {
            if (prefUtil.isPremium) {
                onDismiss()
            } else {
                if (Const.countSysn % 3 == 0 && Const.countSysn > 1) {
                    navController?.navigate(
                        R.id.iapFragment,
                        bundleOf(Const.KEY_SHOW_IAP_ACTION to true)
                    )
                } else {
                    showAds(
                        activity, PlacementAds.PLACEMENT_EDIT_BACK
                    ) {
                        onDismiss()
                    }
                }

            }
        }

    }

    private fun showAds(
        activity: Activity?, placement: String, noActive: () -> Unit
    ) {
        InterAdsManagers.showAndReloadInterAds(
            activity, placement, noActive
        )

        Log.i("qweqweqe", "showInter: ")
    }

    override fun onSubscribeObserver(view: View) {

    }

}