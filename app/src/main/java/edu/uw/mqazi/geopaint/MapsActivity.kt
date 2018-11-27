package edu.uw.mqazi.geopaint

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.FileProvider
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AlertDialog
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ShareActionProvider
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import org.json.JSONException
import org.xdty.preference.colorpicker.ColorPickerDialog
import java.io.*
import java.lang.StringBuilder

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, LocationListener {


    private val TAG = "MAPSACTIVITY"

    private val LAST_LOCATION_REQUEST_CODE = 1
    private var LOCATION_REQUEST_CODE = 2
    private var WRITE_REQUEST_CODE = 3
    private val PREF_PEN_KEY = "pref_pen"
    private var CUSTOM_COLOR = "custom_color"

    private lateinit var mMap: GoogleMap
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var line: Polyline
    private lateinit var polyline: PolylineOptions
    private lateinit var polylineList: MutableList<Polyline>
    private lateinit var convertedList: List<PolylineOptions>
    private lateinit var fileName: String
    private lateinit var geojson: String

    private var mSelectedColor: Int = 0
    private lateinit var mColors: IntArray

    private lateinit var points: ArrayList<LatLng>


    companion object {
        val PREF_FILE_KEY = "pref_file"
        val CONVERTED_KEY = "converted"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        mColors = resources.getIntArray(R.array.default_rainbow)
        mSelectedColor = ContextCompat.getColor(this, R.color.flamingo);


        polylineList = mutableListOf()
        convertedList = listOf()

        points = arrayListOf()

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        if (sharedPreferences.getString(PREF_FILE_KEY, null) == null) {
            if (!sharedPreferences.getBoolean(PREF_PEN_KEY, false)) {
                sharedPreferences.edit().putBoolean(PREF_PEN_KEY, false).commit()
            }


            var input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
            }
            var builder = AlertDialog.Builder(this).apply {
                setTitle("Enter the file name to store the art")
                setView(input)
                setPositiveButton("SAVE") { dialog, which ->
                    fileName = input.text.toString()
                    sharedPreferences.edit().putString(PREF_FILE_KEY, fileName).commit()
                }
                setNegativeButton("CANCEL") { dialog, which ->
                    dialog.cancel()
                }
            }

            builder.show()

        } else {
            var getFile = Uri.fromFile(
                File(
                    getExternalFilesDir(null).absolutePath + File.separator + sharedPreferences.getString(
                        PREF_FILE_KEY, null
                    ) + ".geojson"
                )
            )
            var readFile = File(getFile.path)

            try {
                var inputStream = FileInputStream(File(readFile.absolutePath))
                if (inputStream != null) {
                    var inputStreamReader = InputStreamReader(inputStream)
                    var bufferedReader = BufferedReader(inputStreamReader)
                    var result = ""

                    var stringBuilder = StringBuilder()

                    result = bufferedReader.readLine()

                    while (result != null) {
                        stringBuilder.append(result)
                        result = bufferedReader.readLine()

                    }

                    inputStream.close()
                    geojson = stringBuilder.toString()
                    convertedList = convertFromGeoJson(geojson)

                }
            } catch (e: NoSuchFieldException) {
                Log.v(TAG, "File not found")
            } catch (ioe: IOException) {
                Log.v(TAG, Log.getStackTraceString(ioe))
            } catch (e: JSONException) {
                e.printStackTrace()
            }


        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getLastLocation()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    override fun onStart() {
        super.onStart()
        startLocationUpdates()
    }

    override fun onStop() {
        Log.v("STOPPED", "location removed")
        super.onStop()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        if (convertedList != null) {
            for (item in convertedList.indices) {
                var oldLine = convertedList.get(item)
                mMap.addPolyline(oldLine)
            }
        }
    }


    fun getLastLocation() {

        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {

            //access last location, asynchronously!
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                displayLocation(location)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LAST_LOCATION_REQUEST_CODE
            )
        }
    }


    fun startLocationUpdates() {
        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest().apply {
                interval = 10000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult?) {
                    locationResult ?: return
                    displayLocation(locationResult.locations[0])
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }

    }

    fun displayLocation(location: Location?) {
        if (location != null) {
            var lat = location.latitude
            var longt = location.longitude
            var latLong = LatLng(lat, longt)
            mMap.addMarker(MarkerOptions().position(latLong).title("Current Location"))
            polyline = PolylineOptions().width(20.toFloat()).color(sharedPreferences.getInt(CUSTOM_COLOR, 0))
            polyline.add(latLong)


            points.add(latLong)

            redrawLine()

            mMap.addPolyline(polyline)

            mMap.addMarker(MarkerOptions().position(latLong).title("Current Location"))
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLong))
        }

    }

    fun redrawLine() {
        mMap.clear()
        var options = PolylineOptions().width(20.toFloat()).color(sharedPreferences.getInt(CUSTOM_COLOR, Color.RED))
            .geodesic(true)
        for (item in points.indices) {
            var point = points.get(item)
            options.add(point)

        }
        line = mMap.addPolyline(options)

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                }
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }

            WRITE_REQUEST_CODE -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                }
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        }


    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.toggle_pen -> {
                togglePen(item)
                return true
            }
            R.id.custom_color -> {
                chooseColor()
                return true
            }
            R.id.share -> {
                share(item)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }


    fun togglePen(item: MenuItem) {
        if (item != null) {
            if (sharedPreferences.getBoolean(PREF_PEN_KEY, true)) {
                item.title = "Lower Pen"
                sharedPreferences.edit().putBoolean(PREF_PEN_KEY, false).commit()
            } else {
                polyline =
                        PolylineOptions().width(20.toFloat()).color(sharedPreferences.getInt(CUSTOM_COLOR, Color.RED))
                item.title = "Raise Pen"
                sharedPreferences.edit().putBoolean(PREF_PEN_KEY, true).commit()
            }

        }
    }

    fun chooseColor() {
        val dialog = ColorPickerDialog.newInstance(
            R.string.color_picker_default_title,
            mColors,
            sharedPreferences.getInt(CUSTOM_COLOR, Color.RED),
            5, // Number of columns
            ColorPickerDialog.SIZE_SMALL,
            true // True or False to enable or disable the serpentine effect
        )

        dialog.setOnColorSelectedListener { color ->
            sharedPreferences.edit().putInt(CUSTOM_COLOR, color).commit()
        }

        dialog.show(getFragmentManager(), "color_dialog");

    }

    fun share(item: MenuItem) {
        var mSharedProvider = MenuItemCompat.getActionProvider(item) as ShareActionProvider;
        var sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
        }

        var shareFile = File(
            getExternalFilesDir(null).absolutePath + File.separator + sharedPreferences.getString(
                PREF_FILE_KEY, null
            ) + ".geojson"
        )

        var contentUri = FileProvider.getUriForFile(applicationContext, "edu.uw.mqazi.geopaing.fileprovider", shareFile)
        sendIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
        mSharedProvider.setShareIntent(sendIntent)
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            var lat = location.latitude
            var lng = location.longitude
            var latLng = LatLng(lat, lng)


            if (sharedPreferences.getBoolean(PREF_PEN_KEY, true)) {
                if (polyline == null) {
                    polyline = PolylineOptions()
                        .width(25.toFloat())
                        .color(sharedPreferences.getInt(CUSTOM_COLOR, Color.BLUE));
                }
                polyline.add(latLng);
                line = mMap.addPolyline(polyline);
                if (polylineList != null) {
                    polylineList.clear();
                }
                polylineList.add(line);
                var converted = convertToGeoJson(polylineList);
                intent = Intent(this@MapsActivity, MapsSavingService::class.java);
                intent.putExtra(CONVERTED_KEY, converted);
                var permissionCheck =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                    startService(intent);
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        WRITE_REQUEST_CODE
                    );
                }
            }

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }
    }


}
