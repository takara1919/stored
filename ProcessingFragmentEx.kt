package com.tech.wallpaper.ui.preview

import android.util.Log
import com.tech.wallpaper.util.Constants
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

fun ProcessingFragment.download(
    path:String,
    link:String,
    onProgressUpdate: (progress: Int) -> Unit,
    onError: () -> Unit,
    onComplete: () -> Unit
) {

    val client = Constants.getUnsafeOkHttpClient()
    val request = okhttp3.Request.Builder()
        .url(link)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            activity?.runOnUiThread {
                ///e.printStackTrace()
                Log.d("TAVBDNVDNBVSN",e.message.toString())
                //Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                activity?.runOnUiThread {
                    handler.removeCallbacksAndMessages(null)
                    onError()
                }
            }
        }

        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) {
                return
            }
            val inputStream = response.body?.byteStream()
            val outputStream = FileOutputStream(File(path))

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            activity?.runOnUiThread {
                onComplete()
            }

            // println("File downloaded successfully: $pathSave")
        }
    })

}