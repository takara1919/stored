package com.tech.wallpaper.ui.preview

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.RequestManager
import com.colorphone.callscreen.callback.InterstitialOnLoadCallBack
import com.colorphone.callscreen.callback.InterstitialOnShowCallBack
import com.tech.wallpaper.BuildConfig
import com.tech.wallpaper.R
import com.tech.wallpaper.adsconfig.AdmobInterstitialAds
import com.tech.wallpaper.base.BaseFragment
import com.tech.wallpaper.databinding.DialogProgressBinding
import com.tech.wallpaper.databinding.FragmentProcessingBinding
import com.tech.wallpaper.model.Theme
import com.tech.wallpaper.util.AdmobNative
import com.tech.wallpaper.util.Constants
import com.tech.wallpaper.util.PrefUtil
import com.tech.wallpaper.util.animRotation3
import com.tech.wallpaper.util.getPathSave
import com.tech.wallpaper.util.gone
import com.tech.wallpaper.util.haveNetworkConnection
import com.tech.wallpaper.util.inv
import com.tech.wallpaper.util.setPreventDoubleClick
import com.tech.wallpaper.util.setPreventDoubleClickScaleView
import com.tech.wallpaper.util.show
import com.tech.wallpaper.util.toDp
import com.tech.wallpaper.video.GLWallpaperService
import com.tech.wallpaper.video.WallPaperCardUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ProcessingFragment :
    BaseFragment<FragmentProcessingBinding>(FragmentProcessingBinding::inflate) {
    @Inject
    lateinit var glide: RequestManager

    var isCompleted = false

    var handler = Handler(Looper.getMainLooper())

    private var pathSave = ""

    var option = Constants.ALL

    @Inject
    lateinit var prefUtil: PrefUtil

    @Inject
    lateinit var admobInterstitialAds: AdmobInterstitialAds

    private var theme: Theme? = null

    private var dialog: androidx.appcompat.app.AlertDialog? = null

    private var bindingDialog: DialogProgressBinding? = null

    private fun createDialog() {
        try {
            context?.let {
                val view: View =
                    LayoutInflater.from(context).inflate(R.layout.dialog_progress, null)
                val builder =
                    androidx.appcompat.app.AlertDialog.Builder(it)
                        .setView(view)
                        .setCancelable(false)

                dialog = builder.create()
                dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                bindingDialog = DialogProgressBinding.bind(view)
                bindingDialog?.tvSubtitle?.gone()
                dialog?.setOnShowListener {
                    bindingDialog?.circularSeekBar?.animRotation3()
                }
                dialog?.setOnDismissListener {
                    bindingDialog?.circularSeekBar?.clearAnimation()
                }
            }

        } catch (e: Exception) {

        }
    }

    private fun showDialog() {
        bindingDialog?.circularSeekBar?.clearAnimation()
        bindingDialog?.circularSeekBar?.animRotation3()
        context?.let {
            bindingDialog?.ctContent?.setPadding(0, (19).toDp.toInt(), 0, 0)
            bindingDialog?.llSuccess?.gone()
        }
        if (dialog?.isShowing == false) {
            try {
                dialog?.show()
            } catch (_: Exception) {

            }
        }
    }

    private fun hideDialog() {
        try {
            dialog?.dismiss()
        } catch (e: Exception) {
        }
    }

    override fun init(view: View) {

        theme = arguments?.getParcelable("theme")
        option = arguments?.getString("option").toString()
        initNativeAds()
        glide.load(R.drawable.bg_home).into(binding.imgBg)
        glide.load(theme?.url).into(binding.imgTheme)
        createDialog()
        initListener()
        Log.d("TGAHDNDNDNDN", "xxxxx")
        if (!Constants.isConfigChange) {
            binding.tvCancel.show()
            downloading()
        } else {
            Constants.isConfigChange = false
            gotoSuccess()
        }

        Constants.logEvent("ProcessingWP_Show")
    }

    val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                theme?.isHistory = true
                theme?.let { homeViewModel.updateTheme(it) {} }
                gotoSuccess()
            } else {
                findNavController().popBackStack()
            }
        }

    private fun initListener() {
        binding.apply {
            ivBack.setPreventDoubleClickScaleView {
                isCompleted = true
                Constants.logEvent("ProcessingWP_Back_Click")
                showAdsApply {
                    try {
                        findNavController().popBackStack()
                    }catch (e: Exception){}
                }

            }

            tvCancel.setPreventDoubleClick {
                isCompleted = true
                showAdsApply {
                    try {
                        findNavController().popBackStack()
                    }catch (e: Exception){}

                }

            }

            tvSetWallpaper.setPreventDoubleClick {
            }

            ivPreview.setPreventDoubleClick {
                showAdsApply {
                    gotoReview()
                }
            }

        }

        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, true) {
            Constants.logEvent("ProcessingWP_Back_Click")
            isCompleted = true
            findNavController().popBackStack()
        }
    }

    private fun showAdsApply(onDismiss: () -> Unit) {
        if (Constants.isShowOpenAds || Constants.ads.find { it.spaceName == "Wallpaper_Preview_Back_Inter" }?.isOn == false) {
            hideDialog()
            Log.d("TAGVNJHJJKKKK", "a")
            onDismiss()
            Constants.isShowAdsBefore = false
        } else {
            bindingDialog?.tvSubtitle?.show()
            if (admobInterstitialAds.isCanShow()) {
                Constants.logEvent("Show_Ads_Inter")
                showDialog()
                if (admobInterstitialAds.isInterstitialLoaded()) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d("TAGVNJHJJKKKK", "b")
                        showInter(onShow = {

                        }, onDismiss = {
                            onDismiss()
                        })
                    }, 1500)
                } else {
                    Constants.logEvent("load_inter_pre")
                    loadAdsInter(getString(R.string.admob_inter_theme_ids))
                    var count = 0
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed(object : Runnable {
                        override fun run() {
                            if (count > 6) {
                                hideDialog()
                                onDismiss()
                                Constants.isShowAdsBefore = false
                                handler.removeCallbacksAndMessages(null)
                            } else {
                                if (admobInterstitialAds.isInterstitialLoaded()) {
                                    handler.removeCallbacksAndMessages(null)
                                    Log.d("TAGVNJHJJKKKK", "c")
                                    showInter(onShow = {

                                    }, onDismiss = {
                                        onDismiss()
                                    })
                                } else {
                                    count++
                                    handler.postDelayed(this, 1000)
                                }
                            }
                        }
                    }, 0)
                }

            } else {
                Log.d("TAGVNJHJJKKKK", "d")
                hideDialog()
                Constants.isShowAdsBefore = false
                onDismiss()
            }
        }
    }

    private fun loadAdsInter(id: String) {
        context?.haveNetworkConnection()?.let {
            admobInterstitialAds.loadInterstitialAd(id,
                activity,
                false,
                it,
                object : InterstitialOnLoadCallBack {
                    override fun onAdFailedToLoad(adError: String) {

                    }

                    override fun onAdLoaded() {
                        super.onAdLoaded()
                        Log.d(
                            "TAGVNHHHHHHH",
                            admobInterstitialAds.isInterstitialLoaded().toString()
                        )
                    }
                })
        }
    }

    private fun showInter(onShow: () -> Unit, onDismiss: () -> Unit) {
        hideDialog()
        Constants.isShowAdsBefore = true
        admobInterstitialAds.showAndLoadInterstitialAd(activity, object :
            InterstitialOnShowCallBack {
            override fun onAdDismissedFullScreenContent() {
                //onDismiss()
                lifecycle.addObserver(object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        if (event == Lifecycle.Event.ON_RESUME) {
                            lifecycle.removeObserver(this)
                            onDismiss()
                        }
                    }
                })
            }

            override fun onAdFailedToShowFullScreenContent() {

            }

            override fun onAdShowedFullScreenContent() {
            }

            override fun onAdClick() {
                Constants.isClickAds = true
            }

            override fun onAdImpression() {
                super.onAdImpression()
            }
        })
    }

    fun apply(type: String) {
        Log.d("TABDNDNDNND",pathSave)
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val displayMetrics = DisplayMetrics()
                activity?.windowManager?.defaultDisplay
                    ?.getRealMetrics(displayMetrics)
                val height = displayMetrics.heightPixels
                val width = displayMetrics.widthPixels
                val `is` = FileInputStream(theme?.url?.let { File(it) })
                val bis = BufferedInputStream(`is`)
                val b: Bitmap = BitmapFactory.decodeStream(bis)
                val bitmapToUse = b.let { scaleCenterCrop(it, height, width) }
                if ("" != theme!!.url) {
                    val myWallpaperManager = WallpaperManager.getInstance(activity)
                    try {
                        when (type) {
                            Constants.HOME_SCREEN -> {
                                myWallpaperManager.setBitmap(
                                    bitmapToUse,
                                    null,
                                    true,
                                    WallpaperManager.FLAG_SYSTEM
                                )
                                withContext(Dispatchers.Main) {
                                    Log.d("TAGGHHDHDHHDHDHD","a")
                                    if (!isCompleted) {
                                        showAdsApply {
                                            gotoSuccess()
                                        }
                                    }
                                }
                            }

                            Constants.LOCK_SCREEN -> {
                                myWallpaperManager.setBitmap(
                                    bitmapToUse,
                                    null,
                                    true,
                                    WallpaperManager.FLAG_LOCK
                                )
                                withContext(Dispatchers.Main) {
                                    Log.d("TAGGHHDHDHHDHDHD","b")
                                    if (!isCompleted) {
                                        showAdsApply {
                                            gotoSuccess()
                                        }
                                    }
                                }
                            }

                            else -> {
                                if (getDeviceName()?.contains("oppo") == true) {
                                    myWallpaperManager.setBitmap(bitmapToUse)
                                } else {
                                    myWallpaperManager.setBitmap(
                                        bitmapToUse,
                                        null,
                                        true,
                                        WallpaperManager.FLAG_LOCK
                                    )
                                }
                                myWallpaperManager.setBitmap(
                                    bitmapToUse,
                                    null,
                                    true,
                                    WallpaperManager.FLAG_SYSTEM
                                )

                                withContext(Dispatchers.Main) {
                                    Log.d("TAGGHHDHDHHDHDHD","c")
                                    if (!isCompleted) {
                                        showAdsApply {
                                            gotoSuccess()
                                        }
                                    }
                                }
                            }
                        }
                        theme?.isHistory = true
                        theme?.let { homeViewModel.updateTheme(it) {} }

                    } catch (e: Exception) {
                        Log.e("TBVHHHHHH", "appl13: ${e.message}")
                        e.printStackTrace()
                    }
                }
                b.recycle()
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                Log.e("TBVHHHHHH", "appl1y111: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("TBVHHHHHH", "appl1y: ${e.message}")
            } catch (e: IOException) {
                Log.e("TBVHHHHHH", "apply2: ${e.message}")
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog?.isShowing == true) {
                job.cancel()
                hideDialog()
                try {
                    Toast.makeText(
                        activity,
                        getString(R.string.error),
                        Toast.LENGTH_LONG
                    ).show()
                }catch (e: Exception){}

            }
        }, 20000)
    }

    private fun gotoSuccess() {
        val bundle = Bundle()
        bundle.putParcelable("theme",theme)
        if (navController?.currentDestination?.id == com.tech.wallpaper.R.id.processingFragment) {
            navController?.navigate(com.tech.wallpaper.R.id.action_processingFragment_to_maiFragment,bundle)
        }

//        findNavController().popBackStack(com.tech.wallpaper.R.id.mainFragment,false)
//        if(Constants.listCollectionVip.isEmpty()){
//            if (navController?.currentDestination?.id == com.tech.wallpaper.R.id.processingFragment) {
//                navController?.navigate(com.tech.wallpaper.R.id.action_processingFragment_to_successFragment)
//            }
//        }else{
//            val bundle = Bundle()
//            bundle.putParcelable("theme",theme)
//            if (navController?.currentDestination?.id == com.tech.wallpaper.R.id.processingFragment) {
//                navController?.navigate(com.tech.wallpaper.R.id.action_processingFragment_to_successGiftFragment,bundle)
//            }
//        }

    }

    private fun getDeviceName(): String? {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.lowercase(Locale.getDefault())
                .startsWith(manufacturer.lowercase(Locale.getDefault()))
        ) {
            capitalize(model)
        } else {
            capitalize(manufacturer) + " " + model
        }
    }

    private fun capitalize(s: String?): String? {
        if (s == null || s.length == 0) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            first.uppercaseChar().toString() + s.substring(1)
        }
    }

    private fun scaleCenterCrop(
        source: Bitmap, newHeight: Int,
        newWidth: Int
    ): Bitmap? {
        val sourceWidth = source.width
        val sourceHeight = source.height
        val xScale = newWidth.toFloat() / sourceWidth
        val yScale = newHeight.toFloat() / sourceHeight
        val scale = xScale.coerceAtLeast(yScale)
        // Now get the size of the source bitmap when scaled
        val scaledWidth = scale * sourceWidth
        val scaledHeight = scale * sourceHeight
        val left = (newWidth - scaledWidth) / 2
        val top = (newHeight - scaledHeight) / 2
        val targetRect = RectF(
            left, top, left + scaledWidth, top
                    + scaledHeight
        ) //from ww w  .j a va 2s. co m
        if (newHeight < 1) return null
        val dest = Bitmap.createBitmap(
            newWidth, newHeight,
            source.config
        )
        val canvas = Canvas(dest)
        canvas.drawBitmap(source, null, targetRect, null)
        //        source.recycle();
        return dest
    }

    private fun gotoReview() {
        if (navController?.currentDestination?.id == R.id.previewDownloadingFragment) {
            navController?.navigate(R.id.action_previewDownloadingFragment_to_reviewFragment)
        }
    }


    private fun initNativeAds() {
        if (Constants.ads.isEmpty()) {
            loadNative(getString(R.string.admob_native_onboarding))
        } else {
            try {
                val nativeAds =
                    Constants.ads.find { it.spaceName == "Wallpaper_ProcessingWP_Native" }
                if (nativeAds != null && nativeAds.isOn) {
                    var id = nativeAds.id
                    if (BuildConfig.DEBUG) {
                        id = getString(R.string.admob_native_onboarding)
                    }
                    loadNative(id)
                } else {
                    binding.flNativeAds.gone()
                }
            } catch (_: Exception) {
                binding.flNativeAds.gone()
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun loadNative(id: String) {
        val layoutAds320x250 =
            LayoutInflater.from(activity).inflate(R.layout.layout_native_ads_big, null)
        activity?.let {
            CoroutineScope(Dispatchers.IO).launch {
                AdmobNative.load(it, object : AdmobNative.AdCallBack {
                    override fun loadSuccess() {
                        it.runOnUiThread {
                            AdmobNative.show(it, binding.flNativeAds, layoutAds320x250)
                        }
                    }

                    override fun onAdFailToLoad() {

                    }

                    override fun onAdClick() {
                        Constants.isClickAds = true
                    }

                }, id)
            }
        }
    }


    override fun onResume() {
        if (Constants.isShowOpenAds) {
            binding.flNativeAds.inv()
        } else {
            binding.flNativeAds.show()
        }
        super.onResume()
    }

    private fun onBack() {
        try {
            findNavController().popBackStack()
        } catch (e: Exception) {
        }
    }

    private fun downloading() {
        theme?.let {
            if (it.url.contains("https")) {
                initFile(it.url)
                try {
                    binding.tvDownload.text = "${getString(R.string.text_downloading)}${0}%"
                } catch (e: Exception) {
                }

                download(pathSave, it.url, onProgressUpdate = { per ->
                    try {
                        binding.tvDownload.text = "${getString(R.string.text_downloading)}${per}%"
                    } catch (e: Exception) {
                    }

                }, onError = {
                    Toast.makeText(context, "Download Faile", Toast.LENGTH_SHORT).show()
                    onBack()
                }, onComplete = {
                    binding.tvCancel.gone()
                    try {
                        binding.tvDownload.text =
                            getString(com.tech.wallpaper.R.string.processing_wallpaper)
                    } catch (e: Exception) {
                    }
                    if (!isCompleted) {
                        downloadComplete()
                    }

                })
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isCompleted) {
                        if (it.type == Constants.TYPE_IMAGE) {
                            binding.tvCancel.gone()
                            apply(option)
                        } else {
                            setLive()
                        }
                    }

                }, 2000)

            }

        }
    }

    @Suppress("DEPRECATION")
    private fun downloadComplete() {
        theme?.url = pathSave
        theme?.isDownload = true
        theme?.let {
            try {
                homeViewModel.updateTheme(it) {}
            } catch (_: Exception) {
            }
            if (it.type == Constants.TYPE_IMAGE) {
                apply(option)
            } else {
                setLive()
            }
        }

    }

    private fun setLive() {
        showAdsApply {
            try {
                prefUtil.typeSave = WallPaperCardUtils.TYPE_EXTERNAL
                prefUtil.videoPath = theme?.url
                val intent =
                    Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    context?.let { ComponentName(it, GLWallpaperService::class.java) })
                Constants.isClickAds = true
                startForResult.launch(intent)
            } catch (e: Exception) {
                try {
                    Toast.makeText(
                        context,
                        "This device support live wallpaper",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                } catch (e: Exception) {
                }
            }
        }

    }

    private fun initFile(url: String) {
        val path = activity?.getPathSave(url).toString() + "/4K Wallpaper/"
        val file = File(path)
        if (!file.exists()) {
            file.mkdirs()
        }

        pathSave =
            file.path + url.length.let {
                url.lastIndexOf("/").let { it1 ->
                    url.substring(
                        it1,
                        it
                    )
                }
            }
    }

    override fun onDestroyView() {
        Constants.isShowAdsInter = false
        super.onDestroyView()
    }

    override fun onSubscribeObserver(view: View) {

    }

    override fun screenName(): String? {
        return ""
    }

}