package edu.tcu.akumar.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.internal.FallbackServiceBroker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import edu.tcu.akumar.weather.databinding.ActivityMainBinding
import edu.tcu.akumar.weather.model.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File.separator
import java.text.DateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    //Tracker variables
    var minute: Int = 0
    var itr: Int = 0
    var waitTime: Long = 2000

    private lateinit var binding: ActivityMainBinding //relates to the activity_main xml folder.
    private lateinit var view: View
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var dialog: Dialog
    private lateinit var service: WeatherService
    private lateinit var weatherResponse: WeatherResponse
    //lateinit only applies to var because you will assign it later
    val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {

                Snackbar.make(
                    view,
                    R.string.location_permission_granted,
                    Snackbar.LENGTH_SHORT
                ).show()
                updateLocationAndWeatherRepeatedly()

            } else {

                Snackbar.make(
                    view,
                    R.string.location_permission_denied,
                    Snackbar.LENGTH_LONG
                ).show()

            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Step4: requestLocationPermission()
        binding = ActivityMainBinding.inflate(layoutInflater)
        view = binding.root
        setContentView(view)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val retrofit = Retrofit.Builder().baseUrl(getString(R.string.base_url))
            .addConverterFactory(GsonConverterFactory.create()).build()
        service = retrofit.create(WeatherService::class.java)

        requestLocationPermission()



        //binding.windCv -> capitalizes the first word after an underscore/dot wind.cv
        //retrofit -> Connect to the internet
        //Gson -> Convert Json data to the let file
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                updateLocationAndWeatherRepeatedly()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                val snackbar = Snackbar.make(
                    view,
                    R.string.location_permission_required,
                    Snackbar.LENGTH_INDEFINITE
                )

                snackbar.setAction("OK") {
                    requestPermissionLauncher.launch(
                        Manifest.permission.ACCESS_COARSE_LOCATION)

                }.show()
            }
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )

            }
        }

    }


    private fun updateLocationAndWeatherRepeatedly(){
        //update location every minute (60,000 ms)
        //coRoutine involved
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                withContext(Dispatchers.Main){ updateLocationAndWeather() }
                delay(15000)
            }
        }
    }

    private suspend fun updateLocationAndWeather(){
        //suspend goes into both update locations
        when(PackageManager.PERMISSION_GRANTED){
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> {

                showInProgress()
                val cancellationTokenSource = CancellationTokenSource()
                var taskSuccesful = false
                println("hiii")
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token).addOnSuccessListener {
                    taskSuccesful = true

                    if(it != null) {

                        updateWeather(it)}
                    else {

                        displayUpdateFailed()
                    }
                }



                withContext(Dispatchers.IO) {
                    //1st wait is 2 sec, 2nd is 4, 3rd wait is 6, 4th wait is 8, 5th wait is for 10sec and stays at 10sec until a location grab is successful and timer is reset to 2 seconds
                    //Can test with airplane mode
                    itr++

                    if(itr <= 1)
                    {
                        waitTime = 2000
                    }
                    else if(itr == 2)
                    {
                        waitTime = 4000
                    }
                    else if(itr == 3)
                    {
                        waitTime = 6000
                    }
                    else if(itr == 4)
                    {
                        waitTime = 8000
                    }
                    else if(itr >= 5)
                    {
                        waitTime = 10000
                    }

                    delay(waitTime)
                    if(!taskSuccesful) {

                        cancellationTokenSource.cancel()
                        withContext(Dispatchers.Main){
                            displayUpdateFailed()
                        }
                    }
                    else
                    {
                        waitTime = 2000
                        itr = 0
                    }
                }
            }
        }
    }



    private fun showInProgress(){
        dialog = Dialog(this)
        dialog.setContentView(R.layout.in_progress)
        dialog.setCancelable(false)
        dialog.show()
    }

    private fun updateWeather(location: Location){
        val call: Call<WeatherResponse> = service.getWeather(
            location.latitude,
            location.longitude,
            getString(R.string.appid),
            "imperial"
        )

        call.enqueue(
            object: Callback<WeatherResponse>{

                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>)
                {

                    //displayWeather()
                    println(response.body())
                    val weatherResponseNullable = response.body()
                    if (weatherResponseNullable != null)
                    {
                        weatherResponse = weatherResponseNullable
                        displayWeather()
                    }
                    else
                    {
                        displayUpdateFailed()
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable)
                {
                    displayUpdateFailed()
                }
            }
        )

    }

    private fun displayWeather(){
        minute = 0
        binding.connectionTv.text = "Updated just now"
        var min: Float = (1.8 * (weatherResponse.main.temp_min - 273) + 32).toFloat()
        var max: Float = (1.8 * (weatherResponse.main.temp_max - 273) + 32).toFloat()
        dialog.dismiss()
        binding.cityTv.text = weatherResponse.name


        binding.temperatureTv.text = getString(R.string.temperature).format((1.8 * (weatherResponse.main.temp - 273) + 32).toFloat())
        binding.descriptionTv.text = getString(R.string.description).format(weatherResponse.weather.get(0).description.capitalizeWords, max, min)
        binding.sunDataTv.text = getString(R.string.sun_data).format(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(weatherResponse.sys.sunrise.toLong()*1000)), DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(weatherResponse.sys.sunset.toLong()*1000)))

        binding.windDataTv.text = getString(R.string.wind_data).format((weatherResponse.wind.speed*2.237).toFloat(), weatherResponse.wind.deg, (weatherResponse.wind.gust*2.237.toFloat()))
        if(weatherResponse.rain == null && weatherResponse.snow == null)
        {
            binding.precipitationDataTv.text = getString(R.string.precipitation_data).format(weatherResponse.main.humidity, weatherResponse.clouds.all)
        }
        else if(weatherResponse.rain != null)
        {
            binding.precipitationDataTv.text = getString(R.string.rain).format(weatherResponse.rain!!.one_h.toFloat(), weatherResponse.rain!!.three_h.toFloat())
        }
        else
        {
            binding.precipitationDataTv.text = getString(R.string.snow).format(weatherResponse.snow!!.one_h.toFloat(), weatherResponse.snow!!.three_h.toFloat())
        }
        binding.otherDataTv.text = getString(R.string.other_data).format((1.8 * (weatherResponse.main.temp - 273) + 32).toFloat(), (weatherResponse.visibility/1609).toFloat(), (weatherResponse.main.pressure*0.0295333).toFloat())

        if(weatherResponse.weather.get(0).icon == "01d")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_01d)
        }
        else if(weatherResponse.weather.get(0).icon == "01n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_01n)
        }
        else if(weatherResponse.weather.get(0).icon == "02d")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_02d)
        }
        else if(weatherResponse.weather.get(0).icon == "02n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_02n)
        }
        else if(weatherResponse.weather.get(0).icon == "03d" || weatherResponse.weather.get(0).icon == "03n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_03)
        }
        else if(weatherResponse.weather.get(0).icon == "04d" || weatherResponse.weather.get(0).icon == "04n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_04)
        }
        else if(weatherResponse.weather.get(0).icon == "09d" || weatherResponse.weather.get(0).icon == "09n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_09)
        }
        else if(weatherResponse.weather.get(0).icon == "10n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_10n)
        }
        else if(weatherResponse.weather.get(0).icon == "10d")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_10d)
        }
        else if(weatherResponse.weather.get(0).icon == "11d" || weatherResponse.weather.get(0).icon == "11n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_11)
        }
        else if(weatherResponse.weather.get(0).icon == "13d" || weatherResponse.weather.get(0).icon == "13n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_13)
        }
        else if(weatherResponse.weather.get(0).icon == "50d" || weatherResponse.weather.get(0).icon == "50n")
        {
            binding.conditionIv.setImageResource(R.drawable.ic_50)
        }



    }

    private fun displayUpdateFailed(){
        minute++
        binding.connectionTv.text = "Updated ${minute} minute ago"
        dialog.dismiss()
    }
    val String.capitalizeWords
        get() = this.toLowerCase().split(" ").joinToString(" ") { it.capitalize() }
}