package com.example.heremapex.views.viewmodel

import android.graphics.Color
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.heremapex.R
import com.google.android.gms.maps.model.LatLng
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.Image
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.*

class MapViewModel : ViewModel() {
    var lastLocation: MutableLiveData<LatLng> = MutableLiveData()
    var endpoint: MutableLiveData<LatLng> = MutableLiveData()
    var resultShortest: MutableLiveData<String> = MutableLiveData()
    var resultFastest: MutableLiveData<String> = MutableLiveData()
    var mapObjectList: MutableLiveData<ArrayList<MapObject>> = MutableLiveData()
    var transport: MutableLiveData<String> = MutableLiveData()


    private var mapRoute: MapRoute? = null
    var map: Map? = null

    init {
        transport.value = "Car"
        resultFastest.value = ""
        resultShortest.value = ""
        mapObjectList.value = ArrayList()
    }

    fun updateLastLocation(position: LatLng) {
        lastLocation.postValue(position)
    }

    fun createRoute(count: Int, transport: String) {
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
        val lastLocation = lastLocation.value
        val endPoint = endpoint.value
        val startPoint =
            RouteWaypoint(GeoCoordinate(lastLocation!!.latitude, lastLocation.longitude))
        val destination = RouteWaypoint(GeoCoordinate(endPoint!!.latitude, endPoint.longitude))
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
                        mapRoute = MapRoute(route)
                        mapRoute?.isManeuverNumberVisible = true
                        if (count == 2)
                            mapRoute?.color = Color.YELLOW
                        else mapRoute?.color = Color.BLUE
                        map?.addMapObject(mapRoute!!)
                        mapObjectList.value?.add(mapRoute!!)
                        map?.zoomTo(
                            route.boundingBox!!, Map.Animation.NONE,
                            Map.MOVE_PRESERVE_ORIENTATION
                        )
                        var informationFast: String
                        var information: String
                        if (count == 2) {
                            informationFast = ""
                            informationFast += "Đường đi nhanh nhất(Vàng): \n"
                            informationFast += "Thời gian: ${
                                formatTime(
                                    route.getTtaExcludingTraffic(
                                        Route.WHOLE_ROUTE
                                    )!!.duration
                                )
                            } \n"
                            informationFast += "Khoảng cách: ${mapRoute!!.route!!.length / 1000.0} km\n"
                            resultShortest.value = informationFast
                        } else if (count == 1) {
                            information = "Đường đi ngắn nhất(Xanh):\n"
                            information += "Thời gian: ${
                                formatTime(
                                    route.getTtaExcludingTraffic(
                                        Route.WHOLE_ROUTE
                                    )!!.duration
                                )
                            } \n"
                            information += "Khoảng cách: ${mapRoute!!.route!!.length / 1000.0} km\n"
                            resultFastest.value = information
                        }
                    } else {
                        Log.d("TAG", "onCalculateRouteFinished: $routingError")
                    }
                }
            })
    }

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

    fun cleanMap() {
        if (mapObjectList.value!!.isNotEmpty()) {
            while (mapObjectList.value!!.size > 1) {
                dropMarker()
            }
        }
    }

    fun reverseTwoPoint() {
        var temp = lastLocation.value
        lastLocation.value = endpoint.value
        endpoint.value = temp!!
        cleanMap()
    }

    fun searchAddress(address: String) {
        cleanMap()
        setMarker(lastLocation.value!!.latitude, lastLocation.value!!.longitude)
        val request = SearchRequest(address)
        request.setSearchCenter(map!!.center)
        request.execute { discoveryResultPage, errorCode ->
            if (discoveryResultPage?.items?.size!! > 0) {
                val arr = discoveryResultPage.items
                val item = arr[arr.size - 1] as PlaceLink
                addMarkerAtPlace(item)
                endpoint.value = LatLng(item.position!!.latitude, item.position!!.longitude)
                createRoute(1, transport.value!!)
                createRoute(2, transport.value!!)
            } else {
                Log.d("TAG", "searchAddress: $errorCode")
            }
        }
    }

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
        map?.addMapObject(mapMarker)
        mapObjectList.value?.add(mapMarker)
        map?.setCenter(
            GeoCoordinate(placeLink.position!!),
            Map.Animation.NONE
        )
        map?.zoomLevel = (map!!.maxZoomLevel + map!!.minZoomLevel) / 2
    }

    fun setMarker(lat: Double, long: Double) {
        val img = Image()
        try {
            img.setImageResource(R.drawable.location_pin)
        } catch (ex: Exception) {
            Log.d("TAG", "onCreate: ${ex.message}")
        }
        val mapMarker = MapMarker()
        mapMarker.icon = img
        mapMarker.coordinate = GeoCoordinate(lat, long)
        map?.addMapObject(mapMarker)
        mapObjectList.value?.add(mapMarker)
    }

    fun dropMarker() {
        if (mapObjectList.value!!.size > 1) {
            map!!.removeMapObject(mapObjectList.value!![mapObjectList.value?.size!! - 1])
            mapObjectList.value?.removeAt(mapObjectList.value?.size!! - 1)
        }
    }
    fun setCamera(){
        map?.setCenter(
            GeoCoordinate(lastLocation.value!!.latitude,lastLocation.value!!.longitude),
            Map.Animation.NONE
        )
        map?.zoomLevel = (map!!.maxZoomLevel + map!!.minZoomLevel) / 2
    }
}