package com.example.routeappyes

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import java.text.DecimalFormat
import org.json.JSONObject





class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var originLatitude: Double = 28.5021359
    private var originLongitude: Double = 77.4054901
    private var destinationLatitude: Double = 28.5151087
    private var destinationLongitude: Double = 77.3932163

    var listLoc1:MutableList<Double> = mutableListOf<Double>()
    var listLoc2:MutableList<Double> = mutableListOf<Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)



        listLoc1.add(originLatitude)
        listLoc1.add(originLongitude)
        listLoc2.add(destinationLatitude)
        listLoc2.add(destinationLongitude)



        // Fetching API_KEY which we wrapped
        val ai: ApplicationInfo = applicationContext.packageManager
            .getApplicationInfo(applicationContext.packageName, PackageManager.GET_META_DATA)
        val value = ai.metaData["com.google.android.geo.API_KEY"]
        val apiKey = value.toString()




        // Initializing the Places API with the help of our API_KEY
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }



        // Map Fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)



        fetchLatLon(apiKey,R.id.autocomplete_fragment1,"one")
        fetchLatLon(apiKey,R.id.autocomplete_fragment2,"two")



        val gd =findViewById<Button>(R.id.directions)


        gd.setOnClickListener {
            mapFragment.getMapAsync {
                mMap = it
                mMap.clear()
                val originLocation = LatLng(listLoc1.get(0),listLoc1.get(1))
                mMap.addMarker(MarkerOptions().position(originLocation))
                val destinationLocation = LatLng(listLoc2.get(0), listLoc2.get(1))
                mMap.addMarker(MarkerOptions().position(destinationLocation))
                val urll = getDirectionURL(originLocation, destinationLocation, apiKey)
                GetDirection(urll).execute()
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(originLocation, 14F))


                val locationJsonObject = JSONObject()
                locationJsonObject.put("origin", "${listLoc1.get(0)},${listLoc1.get(1)}")
                locationJsonObject.put("destination", "${listLoc2.get(0)},${listLoc2.get(1)}")
                LatlngCalc(apiKey,locationJsonObject)


            }
        }




    }






    @Throws(JSONException::class)
    private fun LatlngCalc(apiKey: String,locationJsonObject: JSONObject) {
        val queue: RequestQueue = Volley.newRequestQueue(this)
        val url = "https://maps.googleapis.com/maps/api/distancematrix/" +
                "json?origins=" + locationJsonObject.getString("origin") + "&destinations=" + locationJsonObject.getString("destination") + "&mode=driving&" +
                "language=en-EN&sensor=false" + "&key=" +apiKey
        val jsonObjectRequest = object : JsonObjectRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            Response.Listener {
                print("********************************************$it*****************************")

                var json1=it.getJSONArray("rows")

                    var json2=json1.getJSONObject(0)

                    var json3=json2.getJSONArray("elements")
                var json4=json3.getJSONObject(0)
                var json5=json4.getJSONObject("distance")

                var distance=json5.getString("text")
                var json6=json4.getJSONObject("duration")
                var duration=json6.getString("text")
                findViewById<TextView>(R.id.dist).text="Distance: "+distance.toString()+" Duration: "+duration.toString()

            },
            Response.ErrorListener {

                Toast.makeText(
                    this as Context,
                    "Some Error occurred!!!",
                    Toast.LENGTH_SHORT
                ).show()

            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()

                headers["Content-type"] = "application/json"

                return headers
            }
        }

        queue.add(jsonObjectRequest)
    }




    private fun getDirectionURL(origin:LatLng, dest:LatLng, secret: String) : String{
        return "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&sensor=false" +
                "&mode=driving" +
                "&key=$secret"
    }



    @SuppressLint("StaticFieldLeak")
    private inner class GetDirection(val url : String) : AsyncTask<Void, Void, List<List<LatLng>>>(){
        override fun doInBackground(vararg params: Void?): List<List<LatLng>> {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val data = response.body!!.string()

            val result =  ArrayList<List<LatLng>>()
            try{
                val respObj = Gson().fromJson(data,MapData::class.java)
                val path =  ArrayList<LatLng>()
                for (i in 0 until respObj.routes[0].legs[0].steps.size){
                    path.addAll(decodePolyline(respObj.routes[0].legs[0].steps[i].polyline.points))
                }
                result.add(path)
            }catch (e:Exception){
                e.printStackTrace()
            }
            return result
        }

        override fun onPostExecute(result: List<List<LatLng>>) {
            val lineoption = PolylineOptions()
            for (i in result.indices){
                lineoption.addAll(result[i])
                lineoption.width(10f)
                lineoption.color(Color.MAGENTA)
                lineoption.geodesic(true)
            }
            mMap.addPolyline(lineoption)
        }
    }





    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val latLng = LatLng((lat.toDouble() / 1E5),(lng.toDouble() / 1E5))
            poly.add(latLng)
        }
        return poly
    }




    override fun onMapReady(p0: GoogleMap) {
        mMap = p0!!
        mMap.clear()

    }





    fun fetchLatLon(apiKey:String,autocomplete_fragment:Int,from:String)
    {


        // Initializing the Places API
        // with the help of our API_KEY
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        // Initialize Autocomplete Fragments
        // from the main activity layout file
        val autocompleteSupportFragment1 = supportFragmentManager.findFragmentById(autocomplete_fragment) as AutocompleteSupportFragment?

        // Information that we wish to fetch after typing
        // the location and clicking on one of the options
        autocompleteSupportFragment1!!.setPlaceFields(
            listOf(

                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.PHONE_NUMBER,
                Place.Field.LAT_LNG,
                Place.Field.OPENING_HOURS,
                Place.Field.RATING,
                Place.Field.USER_RATINGS_TOTAL

            )
        )


        // Display the fetched information after clicking on one of the options
        autocompleteSupportFragment1.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {


                val latlng = place.latLng
                val latitude = latlng?.latitude
                val longitude = latlng?.longitude



                if(from.equals("one"))
                {

                    listLoc1.set(0,latitude!!)
                    listLoc1.set(1,longitude!!)

                }
                if(from.equals("two"))
                {
                    listLoc2.set(0,latitude!!)
                    listLoc2.set(1,longitude!!)

                }

            }

            override fun onError(status: Status) {
                Toast.makeText(applicationContext,"Some error occurred", Toast.LENGTH_SHORT).show()
            }
        })
    }




}