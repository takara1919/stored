package com.gom.draw.ui.home

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.bumptech.glide.RequestManager
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.applySkeleton
import com.gom.draw.R
import com.gom.draw.base.BaseFragment
import com.gom.draw.databinding.FragmentHomeBinding
import com.gom.draw.model.Image
import com.gom.draw.ui.ModeFragment
import com.gom.draw.ui.base.MainActivity
import com.gom.draw.ui.home.adapter.CategoryAdapter
import com.gom.draw.ui.home.adapter.CategoryDecoration
import com.gom.draw.ui.home.adapter.CollectionAdapter
import com.gom.draw.ui.home.adapter.CollectionDecoration
import com.gom.draw.ui.home.adapter.ImageViewPagerAdapter
import com.gom.draw.util.logData
import com.google.android.material.appbar.AppBarLayout
import com.util.ads.admob.nativead.NativeAdCustom
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HomeFragment(val requestManager: RequestManager) :
    BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {
    private val vm: HomeViewModel by viewModels()
    private lateinit var nativeAdMainCustomView: NativeAdCustom
    var loadTime: Long = 0

    private lateinit var viewPagerAdapter: ImageViewPagerAdapter

    private lateinit var collectionSkeleton: Skeleton
    private lateinit var categorySkeleton: Skeleton

    private var collectionAdapter = CollectionAdapter(requestManager) {
        logEvent("Main_${it.title}_Click")
        if (navController?.currentDestination?.id == R.id.homeFragment) {
            val action = HomeFragmentDirections.actionHomeToCollection(
                collection = it
            )
            navController?.navigate(action)
        }
    }

    private var categoryAdapter = CategoryAdapter(
        glide = requestManager,
    ) { index ->
        vm.chooseCategory(index)
        if(index in 0 until (binding.viewPager.adapter?.itemCount ?: 0))
            binding.viewPager.currentItem = index
    }

    fun initAds() {
        val nativeAds = mainViewModel.getAdsModel("AR_Main_Native")
        if (nativeAds.isOn) {
            binding.nativeAdContainer.visibility = View.VISIBLE
            nativeAdMainCustomView.setOnCollapse(nativeAds.isCollapse)
            binding.nativeAdContainer.removeAllViews()
            binding.nativeAdContainer.addView(nativeAdMainCustomView)
        } else {
            View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getTimeActive = {
            val timeActive = formatDuration(System.currentTimeMillis() - timeStartFragment)
            logEvent("Main_Activite_Time:$timeActive")
            Log.d(
                "FragmentBase",
                "thoi gian hoat dong cua ${this::class.simpleName} " + timeActive.toString()
            )
        }
    }

    override fun hasCustomBackPress() {

    }

    override fun init(view: View) {
        binding.apply {
            viewPager.apply {
                registerOnPageChangeCallback(object : OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        vm.chooseCategory(position)
                        categoryAdapter.chooseItem(position)
                        binding.rvCategory.scrollToPosition(position)
                    }
                })
                isSaveEnabled = false
                offscreenPageLimit = dataViewModel.images.value?.size ?: 300
            }

            swipeLayout.apply {
                setOnRefreshListener {
                    refreshView()

                    swipeLayout.isRefreshing = false
                }

                setOnChildScrollUpCallback { parent, child ->
                    child as CoordinatorLayout

                    val appBar = child.findViewById<AppBarLayout>(R.id.mainAppBar)
                    val appBarNotExpanded = appBar.top < 0

                    appBarNotExpanded
                }
            }

            rvCollection.apply {
                layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                adapter = collectionAdapter
                setHasFixedSize(true)
                addItemDecoration(CollectionDecoration(requireContext()))
                collectionSkeleton = applySkeleton(R.layout.collection_layout)
            }

            btnSetting.setOnClickListener {
                logEvent("Main_Setting_Click")
                if (navController?.currentDestination?.id == R.id.homeFragment) {
                    navController?.navigate(R.id.action_home_to_setting)
                }
            }

            searchBtn.setOnClickListener {
                logEvent("Main_Search_Click")
                navController?.navigate(R.id.home_to_search)
            }

            rvCategory.apply {
                layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                adapter = categoryAdapter
                addItemDecoration(CategoryDecoration(requireContext()))
                categorySkeleton = applySkeleton(R.layout.category_layout)
            }

            galleryButton.setOnClickListener {
                logEvent("Main_Gallery_Click")
                Log.d("HomeFragment", "${navController?.currentDestination?.navigatorName}")
                if (navController?.currentDestination?.id == R.id.homeFragment) {
                    Log.d("HomeFragment", " galleryButton.setOnClickListener")
                    findNavController().navigate(R.id.action_homeFragment_to_addImageAlbum)
                }
            }

            cameraButton.setOnClickListener {
                logEvent("Main_Camera_Click")
                mainViewModel.modeFragment = ModeFragment.SELECTION_DRAW
                if (navController?.currentDestination?.id == R.id.homeFragment)
                    findNavController().navigate(R.id.action_homeFragment_to_captureImageFragment)
            }

            btnSeeAll.setOnClickListener {
                logEvent("Main_SeeAll_Click")
                navController?.navigate(R.id.home_to_all_collection)
            }

            btnRefresh.setOnClickListener {
                failedScreen.visibility = View.GONE
                dataViewModel.initData()
            }

            collectionSkeleton.showSkeleton()
            categorySkeleton.showSkeleton()
        }

    }

    override fun onStart() {
        this@HomeFragment.requireActivity().findViewById<ViewGroup>(R.id.load_ads).visibility =
            View.GONE
        logEvent("Main_Show")
        try {
            val nativeAds = mainViewModel.getAdsModel("AR_Main_Native")
            if (nativeAds.isOn) {
                nativeAdMainCustomView = NativeAdCustom((this.requireActivity()))
                mainViewModel.adNativeMain.loadAdsNative(
                    mainViewModel.getAdsModel("AR_Main_Native").id,
                    nativeAdMainCustomView
                )
                initAds()
            }
        } catch (e: Exception) {

        }
        loadTime = System.currentTimeMillis()
        Log.d("Home_fragment", loadTime.toString())
        super.onStart()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainViewModel.isFirstHomeFragment = false
    }

    override fun onSubscribeObserver(view: View) {
        dataViewModel.images.observe(this) {
            if(it.isNullOrEmpty()) return@observe
//            Log.d("TAGG", "Change Images DataViewmodel $it")
            vm.updateImages(it)
        }
        dataViewModel.categories.observe(this) {
            if(it.isNullOrEmpty()) return@observe
//            Log.d("TAGG", "Change Categories DataViewmodel $it")
            vm.updateCategory(it)
        }
        dataViewModel.initLoading.observe(this) {
//            Log.d("TAGG", "Change InitLoading DataViewmodel: $it")
            if (it) {
                binding.btnSeeAll.visibility = View.INVISIBLE
            } else {
                binding.btnSeeAll.visibility = View.VISIBLE
                collectionSkeleton.showOriginal()
                categorySkeleton.showOriginal()
            }
        }
        dataViewModel.isFailed.observe(this) {
//            Log.d("TAGG", "Change IsFailed DataViewmodel: $it")
            if (it) {
                binding.failedScreen.visibility = View.VISIBLE
            }
        }
        activity?.let { act ->
            mainViewModel.isRefresh.observe(act) {
//                Log.d("TAGG", "Change IsRefresh MainViewModel: $it")
                if (it && !vm.images.value.isNullOrEmpty()) {
                    refreshView()
                }
            }
            dataViewModel.collections.observe(act) {
                if(it.isNullOrEmpty()) return@observe
//                Log.d("TAGG", "Change Collections DataViewmodel $it")
                vm.updateCollections(it)
            }
            vm.categories.observe(act) { state ->
                if(state.isNullOrEmpty()) return@observe
//                Log.d("TAGG", "Change Categories HomeViewModel $state")
                categoryAdapter.submitList(state)
            }
            vm.collections.observe(act) {
                if(it.isNullOrEmpty()) return@observe
//                Log.d("TAGG", "Change Collections HomeViewModel $it")
                collectionAdapter.submitList(null)
                collectionAdapter.submitList(it)
            }


            vm.images.observe(act) {
                if(it.isNullOrEmpty()) return@observe
                if (it.isNotEmpty()) {
                    if (mainViewModel.isRefresh.value == true) {
                        refreshView()
                        mainViewModel.isRefresh.postValue(false)
                    }
//                    Log.d("TAGG", "Change Images HomeViewModel $it")
                    viewPagerAdapter = ImageViewPagerAdapter(
                        requestManager = requestManager,
                        onFavoriteClick = {
                            logEvent("Main_Favourite_Click")
                            dataViewModel.updateFavoriteImage(it.id)
                            favoriteImageViewModel.toggleFavorite(it.id)
                            vm.updateFavoriteImage(it.id)
                        },
                        onItemClick = { bitmap, image ->
                            logEvent("Main_ChooseAR_Click")
                            if (bitmap != null) {
                                actionChoseImage(
                                    bitmap,
                                    image,
                                    this,
                                    R.id.action_homeFragment_to_drawSelectionFragment
                                )

                            } else {
                                (this.requireActivity() as MainActivity).showLoadingFailedImageDialog(
                                    this.requireContext(),
                                    {})
                            }
                        },
                        images = it,
                        categories = dataViewModel.categories.value ?: emptyList()
                    )
                    binding.viewPager.apply {
                        adapter = viewPagerAdapter
                        currentItem = vm.chosenCategory.value ?: 0
                    }
                }
            }
        }

    }

    private fun refreshView() {
        logData("Calling Refresh View")
        dataViewModel.shuffleCollections()
        vm.shuffleImages()
    }

    fun actionChoseImage(
        bitmap: Bitmap?,
        image: Image,
        fragement: HomeFragment,
        navigationAction: Int
    ) {
        var bitmapUrl: Bitmap? = null
        onToastLoadingWhenBackPress()
        disableTounchScreen()

        this@HomeFragment.requireActivity().findViewById<TextView>(R.id.tv_loading).text = mainViewModel.textLoadImage
        this@HomeFragment.requireActivity().findViewById<ViewGroup>(R.id.load_ads).visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("ImageAdapter", (bitmap == null).toString())
            withContext(Dispatchers.Main) {
                this@HomeFragment.requireActivity().findViewById<TextView>(R.id.tv_loading).text = mainViewModel.textLoadImage
                this@HomeFragment.requireActivity().findViewById<ViewGroup>(R.id.load_ads).visibility = View.VISIBLE
//                fragement.binding.pbLoadImage.visibility = View.VISIBLE
            }
            Log.d("InterHomeFragment", "load image   time ")
            bitmapUrl = mainViewModel.getImageFromUrl(fragement.requireContext(), image.imageUrl)

            Log.d("InterHomeFragment", "load image after  time ")
            withContext(Dispatchers.Main) {
                try {
                    bitmapUrl?.let {
                        val interAdsModel = mainViewModel.getAdsModel("AR_ChooseAR_Inter")
                        Log.d("ImageAdapter", " load image by Url")
                        mainViewModel.bitmapDraw = bitmapUrl
                        this@HomeFragment.requireActivity().findViewById<TextView>(R.id.tv_loading).text = mainViewModel.textLoadAds
                        val timebase = if (mainViewModel.adInterSplash.isShowed) {
                            interAdsModel.timeBase
                        } else {
                            0L
                        }
                        Log.d("InterAds", "timebase of home " + timebase)
                        mainViewModel.adInterChooseAR.getAdsToShow(
                            condition = {
                                interAdsModel.isOn && !mainViewModel.adInterBack.isShowed
                            },
                            timeBase = timebase,
                            timeout = interAdsModel.timeOut,
                            timeStart = this@HomeFragment.loadTime,
                            id = interAdsModel.id,
                            activity = this@HomeFragment.requireActivity(),
                            actionMove = { navigationToDraw(R.id.action_homeFragment_to_drawSelectionFragment) },
                            adsProvider = mainViewModel.adProviderSevice
                        )
                        mainViewModel.adInterBack.isShowed = false
                        mainViewModel.adInterSplash.isShowed = false
                    }
                }catch (e: Exception){
                }

                if (bitmapUrl == null) clearEffect()

            }

        }
    }


    fun clearEffect(){
        diminishToastLoadingWhenPress()
        clearDisableTounchScreen()
        this.requireActivity().findViewById<ViewGroup>(R.id.load_ads).visibility = View.GONE
        (this.requireActivity() as MainActivity).showLoadingFailedImageDialog(this.requireActivity(), {})
    }
    fun navigationToDraw(navigationAction: Int) {
        try {
            if (navController?.currentDestination?.id == R.id.homeFragment) {
                mainViewModel.choseArFromHomeScreen = true
                this@HomeFragment.requireActivity().findViewById<ViewGroup>(R.id.load_ads).visibility =
                    View.GONE
                mainViewModel.choseOption(choseCameraOption = false)

                diminishToastLoadingWhenPress()
                clearDisableTounchScreen()
                findNavController().navigate(navigationAction)
            }
        }catch (e: Exception){}

    }
}
