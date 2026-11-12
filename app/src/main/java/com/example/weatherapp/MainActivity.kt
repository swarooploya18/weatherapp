package com.example.weatherapp

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.weatherapp.adapter.ForecastAdapter
import com.example.weatherapp.model.ForecastDay
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var tvCity: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvCondition: TextView
    private lateinit var tvWind: TextView
    private lateinit var tvWeatherIcon: TextView

    private lateinit var btnUnit: Button
    private lateinit var btnFavourite: Button
    private lateinit var btnFavorites: Button
    private lateinit var btnLogout: Button

    private lateinit var rvForecast: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val executor = Executors.newSingleThreadExecutor()

    private val prefs by lazy {
        getSharedPreferences("WeatherPrefs", MODE_PRIVATE)
    }

    private var latitude = 0.0
    private var longitude = 0.0
    private var isCelsius = true
    private var currentTemp = 0.0
    private var windSpeed = 0.0
    private var currentCityName = "Current Location"

    private var forecastList = listOf<ForecastDay>()

    // Save multiple favourite cities
    private fun saveFavouriteCities(cities: List<FavouriteCity>) {
        val array = JSONArray()

        cities.forEach { city ->
            val obj = JSONObject()
            obj.put("name", city.name)
            obj.put("latitude", city.latitude)
            obj.put("longitude", city.longitude)
            array.put(obj)
        }

        prefs.edit()
            .putString("favouriteCities", array.toString())
            .apply()
    }

    // Load favourite cities
    private fun loadFavouriteCities(): MutableList<FavouriteCity> {
        val cities = mutableListOf<FavouriteCity>()
        val data = prefs.getString("favouriteCities", null)

        if (data != null) {
            try {
                val array = JSONArray(data)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)

                    cities.add(
                        FavouriteCity(
                            obj.getString("name"),
                            obj.getDouble("latitude"),
                            obj.getDouble("longitude")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return cities
    }

    private fun updateFavouriteButton() {
        val saved = loadFavouriteCities().any {
            it.latitude == latitude && it.longitude == longitude
        }

        btnFavourite.text = if (saved) "★ Saved" else "☆ Save"
    }

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                getCurrentLocation()
            } else {
                showCachedWeather()
                Toast.makeText(
                    this,
                    "Location permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure user is authenticated
        if (FirebaseAuth.getInstance().currentUser == null) {
            openLogin()
            return
        }

        setContentView(R.layout.activity_main)

        tvCity = findViewById(R.id.tvCity)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvCondition = findViewById(R.id.tvCondition)
        tvWind = findViewById(R.id.tvWind)
        tvWeatherIcon = findViewById(R.id.tvWeatherIcon)

        btnUnit = findViewById(R.id.btnUnit)
        btnFavourite = findViewById(R.id.btnFavourite)
        btnFavorites = findViewById(R.id.btnFavorites)
        btnLogout = findViewById(R.id.btnLogout)

        rvForecast = findViewById(R.id.rvForecast)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        isCelsius = prefs.getBoolean("isCelsius", true)
        latitude = prefs.getFloat("latitude", 0f).toDouble()
        longitude = prefs.getFloat("longitude", 0f).toDouble()

        rvForecast.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(
                this,
                "Logged out successfully",
                Toast.LENGTH_SHORT
            ).show()
            openLogin()
        }

        btnUnit.setOnClickListener {
            isCelsius = !isCelsius
            prefs.edit().putBoolean("isCelsius", isCelsius).apply()
            updateWeatherDisplay()
        }

        btnFavourite.setOnClickListener {
            val cities = loadFavouriteCities()

            val existing = cities.find {
                it.latitude == latitude && it.longitude == longitude
            }

            if (existing != null) {
                cities.remove(existing)
                Toast.makeText(
                    this,
                    "Removed from favourites",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                cities.add(
                    FavouriteCity(
                        currentCityName,
                        latitude,
                        longitude
                    )
                )
                Toast.makeText(
                    this,
                    "Added to favourites",
                    Toast.LENGTH_SHORT
                ).show()
            }

            saveFavouriteCities(cities)
            updateFavouriteButton()
        }

        btnFavorites.setOnClickListener {
            showFavouriteCities()
        }

        swipeRefresh.setOnRefreshListener {
            if (currentCityName == "Current Location") {
                getCurrentLocation()
            } else {
                fetchWeather(latitude, longitude, currentCityName)
            }
        }

        getCurrentLocation()
    }

    private fun openLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Get device location
    private fun getCurrentLocation() {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            null
        ).addOnSuccessListener { location ->

            if (location != null) {
                latitude = location.latitude
                longitude = location.longitude
                currentCityName = "Current Location"
                fetchWeather(latitude, longitude, currentCityName)
            } else {
                swipeRefresh.isRefreshing = false
                showCachedWeather()
                Toast.makeText(
                    this,
                    "Unable to get location. Check GPS.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }.addOnFailureListener {
            swipeRefresh.isRefreshing = false
            showCachedWeather()
            Toast.makeText(
                this,
                "Location error: ${it.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Open-Meteo weather API
    private fun fetchWeather(
        lat: Double,
        lon: Double,
        cityName: String = "Current Location"
    ) {
        swipeRefresh.isRefreshing = true

        executor.execute {
            try {
                val apiUrl =
                    "https://api.open-meteo.com/v1/forecast" +
                            "?latitude=$lat" +
                            "&longitude=$lon" +
                            "&current=temperature_2m,weather_code,wind_speed_10m" +
                            "&daily=temperature_2m_max,temperature_2m_min" +
                            "&timezone=auto"

                val connection =
                    URL(apiUrl).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val response =
                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }

                connection.disconnect()

                runOnUiThread {
                    latitude = lat
                    longitude = lon
                    currentCityName = cityName
                    parseWeather(
                        response,
                        cityName == "Current Location"
                    )
                    swipeRefresh.isRefreshing = false
                }

            } catch (e: Exception) {
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(
                        this,
                        "Unable to fetch weather. Check internet.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Parse weather JSON
    private fun parseWeather(
        jsonString: String,
        saveCache: Boolean
    ) {
        try {
            val json = JSONObject(jsonString)
            val current = json.getJSONObject("current")

            currentTemp = current.getDouble("temperature_2m")
            windSpeed = current.getDouble("wind_speed_10m")

            val weatherCode = current.getInt("weather_code")

            tvCondition.text = getWeatherCondition(weatherCode)
            tvWeatherIcon.text = getWeatherIcon(weatherCode)

            val daily = json.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val minTemps = daily.getJSONArray("temperature_2m_min")

            val tempList = mutableListOf<ForecastDay>()

            for (i in 0 until dates.length()) {
                val date = dates.getString(i)

                val dayName = try {
                    val parsed = SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                    ).parse(date)

                    SimpleDateFormat(
                        "EEE",
                        Locale.getDefault()
                    ).format(parsed!!)
                } catch (e: Exception) {
                    date
                }

                tempList.add(
                    ForecastDay(
                        dayName,
                        maxTemps.getDouble(i),
                        minTemps.getDouble(i),
                        getWeatherIcon(weatherCode)
                    )
                )
            }

            forecastList = tempList
            tvCity.text = currentCityName

            updateWeatherDisplay()
            displayForecast()
            updateFavouriteButton()

            if (saveCache) {
                prefs.edit()
                    .putString("weatherCache", jsonString)
                    .putFloat("latitude", latitude.toFloat())
                    .putFloat("longitude", longitude.toFloat())
                    .apply()
            }

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to read weather data",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateWeatherDisplay() {
        val temperature = if (isCelsius) {
            currentTemp
        } else {
            (currentTemp * 9 / 5) + 32
        }

        val unit = if (isCelsius) "°C" else "°F"

        tvTemperature.text = "${temperature.toInt()}$unit"

        tvWind.text = String.format(
            Locale.getDefault(),
            "Wind Speed: %.1f km/h",
            windSpeed
        )

        btnUnit.text = if (isCelsius) {
            "Switch to °F"
        } else {
            "Switch to °C"
        }

        displayForecast()
    }

    private fun displayForecast() {
        val displayList = forecastList.map { day ->
            if (isCelsius) {
                day
            } else {
                day.copy(
                    maxTemp = day.maxTemp * 9 / 5 + 32,
                    minTemp = day.minTemp * 9 / 5 + 32
                )
            }
        }

        rvForecast.adapter = ForecastAdapter(displayList)
    }

    // Search city using Open-Meteo Geocoding API
    private fun searchCity(cityName: String) {
        executor.execute {
            try {
                val encoded = URLEncoder.encode(cityName, "UTF-8")

                val apiUrl =
                    "https://geocoding-api.open-meteo.com/v1/search" +
                            "?name=$encoded&count=10&language=en&format=json"

                val connection =
                    URL(apiUrl).openConnection() as HttpURLConnection

                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val response =
                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }

                connection.disconnect()

                val results = JSONObject(response).optJSONArray("results")

                runOnUiThread {
                    if (results == null || results.length() == 0) {
                        Toast.makeText(
                            this,
                            "No city found",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showCitySearchResults(results)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "City search failed. Check internet.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showCitySearchResults(results: JSONArray) {
        val cities = mutableListOf<FavouriteCity>()
        val names = mutableListOf<String>()

        for (i in 0 until results.length()) {
            val obj = results.getJSONObject(i)

            val name = obj.getString("name")
            val country = obj.optString("country", "")
            val admin = obj.optString("admin1", "")

            names.add(
                listOf(name, admin, country)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
            )

            cities.add(
                FavouriteCity(
                    name,
                    obj.getDouble("latitude"),
                    obj.getDouble("longitude")
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Select a City")
            .setItems(names.toTypedArray()) { _, which ->
                val selected = cities[which]
                val saved = loadFavouriteCities()

                val exists = saved.any {
                    it.latitude == selected.latitude &&
                            it.longitude == selected.longitude
                }

                if (exists) {
                    Toast.makeText(
                        this,
                        "City already exists",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    saved.add(selected)
                    saveFavouriteCities(saved)

                    Toast.makeText(
                        this,
                        "${selected.name} added to favourites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCityDialog() {
        val input = EditText(this)
        input.hint = "Enter city name"
        input.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val padding =
            (20 * resources.displayMetrics.density).toInt()

        layout.setPadding(padding, padding, padding, 0)
        layout.addView(input)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Favourite City")
            .setView(layout)
            .setPositiveButton("Search", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val city = input.text.toString().trim()

                    if (city.isNotEmpty()) {
                        dialog.dismiss()
                        searchCity(city)
                    } else {
                        input.error = "Enter a city name"
                    }
                }
        }

        dialog.show()
    }

    // Display favourite cities
    private fun showFavouriteCities() {
        val cities = loadFavouriteCities()
        val listView = ListView(this)

        val names = cities.map { it.name }

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            names
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Favourite Cities")
            .setView(listView)
            .setPositiveButton("Add City", null)
            .setNegativeButton("Close", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = cities[position]
            dialog.dismiss()

            fetchWeather(
                selected.latitude,
                selected.longitude,
                selected.name
            )
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val selected = cities[position]

            AlertDialog.Builder(this)
                .setTitle("Remove Favourite")
                .setMessage("Remove ${selected.name}?")
                .setPositiveButton("Remove") { _, _ ->
                    val updated = loadFavouriteCities()

                    updated.removeAll {
                        it.latitude == selected.latitude &&
                                it.longitude == selected.longitude
                    }

                    saveFavouriteCities(updated)
                    dialog.dismiss()
                    showFavouriteCities()
                }
                .setNegativeButton("Cancel", null)
                .show()

            true
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    dialog.dismiss()
                    showAddCityDialog()
                }
        }

        dialog.show()

        if (cities.isEmpty()) {
            Toast.makeText(
                this,
                "No favourite cities saved. Tap Add City.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown Weather"
        }
    }

    private fun getWeatherIcon(code: Int): String {
        return when (code) {
            0 -> "☀️"
            1, 2 -> "🌤️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            51, 53, 55 -> "🌦️"
            61, 63, 65, 66, 67 -> "🌧️"
            71, 73, 75, 77 -> "❄️"
            80, 81, 82 -> "🌧️"
            85, 86 -> "❄️"
            95, 96, 99 -> "⛈️"
            else -> "🌡️"
        }
    }

    // Offline cache
    private fun showCachedWeather() {
        val cached = prefs.getString("weatherCache", null)

        if (cached != null) {
            latitude = prefs.getFloat("latitude", 0f).toDouble()
            longitude = prefs.getFloat("longitude", 0f).toDouble()
            currentCityName = "Current Location"

            parseWeather(cached, false)

            Toast.makeText(
                this,
                "Showing saved weather data",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            tvCondition.text = "No internet or cached weather"
        }

        swipeRefresh.isRefreshing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
