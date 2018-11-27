package edu.uw.mqazi.geopaint

import android.app.IntentService
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.preference.PreferenceManager
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter

class MapsSavingService : IntentService("MapSavingService") {
    private val TAG = "MapSavingSerivce"
    private lateinit var mHandler: Handler


    override fun onCreate() {
        mHandler = Handler()
        super.onCreate()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(p0: Intent?) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val saveFile = sharedPreferences.getString(MapsActivity.PREF_FILE_KEY, null)!! + ".geojson"
        val converted = p0!!.getStringExtra(MapsActivity.CONVERTED_KEY)

        if(isExternalStorageReadable()) {
            saveToExternalStorage(saveFile, converted)
        }
    }
}

fun saveToExternalStorage(FILE_NAME: String, textEntry: String) {
    try {
        val dir = getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!dir.exists()) { dir.mkdirs() } //make dir if doesn't otherwise exist (pre-19)
        val file = File(dir, FILE_NAME)

        val out = PrintWriter(FileWriter(file, true))
        out.println(textEntry)
        out.close()
    } catch (ioe: IOException) {
        Log.d(TAG, Log.getStackTraceString(ioe))
    }

}

fun isExternalStorageWritable(): Boolean {
    return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
}

fun isExternalStorageReadable(): Boolean {
    return Environment.getExternalStorageState() in
            setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
}
