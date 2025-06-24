package com.example.colorphone.base

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.example.colorphone.R
import com.example.colorphone.model.NoteModel
import com.example.colorphone.room.DataConverter
import com.example.colorphone.ui.bottomDialogColor.ui.BottomColorLabel
import com.example.colorphone.ui.main.screenVp2.settings.googleDriver.GoogleSignInFragment
import com.example.colorphone.ui.main.screenVp2.settings.googleDriver.helper.GoogleDriveApiDataRepository
import com.example.colorphone.ui.main.screenVp2.settings.widget.provider.NoteProvider
import com.example.colorphone.ui.main.screenVp2.settings.widget.remoteService.WidgetService
import com.example.colorphone.ui.main.viewmodel.ListShareViewModel
import com.example.colorphone.ui.main.viewmodel.TextNoteViewModel
import com.example.colorphone.util.Const
import com.example.colorphone.util.Const.KEY_TYPE_CREATE_SHORTCUT
import com.example.colorphone.util.ext.dialogProcess
import com.example.colorphone.util.ext.showDialogLoginSuccess
import com.example.colorphone.util.ext.showDialogNoInternet
import com.example.colorphone.util.ext.showDialogSyncSuccess
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.Drive
import com.wecan.inote.util.haveNetworkConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

typealias Inflate<T> = (LayoutInflater, ViewGroup?, Boolean) -> T

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseFragment<B : ViewBinding>(val inflate: Inflate<B>) : GoogleSignInFragment() {

    var navController: NavController? = null
    private lateinit var _binding: B
    val binding get() = _binding

    val viewModelTextNote: TextNoteViewModel by activityViewModels()
    val shareViewModel: ListShareViewModel by activityViewModels()
    private val navBuilder = NavOptions.Builder()

    var repository: GoogleDriveApiDataRepository? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init(view)
        onSubscribeObserver(view)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = inflate.invoke(inflater, container, false)
        navBuilder.setEnterAnim(android.R.anim.fade_in).setExitAnim(android.R.anim.fade_out)
            .setPopEnterAnim(android.R.anim.fade_in).setPopExitAnim(android.R.anim.fade_out)
        return binding.root
    }

    fun getData() {
        lifecycleScope.launch {
            delay(1)
            try{
                viewModelTextNote.getListTextNote(prefUtil.sortType)
            }catch (e: Exception){}

        }
    }

    override fun onGoogleDriveSignedInSuccess(driveApi: Drive?) {
        super.onGoogleDriveSignedInSuccess(driveApi)
        driveApi?.let { drive ->
            shareViewModel.driverRepo(drive)
        }
    }

    private fun listenIntentShortcut(intent: Intent?) {
        val i = intent?.getStringExtra(KEY_TYPE_CREATE_SHORTCUT)
        if (i != null) {
            if (navController?.currentDestination?.id == R.id.splashFragment) {
                navController?.navigate(R.id.mainFragment)
            } else {
                navigationWithAnim(R.id.editFragment, bundleOf(Const.TYPE_ITEM_EDIT to i))
                intent.removeExtra(KEY_TYPE_CREATE_SHORTCUT)
            }
        }
    }

    fun navigationWithAnim(des: Int, bundle: Bundle? = null) {
        navController?.navigate(des, bundle, navBuilder.build())
    }

    fun putFragmentListener(key: String, bundle: Bundle = bundleOf()) {
        activity?.supportFragmentManager?.setFragmentResult(key, bundle)
    }

    fun getFragmentListener(key: String, callback: (Bundle) -> Unit) {
        activity?.supportFragmentManager?.setFragmentResultListener(
            key, viewLifecycleOwner
        ) { _, result ->
            callback(result)
        }
    }

    abstract fun init(view: View)
    abstract fun onSubscribeObserver(view: View)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = findNavController()
        listenIntentShortcut(activity?.intent)
    }

    fun isConnectedViaWifi(): Boolean {
        val connectivityManager =
            context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mWifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        val mMobile = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
        return mWifi?.isConnected == true || mMobile?.isConnected == true
    }

    open fun setUpGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestScopes(Scope(Scopes.DRIVE_FILE)).requestEmail().build()

        googleSignInClient = activity?.let { GoogleSignIn.getClient(it, gso) }
    }

    override fun showLoginSuccess() {
        super.showLoginSuccess()
//        context?.showDialogLoginSuccess {
//            Const.checking("Setting_diaLogInSuccess_Sync_Click")
//            handleSyncData { }
//        }
        shareViewModel.loginSuccess()
    }

    fun showBottomSheet(
        currentColor: String? = null, fromScreen: String, colorClick: (String) -> Unit,onDismiss: () -> Unit
    ) {
        val addPhotoBottomDialogFragment: BottomColorLabel =
            BottomColorLabel.newInstance(currentColor, fromScreen, colorClick,onDismiss)

        activity?.supportFragmentManager?.let {
            addPhotoBottomDialogFragment.show(
                it, "TAG"
            )
        }
    }

    fun check(key: String) {
        Const.checking(key)
    }

    fun addWidget(note: NoteModel, callSuccess: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (note.isCheckList()) {
                if (!isMyServiceRunning(WidgetService::class.java)) {
                    val serviceIntent = Intent(activity, WidgetService::class.java)
                    serviceIntent.putExtra(Const.ID_NOTE_CHECKLIST_WIDGET, note.ids)
                    activity?.startService(serviceIntent)
                } else {
                    val intent2 = Intent(Const.UPDATE_REMOTE_CHECK_LIST)
                    intent2.putExtra(Const.ID_NOTE_CHECKLIST_WIDGET, note.ids)
                    context?.sendBroadcast(intent2)
                }
            }

            context?.let { ct ->
                val appWidgetManager = ct.getSystemService(
                    AppWidgetManager::class.java
                )
                val myProvider = ComponentName(ct, NoteProvider::class.java)
                if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                    val intent = Intent(activity, NoteProvider::class.java)
                    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    val ids = appWidgetManager.getAppWidgetIds(myProvider)
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    intent.putExtra(Const.ACTION_UPDATE_WIDGET_EDIT, Const.ADD_NOTE_WIDGET)
                    intent.putExtra(Const.POST_ID_NOTE_UPDATE_WIDGET, note.ids)
                    val successCallback = PendingIntent.getBroadcast(
                        context,
                        1,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
                    callSuccess.invoke(true)
                }
            }
        } else {
            Toast.makeText(context, getString(R.string.deviceNotSupport), Toast.LENGTH_SHORT).show()
            callSuccess.invoke(false)
        }
    }

    fun isMyServiceRunning(cls: Class<*>): Boolean {
        return try {
            for (runningServiceInfo in (activity?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(
                Int.MAX_VALUE
            )) {
                if (cls.name == runningServiceInfo.service.className) {
                    return true
                }
            }
            false
        } catch (e: java.lang.Exception) {
            false
        }
    }

    private var inAppSystem = false
    override fun onResume() {
        super.onResume()
        inAppSystem = true
    }

    override fun onPause() {
        super.onPause()
        inAppSystem = false
    }

    fun updateWidgetWithId(model: NoteModel, idWidgetConfig: Int? = null) {
        val idsWidget = model.ids?.let { id ->
            prefUtil.getIdWidgetNote(id)
        }
        lifecycleScope.launch {
            try {
                if (model.isCheckList()) {
                    if (!isMyServiceRunning(WidgetService::class.java)) {
                        val serviceIntent = Intent(activity, WidgetService::class.java)
                        serviceIntent.putExtra(Const.ID_NOTE_CHECKLIST_WIDGET, model.ids)
                        if (inAppSystem) {
                            try {
                                context?.startService(serviceIntent)
                            } catch (e: Exception) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        context?.startForegroundService(serviceIntent)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }

                    } else {
                        val intent2 = Intent(Const.UPDATE_REMOTE_CHECK_LIST)
                        intent2.putExtra(Const.ID_NOTE_CHECKLIST_WIDGET, model.ids)
                        context?.sendBroadcast(intent2)
                    }
                }
            } catch (_: Exception) {
            }
            if (idsWidget != -1 || (idWidgetConfig != null && idWidgetConfig != 0)) {
                lifecycleScope.launch {
                    val intent = Intent(activity, NoteProvider::class.java)
                    val actionUpdate =
                        if (model.title.isEmpty() && model.content.isEmpty() && model.listCheckList.isNullOrEmpty()) Const.DELETE_NOTE_WIDGET
                        else Const.UPDATE_NOTE_WIDGET
                    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    intent.putExtra(Const.ACTION_UPDATE_WIDGET_EDIT, actionUpdate)
                    intent.putExtra(Const.POST_ID_NOTE_UPDATE_WIDGET, model.ids)
                    delay(500)
                    activity?.sendBroadcast(intent)
                }
            }
        }
    }

    fun handleClickSync(onNext : () -> Unit) {
        if (context?.haveNetworkConnection() == false) {
            check("Main_DiaNoInternet_Show")
            context?.showDialogNoInternet {
                check("Main_DiaNoInternet_Connect_Click")
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                } else {
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                }
                context?.startActivity(intent)
            }
        } else {
//            if (prefUtil.statusEmailUser == null) {
//                startGoogleDriveSignIn()
//            } else {
//
//                //handleSyncData {}
//            }
            onNext()
        }
    }

//    private var isAdsShowed = false
//    private var isDialogSyncSuccessShow = false

    fun handleSyncData(onComplete: () -> Unit) {
//        isAdsShowed = false
//        isDialogSyncSuccessShow = false
        val dialogProgress =
            context?.dialogProcess(getString(R.string.processing).plus("..."), !prefUtil.isPremium)
        if (dialogProgress?.isShowing == false) {
           // dialogProgress.show()
        }
//        showInterSyncSuccess()
        var noteData: String? = ""
        var idNote = 0
        var countUpdate = 0
        var countThread = 0
        Log.d("TABBBDBDBD","c")
        viewModelTextNote.getAllData {
            Log.d("TABBBDBDBD","d")
            val listNoteLocal = mutableListOf<NoteModel>()
            listNoteLocal.addAll(it)
            noteData = DataConverter().fromListNote(ArrayList(listNoteLocal))

            try {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        Log.d("TABBBDBDBD","e${repository}")
                        val id = repository?.query()
                        if (id.isNullOrEmpty()) {
                            try {
                                Log.d("TABBBDBDBD","f")
                                val idFile = repository?.createFile(
                                    "text/plain", ""
                                )?.id.toString()
                                withContext(Dispatchers.IO) {
                                    repository?.uploadFile(idFile, "iNote", noteData.toString())
                                    withContext(Dispatchers.Main) {
                                        prefUtil.lastSync = System.currentTimeMillis()
                                        dialogProgress?.dismiss()
//                                        if (isAdsShowed) {
//                                            if (!isDialogSyncSuccessShow) {
//                                                isDialogSyncSuccessShow = true
                                       // context?.showDialogSyncSuccess()
//                                            }
//                                        }
                                        onComplete()
                                        shareViewModel.syncSuccess()
                                    }
                                }
                            } catch (e: Exception) {
                                dialogProgress?.dismiss()
                            }
                        } else {
                            withContext(Dispatchers.IO) {
                                val json = repository?.readFile(id)
                                val listDriver = DataConverter().toListNote(json)
                                var count = 0

                                var idMax = 0
                                listDriver?.forEach { note ->
                                    if ((note.ids ?: 0) > idMax) idMax = note.ids!!
                                }
                                idNote = idMax

                                if (listDriver != null) {

                                    for (itemDriver in listDriver) {
                                        val itemLocal =
                                            listNoteLocal.find { it.token == itemDriver.token }
                                        if (itemLocal == null) {
                                            idNote++
                                            count++
                                            itemDriver.ids = idNote
                                            countUpdate++
                                            viewModelTextNote.addNote(itemDriver) {
                                                countThread++
                                            }
                                        } else {

                                            if ((itemDriver.modifiedTime
                                                    ?: 0) > (itemLocal.modifiedTime ?: 0)
                                            ) {
                                                countUpdate++

                                                viewModelTextNote.updateNote(
                                                    itemDriver.apply { ids = itemLocal.ids }) {
                                                    countThread++
                                                }

                                            } else {
                                                countUpdate++
                                                viewModelTextNote.updateNote(itemLocal) {
                                                    countThread++
                                                }
                                            }
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        val handler = Handler(Looper.getMainLooper())
                                        handler.postDelayed(object : Runnable {
                                            override fun run() {
                                                if (countUpdate == countThread) {
                                                    handler.removeCallbacksAndMessages(null)

                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        try {
                                                            viewModelTextNote.getAllData { listNoteAdded ->
                                                                noteData =
                                                                    DataConverter().fromListNote(
                                                                        ArrayList(listNoteAdded)
                                                                    )
                                                                lifecycleScope.launch(Dispatchers.IO) {
                                                                    try {

                                                                        withContext(Dispatchers.Main) {
                                                                            prefUtil.lastSync =
                                                                                System.currentTimeMillis()
                                                                            dialogProgress?.dismiss()
//                                                                            if (isAdsShowed) {
//                                                                                if (!isDialogSyncSuccessShow) {
//                                                                                    isDialogSyncSuccessShow =
//                                                                                        true
                                                                            //context?.showDialogSyncSuccess()
//                                                                                }
//                                                                            }
                                                                            shareViewModel.syncSuccess()
                                                                            onComplete()
                                                                            for (item in listNoteAdded) {
                                                                                if (item.deletedAll) {
                                                                                    viewModelTextNote.deleteEveryWhere(
                                                                                        item.ids!!
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        repository?.uploadFile(
                                                                            id,
                                                                            "iNote",
                                                                            noteData.toString()
                                                                        )
                                                                    } catch (e: Exception) {
                                                                        dialogProgress?.dismiss()
                                                                    }
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            dialogProgress?.dismiss()
                                                        }
                                                    }
                                                } else {
                                                    handler.postDelayed(this, 1)
                                                }
                                            }
                                        }, 0)

                                    }
                                }
                            }
                        }
                    } catch (e: UserRecoverableAuthIOException) {
                        dialogProgress?.dismiss()
                        try {
                            launcher.launch(e.intent)
                        } catch (e: Exception) {
                            Log.i("TAGJKKNKNKBKKBKB", "handleSyncData: $e")  // handle exception
                        }
                    }
                }

            } catch (e: IOException) {
                Log.i("TAGJKKNKNKBKKBKB", "handleSyncData: $e")
            }
        }
    }

//    fun showInterSuccess(
//        activity: Activity?, placement: String, call: (Int) -> Unit
//    ) {
//        val callback = object : InterstitialOnShowCallBack {
//            override fun onAdDismissedFullScreenContent() {
//                call.invoke(1)
//            }
//
//            override fun onAdFailedToShowFullScreenContent() {
//                call.invoke(1)
//            }
//
//            override fun onAdShowedFullScreenContent() {
//                call.invoke(1)
//            }
//
//            override fun onAdClick() {
//                super.onAdClick()
//                call.invoke(1)
//            }
//
//            override fun onAdImpression() {
//                super.onAdImpression()
//                call(1)
//            }
//        }
//    }

//    private fun showInterSyncSuccess() {
//        activity?.let { ac ->
//            if (prefUtil.isPremium) {
//                isAdsShowed = true
//            } else {
//                InterAdsManagers.loadInterMain(
//                    getString(R.string.no1_Inter_SyncSuccess_Click),
//                    ac
//                ) {
//                    showInterSuccess(ac, PlacementAds.PLACEMENT_MAIN_BACKUP_SUCCESS_CLICK) {
//                        isAdsShowed = true
//                        if (!isDialogSyncSuccessShow && it == 1) {
//                            isDialogSyncSuccessShow = true
//                            context?.showDialogSyncSuccess()
//                        }
//                    }
//                }
//            }
//        }
//    }

}