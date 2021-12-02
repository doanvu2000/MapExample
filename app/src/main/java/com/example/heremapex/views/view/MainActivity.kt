package com.example.heremapex.views.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.example.heremapex.R
import com.example.heremapex.views.viewmodel.MapViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.mapping.*
import com.here.android.mpa.search.*
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_ASK_PERMISSIONS = 1
        private val RUNTIME_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        private var mMapFragment: AndroidXMapFragment? = null
        private var fusedLocationClient: FusedLocationProviderClient? = null
        private var transport = "Car"
        private var information = ""
        private var informationFast = ""
        private var isFirstClick = true
        private var isMarkerOnMap = false
    }

    lateinit var viewModel: MapViewModel

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (hasPermissions(this, *RUNTIME_PERMISSIONS)) {
            setUpMapFragment()
        } else {
            ActivityCompat
                .requestPermissions(this, RUNTIME_PERMISSIONS, REQUEST_CODE_ASK_PERMISSIONS)
        }
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]
        observe()
        buttonClick()
    }

    private fun observe() {
        viewModel.transport.observe(this, {
            transport = it
            viewModel.cleanMap()
        })
        viewModel.resultShortest.observe(this, {
            information = it
        })
        viewModel.resultFastest.observe(this, {
            informationFast = it
        })
    }

    private fun buttonClick() {
        btnMyLocation.setOnClickListener {
            viewModel.setCamera()
        }
        btnSearch.setOnClickListener {
            val query = edtQuerySearch.text.toString()
            viewModel.cleanMap()
            viewModel.searchAddress(query)
            showActionButton()
        }
        //Đảo ngược 2 vị trí với nhau
        btnReverse.setOnClickListener {
            Toast.makeText(this, "Đảo 2 vị trí", Toast.LENGTH_SHORT).show()
            viewModel.reverseTwoPoint()
            viewModel.createRoute(1, transport)
            viewModel.createRoute(2, transport)
        }
        //Đổi phương tiện di chuyển sang xe đạp
        btnTransportBicycle.setOnClickListener {
            Toast.makeText(this, "Di chuyển bằng xe đạp", Toast.LENGTH_SHORT).show()
            viewModel.transport.value = "Bicycle"
            viewModel.createRoute(1, transport)
            viewModel.createRoute(2, transport)
        }
        //Đổi phương tiện di chuyển sang ô tô
        btnTransportCar.setOnClickListener {
            Toast.makeText(this, "Di chuyển bằng Ô tô", Toast.LENGTH_SHORT).show()
            viewModel.transport.value = "Car"
            viewModel.createRoute(1, transport)
            viewModel.createRoute(2, transport)
        }
        //Đổi phương tiện di chuyển sang đi bộ
        btnTransportPedestrian.setOnClickListener {
            Toast.makeText(this, "Di chuyển bằng đi bộ", Toast.LENGTH_SHORT).show()
            viewModel.transport.value = "Pedestrian"
            viewModel.createRoute(1, transport)
            viewModel.createRoute(2, transport)
        }
        btnResult.setOnClickListener {
            val string = when (transport) {
                "Car" -> "Ô tô"
                "Bicycle" -> "Xe đạp"
                else -> "Đi bộ"
            }
            AlertDialog.Builder(this)
                .setTitle("Thông tin lộ trình di chuyển bằng $string: ")
                .setMessage(information + "\n" + informationFast)
                .setPositiveButton("OK") { _, _ -> }
                .show()
        }
    }

    private fun showActionButton() {
        btnReverse.visibility = View.VISIBLE
        btnTransportCar.visibility = View.VISIBLE
        btnTransportBicycle.visibility = View.VISIBLE
        btnTransportPedestrian.visibility = View.VISIBLE
    }

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_ASK_PERMISSIONS -> {
                var index = 0
                while (index < permissions.size) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        if (!ActivityCompat
                                .shouldShowRequestPermissionRationale(this, permissions[index])
                        ) {
                            Toast.makeText(
                                this, "Required permission " + permissions[index]
                                        + " not granted. "
                                        + "Please go to settings and turn on for sample app",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this, "Required permission " + permissions[index]
                                        + " not granted", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    index++
                }
                setUpMapFragment()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun setUpMapFragment() {
        mMapFragment =
            supportFragmentManager.findFragmentById(R.id.mapfragment) as AndroidXMapFragment?
        if (mMapFragment != null) {

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf((Manifest.permission.ACCESS_FINE_LOCATION)), 1
                )
                return
            }
            fusedLocationClient!!.lastLocation.addOnSuccessListener {
                if (it != null) {
                    viewModel.updateLastLocation(LatLng(it.latitude, it.longitude))
                    mMapFragment!!.init { error ->
                        if (error == OnEngineInitListener.Error.NONE) {
                            viewModel.map = mMapFragment!!.map!!
                            viewModel.setCamera()
                            viewModel.setMarker(it.latitude, it.longitude)
                            mMapFragment!!.mapGesture!!.addOnGestureListener(object :
                                MapGesture.OnGestureListener.OnGestureListenerAdapter() {
                                override fun onTapEvent(p0: PointF): Boolean {
                                    val geo = viewModel.map!!.pixelToGeo(p0)
                                    if (!isFirstClick && isMarkerOnMap)
                                        viewModel.dropMarker()
                                    isFirstClick = false
                                    isMarkerOnMap = true
                                    viewModel.setMarker(geo!!.latitude, geo.longitude)
                                    return false
                                }

                                override fun onLongPressEvent(p0: PointF): Boolean {
                                    val geo = viewModel.map!!.pixelToGeo(p0)
                                    reverseGeocodeToLocation(geo!!)
                                    return false
                                }
                            }, 100, true)
                        }
                    }
                }
            }
        }

    }
    //Hiển thị dialog thông tin của điểm đang giữ gồm: Địa chỉ, latitude - longitude
    fun reverseGeocodeToLocation(position: GeoCoordinate) {
        val request = ReverseGeocodeRequest(position)
        request.execute { t, _ ->
            val address = t?.address
            AlertDialog.Builder(this)
                .setTitle(address!!.text)
                .setMessage("lat: ${position.latitude} - long:${position.longitude}")
                .setPositiveButton(
                    "Tìm đường"
                ) { _, _ ->
                    viewModel.endpoint.value = LatLng(position.latitude, position.longitude)
                    viewModel.cleanMap()
                    viewModel.setMarker(position.latitude, position.longitude)
                    viewModel.createRoute(1, transport)
                    viewModel.createRoute(2, transport)
                    isMarkerOnMap = false
                    showActionButton()
                }
                .setNegativeButton("OK") { _, _ ->
                    isMarkerOnMap = true
                }
                .show()
        }
    }
}