package com.example.heremapex.views

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.heremapex.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.Image
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.*
import com.here.android.mpa.search.Location
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
        private var mMap: Map? = null
        private var mMapFragment: AndroidXMapFragment? = null
        private var mMapObjectList: ArrayList<MapObject> = ArrayList()
        private var resultSearchList: ArrayList<DiscoveryResult> = ArrayList()
        private var m_mapRoute: MapRoute? = null
        private var fusedLocationClient: FusedLocationProviderClient? = null
        private var lastLocation: LatLng? = null
        private var endPoint: LatLng? = null
        private var transport = "Car"
        private var information = ""
        private var isFirstClick = true
        private var isMarkerOnMap = false
    }


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
        //Tìm kiếm địa điểm và chỉ đường đến đó
        btnSearch.setOnClickListener {
            val query = edtQuerySearch.text.toString()
            cleanMap()
            val searchRequest = SearchRequest(query)
            searchRequest.setSearchCenter(mMap!!.center)
            searchRequest.execute(discoveryResultPageListener)
            btnReverse.visibility = View.VISIBLE
        }
        //Đảo ngược 2 vị trí với nhau
        btnReverse.setOnClickListener {
            Toast.makeText(this, "Đảo 2 vị trí", Toast.LENGTH_SHORT).show()
            information = ""
            var temp = lastLocation
            lastLocation = endPoint
            endPoint = temp
            mMap?.removeMapObject(m_mapRoute!!)
            m_mapRoute = null
            createRoute(1)
            createRoute(2)
        }
        //Đổi phương tiện di chuyển sang xe đạp
        btnTransportBicycle.setOnClickListener {
            Toast.makeText(this, "Di chuyển bằng xe đạp", Toast.LENGTH_SHORT).show()
            transport = "Bicycle"
            information = ""
            dropMarker()
            dropMarker()
            createRoute(1)
            createRoute(2)
        }
        //Đổi phương tiện di chuyển sang ô tô
        btnTransportCar.setOnClickListener {
            Toast.makeText(this, "Di chuyển bằng Ô tô", Toast.LENGTH_SHORT).show()
            transport = "Car"
            information = ""
            dropMarker()
            dropMarker()
            createRoute(1)
            createRoute(2)
        }
        //Đổi phương tiện di chuyển sang đi bộ
        btnTransportPedestrian.setOnClickListener {
            Toast.makeText(this, "Di chuyển bằng đi bộ", Toast.LENGTH_SHORT).show()
            transport = "Pedestrian"
            information = ""
            dropMarker()
            dropMarker()
            createRoute(1)
            createRoute(2)
        }
        //Xem thông tin lộ trình di chuyển
        btnResult.setOnClickListener {
            val string = when (transport) {
                "Car" -> "ô tô"
                "Bicycle" -> "xe đạp"
                else -> "đi bộ"
            }
            AlertDialog.Builder(this)
                .setTitle("Thông tin lộ trình di chuyển bằng $string")
                .setMessage(information)
                .setPositiveButton("OK") { _, _ -> }
                .show()
        }
    }

    //Tìm kiếm và vẽ 2 đường đi: ngắn nhất(Blue) và nhanh nhất(Yeallow)
    private val discoveryResultPageListener: ResultListener<DiscoveryResultPage> =
        ResultListener<DiscoveryResultPage> { discoveryResultPage, errorCode ->
            Log.d("TAG", "result:$discoveryResultPage ")
            if (errorCode == ErrorCode.NONE) {
                resultSearchList.clear()
                resultSearchList.addAll(discoveryResultPage!!.items)
                val item = resultSearchList[resultSearchList.size - 1] as PlaceLink
                cleanMap()
                addMarkerAtPlace(item)
                endPoint = LatLng(item.position!!.latitude, item.position!!.longitude)
                information = ""
                createRoute(1)
                createRoute(2)
            }
        }

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permissions != null) {
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

    /*  Thiết lập map gồm:
    * Hiển thị map tại vị trí hiện tại (yêu cầu GPS)
    * Click và giữ 1 điểm trên map
    * */
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
                    lastLocation = LatLng(it.latitude, it.longitude)
                    mMapFragment!!.init { error ->
                        if (error == OnEngineInitListener.Error.NONE) {
                            mMap = mMapFragment!!.map!!
                            mMap!!.setCenter(
                                GeoCoordinate(it.latitude, it.longitude),
                                Map.Animation.NONE
                            )
                            setMarker(it.latitude, it.longitude)

                            mMapFragment!!.mapGesture!!.addOnGestureListener(object :
                                MapGesture.OnGestureListener.OnGestureListenerAdapter() {
                                override fun onTapEvent(p0: PointF): Boolean {
                                    val geo = mMap!!.pixelToGeo(p0)
                                    if (!isFirstClick && isMarkerOnMap)
                                        dropMarker()
                                    isFirstClick = false
                                    isMarkerOnMap = true
                                    setMarker(geo!!.latitude, geo!!.longitude)
                                    return false
                                }

                                override fun onLongPressEvent(p0: PointF): Boolean {
                                    val geo = mMap!!.pixelToGeo(p0)
                                    reverseGeocode(geo!!)
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
    fun reverseGeocode(position: GeoCoordinate) {
        val request = ReverseGeocodeRequest(position)
        request.execute(ResultListener<Location> { t, errorCode ->
            val address = t?.address
            AlertDialog.Builder(this)
                .setTitle(address!!.text)
                .setMessage("lat: ${position.latitude} - long:${position.longitude}")
                .setPositiveButton(
                    "Tìm đường"
                ) { _, _ ->
                    endPoint = LatLng(position.latitude, position.longitude)
                    while (mMapObjectList.size > 1) {
                        mMap!!.removeMapObject(mMapObjectList[mMapObjectList.size - 1])
                        mMapObjectList.removeAt(mMapObjectList.size - 1)
                    }
                    setMarker(position.latitude, position.longitude)
                    information = ""
                    createRoute(1)
                    createRoute(2)
                    isMarkerOnMap = false
                }
                .setNegativeButton("OK") { _, _ ->
                    isMarkerOnMap = true
                }
                .show()
        })
    }

    //Xóa một điểm hoặc một đường trên map
    private fun dropMarker() {
        if (mMapObjectList.size > 1) {
            mMap!!.removeMapObject(mMapObjectList[mMapObjectList.size - 1])
            mMapObjectList.removeAt(mMapObjectList.size - 1)
        }
    }

    //Xóa toàn bộ các điểm hoặc đường đã vẽ ở trên map
    private fun cleanMap() {
        if (mMapObjectList.isNotEmpty()) {
            mMap?.removeMapObjects(mMapObjectList)
            mMapObjectList.clear()
        }
    }

    //Đánh dấu 1 điểm dựa vào vị trí latitude và longitude
    private fun setMarker(lat: Double, long: Double) {
        val img = Image()
        try {
            img.setImageResource(R.drawable.location_pin)
        } catch (ex: Exception) {
            Log.d("TAG", "onCreate: ${ex.message}")
        }
        val mapMarker = MapMarker()
        mapMarker.icon = img
        mapMarker.coordinate = GeoCoordinate(lat, long)
        mMap?.addMapObject(mapMarker)
        mMapObjectList.add(mapMarker)
    }

    //Đánh dấu 1 điểm dựa vào PlaceLink
    private fun addMarkerAtPlace(placeLink: PlaceLink) {
        val img = Image()
        try {
            img.setImageResource(R.drawable.location_pin)
        } catch (ex: Exception) {
            Log.d("TAG", "onCreate: ${ex.message}")
        }
        val mapMarker = MapMarker()
        mapMarker.icon = img
        mapMarker.coordinate = GeoCoordinate(placeLink.position!!)
        mMap?.addMapObject(mapMarker)
        mMapObjectList.add(mapMarker)
        mMap!!.setCenter(
            GeoCoordinate(placeLink.position!!),
            Map.Animation.NONE
        )
        mMap!!.zoomLevel = (mMap!!.maxZoomLevel + mMap!!.minZoomLevel) / 2
    }

    //Vẽ đường đi giữa 2 điểm
    private fun createRoute(count: Int) {
        val coreRouter = CoreRouter()
        val routePlan = RoutePlan()
        val routeOptions = RouteOptions()
        if (transport != "Car") {
            if (transport == "Bicycle") {
                routeOptions.transportMode = RouteOptions.TransportMode.BICYCLE
            } else if (transport == "Pedestrian") {
                routeOptions.transportMode = RouteOptions.TransportMode.PEDESTRIAN
            }
        } else {
            routeOptions.transportMode = RouteOptions.TransportMode.CAR
        }
        routeOptions.setHighwaysAllowed(false)
        if (count == 1)
            routeOptions.routeType = RouteOptions.Type.SHORTEST
        else if (count == 2)
            routeOptions.routeType = RouteOptions.Type.FASTEST
        routeOptions.routeCount = 1
        routePlan.routeOptions = routeOptions
        val startPoint =
            RouteWaypoint(GeoCoordinate(lastLocation!!.latitude, lastLocation!!.longitude))
        val destination = RouteWaypoint(GeoCoordinate(endPoint!!.latitude, endPoint!!.longitude))
        routePlan.addWaypoint(startPoint)
        routePlan.addWaypoint(destination)
        coreRouter.calculateRoute(routePlan,
            object : Router.Listener<List<RouteResult>, RoutingError> {
                override fun onProgress(i: Int) {
                }

                override fun onCalculateRouteFinished(
                    routeResults: List<RouteResult>,
                    routingError: RoutingError
                ) {
                    if (routingError == RoutingError.NONE) {
                        val route = routeResults[0].route
                        m_mapRoute = MapRoute(route)
                        m_mapRoute!!.isManeuverNumberVisible = true
                        if (count == 2)
                            m_mapRoute!!.color = Color.YELLOW
                        mMap?.addMapObject(m_mapRoute!!)
                        mMapObjectList.add(m_mapRoute!!)
                        mMap?.zoomTo(
                            route.boundingBox!!, Map.Animation.NONE,
                            Map.MOVE_PRESERVE_ORIENTATION
                        )
                        if (count == 2) information += "Trường hợp nhanh nhất: \n"
                        information += "Thời gian: ${formatTime(route.getTtaExcludingTraffic(Route.WHOLE_ROUTE)!!.duration)} \n"
                        information += "Khoảng cách: ${m_mapRoute!!.route!!.length / 1000.0} km\n"
                        tvResult.text = information
                    } else {
                        Log.d("TAG", "onCalculateRouteFinished: $routingError")
                    }
                }
            })
        btnTransportCar.visibility = View.VISIBLE
        btnTransportBicycle.visibility = View.VISIBLE
        btnTransportPedestrian.visibility = View.VISIBLE
        btnReverse.visibility = View.VISIBLE
        if (count == 2) {
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d("TAG", "onCalculateRouteFinished: $information")
            }, 1500)
        }


    }

    //Định dạng thời gian
    private fun formatTime(s: Int): String {
        var result = ""
        var second = s
        val hours = second / 3600
        second -= hours * 3600
        val minutes = second / 60
        second -= minutes * 60
        if (hours > 0) {
            result += "$hours giờ "
        }
        if (minutes > 0) {
            result += " $minutes phút"
        }

        return result
    }
}